# 部署

## 目标

为 AUBB-Server 提供最小可交付的工程化发布基线：

- 本地可通过 Docker Compose 联调 app + 基础设施
- CI 固化 `verify -> image`
- deploy 保持最小版本，通过 GitHub Actions 手动触发，把指定镜像版本部署到远程主机
- 不扩展到 Helm / Kubernetes

## 当前交付物

- 根目录 `Dockerfile`
- 根目录 `compose.yaml`
  - 默认：基础设施依赖
  - `app` profile：应用 + 基础设施联调
- `deploy/compose.yaml`
  - 远程主机最小部署编排，只部署应用容器
- `deploy/.env.production.example`
  - 远程部署环境变量模板
- `.github/workflows/ci.yml`
  - `verify -> image`
- `.github/workflows/deploy.yml`
  - 手动指定镜像 tag 执行远程部署

## 本地容器联调

### 基础设施模式

适用于宿主机运行应用：

```bash
docker compose up -d
```

这会拉起：

- PostgreSQL `16.10`
- RabbitMQ `4.1.0-management`
- Redis `7.4.2-alpine`
- MinIO `RELEASE.2025-09-07T16-13-09Z`
- go-judge（由仓库 `docker/go-judge/Dockerfile` 构建）

### 应用联调模式

适用于整套容器联调：

```bash
AUBB_JWT_SECRET='replace-with-at-least-32-characters' \
docker compose --profile app up --build
```

约定：

- `app` profile 才会拉起应用服务，避免影响宿主机 `spring-boot:run`
- `compose up/down/config` 默认仍可在未注入 `AUBB_JWT_SECRET` 的情况下用于基础设施模式；只有 `app` 容器真正启动时才会对 JWT 密钥做 fail-fast 检查
- 容器内默认启用：
  - MinIO
  - go-judge
  - RabbitMQ 队列
- 如需首启初始化，可再追加 `AUBB_BOOTSTRAP_*` 变量；`app` profile 当前会把这些变量完整透传到应用容器
- 若本机已有服务占用默认端口，可覆盖 `AUBB_APP_PORT`、`AUBB_GO_JUDGE_PORT`、`AUBB_POSTGRES_PORT`、`AUBB_RABBITMQ_PORT`、`AUBB_REDIS_PORT`、`AUBB_MINIO_PORT`

关键端口默认值：

- app: `8080`
- PostgreSQL: `5432`
- RabbitMQ: `5672`
- RabbitMQ Console: `15672`
- Redis: `6379`
- MinIO API: `9000`
- MinIO Console: `9001`
- go-judge: `5050`

## 镜像构建与版本

### 本地构建

```bash
docker build -t aubb-server:local .
```

### CI 镜像版本

`ci.yml` 当前会把应用镜像推送到 GHCR：

- `ghcr.io/<owner>/<repo>:sha-<commit>`
- `ghcr.io/<owner>/<repo>:<branch>`
- 若触发 git tag，例如 `v0.1.0`
  - 额外生成 `ghcr.io/<owner>/<repo>:v0.1.0`

建议远程部署优先使用：

- 日常环境：`sha-<commit>`
- 人工发布：语义版本 tag

## CI

### verify

`ci.yml` 的 `verify` job 会执行：

```bash
bash ./mvnw -B verify
```

失败或成功都会上传：

- `target/surefire-reports/**`
- `target/failsafe-reports/**`
- `target/*.log`

### image

`image` job 在 `verify` 通过后构建应用镜像：

- PR：只验证镜像可构建，不推送
- `main` / `v*` tag：推送到 GHCR

## 最小 deploy

### 入口

使用 GitHub Actions 手动触发：

- Workflow：`.github/workflows/deploy.yml`
- 输入：
  - `environment`
    - `staging` 或 `production`
  - `image_tag`
    - 例如 `sha-abcdef1`、`v0.1.0`

### 部署模型

当前 deploy 只负责应用容器，不负责替代外部基础设施编排。

远程主机需要：

- Docker + Compose Plugin
- 能访问 PostgreSQL、RabbitMQ、Redis、MinIO、go-judge
- 能访问 GHCR

### GitHub Environment Secrets / Vars

`deploy.yml` 依赖 GitHub Environment 注入以下参数。

必填 secrets：

- `DEPLOY_HOST`
- `DEPLOY_USER`
- `DEPLOY_SSH_KEY`
- `DEPLOY_PATH`
- `GHCR_USERNAME`
- `GHCR_TOKEN`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `AUBB_JWT_SECRET`

按需 secrets：

- `DEPLOY_PORT`
- `SPRING_RABBITMQ_HOST`
- `SPRING_RABBITMQ_PORT`
- `SPRING_RABBITMQ_USERNAME`
- `SPRING_RABBITMQ_PASSWORD`
- `SPRING_DATA_REDIS_HOST`
- `SPRING_DATA_REDIS_PORT`
- `AUBB_MINIO_ENDPOINT`
- `AUBB_MINIO_ACCESS_KEY`
- `AUBB_MINIO_SECRET_KEY`
- `AUBB_GO_JUDGE_BASE_URL`

常用 vars：

- `AUBB_APP_PORT`
- `AUBB_API_DOCS_ENABLED`
- `AUBB_SWAGGER_UI_ENABLED`
- `AUBB_BOOTSTRAP_ENABLED`
- `AUBB_MINIO_ENABLED`
- `AUBB_MINIO_BUCKET`
- `AUBB_MINIO_AUTO_CREATE_BUCKET`
- `AUBB_GO_JUDGE_ENABLED`
- `AUBB_JUDGE_QUEUE_ENABLED`
- `AUBB_JUDGE_QUEUE_NAME`
- `AUBB_JUDGE_QUEUE_CONCURRENCY`

示例模板见 [deploy/.env.production.example](../deploy/.env.production.example)。

### 执行过程

`deploy.yml` 当前会：

1. 计算目标镜像 `ghcr.io/<owner>/<repo>:<image_tag>`
2. 渲染远程 `.env.production`
3. 通过 SSH / SCP 上传 `deploy/compose.yaml` 与 `.env.production`
4. 远程执行：
   - `docker login ghcr.io`
   - `docker compose pull app`
   - `docker compose up -d`
   - `docker compose exec -T app curl -fsS http://localhost:8080/actuator/health`

若部署失败，工作流会回收远程应用最近日志并上传为 artifact。

## 回滚

当前回滚入口就是同一个 `deploy.yml`：

1. 找到上一个稳定镜像版本
   - 推荐使用历史 `sha-<commit>` tag
   - 或稳定语义版本 tag
2. 重新手动触发 `deploy.yml`
3. 把 `image_tag` 改成要回滚的旧 tag

因为远程部署始终由 `AUBB_APP_IMAGE` 驱动，所以回滚不需要重新构建，只需要重新部署旧镜像。

## 当前实现边界

- 当前 deploy 不负责远程主机初始化，也不负责 PostgreSQL / RabbitMQ / Redis / MinIO / go-judge 的生产编排
- 当前没有蓝绿、金丝雀或多副本滚动升级
- 当前没有自动数据库备份、自动回滚或 Helm / Kubernetes 资产
- 当前 CI 只构建应用镜像，不单独发布 go-judge 镜像
