# 2026-04-17 refresh token / revoke / 强制失效机制

## 目标

在不推翻现有 JWT Bearer 主链路的前提下，增量补齐 refresh token、session revoke 和管理员 / 用户状态触发的旧会话失效能力。

## 范围

- `identityaccess` 认证控制器、应用服务、JWT 校验链路
- `db/migration` 中新增认证会话表
- 认证与平台治理相关测试
- README、安全文档、IAM 规格、数据库结构与工作记忆

## 非目标

- 不引入 OAuth2 / OIDC / SSO
- 不做设备管理、会话列表、自助会话审计界面
- 不引入独立 token blacklist / revocation 表或外部缓存

## 根因

1. 当前只有 access token，没有 refresh token 或服务端 session 锚点。
2. `logout` 只写审计，不会让既有 JWT 立即失效。
3. Bearer 请求在验签后直接信任 JWT claim，还原 principal 时不回查用户或会话状态。

## 最小实现方案

1. 新增 `auth_sessions` 表，承载 refresh token、会话状态和 revoke 原因
2. 登录时创建会话，返回 access token + opaque refresh token
3. access token 增加 `sid` claim，请求链路按 `sid` 回查 `auth_sessions`
4. 新增 `POST /api/v1/auth/refresh` 和 `POST /api/v1/auth/revoke`
5. `logout` 改为真正 revoke 当前 session
6. 用户禁用和管理员强制失效时批量 revoke 该用户活跃会话

## 风险

- 每个受保护请求都会新增一次数据库会话校验，需要保持实现简单并控制查询量
- refresh token 轮换需要避免把旧 refresh token 继续留在可用状态
- 登录返回模型新增字段，必须保证对现有 access token 客户端兼容

## 验证路径

- `bash ./mvnw -Dtest=AuthApiIntegrationTests,PlatformGovernanceApiIntegrationTests test`
- 必要时补 `bash ./mvnw -Dtest=JwtSecurityPropertiesValidationTests,AubbServerApplicationTests,HarnessHealthSmokeTests test`
- 提交前执行 `bash ./mvnw spotless:apply`

## 实施结果

1. 新增 `auth_sessions` 单表模型，承载 refresh token hash、会话过期与 revoke 原因
2. 登录改为创建 session，响应返回 access token + opaque refresh token
3. access token 新增 `sid`，请求链路按 `sid` 校验 session 与用户状态
4. 新增 `POST /api/v1/auth/refresh`、`POST /api/v1/auth/revoke`
5. `logout`、用户禁用、管理员强制失效都会立即撤销旧会话
6. Bean 环通过抽出 `AuthenticatedPrincipalLoader` 只读服务解开，未改动现有 Bearer 主链路

## 验证结果

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -Dtest=AccessTokenSessionValidatorTests,OpaqueRefreshTokenCodecTests,JwtTokenServiceTests,AuthApiIntegrationTests,PlatformGovernanceApiIntegrationTests test`
- 结果：`BUILD SUCCESS`，定向 `27` 个测试通过
