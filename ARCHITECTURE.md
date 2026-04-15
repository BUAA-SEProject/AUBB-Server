# 架构

## 运行时基线

AUBB-Server 是一个运行在 Java 25 上的 Spring Boot 4 后端服务。当前基线包含 Spring Security、Actuator、Web MVC、WebSocket、Flyway、MyBatis-Plus、PostgreSQL、RabbitMQ、Redis、Prometheus 指标以及 OpenAPI 支持。

## 当前状态

仓库当前聚焦于首个真实治理切片：平台配置、组织与 IAM。该切片已经按最新需求收敛为：

- JWT Bearer Token 认证
- 学校 / 学院 / 课程 / 班级四层组织
- 按组织作用域分配的多身份管理员模型
- 用户列表与详情查询能力
- 单份即时生效的平台配置
- 基础审计与公开健康检查

## 目标包结构

- `config`：安全、持久化、序列化等跨领域框架配置
- `api`：控制器、请求 DTO、响应 DTO
- `application`：用例编排、事务控制、授权协调
- `domain`：组织策略、身份策略、密码策略、授权规则
- `infrastructure`：MyBatis 映射、Flyway 迁移、外部适配器

首批业务子域建议如下：

- `api.auth` / `application.auth` / `domain.iam`：JWT 登录、退出、当前用户、账号状态
- `api.admin.platformconfig` / `application.platformconfig`：平台配置读取与即时更新
- `api.admin.organization` / `application.organization` / `domain.organization`：四层组织树与层级校验
- `api.admin.user` / `application.user` / `domain.iam`：用户创建、导入、查询、身份分配、账号状态
- `api.admin.audit` / `application.audit` / `domain.audit`：审计写入与检索

## 基础设施边界

- PostgreSQL 是关系型事实来源
- Redis 保留为缓存与短生命周期协调状态扩展位，本切片不再承担登录态
- RabbitMQ 保留给后续异步流程
- Flyway 负责数据库模式演进
- MyBatis-Plus 负责主数据持久化

当前治理批次的核心表为：

- `platform_configs`
- `org_units`
- `users`
- `user_scope_roles`
- `audit_logs`

## 请求流向

请求沿 `api -> application -> domain -> infrastructure` 向内流动。认证信息先由安全配置解出 JWT，再由应用层和领域层执行业务级授权与作用域校验。

## 认证与授权

- 认证方式：JWT Bearer Token，服务端签发，服务端无状态校验。
- 令牌内容：用户标识、账号状态、默认组织、身份列表和授权所需的 authority。
- 平台治理身份：
  - `SCHOOL_ADMIN`
  - `COLLEGE_ADMIN`
  - `COURSE_ADMIN`
  - `CLASS_ADMIN`
- 身份分配模型：一个用户可拥有多个作用域身份，每个身份都绑定一个组织节点。
- 当前实现边界：
  - 学校管理员拥有平台配置与全局审计能力
  - 学院 / 课程 / 班级管理员只管理各自作用域内的组织和用户
  - 课程业务权限仍在后续课程域中独立建模

## 组织与治理模型

- 组织树固定为四层：`SCHOOL -> COLLEGE -> COURSE -> CLASS`
- 不再支持任意类型的自由树形扩展
- 教师在平台治理阶段等价于班级管理员身份
- 用户系统拆为三个层次：
  - 用户基础资料：登录标识、显示名、邮箱、默认归属组织、账号状态与生命周期字段
  - 平台治理身份：作用域管理员身份，存放于 `user_scope_roles`
  - 课程成员角色：教师 / 助教 / 学员等课程域角色，保留给后续 `course_members` 建模
- 用户主组织与管理身份解耦：
  - `users.primary_org_unit_id` 表示默认归属
  - `user_scope_roles` 表示实际治理权限来源

## 平台配置模型

- 平台配置采用单份实时配置，不再存在草稿、发布、回退和历史版本
- 修改配置后立即成为当前生效配置
- 若后续需要历史追溯，应通过审计扩展或独立配置快照表实现，而不是恢复版本化工作流

## 审计与可观测性

- 关键动作必须在事务边界内写入审计记录，至少覆盖：
  - 登录成功 / 失败
  - 平台配置更新
  - 用户导入
  - 身份变更
  - 账号状态变更
  - 组织节点创建
- 请求链路保留 `requestId`
- `/actuator/health` 继续保持公开读取

## Harness 约束

- 架构变更必须同步更新 [docs/quality-score.md](docs/quality-score.md)
- 设计决策应记录到 [docs/design-docs](docs/design-docs/index.md)
- 较大改动应在 [docs/exec-plans](docs/exec-plans/active/README.md) 下留下执行轨迹
