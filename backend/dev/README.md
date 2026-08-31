# 本地开发与试运行维护

`seed-local.sql` 只用于本地开发，不会由 Flyway 自动执行，也禁止用于生产环境。

## 试运行用户与 PIN 维护

构建后，在后端目录以一次性维护模式执行。PIN 以环境变量传入，避免写入命令历史；所有 PIN 必须为四位数字字符串，允许前导零。

```powershell
$env:USER_MAINTENANCE_PIN = '0376'
java -jar target/museum-meeting-room-backend-0.0.1-SNAPSHOT.jar `
  --spring.main.web-application-type=none `
  --spring.profiles.active=pin-maintenance `
  --user-maintenance.enabled=true `
  --user-maintenance.action=create `
  --user-maintenance.name='方原' `
  --user-maintenance.pin=$env:USER_MAINTENANCE_PIN `
  --user-maintenance.role-code=USER
```

修改 PIN 使用 `action=set-pin --user-maintenance.user-id=用户ID`；停用使用 `action=disable --user-maintenance.user-id=用户ID`。启用时使用 `action=enable --user-maintenance.user-id=用户ID`，并重新提供该用户现有的四位 PIN，以便在启用前校验不会与同名 ACTIVE 用户冲突。维护工具使用 BCrypt 写入 `sys_user.pin_hash`，不会输出或保存明文 PIN；同名 ACTIVE 用户若 PIN 相同会被拒绝。
