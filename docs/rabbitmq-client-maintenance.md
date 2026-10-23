# RabbitMQ 客户端专项维护

本轮是依赖安全维护，不是重新发行、生产安全认证或业务改造。起点为
`main@840c45c60a1922c7e02e2f4b4ec65633a9ab63aa`；原有发行报告、
`.github/verification-baseline.json` 和发布资产不追溯改写。

## 变更与证据边界

原依赖链为 Spring Boot `3.5.16` → Spring AMQP `3.2.12` →
`com.rabbitmq:amqp-client:5.25.0`。本次只用 Boot 的
`rabbit-amqp-client.version` 属性覆盖为 `5.37.0`，不修改 Spring 版本、
业务 Java、SQL、生产 Broker 镜像或 MQ 默认关闭的配置。
完整 Maven 解析树比较包含 140 个坐标（含项目根节点），唯一版本差异为上述客户端；
未增加或删除依赖坐标。测试报告目录配置独立于运行时依赖。

`RabbitEventConfig` 的 Jackson 转换及 trusted packages 位于协议解码之后，
不能替代 AMQP 字段长度与递归深度检查。本次证据沿三层分别记录：

1. 上游源码：旧版 `ValueReader` 先按声明长度分配数组，且表/数组递归没有深度上限；
   新版在分配前校验可读长度，并限制字段嵌套深度。
2. 同输入旧新版对照：小输入观察异常类型和深度拒绝，不制造真实 OOM 或栈溢出。
3. Spring AMQP 兼容性：真实 Broker 上使用项目自身配置、发布器与两个监听器；
   业务服务在测试中替换为 mock，不宣称重跑了数据库、邮件或端到端业务验收。

## 2026-10-24 有界复验

环境：Windows、JDK 17.0.12、Maven 3.9.11、RabbitMQ 4.3.2，使用独立测试 vhost
和仅获该 vhost 权限的临时账号。旧新版使用同一组输入，进程时区为 Asia/Tokyo。

| 检查 | 旧客户端 5.25.0 | 候选客户端 5.37.0 | 结论限度 |
| --- | --- | --- | --- |
| LongString/bytes 声明 64 KiB，但没有字段正文，2 项 | 未按新长度规则拒绝，断言失败 | 按规则拒绝 | 结合源码说明分配边界；未耗尽进程内存 |
| 40 层表与表/数组混合嵌套，2 项 | 接受，拒绝断言失败 | 拒绝 | 不外推为所有解析分支都已验证 |
| 正常 4 层表与混合数组，2 项 | 通过 | 通过 | 正常字段控制组 |
| Spring 连接接收异常 connection.start 属性，1 项 | 未产生所要求的畸形字段异常 | 保留畸形字段异常原因 | 观察认证前协议入口，不是公网攻击实验 |
| 真实 Spring AMQP 生命周期，7 项 | 通过 | 通过 | 所列场景兼容，不等于任意部署安全 |

解析套件旧版结果为 `7 tests / 5 failures / 0 errors / 0 skipped`，新版为 7 项通过。
旧版失败是保留的负控，不能删除或改写成历史 PASS。

真实生命周期套件覆盖：

- `RabbitDomainEventPublisher` 的 correlated confirm、不可路由 Return 和冷却后恢复；
- 两个 `@RabbitListener` 的 Integer 转换、调用和消费确认；
- JSON 转换失败与处理器异常，经实际 `RepublishMessageRecoverer` 重发布到死信交换机，
  保留原正文及异常头，停止消费者后检查原队列没有重新入队的消息；
- 客户端连接重置后的监听器重建与后续消息处理；
- MQ 关闭时监听器不启动、发布不建连接；不可达回环端口触发发布失败及冷却。

主队列保持无 `x-dead-letter-exchange` 参数；这里验证的是应用重发布恢复，
不是 Broker 原生 DLX。连接重置和拒绝连接分别是有界故障替身，未执行
Broker 集群切换、服务停机、磁盘故障或长时间分区试验。
不承诺失败恢复器的再次发布在任意故障下都不会丢失消息。

测试编写期间的首次编译失败（缺少 List 导入）和两次关闭模式统计断言失败均保留。
后两次是测试误用了缓存统计字段/未建连接端口值，不是业务修复；最终同时核对
CHANNEL 缓存模式、端口 0 及连接监听事件。没有为通过测试修改生产行为。
复核另发现 Surefire 不识别所猜测的报告目录系统属性；已在插件配置中显式绑定
`test.report.directory`，分开保存旧新版报告，避免覆盖。

## 公告候选与剩余未知

2026-10-24 OSV 对 **这个 Maven 包** 的查询，旧版匹配 10 条公告，新版返回无匹配。
这不是整个 Maven 依赖树的全量安全扫描，也不证明公告所列全部机制在本项目可达。
以下记录的所有者为项目维护者；后续动作限于本表，不隐含接受部署风险。

| 公告 | 本轮状态与依据 | 后续触发条件 |
| --- | --- | --- |
| [CVE-2026-69219](https://github.com/rabbitmq/rabbitmq-java-client/security/advisories/GHSA-68mj-5wr7-6fgg) | 长度分配机制有旧新版源码及有界反例；候选拒绝所测异常字段 | 修改客户端、连接工厂或解码路径时重跑 |
| [CVE-2026-69220](https://github.com/rabbitmq/rabbitmq-java-client/security/advisories/GHSA-93j5-89vc-pph4) | 嵌套机制有旧新版源码及表/数组对照；候选拒绝所测深度 | 同上；新增字段形式独立核查 |
| [CVE-2026-106121](https://github.com/rabbitmq/rabbitmq-java-client/security/advisories/GHSA-cqgh-8p3p-mx4m) | 当前源码未使用默认 JSON-RPC mapper；不能包装为已修复的业务漏洞 | 引入 Rabbit JSON-RPC 时重新审查 |
| [CVE-2026-63337](https://github.com/rabbitmq/rabbitmq-java-client/security/advisories/GHSA-6g32-pxv4-2wfj) | 当前源码未使用 Rabbit JSON-RPC；未执行类加载反例 | 引入该 RPC 路径时重新审查 |
| [CVE-2026-106123](https://github.com/rabbitmq/rabbitmq-java-client/security/advisories/GHSA-h6w7-qmcm-q6xr) | 当前源码未调用 ConnectionFactoryConfigurator.load；未执行凭据泄露反例 | 更换配置加载方式时核查框架及调用链 |
| [CVE-2026-63336](https://github.com/rabbitmq/rabbitmq-java-client/security/advisories/GHSA-5m9f-rphj-c435) | 旧版版本命中保留；本轮未验证 TLS/证书部署，适用性未知 | 启用或更改 TLS 时独立验证 |
| [CVE-2026-61634](https://github.com/rabbitmq/rabbitmq-java-client/security/advisories/GHSA-5xwg-cfvj-gff5) | 版本命中保留，未单独验证协商 frame_max | 后续帧限制专项，不用字段测试替代 |
| [CVE-2026-75516](https://github.com/rabbitmq/rabbitmq-java-client/security/advisories/GHSA-jh4v-gfqj-7rhx) | 版本命中保留，未单独验证零值协商路径 | 同上；不搬用其他项目旧版的反证 |
| [CVE-2026-63335](https://github.com/rabbitmq/rabbitmq-java-client/security/advisories/GHSA-qx7j-jv8m-fppr) | 版本命中保留，未执行异常正文组装反例 | 组装路径变更或后续专项时处理 |
| [CVE-2026-106122](https://github.com/rabbitmq/rabbitmq-java-client/security/advisories/GHSA-7822-rcf6-97fx) | 版本命中保留，未执行畸形 UTF-8/RPC 消费者反例 | 使用 RPC 或变更属性解码时独立核查 |

“候选版本没有匹配公告”仅说明版本查询结果变化，不把以上未验证项逐条宣称为修复。
Broker 是否受控、网络是否受攻击、非默认配置及实际部署暴露面仍需各部署方核实。
`frame_max`、整条消息正文限制、字段解码限制是不同边界，证据不能互换。

## 复跑与持续门禁

在 `backend/dark-room-library-api` 执行（PowerShell 请给含点号的 `-D` 参数加引号）：

```sh
mvn -B -ntp -Duser.timezone=Asia/Tokyo clean verify
node ../../scripts/check-backend-coverage.mjs
mvn -B -ntp -Duser.timezone=Asia/Tokyo -Dtest=RabbitClientBoundaryTest test
mvn -B -ntp -Duser.timezone=Asia/Tokyo -Drabbit-amqp-client.version=5.25.0 -Ddarkroom.expected-client-version=5.25.0 -Dtest=RabbitClientBoundaryTest test
```

最后一条预期退出非零，必须核对是上述 5 项断言失败而非编译、下载或环境失败。
CI 单独保存旧版报告并校验失败数量、用例身份及失败原因，不用 `continue-on-error` 放过任意错误。
两套测试在开始前断言实际加载 JAR 的 Implementation-Version；不以 POM 声明替代加载事实。
本地还对 CI 内同一段验证器作内存负控：缺少测试、执行错误、错误失败原因、
JVM 错误类型、错误加载版本、错误正常对照与缺失报告均被拒绝。

真实 Broker 测试须显式设置 `DARKROOM_MQ_TEST=1`、
`DARKROOM_MQ_TEST_VHOST=darkroom-test-...`、`DARKROOM_MQ_TEST_USER`、
`DARKROOM_MQ_TEST_PASSWORD`；可选 `DARKROOM_MQ_TEST_PORT`，默认 5672。
仅连接本机；使用新建专用测试 vhost，不得指定业务 vhost。

```sh
mvn -B -ntp -Duser.timezone=Asia/Tokyo -Dtest=RabbitSpringLifecycleIT test
mvn -B -ntp -Duser.timezone=Asia/Tokyo -Drabbit-amqp-client.version=5.25.0 -Ddarkroom.expected-client-version=5.25.0 -Dtest=RabbitSpringLifecycleIT test
```

套件仅创建 UUID 前缀的交换机/队列，结束后清理这些对象；测试账号及 vhost 由运行者
清理，不停用共享 Broker。CI 使用随任务销毁的 RabbitMQ 服务容器。
测试凭据仅为隔离夹具，不是应用或生产凭据。

`Backend tests` 在原覆盖率检查之后运行旧版负控与新版真实 Broker 检查。
新增 `*IT` 不混入原单元覆盖率分母，也不改变原最低门槛。
CI 的 `backend-evidence-<SHA>-<attempt>` 保存 XML、覆盖率与日志 90 天，包括失败尝试；
更长留存需在到期前下载。源码 SHA、报告中的客户端版本与运行号共同定位证据，
合并后的 exact-main CI 另行核对，PR 绿灯不替代主线结果。

本轮不创建 Release、不变更生产 Broker、不重写 DB 补偿或图书业务，不授予新部署资格。
