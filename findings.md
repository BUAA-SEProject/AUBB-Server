# 发现与决策

## 当前实现观察

- 当前仓库已经实现 Phase 2 的核心治理骨架：JWT 登录、四层组织、作用域身份、平台配置即时生效、基础审计。
- `GET /api/v1/admin/users` 已存在，但目前用户返回模型缺少归属组织摘要、锁定/失效等管理核对信息，管理价值仍偏弱。
- 自动化测试已覆盖登录成功、登录锁定、组织层级、多身份用户、用户导入、状态更新和作用域过滤。
- `AGENTS.md` 要求“登录失败锁定规则、账号状态规则、会话超时规则必须有测试”，但当前缺少对 `DISABLED`、`EXPIRED` 和 JWT 默认会话时长的测试证据。

## 文档问题

- `docs/task_plan.md`、`docs/findings.md`、`docs/progress.md` 属于过程性工作记忆，不应继续保留在全局文档目录下。
- `../docs/04-development/backend.md`、`../docs/04-development/database.md` 仍保留旧的 Session、UUID、`user_identities`、平台全局角色等口径，与当前实现不一致。
- `../docs/02-process-docs/user-manual.md` 仍包含“保存草稿并发布生效”“检查浏览器 Cookie”等旧表述，与即时生效配置和 JWT 不一致。
- `../docs/05-api/platform-admin-api.md` 尚未反映用户列表/详情查询等当前或计划中的能力。

## 用户系统设计结论

- 平台治理阶段的用户系统应聚焦三层对象：
  - 用户基础资料：登录标识、显示名、邮箱、手机号、默认归属组织、账号状态、生命周期字段。
  - 平台治理身份：`SCHOOL_ADMIN / COLLEGE_ADMIN / COURSE_ADMIN / CLASS_ADMIN`，必须绑定组织作用域。
  - 课程域成员角色：教师、助教、学员不在当前平台治理接口中直接落库为平台全局角色，后续进入课程域表建模。
- 对管理员来说，最小可用的用户查询应支持：
  - 列表分页与关键字筛选
  - 按账号状态和组织过滤
  - 查看用户详情、归属组织摘要、身份列表、最近登录时间、锁定/失效信息
- “解锁”在当前实现中可通过把状态恢复为 `ACTIVE` 完成；文档需要明确这一点。

## 文档治理结论

- `docs/` 只保留长期稳定的仓库知识与规范。
- 单次任务执行轨迹只保留在：
  - 仓库根目录：`task_plan.md`、`findings.md`、`progress.md`
  - `docs/exec-plans/active|completed/`
- 开发流程规范应显式写入：
  - 先计划再实现
  - TDD 作为默认实现路径
  - 代码格式化和验证命令
  - 新增或修改代码时只加必要的中文注释
  - 架构/API/数据库/安全变更的文档同步要求

## 模块化单体重构观察

- 当前仓库已经具备较清晰的层次划分，但业务代码仍主要分散在顶层 `api / application / domain / infrastructure` 目录中，模块边界主要依赖子包命名约定，而不是目录本身。
- 当前已落地治理能力可以自然收敛为四个首批模块：
  - `identityaccess`：认证、JWT、用户、账号状态、平台治理身份、作用域授权
  - `organization`：组织树与组织摘要
  - `platformconfig`：平台配置
  - `audit`：审计写入与查询
- 若把 `auth`、`iam`、`user` 强行拆成三个独立模块，会在当前阶段引入较多跨模块直接依赖；先合并为 `identityaccess` 更符合当前业务内聚性。
- 模块化单体重构的首要目标应是“模块边界可见、共享包最小化、后续课程域可继续挂接”，而不是一次性引入 Maven 多模块或远程服务拆分。

## 模块化单体重构结论

- 当前业务代码已经迁移到 `com.aubb.server.modules.<module>.<layer>`，首批模块为 `identityaccess`、`organization`、`platformconfig`、`audit`。
- 共享代码当前只保留在顶层 `common`、`config` 与 `infrastructure.persistence`，避免把跨模块基础设施误塞回业务模块。
- `RepositoryHarnessTests` 已新增模块优先目录约束，能够拒绝旧的顶层业务目录继续承载新增实现。
- 针对性回归测试与全量 `./mvnw verify` 已通过，说明这次重构属于结构重整而非行为变更。
- 后续新增业务域应直接以 `com.aubb.server.modules.<new-module>` 形式挂接，避免回退到“按层堆目录”的方式。

## 用户系统深化结论

- `user_system.md` 中与当前平台治理边界高度匹配的两类模型已经落地：
  - `academic_profiles`：承接学号/工号、真实姓名、身份类型、画像状态
  - `user_org_memberships`：承接课程/班级等业务成员关系
- 当前用户系统已经从“账号 + 治理身份”扩展为“账号 + 教务画像 + 业务成员关系 + 治理身份”四层组合。
- 登录返回与 `/api/v1/auth/me` 已携带画像快照，但仍保持 JWT 无状态模型，没有引入 refresh token 或服务端会话。
- 平台治理权限与业务成员关系继续分离：`user_scope_roles` 负责治理授权，`user_org_memberships` 只负责业务归属，不互相替代。
- `user_system.md` 中的 `policy-module`、PAT、OAuth2/SSO、integration/outbox 仍处于后续扩展位，本轮未混入当前切片。

## 课程系统第一切片结论

- 课程域当前已作为独立 `modules.course` 聚合模块落地，首轮不再继续细拆为多个 `course-*` 模块。
- 已实现的课程主链路为：
  - `academic_terms`
  - `course_catalogs`
  - `course_offerings`
  - `course_offering_college_maps`
  - `teaching_classes`
  - `course_members`
- 当前课程权限采用双轨：
  - 平台治理侧仍由学校/学院/课程/班级管理员控制平台级治理入口
  - 课程教学侧由 `course_members` 控制教师、助教、学生权限
- 学生自主选课在本轮被显式禁用，课程成员仅允许教师批量添加或导入既有系统用户。
- 助教权限当前已收敛为“可查看授权教学班成员，但不可修改成员和班级功能开关”；更细粒度 staff scope 仍留作后续扩展。
- 同一用户可在同一开课实例下同时承担不同班级的不同课程角色，例如在一个班级是学生、在另一个班级是助教。

## Harness 校验调整结论

- 仓库已取消专门的 harness verify 自动工作流。
- 文档不再进入自动工作流检查，也不再由测试自动校验 Markdown 链接和必需文档路径。
- 自动化层当前只保留代码结构约束测试 `RepositoryStructureTests`。
