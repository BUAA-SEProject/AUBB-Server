# 执行计划：基于 user_system.md 深化用户系统

## 目标

以 `user_system.md` 为直接设计输入，在当前 AUBB-Server 中补齐更完整的用户系统基础能力，使“账号 + 教务画像 + 组织成员关系 + 治理身份”的组合可以支撑后续课程、实验和作业模块继续接入。

## 范围

- `src/main/resources/db/migration` 下的用户系统增量表结构
- `src/main/java/com/aubb/server/modules/identityaccess` 与相关 `organization` 协作代码
- `src/test/java/com/aubb/server/api` 下与用户治理相关的集成测试
- 仓库内的产品规格、数据库文档和必要的系统级设计/API 文档

## 不在范围

- Refresh Token 持久化与轮转
- PAT、OAuth2 / OIDC / SSO 正式接入
- 通用 ABAC 决策引擎与实验域策略校验
- 多模块拆分、异步 outbox 基础设施和远程服务拆分

## 本轮落地重点

1. `academic_profile`
   - 学号/工号
   - 身份类型
   - 画像状态
   - 真实姓名、手机号、邮箱补充口径
2. `user_org_memberships`
   - 用户与课程/班级/组织的业务成员关系
   - 成员类型、状态、来源、起止时间
3. 管理接口补强
   - 用户详情返回画像与成员关系
   - 用户列表支持更多用户治理筛选
   - 创建用户时支持同步写入画像与成员关系

## 风险

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 直接照搬 `user_system.md` 会把过多后续能力混入当前切片 | 范围失控 | 先显式划分“本轮落地 / 后续扩展位” |
| 新表结构引入后测试数据和导入逻辑易失配 | 回归失败 | 先写集成测试，再补 Flyway 与服务逻辑 |
| 用户成员关系与治理身份语义混淆 | 数据模型难以维护 | 在代码与文档中明确“成员关系不等于权限” |

## 验证路径

- `PlatformGovernanceApiIntegrationTests`
  - 用户详情返回画像与成员关系
  - 用户列表支持新的治理筛选
  - 创建用户可带画像与成员关系
- `AuthApiIntegrationTests`
  - 认证主链路不因用户系统扩展回归
- `./mvnw verify`

## 决策记录

- 本轮继续以平台治理边界为主，不把教学域角色权限正式并入治理权限模型。
- 用户成员关系作为业务关系建模，不替代 `primary_org_unit_id` 或 `user_scope_roles`。

## 退出条件

- `academic_profiles` 与 `user_org_memberships` 已通过 Flyway 落地
- 用户创建、查询、详情、画像更新、成员关系更新接口已经可用
- 登录返回和 `/api/v1/auth/me` 已包含画像快照
- 仓库内外文档已同步到当前实现口径
- `./mvnw verify` 通过

## 完成记录

- 2026-04-15：新增 `academic_profiles` 与 `user_org_memberships` 两张用户系统增量表。
- 2026-04-15：补齐用户列表筛选、用户详情、创建用户、更新画像、更新成员关系接口，并将画像快照加入 JWT 登录返回和当前用户接口。
- 2026-04-15：同步更新数据库结构、架构说明、产品规格、认证 API、平台治理 API 和 `user_system.md`。
- 2026-04-15：执行 `./mvnw spotless:apply`、`./mvnw -Dtest=AuthApiIntegrationTests test`、`./mvnw -Dtest=PlatformGovernanceApiIntegrationTests test` 与 `./mvnw verify`，验证通过。

## 结果说明

- 本轮完成后，用户系统已经具备“账号 + 教务画像 + 组织成员关系 + 治理身份”的基础组合，可继续作为课程域和实验域的上游输入。
