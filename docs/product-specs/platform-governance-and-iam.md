# 平台治理与 IAM

## 目标

交付 AUBB V1 的首条可验证后台治理链路，使平台能够在统一的学校/学院/课程/班级结构下完成用户、身份、配置和基础审计治理。

## 覆盖范围

### 功能范围

- 平台基础信息配置：名称、简称、Logo、页脚、默认首页
- 品牌与模块配置：主题、登录页文案、模块开关
- 单份平台配置即时更新并立刻生效
- 四层组织维护：学校、学院、课程、班级
- 用户创建与导入：单个创建、批量导入、逐行校验结果
- 用户查询：分页列表、详情查看、归属组织摘要、教务画像、成员关系、最近登录与生命周期信息
- 多身份治理：为同一用户分配多个组织作用域身份
- 教务画像维护：学号/工号、真实姓名、身份类型、画像状态
- 组织成员关系维护：课程/班级等业务成员归属
- 账号状态管理：启用、停用、锁定、失效
- JWT 身份认证：登录、刷新、撤销、退出、当前登录用户
- 会话治理：管理员强制失效指定用户的活跃登录会话
- 服务端授权：基于身份与作用域的资源保护
- 基础审计：登录、配置更新、用户导入、身份与状态变更、组织创建

### 不在范围

- 平台配置版本化、发布、回退、历史查询
- OAuth2 / OIDC / SAML 正式接入
- 平台运营概览与异常事件中心

## 核心业务规则

1. 平台配置只有一份当前生效配置，修改后立刻生效。
2. 组织结构固定为 `SCHOOL -> COLLEGE -> COURSE -> CLASS`。
3. 管理权限必须绑定到组织节点，不能仅靠全局角色表达。
4. 用户系统分为基础资料、平台治理身份、课程成员角色扩展位三层；课程成员角色不在当前平台治理接口中直接建模。
5. 一个用户可以同时拥有多个平台治理身份。
6. 教师在平台治理阶段映射为班级管理员身份。
7. 批量导入必须返回逐行校验结果和失败原因。
8. 密码长度不少于 8 位，且必须包含字母和数字。
9. 连续 5 次登录失败后，账号默认锁定 30 分钟。
10. JWT 访问令牌默认有效期为 2 小时。
11. refresh token 默认有效期为 14 天，并在每次刷新后轮换。
12. 用户主动退出、refresh token 撤销、管理员强制失效、账号停用都必须让既有会话即时失效。

## 身份模型

### 学校管理员

- 管理平台配置
- 管理全量组织、用户、身份和平台级审计

### 学院管理员

- 管理本学院及下属课程、班级
- 管理本作用域内用户与身份分配

### 课程管理员

- 管理本课程及下属班级
- 管理本作用域内用户与班级管理员分配

### 班级管理员

- 管理本班级内用户
- 平台治理阶段中的教师身份落在该管理员层级

## API 边界

### 认证

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/revoke`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`

### 平台配置

- `GET /api/v1/admin/platform-config/current`
- `PUT /api/v1/admin/platform-config/current`

### 组织

- `GET /api/v1/admin/org-units/tree`
- `POST /api/v1/admin/org-units`

### 用户

- `GET /api/v1/admin/users`
- `GET /api/v1/admin/users/{userId}`
- `POST /api/v1/admin/users`
- `POST /api/v1/admin/users/import`
- `PATCH /api/v1/admin/users/{userId}/status`
- `POST /api/v1/admin/users/{userId}/sessions/revoke`
- `PUT /api/v1/admin/users/{userId}/identities`
- `PUT /api/v1/admin/users/{userId}/profile`
- `PUT /api/v1/admin/users/{userId}/memberships`

### 审计

- `GET /api/v1/admin/audit-logs`

## 数据模型摘要

- `platform_configs`
- `org_units`
- `users`
- `auth_sessions`
- `academic_profiles`
- `user_org_memberships`
- `user_scope_roles`
- `audit_logs`

详细字段以 [../generated/db-schema.md](../generated/db-schema.md) 为准。

## 用户返回模型摘要

- 基础资料：`id`、`username`、`displayName`、`email`、`phone`
- 教务画像：`academicProfile.academicId`、`realName`、`identityType`、`profileStatus`
- 默认归属：`primaryOrgUnitId` 与 `primaryOrgUnit`
- 生命周期：`accountStatus`、`lastLoginAt`、`lockedUntil`、`expiresAt`
- 治理身份：`identities`
- 组织成员关系：`memberships`

## 当前实现边界

- 平台配置和审计查询暂由学校管理员独占。
- 首次学校与学校管理员当前通过受配置开关控制的启动期 bootstrap 完成；不会开放匿名 HTTP bootstrap API。
- 认证链路当前只实现单表 `auth_sessions` 的最小会话模型，不提供设备列表、登录终端画像或自助会话管理界面。
- 当前已实现教务画像和组织成员关系；教师 / 助教 / 学员课程角色已进入 `course_members`，assignment 和 submission 第一切片已开始复用该授权边界，judge / grading 等后续课程子域仍未实现。
- 用户查询按管理员作用域过滤，课程域成员查询留待后续课程模块实现。

## 验收标准

- 管理员可读取并即时更新平台配置
- 用户通过 JWT 登录后可访问受保护资源
- 平台能维护学校/学院/课程/班级四层组织，非法层级创建会被拒绝
- 管理员可分页查询用户并查看自己作用域内用户详情
- 管理员可创建用户、批量导入用户并获得逐行校验结果
- 同一用户可被分配多个作用域身份
- 管理员可调整用户账号状态
- 用户登录后可刷新 access token，且旧 refresh token 会失效
- 用户主动退出、管理员强制失效和账号停用后，旧会话不能继续访问受保护资源
- 新环境可通过标准启动参数一次性完成学校、首个管理员和必要平台配置初始化，重复执行不会产生脏数据
- 未登录用户无法访问受保护资源；超出作用域的管理员会被拒绝
- 登录失败锁定、账号状态限制、refresh/revoke、JWT 登录与配置即时生效具备自动化测试证据
