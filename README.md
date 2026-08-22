# 中山大学校史馆会议室预约系统

当前仓库包含后端工程骨架和首份 Flyway 数据库迁移。尚未实现预约 REST API、登录认证、Spring Security 或前端。

## 前置条件

- JDK 21
- Maven Wrapper（仓库已包含）；如使用本机 Maven，要求 Maven 3.9+
- MySQL 8 服务已启动

## 创建本地数据库

应用不会自动创建数据库。先使用有建库权限的 MySQL 账号执行：

```sql
CREATE DATABASE museum_meeting_room
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
```

然后为本地开发账号授予该数据库权限。不要把真实密码写入仓库或 `application.yml`。

## 配置环境变量

PowerShell 示例：

```powershell
$env:DB_HOST = 'localhost'
$env:DB_PORT = '3306'
$env:DB_NAME = 'museum_meeting_room'
$env:DB_USERNAME = 'dev'
$env:DB_PASSWORD = '<你的本地密码>'
```

默认值仅适用于本地：`localhost:3306`、数据库 `museum_meeting_room`、用户名 `dev`。密码没有默认值。

JDBC URL 使用 utf8mb4、`connectionTimeZone=Asia/Shanghai`，并通过连接初始化 SQL 固定 MySQL 会话为 `+08:00`，避免预约业务 `DATETIME` 出现 UTC/上海时区混乱。

## 启动与验证

在 `backend` 目录执行。推荐使用仓库内的 Maven Wrapper：

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

也可以用本机 Maven 执行等价的 `mvn test` 与 `mvn spring-boot:run -Dspring-boot.run.profiles=local`。

首次启动时 Flyway 自动执行 `classpath:db/migration/V1__init_schema.sql`。应用启动验证器会检查六张业务表和 `flyway_schema_history` 是否存在；缺失时应用启动失败。

可使用 MySQL 查询验证：

```sql
USE museum_meeting_room;
SHOW TABLES;
SELECT installed_rank, version, description, success
FROM flyway_schema_history;
```

应看到六张业务表：`sys_user`、`meeting_room`、`booking`、`booking_slot`、`booking_audit_log`、`idempotency_record`，以及 Flyway 的 `flyway_schema_history`。

应用启动后访问健康检查：

```text
http://localhost:8080/actuator/health
```

预期得到 HTTP 200 和 `{"status":"UP"}`。

## Flyway 规则

`V1__init_schema.sql` 是第一份正式迁移，不应修改已执行版本。后续数据库结构变化必须新增递增版本的迁移脚本。
