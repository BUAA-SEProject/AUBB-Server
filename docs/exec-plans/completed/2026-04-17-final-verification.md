# 2026-04-17 Final Verification

## 验收范围

- 目标分支：`dev`
- 初始提交：`c1b19aa`
- 验收对象：
  - 当前仓库最终代码的全量 `test` / `verify`
  - 应用镜像本地构建
  - `compose.yaml` 的 `app + 基础设施` 最小联调
  - 关键 smoke：
    - `/actuator/health`
    - `/v3/api-docs`
    - bootstrap 管理员登录

## 验收前发现并修复的阻塞

### 1. JWT 配置校验测试未跟上 refresh token 基线

- 现象：首次执行 `bash ./mvnw test` 失败，`JwtSecurityPropertiesValidationTests` 未提供新增必填配置 `aubb.security.jwt.refresh-ttl`
- 处理：补齐测试基线配置与断言
- 文件：
  - `src/test/java/com/aubb/server/config/JwtSecurityPropertiesValidationTests.java`

### 2. compose app profile 未透传 bootstrap 变量

- 现象：`docker compose --profile app up -d --build` 后，`app` 容器因 `AUBB_BOOTSTRAP_*` 未传入容器而启动失败，无法完成本地完整环境登录 smoke
- 处理：为 `compose.yaml` 的 `app` 服务补齐 bootstrap 变量透传，并同步更新部署文档
- 文件：
  - `compose.yaml`
  - `docs/deployment.md`

## 执行的命令

### 分支与工作区确认

```bash
git branch --show-current
git status --short
git rev-parse --short HEAD
```

### 全量验证

```bash
bash ./mvnw test
bash ./mvnw verify
```

### 针对性阻塞修复回归

```bash
bash ./mvnw -Dtest=JwtSecurityPropertiesValidationTests test
```

### 交付资产与容器联调

```bash
docker compose --profile app config
docker build -t aubb-server:final-verification .
```

```bash
export AUBB_APP_PORT=18080
export AUBB_POSTGRES_PORT=15432
export AUBB_RABBITMQ_PORT=15672
export AUBB_RABBITMQ_MANAGEMENT_PORT=15673
export AUBB_REDIS_PORT=16379
export AUBB_MINIO_PORT=19000
export AUBB_MINIO_CONSOLE_PORT=19001
export AUBB_GO_JUDGE_PORT=15050
export AUBB_JWT_SECRET=compose-final-verification-jwt-secret-0123456789
export AUBB_BOOTSTRAP_ENABLED=true
export AUBB_BOOTSTRAP_SCHOOL_CODE=AUBBBOOT
export AUBB_BOOTSTRAP_SCHOOL_NAME='AUBB Bootstrap School'
export AUBB_BOOTSTRAP_ADMIN_USERNAME=bootstrap-admin
export AUBB_BOOTSTRAP_ADMIN_DISPLAY_NAME='Bootstrap Admin'
export AUBB_BOOTSTRAP_ADMIN_EMAIL=bootstrap-admin@example.edu
export AUBB_BOOTSTRAP_ADMIN_PASSWORD='BootstrapAdmin!123'
export AUBB_BOOTSTRAP_ADMIN_ACADEMIC_ID=BOOT-0001
export AUBB_BOOTSTRAP_ADMIN_REAL_NAME='Bootstrap Admin'

docker compose --profile app up -d --build
docker compose --profile app ps
curl -fsS http://localhost:18080/actuator/health
curl -fsS http://localhost:18080/v3/api-docs
curl -fsS \
  -X POST http://localhost:18080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"bootstrap-admin","password":"BootstrapAdmin!123"}'
docker compose --profile app down -v
```

## 结果摘要

### 全量测试

- `bash ./mvnw test`：通过
  - `Tests run: 133, Failures: 0, Errors: 0, Skipped: 0`
  - 总耗时约 `02:21`
- `bash ./mvnw verify`：通过
  - 产出 `target/server-0.0.1-SNAPSHOT.jar`
  - Spring Boot repackaged jar 成功
  - 总耗时约 `02:18`

### 镜像构建

- `docker build -t aubb-server:final-verification .`：通过
- `docker compose --profile app up -d --build`：通过
  - 应用镜像与 go-judge 镜像均可在当前仓库直接构建

### 本地完整环境联调

- `compose` 最小联调通过：
  - PostgreSQL、RabbitMQ、Redis、MinIO、go-judge、app 全部成功启动
  - `app` profile 在启用 bootstrap 后可完成首个学校 / 管理员初始化

### 关键主链路 smoke

- `/actuator/health`：通过
  - 返回：`{"groups":["liveness","readiness"],"status":"UP"}`
- `/v3/api-docs`：通过
  - 导出体积：`120772` bytes
- `POST /api/v1/auth/login`：通过
  - 使用 bootstrap 管理员 `bootstrap-admin / BootstrapAdmin!123`
  - 成功返回 `accessToken + refreshToken + user`

## flaky 观察

- 本轮没有观察到测试级 flaky case。
- `StructuredProgrammingJudgeIntegrationTests`、`ProgrammingWorkspaceIntegrationTests`、`JudgeIntegrationTests` 等真实链路测试均在全量 `test` 和 `verify` 中稳定通过。
- 容器联调阶段等待应用完全 ready 之前，`curl` 出现过数次 `connection reset by peer`，属于健康探测早于应用就绪，不属于测试用例 flaky。

## 失败项或风险项

- Step 1 初跑并非一次通过，暴露了两个真实阻塞：
  - `JwtSecurityPropertiesValidationTests` 断言基线过时
  - `compose app` profile 未透传 bootstrap 变量
- 两个阻塞均已在本次收尾中修复，并在修复后重新完成全量验证。
- 当前仍存在项目级已知缺口，但不阻塞进入 Step 2：
  - M2 仍是部分完成：judge 产物对象化仅到 phase 1，成绩发布快照尚未落地
  - M5 仍是部分完成：核心业务 metrics、健康检查分级、Redis 去留尚未收口
- 构建日志仍有 JDK 25 下 Mockito 动态加载 agent 警告，当前不影响通过，但应在后续治理包中关注。

## 结论

- 当前 Step 1 已通过。
- 允许进入 Step 2：`M2 收尾 - judge 产物对象化 phase 2`
