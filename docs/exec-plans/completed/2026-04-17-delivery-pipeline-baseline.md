# 2026-04-17 Dockerfile、CI 与发布流水线基线

## 目标

为 AUBB-Server 补齐应用级容器化、本地 compose 联调和最小 GitHub Actions 发布闭环，形成仓库内可复用的 `verify -> image -> deploy` 工程化基线。

## 范围

- 根目录应用 `Dockerfile` 与 `.dockerignore`
- 根 `compose.yaml` 的 app + 基础设施联调能力
- GitHub Actions `ci.yml` 与 `deploy.yml`
- 最小远程部署编排与环境变量模板
- 工程化资产回归测试与部署文档同步

## 非目标

- 不扩展到 Helm、Kubernetes、蓝绿或金丝雀发布
- 不接管 PostgreSQL、RabbitMQ、Redis、MinIO、go-judge 的生产编排
- 不在这一轮引入更复杂的多阶段镜像瘦身、SBOM 治理或签名体系

## 根因

1. 仓库此前只有基础设施 `compose.yaml` 和 go-judge 运行镜像，没有应用自身的标准 Docker 构建入口。
2. `.github/workflows/` 没有 `verify / image / deploy` 流水线，现有 `bash ./mvnw verify` 和镜像构建都停留在人工约定层。
3. 本地联调需要手工拼接 app 环境变量和基础设施地址，README 中也明确写了 compose 不包含应用服务。
4. 最小远程部署缺少版本、环境变量和回滚入口的仓库内标准表达。

## 最小实现方案

1. 新增根目录 `Dockerfile` 与 `.dockerignore`
2. 保持根 `compose.yaml` 默认仍用于宿主机 `spring-boot-docker-compose` 的基础设施模式，并通过 `profiles: [app]` 追加应用服务
3. 在 `app` 容器入口显式校验 `AUBB_JWT_SECRET`，既保留 app 启动时 fail-fast，又不破坏 infra-only 的 `compose up/down/config`
4. 新增 GitHub Actions：
   - `ci.yml`：`verify -> image`，失败时保留 surefire / failsafe / log artifacts
   - `deploy.yml`：手动指定环境和镜像 tag，经 SSH + Compose 执行远程部署
5. 新增 `deploy/compose.yaml` 与 `.env.production.example`，明确版本、环境变量和回滚入口

## 实施结果

1. 已新增根目录 `Dockerfile`、`.dockerignore`
2. 已将根 `compose.yaml` 扩展为“默认 infra + `app` profile 联调”，并补齐 PostgreSQL / RabbitMQ / Redis / MinIO 健康检查
3. 已新增 `.github/workflows/ci.yml` 与 `.github/workflows/deploy.yml`
4. 已新增 `deploy/compose.yaml` 与 `deploy/.env.production.example`
5. 已新增 `DeliveryPipelineAssetsTests`，约束 Dockerfile、workflows 与 compose 关键资产
6. 已更新 `README.md`、`docs/deployment.md`、`docs/index.md`、`docs/reliability.md`

## 验证结果

- `bash ./mvnw spotless:apply`
- `git diff --check`
- `bash ./mvnw -Dtest=DeliveryPipelineAssetsTests,AubbServerApplicationTests,HarnessHealthSmokeTests test`
- `docker build -t aubb-server:local .`
- `docker compose --profile app config`
- `docker compose -f deploy/compose.yaml --env-file deploy/.env.production.example config`
- `docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint:1.7.7`
- `AUBB_JWT_SECRET=compose-local-jwt-secret-with-at-least-32-chars AUBB_GO_JUDGE_PORT=15050 docker compose --profile app up -d --build`
- `curl -fsS http://localhost:8080/actuator/health`
- `docker compose --profile app down -v`
- 结果：`BUILD SUCCESS`，定向 `5` 个测试通过；本地 compose app + 基础设施联调、部署 compose 配置校验和 GitHub Actions lint 通过

## 风险

- 当前应用运行镜像为稳定优先的最小基线，仍偏大，后续可以在不破坏健康检查的前提下继续瘦身。
- deploy 仍是 SSH + Compose 手动触发模型，不包含蓝绿、自动回滚和远程基础设施初始化。
- 本地全栈联调对宿主机端口有占用风险，当前通过 `AUBB_*_PORT` 覆盖变量解决。

## 验证路径

- 本地快速验证：`bash ./mvnw -Dtest=DeliveryPipelineAssetsTests,AubbServerApplicationTests,HarnessHealthSmokeTests test`
- 本地容器验证：`AUBB_JWT_SECRET=... docker compose --profile app up -d --build`
- 远程部署验证：手动触发 `.github/workflows/deploy.yml`，指定 `environment` 和 `image_tag`
