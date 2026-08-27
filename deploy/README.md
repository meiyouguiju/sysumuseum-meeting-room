# Synology Container Manager 部署

本目录用于在 DSM 7.2.2 的 Container Manager 上以 Docker Compose 部署局域网试运行版本。它启动 MySQL、Spring Boot 后端和 Nginx/Vue 前端；浏览器只访问 Nginx。

## 在开发电脑构建成品

群晖只运行 Java、Nginx 和 MySQL，不执行 Maven 或 npm 构建。先在开发电脑完成构建：

```powershell
cd backend
.\mvnw.cmd clean package -DskipTests

cd ..\frontend
npm run build
```

将后端 `target` 中生成的可执行 jar 复制为：

```text
deploy/artifacts/backend/app.jar
```

将 `frontend/dist` 整个目录复制为：

```text
deploy/artifacts/frontend/dist
```

`deploy/artifacts/` 仅保存本地构建成品，已被 Git 忽略，不应提交。

## 准备群晖目录与环境变量

将 `deploy/`、`data/` 和 `logs/` 上传或同步到群晖，例如：

```text
meeting-room/
├── deploy/
│   └── artifacts/
├── data/mysql/
└── logs/backend/
```

进入 `meeting-room/deploy`，复制环境变量模板并填写仅保存在群晖上的真实密码：

```sh
cp .env.example .env
```

`.env` 不应提交到 Git。不要将密码写入 Compose 文件或 Dockerfile。

本次试运行在 SSO 接入前使用 `local,prod` profile：`local` 提供临时测试身份，`prod` 同时启用文件日志。`LOCAL_USER_ID=3` 是整个后端进程共享的身份，因此当前阶段所有访问者都会被视为同一个用户。正式 SSO 上线前必须移除 `local` profile 和 `LOCAL_USER_ID`。

## Container Manager 启动

在 Container Manager 中选择“项目”→“新增”，选择 `meeting-room/deploy/compose.yaml`，再启动项目。首次启动由 Spring Boot Flyway 创建或校验数据库结构。

局域网访问地址为：

```text
http://NAS_IP:8088
```

MySQL 与后端不映射宿主机端口；仅 Nginx 暴露 8088。

## 日志、停止与更新

Container Manager 可查看所有容器日志。后端持久化日志位于 `meeting-room/logs/backend`；生产 profile 会滚动压缩历史日志。首次部署前必须确保该目录允许 backend 容器内的 `app` 用户写入；若启动日志出现 `Permission denied /app/logs`，请先检查 Synology 共享文件夹和目录权限。不要通过 Dockerfile 写死 Synology UID/GID 或使用 chmod 777。

停止项目可在 Container Manager 的项目页面执行停止。更新代码后，在开发电脑重新构建并同步 `deploy/artifacts/` 到 NAS，再在项目页面重新构建并启动；MySQL 数据和后端日志保留在 `data/mysql` 与 `logs/backend` 中。

本部署仅面向局域网试运行。当前身份为 `LOCAL_USER_ID=3`；Cloudflare、SSO 和多用户临时身份将于后续阶段单独实现。
