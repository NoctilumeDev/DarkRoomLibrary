# 数据库入口

全新安装只执行 [init-dark-room-library.sql](init-dark-room-library.sql)，其中已经包含完整表结构与演示数据。
Docker Compose 仅在空 MySQL 数据卷首次启动时导入同一文件；重启与重建镜像不会覆盖已有库。

已有库先备份，按 [部署指南](../docs/deployment.md#22-初始化与数据卷) 核对版本和连接目标，
再选择 `deploy/mysql/upgrades/` 下的一次性升级。升级脚本不能与初始化快照拼接，也不能重复执行。
