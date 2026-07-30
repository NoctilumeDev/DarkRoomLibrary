# 本地全链路测试网络边界

本文是本项目本地高连接测试的唯一网络说明。它记录 Windows 宿主机上的已知容量边界、取证方法和安全处置顺序，不替代 [`deployment.md`](deployment.md) 的部署配置，也不把宿主机故障归因于业务代码。

## 1. 已确认的现象与证据

项目执行三实例并发、浏览器路由巡检和完整中间件验证期间，曾出现以下同一时间窗口内的现象：

- Codex 执行本机命令或采集进程状态时，桌面左上角短暂显示多个命令行小窗口；
- 系统代理仍在监听，但新的代理请求开始超时；
- Codex 或浏览器的远端连接超时、掉线；
- 已建立的本地服务、CPU 和带宽看起来仍然正常。

Windows System 日志中的 `Tcpip` 事件 `4231` 明确记录了 TCP 动态端口分配失败。这证明故障窗口内宿主机无法为部分新建连接分配临时端口，足以解释代理无法建立新上游连接以及依赖远端连接的工具掉线。

现场进程捕获确认，可见小窗口由 `powershell.exe`、`pwsh.exe` 与对应的 `conhost.exe` 提供；其中既有 Codex/ChatGPT 桌面端为采集 CPU、内存和进程状态而启动的命令，也有当前任务执行本机检查时启动的命令。Node.js、Vite、Playwright 或 Mock API 可能是 PowerShell 后续启动的任务，但本轮没有证据表明它们直接创建了已观察到的窗口。

因此，小窗口属于 Codex Windows 命令宿主未完全隐藏控制台的可见性问题，不会直接破坏网络。它与 `4231/4266` 动态端口耗尽是两个不同问题；多任务并行或连续命令只能同时放大窗口数量和连接压力，不能据此建立“闪窗导致断网”的因果关系。

连接回收或系统重启后，`TIME_WAIT` 通常不再保留原始进程归属，因此不能根据事后进程列表认定某一个工具独自耗尽了端口。

## 2. 结论边界

该事件属于测试宿主机的连接容量问题，不等于：

- Spring Boot、MySQL、Redis 或 RabbitMQ 已发生业务故障；
- Docker Desktop 修改了代理、路由或网卡；
- 系统代理进程已经退出；
- VPN 带宽不足；
- Linux `nf_conntrack` 表已经耗尽；
- Docker MTU 必然与隧道不匹配。

本项目的完整测试环境是 Windows 与 Docker Desktop。Linux 原生宿主机上的 `nf_conntrack_count`、`iptables` 和 `docker0` MTU 是另一套诊断模型；没有对应采集结果时，不能把它直接当作本机根因。

更合理的触发条件是多组高连接任务叠加：并发脚本创建短连接、浏览器巡检产生大量资源请求、多个 Vite/Playwright 进程重复启动、Docker 健康检查、镜像或依赖下载以及代理自身的新建连接共同消耗宿主机动态端口。

## 3. 故障当场只读取证

发现代理超时、Codex 掉线或新连接普遍失败时，立即停止继续升压，并在重启、禁用网卡或重载代理前执行：

```powershell
$since = (Get-Date).AddMinutes(-30)

Get-WinEvent -FilterHashtable @{
  LogName = 'System'
  StartTime = $since
} -ErrorAction SilentlyContinue |
  Where-Object {
    ($_.ProviderName -eq 'Tcpip' -and $_.Id -eq 4231) -or
    ($_.ProviderName -eq 'Udpip' -and $_.Id -eq 4266)
  } |
  Select-Object TimeCreated, Id, ProviderName, Message

netsh int ipv4 show dynamicport tcp
netsh int ipv6 show dynamicport tcp
netsh int ipv4 show excludedportrange protocol=tcp

$connections = Get-NetTCPConnection -ErrorAction SilentlyContinue
$connections |
  Group-Object State |
  Sort-Object Count -Descending |
  Select-Object Count, Name

$connections |
  Where-Object OwningProcess -gt 0 |
  Group-Object OwningProcess |
  ForEach-Object {
    $processId = [int]$_.Name
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    [pscustomobject]@{
      ProcessId = $processId
      Process = $process.ProcessName
      Connections = $_.Count
    }
  } |
  Sort-Object Connections -Descending |
  Select-Object -First 20
```

随后分别验证宿主机直连、本地服务和 Docker 状态。使用代理时，应另外发起一次真实代理请求；代理端口处于监听状态只能证明进程存在，不能证明它还能建立上游连接。

```powershell
curl.exe -I --connect-timeout 10 https://github.com/
Test-NetConnection 127.0.0.1 -Port 20606
Test-NetConnection 127.0.0.1 -Port 5175
docker compose ps
```

公开故障报告只记录事件编号、连接状态计数、测试阶段和匿名化进程信息，不提交代理节点、内网地址、凭据或完整个人网络配置。

### 3.1 左上角闪窗取证

闪窗发生时，可使用以下只读命令记录 PowerShell 与控制台宿主的 PID、父进程、创建时间和命令行：

```powershell
$processes = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue

$processes |
  Where-Object {
    $_.Name -match '^(powershell|pwsh|conhost)\.exe$'
  } |
  Select-Object ProcessId, ParentProcessId, Name, CreationDate, CommandLine
```

当前用于取证的 PowerShell 也会出现在结果中，这是预期行为。应结合创建时间和父进程链区分：

- Codex/ChatGPT 桌面端启动的状态采集或工具命令；
- 当前任务主动执行的本机检查；
- 项目脚本启动的 Vite、Playwright、Mock API 或 Java 子进程；
- 与当前任务无关的长期系统进程。

仅凭窗口外观不能判断命令内容，也不能把 `conhost.exe` 数量等同于动态端口占用。不得为了消除闪窗而批量结束 `powershell.exe`、`pwsh.exe` 或 `conhost.exe`；这可能中断 Codex、开发工具或其他用户任务。项目自己启动无需交互的后台辅助进程时，应使用隐藏窗口并保存 PID，在清理阶段等待该进程及已知子进程真正退出。

## 4. 本项目测试顺序

本机验证按以下顺序串行执行，不同时启动多套相同服务：

1. 先运行后端测试，再运行前端单测、ESLint 和构建。
2. 确认上一阶段的 Node.js、Vite、Playwright、Java 和临时端口已经退出。
3. 启动一套 Compose 基础环境，确认健康检查通过。
4. 按批次运行三实例一致性测试；客户端复用 HTTP 连接，不无界创建短连接。
5. 并发测试完全结束后，再执行六身份完整业务链路。
6. 最后只启动一套前端和浏览器环境，执行路由、Console、Network 与布局诊断。
7. 每一阶段完成后保存业务结果、数据库不变量和宿主机网络证据，再进入下一阶段。

百级并发用于验证事务和不变量，不是无限升压。出现 `4231`、`4266`、持续 `SYN_SENT` 或代理新建连接失败时，本轮环境已经失去可信度，应停止测试、保存现场并等待连接回收。

阶段切换时可以保留至少 2 秒的安静窗口，用于减少连续启动造成的瞬时连接峰值，但这只是本机测试的防抖值，不是 Windows TCP 契约，也不是 `4231` 的恢复条件。微软的 [TCP/IP 端口耗尽排查说明](https://learn.microsoft.com/en-us/troubleshoot/windows-client/networking/tcp-ip-port-exhaustion-troubleshooting) 指出，连接关闭后仍可能在 `TIME_WAIT` 保留数分钟；固定等待 2 秒不能证明动态端口已经回收。

脚本应优先等待已知进程树退出并轮询监听端口，而不是只调用 `Start-Sleep`。PowerShell 可使用 [`Wait-Process`](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.management/wait-process) 等待指定进程；若启动器会派生子进程，还必须继续核对 Vite、Playwright、Mock API 或 Java 子进程及其监听端口。进入下一阶段前同时要求近期没有新的 `4231/4266`，真实直连与代理门禁均成功。

### 4.1 Codex 多任务与浏览器运行时

另一次中断发生在两个 Codex 任务并行、其中一个任务保留 Vite 与 Mock API、另一个任务控制 GitHub 内置浏览器时。中断后确认 `ChatGPT.exe` 与 `codex.exe` 已重新创建，但当场没有新的 `4231/4266`、Windows 应用崩溃或资源耗尽事件；宿主机共有约 672 条 TCP 记录、382 条 `TIME_WAIT`，仍有约 7 GiB 可用内存。

这组证据只能确认 Codex 桌面进程发生过重启，不能证明 Vite、Mock API、代理或某个项目导致了重启。它与已由事件日志确认的动态端口耗尽是两个不同等级的结论，应记录为“Codex/内置浏览器运行时疑似竞争或远端会话中断”，不能合并成同一个根因。

同一 Codex Desktop 可以保留多个项目任务，但本机执行期间遵守：

- 同一时刻只允许一个任务控制内置浏览器、Chrome 或 Playwright；
- 一个任务执行浏览器自动化、压测或 Compose 编排时，其他任务暂停高连接操作；
- 切换项目前先确认上一任务启动的 Vite、Mock API、Playwright 和临时 Java 进程已经退出；
- 不自动结束无法确认归属的进程，先记录 PID、命令行、监听端口和所属项目；
- Codex 重启但没有 `4231/4266` 时，单独保存应用进程创建时间、浏览器调用阶段和 Windows 事件，不把它记为端口耗尽。

## 5. 安全恢复顺序

1. 停止新的压测、浏览器批量巡检和依赖下载。
2. 保存事件日志、连接状态、主要进程和当前测试批次。
3. 等待短连接自然回收，再复查动态端口和真实直连请求。
4. 直连正常但代理失败时，由使用者人工切换健康节点或重载代理核心。
5. 宿主机正常但容器失败时，再重载 WSL/Docker 状态。
6. 只有网关、DNS 或宿主机直连也失败时，才由使用者决定是否重置物理网卡或重启系统。
7. 环境恢复后从独立数据库和明确批次重新执行受影响的测试，不能把中断前后的结果拼接成一次成功。

项目脚本必须失败关闭并报告证据，不得自动重启代理、网卡、Docker、路由或有状态中间件。

## 6. 禁止事项

没有现场证据时，不执行以下操作：

- 修改 Windows 动态端口范围、`TIME_WAIT` 注册表参数或防火墙规则；
- 调整网卡跃点、默认路由、DNS、MTU 或虚拟交换机；
- 删除 Docker 数据、网络、卷或 WSL 配置；
- 为绕过问题而并行增加更多后端、浏览器或压测进程；
- 为消除闪窗而批量结束 PowerShell 或控制台宿主进程；
- 把一次网络中断描述成应用吞吐上限或业务一致性失败；
- 根据事后 `TIME_WAIT` 数量反推并指认原始占用进程。

若未来迁移到 Linux 原生服务器，再单独采集 `conntrack`、NAT、套接字、文件描述符和 MTU 证据；这些生产诊断不能替代本文的 Windows 本地测试结论。

## 7. 验证结果解释

宿主机网络容量与应用正确性必须分别报告：

- 应用证据：成功/拒绝数量、HTTP 状态、P95、数据库不变量、死锁、队列与缓存状态。
- 宿主机证据：事件 `4231/4266`、连接状态、动态端口范围、主要进程、命令宿主父子关系和代理/直连结果。

只有测试在同一批次内完整结束，且应用不变量与宿主机网络门禁同时通过，才可以记为有效结果。当前公开验证数字来自完整成功批次；历史网络中断不计入业务成功，也不构成项目缺陷。
