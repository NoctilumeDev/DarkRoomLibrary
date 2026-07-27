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

公开地址为 <https://noctilumedev.github.io/DarkRoomLibrary/>。`main` 分支更新后，`.github/workflows/pages.yml` 使用 `npm run build:demo` 自动部署；该构建启用 Hash 路由和 `/DarkRoomLibrary/` 资源基路径。

浏览器演示通过 Axios adapter 提供会话级数据，只用于低门槛查看界面、六个固定身份和关键业务状态变化。状态保存在 `sessionStorage`，不会上传到 GitHub，也不会持久化到服务器。上传下载、邮件、注册、注销和导出均明确禁用。

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
```

生产数据库应使用权限受限的应用账号，不应沿用 Compose 的 MySQL root 账号。

## 5. 多实例文件存储边界

多后端实例共享 MySQL，只能保证图书、借阅、预约、采购、文件元数据和租约状态共享。当前上传内容写入 `FILE_UPLOAD_DIR`，数据库不会自动复制图片和附件字节。

因此，多实例部署必须满足以下方案之一：

1. 所有实例挂载同一个可靠共享盘，并使用完全一致的 `FILE_UPLOAD_DIR`。
2. 增加对象存储适配层，把文件内容保存到 S3 兼容存储，数据库继续保存业务引用和治理状态。

当前 Compose 只启动一个后端实例，并把上传目录挂载到持久化命名卷，因此不存在实例间文件不可见问题。不能直接把后端副本数调大后仍给每个容器独立本地卷。

对象存储属于明确的未来演进项，不作为当前项目的强制依赖。引入时应保留现有文件生命周期、引用绑定、权限校验、删除租约和 HTML 强制下载规则。

## 6. 数据库版本演进

当前公开版本面向全新安装，继续保留一份可直接执行的 `init-dark-room-library.sql`，让使用者复制或导入一次即可完成 19 张业务表、邮箱配额技术控制表和演示数据初始化。

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
