# 任务计划：MinIO 对象存储接入

## 目标

将 MinIO 作为正式对象存储基础设施接入当前仓库，提供可复用的对象存储服务、健康检查、本地开发依赖和自动化验证路径，为后续 submission 文件上传和工程快照能力打基础。

## 当前阶段

Phase 3 completed

## Skills 选择

- `planning-with-files`：本任务跨依赖、配置、基础设施、测试、文档与验证，需要持续记录阶段和决策。
- `springboot-patterns`：用于保持配置类、共享服务、健康检查和条件装配边界清晰。
- `springboot-verification`：用于约束格式化和全量验证。
- `documentation-writer`：用于同步架构、可靠性、安全和对象存储说明。

## 阶段

### Phase 1：范围收敛与接入设计

- [x] 对齐当前 compose、测试容器和共享配置方式
- [x] 定义 MinIO 配置模型、共享服务接口和健康检查边界
- [x] 建立正式执行计划与工作记忆
- **Status:** completed

### Phase 2：实现与验证路径

- [x] 新增 MinIO 依赖与共享配置
- [x] 新增对象存储服务和 bucket 初始化
- [x] 新增 MinIO 集成测试
- [x] 更新 compose 本地依赖
- **Status:** completed

### Phase 3：文档同步与收尾

- [x] 更新 README、架构、可靠性、安全和对象存储说明
- [x] 执行 `./mvnw spotless:apply`
- [x] 执行 `./mvnw clean verify`
- [ ] 在合适时机做 git 提交
- **Status:** completed

## 已做决策

| Decision | Rationale |
|----------|-----------|
| 先接共享对象存储能力，不直接绑某个业务上传接口 | 避免把 MinIO 接入和 submission 文件上传耦合成一次大改动 |
| MinIO 默认关闭，通过显式配置启用 | 不把新基础设施变成默认启动阻塞条件 |
| 本地开发同时提供 compose 和 Testcontainers 两条验证路径 | 满足仓库可靠性规则，避免只靠文档假设 |
| MyBatis 扫描改为只扫描 `BaseMapper` 子接口 | 解决共享服务接口被误扫为 Mapper 的隐式风险 |

## 错误记录

| Error | Attempt | Resolution |
|-------|---------|------------|
| Spring Boot 4 Actuator 健康检查包路径与旧版本不同 | 首次编译时按旧包名实现 | 切换到 `org.springframework.boot.health.contributor.*` |
| MinIO 8.6.0 传递依赖没有带入真正的 `okhttp-jvm` 实现 | 编译期缺失 `okhttp3.HttpUrl` | 显式补充 `okhttp-jvm:5.1.0` |
| `@MapperScan("com.aubb.server")` 会把 `ObjectStorageService` 误扫成 Mapper | MinIO 测试上下文启动冲突 | 改为只扫描 `BaseMapper` 子接口 |
