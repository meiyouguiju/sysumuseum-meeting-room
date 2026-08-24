# 本地开发 Seed

`seed-local.sql` 仅限本地开发环境使用，禁止用于生产环境；它不由 Flyway 自动执行。

在已完成 V1 Schema 迁移的本地 `museum_meeting_room` 数据库中，以有权限的本地开发账号手工执行：

```powershell
Get-Content -Raw .\dev\seed-local.sql |
    mysql -u dev -p museum_meeting_room
```

如果 `mysql` 不在 `PATH` 中，请替换为本机 MySQL 客户端的完整路径。

脚本可重复执行，不清空任何表，也不插入预约、预约时间槽或审计日志。

local profile 默认的 `LOCAL_USER_ID=1` 对应 Seed 的 ACTIVE 普通用户“前端测试用户A”。如需以其他测试用户启动后端，可在启动前显式设置 `LOCAL_USER_ID` 为对应的用户 ID。
