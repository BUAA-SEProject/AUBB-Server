# 2026-04-17 JWT 默认密钥移除与密钥治理基线

## 目标

移除 JWT 默认密钥回退，要求服务在缺失或过弱密钥时启动即失败，同时保持现有登录与 Bearer 鉴权链路、测试路径和本地开发说明可验证。

## 范围

- `application.yaml` 中 JWT 配置占位符
- `SecurityConfig` 与 `JwtTokenService` 的配置读取方式
- 认证相关测试入口的 JWT secret 注入
- 安全文档、README、工作记忆与执行计划

## 非目标

- 不在本次实现 refresh token、revoke 或服务端强制失效
- 不调整现有登录 API 契约
- 不引入新的密钥管理基础设施（Vault/KMS）

## 根因

1. 主配置与 `SecurityConfig` 仍允许在缺失密钥时回退到硬编码默认值。
2. JWT 配置分散在多个 `@Value` 注入点，缺乏单一校验入口。
3. 现有测试依赖主配置弱默认值，移除默认值后需要显式测试注入。

## 最小实现方案

1. 新增 `JwtSecurityProperties`，统一承载 `issuer / ttl / secret`
2. 用 `@ConfigurationProperties + validation` 在绑定期 fail-fast
3. `SecurityConfig` 与 `JwtTokenService` 改为只消费 `JwtSecurityProperties`
4. `application.yaml` 去掉 secret 默认回退，仅保留外部注入占位符
5. 为测试基类和直接 `SpringBootTest` 入口注入 test secret
6. 补充“缺失 secret 启动失败”测试，并复用认证集成测试验证登录/鉴权正常

## 风险

- 若遗漏任何测试入口的 secret 注入，现有 `SpringBootTest` 会整体启动失败
- 若只校验非空不校验长度，仍可能留下“可启动但过弱”的治理缺口
- README 若不补本地启动示例，开发者会在升级后误判为“应用无法启动”

## 验证路径

- `bash ./mvnw -Dtest=JwtSecurityPropertiesValidationTests,AuthApiIntegrationTests,AubbServerApplicationTests,HarnessHealthSmokeTests test`
- 如需扩展，再补 `bash ./mvnw -Dtest=MinioStorageIntegrationTests test`
- 提交前执行 `bash ./mvnw spotless:apply`

## 完成结果

- 已新增 `JwtSecurityProperties`，在配置绑定阶段统一校验 `issuer / ttl / secret`
- 已移除仓库内 JWT 默认密钥回退，主配置仅接受外部注入的 `AUBB_JWT_SECRET`
- 已通过 `src/test/resources/application.properties` 为测试作用域集中注入 test secret，避免逐个 `SpringBootTest` 改造
- 已新增启动失败单测，并通过认证、健康检查和 MinIO 集成测试回归现有链路

## 实际验证结果

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -Dtest=JwtSecurityPropertiesValidationTests,AuthApiIntegrationTests,AubbServerApplicationTests,HarnessHealthSmokeTests,MinioStorageIntegrationTests test`
- 结果：`BUILD SUCCESS`，定向 `13` 个测试通过
