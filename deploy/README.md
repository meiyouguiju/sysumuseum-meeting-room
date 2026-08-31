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

本次试运行使用 `prod` profile：后端启用姓名加四位 PIN 的真实登录，并同时启用文件日志。NAS 环境不得设置 `LOCAL_USER_ID`，该变量只保留给开发电脑的 `local` profile 使用。所有访问者都必须使用其预先配置的姓名和 PIN 登录。

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

本部署首先面向局域网试运行；浏览器使用姓名加四位 PIN 登录。Cloudflare 的外网访问和正式 SSO 将按后续部署计划单独配置。
