# 暗室藏书部署指南

本文说明本机运行、可选 Docker Compose、健康检查、反向代理和多实例边界。项目仍是模块化 Spring Boot 单体；容器化只改变交付方式，不改变业务架构。

## 1. 部署方式

| 方式 | 适合场景 | 特点 |
| --- | --- | --- |
| 本机直接运行 | 开发、调试、课程验收 | 最容易观察 Java、Node.js 和数据库日志 |
| Docker Compose | 本地演示、独立测试环境 | 一条命令启动完整依赖，不强制日常开发使用 |
| 独立进程 + 反向代理 | 小型服务器部署 | 前后端可同域，便于 HTTPS、日志和进程治理 |
| GitHub Pages 浏览器演示 | 零成本公开预览 | 仅运行 Vue 和会话数据，不替代后端、数据库与中间件 |

`compose.yaml` 是可选的完整演示环境，不是生产集群模板。项目不会因为增加 Compose 就要求开发者安装 Docker。

### 1.1 GitHub Pages 浏览器演示

公开地址为 <https://noctilumedev.github.io/DarkRoomLibrary/>。`main` 分支更新后，`.github/workflows/pages.yml` 在测试、覆盖率与构建通过后部署；失败时线上继续保留上一次成功的版本。当前分支可在前端目录执行 `npm ci`、`npm run build:demo`、`npm run preview` 体验。Demo 构建启用 Hash 路由和 `/DarkRoomLibrary/` 资源基路径。

浏览器演示通过 Axios adapter 提供会话级数据，只用于低门槛查看界面、六个固定身份和关键业务状态变化，包含图书回收、读者书评/留言回收与恢复。状态保存在 `sessionStorage`，不会上传到 GitHub，也不会持久化到服务器。上传下载、邮件、注册、注销、真实定时过期和导出均明确禁用。

这一路径不运行 Spring Boot、MySQL、Redis 或 RabbitMQ，不能用于证明真实事务、并发一致性、文件治理、健康检查或中间件降级。此类能力必须使用 Compose 或本机完整环境验证。

## 2. Docker Compose

### 2.1 启动

```powershell
Copy-Item .env.compose.example .env
docker compose up --build -d
docker compose ps
```

默认入口：

| 服务 | 地址或端口 |
| --- | --- |
| 前端 | `http://localhost:5175` |
| 后端 | `http://localhost:20606/api/dark-room-library/v1` |
| MySQL | `localhost:3307` |
| Redis | `localhost:6380` |
| RabbitMQ | `localhost:5673` |
| RabbitMQ 管理页 | `http://localhost:15673` |

当前 Compose 使用明确的兼容系列标签：MySQL 8.4、Redis 7.4 Alpine、RabbitMQ 4.2.3 Management Alpine、Eclipse Temurin JRE 17 和 Nginx 1.28 Alpine。后端构建阶段固定 Maven 3.9.9 + Eclipse Temurin 17，减少构建环境漂移。

若当前网络无法稳定访问 Docker Hub，应优先稍后重试官方仓库。确需使用国内镜像缓存时，只把它作为一次性下载通道：拉取同一 `library/*` 官方镜像后，在本机重新标记为 `compose.yaml` 中的标准镜像名。不要把个人镜像加速地址、登录凭据或地域镜像路径提交到项目文件。

容器内部仍使用 MySQL `3306`、Redis `6379` 和 RabbitMQ `5672`。宿主机端口刻意错开常见的本机服务，减少与开发环境冲突。

所有宿主机端口默认只绑定 `127.0.0.1`，不会直接暴露给局域网。服务器部署时应让外部反向代理访问前端，MySQL、Redis、RabbitMQ 和后端端口继续保持本机或私有网络可见，不应为了省事全部改成公网监听。

### 2.2 初始化与数据卷

MySQL 数据卷首次创建时，会自动执行唯一入口 `sql/init-dark-room-library.sql`。以后重新构建镜像不会重复覆盖已有数据。

`sql/` 只提供全新安装入口；已有库的一次性增量脚本单独放在 `deploy/mysql/upgrades/`，不会被 Compose 自动执行，也不能与初始化 SQL 拼接导入。先核对连接的主机、端口、库名及现有列，再备份并选择需要的升级。

从 `v1.2.0` 保留数据卷升级到 `v1.2.1` 时，先备份数据库，再确认 `user` 表是否已有 `auth_version`。旧表没有该列时只需执行一次：

```sql
ALTER TABLE `user`
  ADD COLUMN `auth_version` int NOT NULL DEFAULT 1
  COMMENT 'authentication state version'
  AFTER `user_role`;
```

全新安装不需要执行该命令，初始化 SQL 已直接包含该列。`auth_version` 只用于密码、角色和账号状态变化后让旧 JWT 在三个实例上立即失效，不保存令牌或敏感信息。

已有数据卷升级到包含回收笺生命周期的版本时，必须先备份数据库，再**只执行一次**：

```powershell
cmd /c "mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3307 -u root -p dark_room_library < deploy\mysql\upgrades\upgrade-recycle-bin-lifecycle.sql"
```

该脚本为 `book`、`book_review` 与 `message_board` 增加恢复期限、过期时间和治理状态。历史上已经软删除、但没有期限的图书会从迁移执行时起获得一次 30 天恢复窗口。脚本不是可重复迁移；执行前应核对目标库，并在执行后检查三张表的新列和索引。全新数据卷不执行此脚本，因为初始化 SQL 已包含最终结构。

以上连接参数对应默认 Compose 宿主端口。本机 MySQL 默认使用 `3306`；自定义 `DRL_MYSQL_PORT` 时使用实际映射值。执行前可用相同连接参数运行 `SELECT @@hostname, @@port, DATABASE();` 确认目标；`@@port` 返回的是服务器内部端口，Compose 映射仍使用宿主 `3307`。SQL 会话时区及自定义恢复期限按脚本开头说明配置。

普通停止：

```powershell
docker compose down
```

只有明确不再需要当前数据库、上传文件和中间件数据时，才使用：

```powershell
docker compose down -v
```

`-v` 会删除 Compose 管理的全部项目数据卷，下次启动会重新执行初始化 SQL。

### 2.3 本地凭据

Compose 文件提供的默认值只用于降低本地演示门槛。准备公开部署前，必须在未提交的 `.env` 中替换：

- `DRL_MYSQL_ROOT_PASSWORD`
- `DRL_RABBITMQ_PASSWORD`
- `DRL_JWT_SECRET`
- 邮箱账号和 SMTP 授权码

仓库只跟踪 `.env.compose.example`，真实 `.env` 已被 Git 忽略。

需要把 Compose 用作正式部署基线时，还应设置
`DRL_SPRING_PROFILES_ACTIVE=prod`。生产 Profile 会拒绝空密码、仓库内已知演示密码、
长度不足 32 个 UTF-8 字节的 JWT 密钥，以及启用 RabbitMQ 时的已知默认密码或空告警 Webhook；
配置错误会在应用接收流量前直接终止启动。

## 3. 健康检查

后端提供三层探针：

| 路径 | 访问控制 | 返回内容 |
| --- | --- | --- |
| `/health/live` | 无需登录 | 进程是否响应 |
| `/health/ready` | 无需登录 | 总体 `UP`、`DEGRADED` 或 `DOWN` |
| `/health/details` | 仅超级管理员 | 数据库、文件目录、Redis、RabbitMQ 分项状态 |

完整前缀为 `/api/dark-room-library/v1`。公开探针只返回状态和检查时间，不返回主机名、端口、凭据或异常堆栈。

就绪判定遵循项目原有降级边界：

- MySQL 或文件目录不可用：`DOWN`，HTTP `503`。
- Redis 或 RabbitMQ 已启用但不可用：`DEGRADED`，核心业务仍可接收流量。
- 未启用的 Redis 或 RabbitMQ：分项显示 `DISABLED`，不影响就绪。

Compose 使用 `/health/ready` 判断后端何时可以接收前端流量。

## 4. 独立服务器部署

推荐让 Nginx 或 Caddy 统一提供 HTTPS，并把 `/api/` 转发到后端 `20606`。前端生产构建保持相对 API 地址：

```text
VITE_API_BASE_URL=/api/dark-room-library/v1
```

生产后端至少需要：

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/dark_room_library?characterEncoding=UTF-8&useSSL=false&serverTimezone=GMT%2B8"
$env:DB_USERNAME="book_app"
$env:DB_PASSWORD="replace-me"
$env:JWT_SECRET="at-least-32-random-bytes"
$env:CORS_ORIGINS="https://library.example.com"
$env:FILE_UPLOAD_DIR="D:\DarkRoomLibrary\upload"
$env:RECYCLE_BIN_RETENTION_DAYS="30"
$env:RECYCLE_BIN_CLEANUP_CRON="0 15 3 * * ?"
$env:RECYCLE_BIN_CLEANUP_BATCH_SIZE="100"
$env:APP_TIME_ZONE="Asia/Shanghai"
```

生产数据库应使用权限受限的应用账号，不应沿用 Compose 的 MySQL root 账号。

生产前端的 Nginx 会统一返回 CSP、`Referrer-Policy`、`Permissions-Policy`、
`X-Content-Type-Options` 和 `X-Frame-Options`。应用入口同时保留等价的 CSP Meta
作为静态托管兜底；正式反向代理不应覆盖或放宽这些策略。后端为每个响应返回
`X-Request-ID` 并写入同名日志上下文：格式安全且不超过 64 字符的调用方 ID 会被保留，
非法、超长或空值会替换为服务端 UUID。跨域读取该响应头已显式加入 CORS 暴露列表。

后端默认忽略 `X-Forwarded-For` 和 `X-Real-IP`。只有确认后端入口前存在会清理或追加代理链的反向代理时，才能同时设置：

```powershell
$env:TRUST_FORWARDED_HEADERS="true"
$env:TRUSTED_PROXY_CIDRS="127.0.0.1/32,172.30.0.5/32"
```

白名单应填写实际直连代理的精确地址或最小 CIDR，不能为了省事信任全部私有网段。应用只在直连地址命中白名单时读取转发头，并从代理链右侧向左选择第一个非受信地址；开启转发头但白名单为空会直接拒绝启动。限流、登录失败锁定和操作审计使用同一解析结果。

公开文件入口同时受全局 IP 接入限流和独立的公开文件 IP 限流保护。生产 Profile
及 Compose 的默认上限分别为每分钟 `600` 与 `300` 次，匿名和已认证业务预算分别为
每分钟 `30` 与 `300` 次；可通过对应的 `RATE_LIMIT_*` 环境变量调整。临时上传文件只有图片允许匿名预览；
视频和文档在绑定到允许公开访问的业务对象前，仅上传者或具备相应权限的用户可以下载。

生产 Profile 及 Compose 中，登录、注册/重置、验证码和验证码题分别使用容量为
`20/12/6/60` 的令牌桶，完整补充周期分别为 `1/10/10/1` 分钟。容量可通过
`RATE_LIMIT_LOGIN_CAPACITY`、`RATE_LIMIT_ACCOUNT_CAPACITY`、
`RATE_LIMIT_VERIFICATION_CAPACITY` 与 `RATE_LIMIT_CAPTCHA_CAPACITY` 调整。
Redis 可用时 Lua 脚本在多个实例间原子消费；Redis 故障时回退到各实例本地令牌桶，
因此降级期间总预算会随实例数增加。

`verification-code.daily-max-per-email`（默认 `10`）是按规范化邮箱统计的每日**发送尝试预算**，
不是成功送达数量。邮件配置缺失或 SMTP 投递失败也会占用一次当日预算；失败路径会删除未送达的验证码
并释放 60 秒重发槽，但不会退还每日尝试次数。这个边界用于抵抗反复触发外部邮件投递的滥用，
运维排障时不得把“可立即重试”误解为“当日配额已回滚”。

通知达到最大重试次数后会保留数据库终止状态。外部运维告警通过
`NOTIFICATION_ALERT_WEBHOOK_URL` 和可选的 `NOTIFICATION_ALERT_WEBHOOK_TOKEN` 配置；
Webhook 只包含任务 ID、重试次数、主题、脱敏收件地址和截断后的错误，不发送邮件正文。
RabbitMQ 消费异常不会无限重新入队，而是分别进入
`dark.room.library.notification-task.dead` 和 `dark.room.library.book-returned.dead`。
定时监控达到阈值后发送脱敏积压事件，Redis 用于跨实例冷却窗口去重，Redis 不可用时
退回本实例抑制。阈值、首次检查、轮询间隔和冷却时间可通过
`NOTIFICATION_ALERT_DEAD_LETTER_*` 环境变量调整。生产 Profile 启用 RabbitMQ 时强制要求
告警 Webhook，避免死信只能依赖人工巡检；邮件本身不作为唯一告警通道。

## 5. 多实例文件存储边界

多后端实例共享 MySQL，只能保证图书、借阅、预约、采购、文件元数据和租约状态共享。当前上传内容写入 `FILE_UPLOAD_DIR`，数据库不会自动复制图片和附件字节。

因此，多实例部署必须满足以下方案之一：

1. 所有实例挂载同一个可靠共享盘，并使用完全一致的 `FILE_UPLOAD_DIR`。
2. 增加对象存储适配层，把文件内容保存到 S3 兼容存储，数据库继续保存业务引用和治理状态。

当前 Compose 只启动一个后端实例，并把上传目录挂载到持久化命名卷，因此不存在实例间文件不可见问题。不能直接把后端副本数调大后仍给每个容器独立本地卷。

对象存储属于明确的未来演进项，不作为当前项目的强制依赖。引入时应保留现有文件生命周期、引用绑定、权限校验、删除租约和 HTML 强制下载规则。

## 6. 数据库版本演进

仓库继续保留一份可直接执行的 `sql/init-dark-room-library.sql`，让首次使用者复制或导入一次即可完成 24 张物理表（23 张业务与派生表及 1 张邮箱配额技术控制表）和演示数据初始化。已有长期数据卷则按本指南第 2.2 节执行增量升级：`v1.2.1` 的 `auth_version` 单列升级，以及 `deploy/mysql/upgrades/upgrade-recycle-bin-lifecycle.sql`。两者都必须在数据库备份后按需执行一次，不能把全新安装快照当成已有库的覆盖脚本。

回收策略默认保留 30 天，每天 03:15 分批撤销过期恢复资格；分别由 `RECYCLE_BIN_RETENTION_DAYS`、`RECYCLE_BIN_CLEANUP_CRON` 和 `RECYCLE_BIN_CLEANUP_BATCH_SIZE` 配置。业务墙上时间由 `APP_TIME_ZONE` 统一解释，默认 `Asia/Shanghai`；Compose 通过同一个 `DRL_APP_TIME_ZONE` 同时配置 MySQL、RabbitMQ、后端运行环境、后端应用时钟与前端构建，不能只改其中一层。前后端展示和回收截止判断都使用该配置，不依赖浏览器、宿主 JVM 或容器碰巧采用的本地时区。

数据库现有时间列使用 MySQL `DATETIME` 与 Java `LocalDateTime`，保存的是应用时区下的墙上时间，而不是自带偏移量的绝对瞬间。回收生命周期因此不直接使用数据库 `CURRENT_TIMESTAMP` 判断恢复资格，而由后端注入的应用 `Clock` 产生同一次请求的判断时点。若独立部署时修改应用时区，必须同时迁移既有时间数据或保持原时区；仅改配置会改变旧墙上时间的解释，不属于无损切换。

多个实例可以同时触发清理，但留言过期会在行锁与条件更新成功后才释放附件引用，旧清理者不能覆盖已经恢复的新状态。期限调整只影响调整后新进入回收站的内容，不追溯改写现有记录已经保存的 `restore_deadline`。

暂不强制引入 Flyway，原因是当前没有多版本生产数据库需要滚动升级。出现以下条件时再引入更合理：

- 已经存在不能清空的长期运行数据库。
- 多个发布版本需要按顺序升级表结构。
- CI/CD 需要自动校验迁移历史和回滚策略。

届时应保留“全新安装快照”和“增量迁移脚本”两个视角，不能把大量零散 SQL 再暴露给首次使用者。

## 7. 发布前检查

1. 替换演示密码、JWT 密钥、数据库密码和邮件授权码。
2. 确认 CORS 只允许真实前端域名。
3. 使用 HTTPS，并限制 MySQL、Redis、RabbitMQ 管理端口的公网访问。
4. 检查 `/health/ready`，再用超级管理员检查 `/health/details`。
5. 完成数据库备份和上传目录备份。
6. 按 `docs/manual-acceptance-checklist.md` 验收六个固定身份。
