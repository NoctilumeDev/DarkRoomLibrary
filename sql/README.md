# SQL 初始化说明

本目录只保留新环境部署需要的数据库入口脚本。

## 新环境初始化

新用户或空数据库部署时，只需要执行：

```bash
mysql --default-character-set=utf8mb4 -u root -p < sql/init-dark-room-library.sql
```

该脚本会创建 `dark_room_library` 数据库、最终版表结构、索引、外键约束、默认分类、默认书架和默认超级管理员。

默认超级管理员：

- 账号：`drl_root_aurora`
- 密码：由本地验收环境变量或部署者在数据库初始化后设置

公开仓库不记录明文密码。首次登录后请立即修改本地演示账号密码。

## 可选演示数据

本地展示或课程验收时，可以继续执行：

```bash
mysql --default-character-set=utf8mb4 -u root -p < sql/demo-data.sql
```

该脚本添加项目专用的虚构书目、书评、回复、留言、公告、采购物流记录和四类演示账号。
E2E 脚本从环境变量读取密码：

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 馆务协调员 | `drl_keeper_qingwu` | `E2E_COORDINATOR_PASSWORD` |
| 读者 | `drl_reader_yandeng` | `E2E_READER_PASSWORD` |
| 采购员 | `drl_buyer_xinglan` | `E2E_PURCHASER_PASSWORD` |
| 物流员 | `drl_logistics_chenxiang` | `E2E_LOGISTICS_PASSWORD` |

面向公网部署前必须删除演示账号，或逐个修改账号和密码。公开仓库不包含这些环境变量的值。

## 独立 E2E 数据库

项目根目录提供了受限的 E2E 初始化脚本。`-Reset` 只允许删除名称以
`_e2e` 结尾的数据库：

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your-mysql-password"
pwsh -File .\scripts\setup-e2e-database.ps1 -Reset
```

默认创建 `dark_room_library_e2e`，同时加载演示数据；后端测试启动时通过 `DB_URL` 指向该库。

后端启动后，在项目根目录执行 `pwsh -File .\scripts\seed-demo-media.ps1`，
即可通过真实接口上传并绑定演示头像与图书封面。

## 注意事项

- 不需要再执行其他增量 SQL。
- 如果是已有旧库，请先备份，再按实际字段差异手动迁移。
- 脚本使用 `utf8mb4`，建议执行时显式添加 `--default-character-set=utf8mb4`，避免 Windows 终端环境下中文种子数据乱码。
