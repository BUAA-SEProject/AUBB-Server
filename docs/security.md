# 安全

## 当前基线

- Spring Security 默认启用。
- Phase 2 当前使用 JWT Bearer Token 认证，并引入数据库锚定的最小 `auth_sessions` 会话模型承载 refresh token 与撤销状态。
- JWT 签名密钥无仓库内默认值，必须由外部配置提供；缺失、空白或长度不足时应用在启动阶段直接失败。
- access token 默认有效期 2 小时，refresh token 默认有效期 14 天。
- 首个学校 / 管理员 bootstrap 默认关闭；启用时管理员初始密码同样必须通过外部配置注入，不能写入仓库。
- `/actuator/health` 和 `/actuator/info` 保持公开，用于部署检查与 smoke 验证。
- OpenAPI 发现端点当前默认保持公开，以支持后端开发阶段联调；生产环境可通过 `AUBB_API_DOCS_ENABLED=false` 和 `AUBB_SWAGGER_UI_ENABLED=false` 关闭。
- 除登录、健康检查和文档发现端点外，其他业务路由默认要求认证。
- MinIO 访问账号和密钥必须通过环境变量或外部配置注入，不能写入生产配置文件。

## 规则

1. 不要引入匿名可访问的业务端点，除非文档中明确说明原因。
2. JWT 签名密钥不得进入版本库，必须通过环境变量或外部配置注入；推荐使用 `AUBB_JWT_SECRET`，且长度至少 32 个字符。
3. 密码必须以强哈希算法存储，禁止明文或可逆加密。
4. 连续 5 次登录失败后默认锁定 30 分钟，锁定与失败都必须留审计。
5. access token 默认有效期为 2 小时，refresh token 默认有效期为 14 天；refresh 必须轮换 refresh token，旧 refresh token 不能继续使用。
6. `DISABLED`、`LOCKED`、`EXPIRED` 账号都不得成功登录。
7. `logout`、`POST /api/v1/auth/revoke`、管理员强制失效和账号被停用后，既有会话必须即时失效。
8. Bearer 请求在验签通过后仍需按 `sid` 回查 `auth_sessions` 和用户状态，不能只信任 JWT claim。
9. 权限控制必须同时覆盖接口层粗粒度拦截和应用层作用域校验。
10. 平台配置与审计查询当前只开放给学校管理员。
11. 新增外部认证或令牌刷新机制时，必须同步更新本文件和相关设计文档。
12. 对象存储当前已被 `submission` 附件能力消费；后续业务若继续开放文件上传接口，必须显式定义对象键命名、授权边界和可见性规则。
13. 轮换 JWT 签名密钥会让现有 access token 全部失效；执行轮换前必须同步安排客户端重新登录窗口。
14. `aubb.bootstrap.enabled` 必须默认关闭；仅在新环境初始化窗口临时打开，初始化完成后立即关闭。

## Phase 2 安全要点

- 登录、平台配置更新、用户导入、身份变更、账号状态变更都必须写入审计日志。
- 一个用户可拥有多个管理员身份，权限来源必须清晰可追溯。
- 未授权访问返回稳定的 `401`，越权返回稳定的 `403`。
- JWT 改为无状态后，不再依赖 CSRF 令牌。
- 登录失败锁定、refresh token 轮换、禁用/失效账号限制、管理员强制失效与默认会话时长都必须有自动化测试证据。
- 首个学校 / 管理员 bootstrap 必须是幂等的，并且重复执行默认不能重置既有管理员密码。
- 测试环境允许通过 test-scope 配置集中注入 JWT 密钥，但这只是测试装配方式，不能回流成运行时默认值。

## 当前实现边界

- 当前实现使用单表 `auth_sessions` 承载 refresh token hash、过期时间和 revoke 原因，不单独引入 blacklist / revocation 表或外部缓存。
- refresh token 当前是服务端只存 hash 的 opaque token；不会返回设备名、IP 画像或会话列表。
- access token 仍是 JWT Bearer，但每次受保护请求都会额外校验 `sid` 对应的会话和账号状态，以换取立即撤销能力。
- 审计查询仍是平台级视图，学院级或课程级过滤视图留待后续增强。
- MinIO 当前已用于 `submission` 业务级附件接口，但仍没有通用匿名上传入口。
