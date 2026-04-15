# 发现与决策

## 当前任务基线

- 仓库已经具备 PostgreSQL、RabbitMQ、Redis 运行时依赖和 Testcontainers 验证路径，但还没有正式对象存储接入。
- `compose.yaml` 已存在，适合把 MinIO 加入本地开发依赖。
- 当前 submission 第一切片只存文本内容，尚未支持文件和工程快照，因此 MinIO 应优先以共享基础设施形态接入，而不是直接塞入 submission API。

## 接入建模结论

- MinIO 作为共享基础设施进入 `common/storage` 和 `config`，不建立独立业务模块。
- 对象存储提供统一服务接口，暴露最基础的：
  - 上传对象
  - 下载对象
  - 删除对象
  - 检查对象是否存在
  - 生成预签名 GET / PUT URL
- bucket 由配置指定，并支持可选的启动时自动创建。
- 默认关闭 MinIO 接入；启用后才装配客户端、健康检查和初始化逻辑。
- MinIO Java SDK 8.6.0 需要显式补充 `okhttp-jvm`，否则编译期会缺失 `okhttp3.HttpUrl`。
- 当前仓库原有的 `@MapperScan("com.aubb.server")` 范围过大，会把共享接口误判为 Mapper；本轮已收口为只扫描 `BaseMapper` 子接口。
- Spring Boot 4 的健康检查 API 已从旧的 `org.springframework.boot.actuate.health` 迁到 `org.springframework.boot.health.contributor`，后续新增 HealthIndicator 需要沿用新包路径。

## 待特别验证的规则

- MinIO 不启用时，应用启动和现有测试不能受影响。
- MinIO 启用时，健康检查应能反映对象存储连通性。
- 本地 compose 和集成测试必须使用固定镜像版本。
- 访问密钥和 secret 不能硬编码进生产配置，必须通过环境变量覆盖。
- 共享基础设施接入过程中，不能顺手暴露“通用上传接口”，避免绕过现有权限建模。
