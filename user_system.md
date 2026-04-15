# 一体化在线教学与实验平台

# 用户系统详细设计（可开发版，模块化单体）

## 1. 目标与范围

用户系统服务两类核心用户：**教师**、**学生**，并兼顾学校/学院/课程/班级治理人员。
本设计覆盖以下能力：

* 认证与会话管理
* 用户与教务画像管理
* 组织拓扑与成员关系管理
* 角色、权限、作用域管理
* 实验场景下的 ABAC 动态鉴权
* 审计日志与安全追踪
* 对课程、实验、评测、作业模块的事件集成

本设计目标不是“只讲原则”，而是让后端、前端、测试可以按此直接拆任务开发。

---

## 2. 架构选型：模块化单体

### 2.1 选型说明

系统采用**模块化单体（Modular Monolith）**，统一部署为一个应用，但在代码、数据访问、事件通信层面严格分模块。

### 2.2 模块划分

建议拆为以下 8 个核心模块：

1. **auth-module**
   登录、刷新、登出、密码、SSO、PAT、会话控制

2. **user-module**
   用户基础信息、教务画像、状态流转、批量导入

3. **org-module**
   学校/学院/课程/班级/小组组织树及跨节点映射

4. **iam-module**
   角色、权限、作用域绑定、鉴权决策

5. **policy-module**
   ABAC 策略：时间/IP/配额/归档状态/本人可见等

6. **audit-module**
   审计日志、操作追踪、异常安全事件

7. **platform-config-module**
   平台级配置项与全局开关

8. **integration-module**
   面向课程、实验、自动评测、作业模块的领域事件集成

### 2.3 代码结构建议

```text
com.platform
 ├─ common
 ├─ auth
 │   ├─ api
 │   ├─ application
 │   ├─ domain
 │   └─ infrastructure
 ├─ user
 ├─ org
 ├─ iam
 ├─ policy
 ├─ audit
 ├─ platformconfig
 └─ integration
```

### 2.4 模块依赖规则

只允许以下方向依赖：

* `auth -> user, iam`
* `user -> org`
* `iam -> org`
* `policy -> iam, user, org, platformconfig`
* `audit` 不被业务模块直接依赖，统一通过 `AuditPublisher` 接口写入
* 跨模块副作用统一走：

  * **应用服务接口**
  * **领域事件（After-Commit）**

禁止：

* 模块直接访问别的模块数据库实体
* Controller 直接调用别的模块 Repository
* 业务模块之间互相 new Listener / Service

---

## 3. 统一基础约定

## 3.1 统一响应结构

所有接口统一返回：

```json
{
  "code": "SUCCESS",
  "message": "ok",
  "requestId": "req_20260415_xxx",
  "data": {}
}
```

分页接口：

```json
{
  "code": "SUCCESS",
  "message": "ok",
  "requestId": "req_xxx",
  "data": {
    "items": [],
    "page": 1,
    "pageSize": 20,
    "total": 125
  }
}
```

## 3.2 错误码

建议统一错误码前缀：

* `AUTH_` 认证类
* `USER_` 用户类
* `ORG_` 组织类
* `IAM_` 权限类
* `POLICY_` 策略类
* `AUDIT_` 审计类
* `COMMON_` 通用类

核心错误码：

* `AUTH_INVALID_CREDENTIALS`
* `AUTH_ACCOUNT_LOCKED`
* `AUTH_ACCOUNT_DISABLED`
* `AUTH_TOKEN_EXPIRED`
* `AUTH_REFRESH_TOKEN_INVALID`
* `USER_NOT_FOUND`
* `USER_DUPLICATE_USERNAME`
* `USER_DUPLICATE_ACADEMIC_ID`
* `ORG_INVALID_HIERARCHY`
* `ORG_SCOPE_DENIED`
* `IAM_PERMISSION_DENIED`
* `POLICY_TIME_WINDOW_DENIED`
* `POLICY_IP_DENIED`
* `POLICY_QUOTA_INSUFFICIENT`

---

## 4. 核心领域模型

## 4.1 用户与画像

### `sys_user`

认证主体表。

字段建议：

* `user_id` bigint, PK
* `username` varchar(64), unique
* `password_hash` varchar(255)
* `password_version` int
* `account_status` enum
* `account_type` enum(`LOCAL`,`SSO_ONLY`,`MIXED`)
* `last_login_at` datetime
* `last_login_ip` varchar(64)
* `created_at`
* `updated_at`
* `created_by`
* `updated_by`
* `is_deleted`

### `academic_profile`

教务画像表，1:1 关联 `sys_user`。

字段建议：

* `profile_id` bigint, PK
* `user_id` bigint, unique
* `academic_id` varchar(64), unique
  学号/工号，作为学校主数据对接主键
* `real_name` varchar(64)
* `identity_type` enum(`TEACHER`,`STUDENT`,`ADMIN`)
* `email`
* `phone`
* `machine_hour_quota_total` decimal(10,2)
* `machine_hour_quota_used` decimal(10,2)
* `profile_status` enum(`ACTIVE`,`SUSPENDED`,`GRADUATED`,`LEFT`)
* `created_at`
* `updated_at`

> 不建议把“班级归属”直接写死在 `academic_profile`，成员归属应通过关系表表达。

---

## 4.2 组织拓扑

### `org_unit`

统一组织节点表。

字段建议：

* `unit_id` bigint, PK
* `unit_type` enum(`SCHOOL`,`COLLEGE`,`COURSE`,`CLASS`,`GROUP`)
* `unit_code` varchar(64)
* `unit_name` varchar(128)
* `parent_unit_id` bigint, nullable
  主树父节点
* `path` varchar(1024)
  例如 `/1/12/35/108`
* `depth` int
* `status` enum(`ACTIVE`,`ARCHIVED`,`DISABLED`)
* `start_at` datetime, nullable
* `end_at` datetime, nullable
* `created_at`
* `updated_at`

唯一约束建议：

* `(unit_type, unit_code)` unique
* `(parent_unit_id, unit_name)` unique

### `org_unit_relation`

用于表达跨节点映射和非树状关系。

字段建议：

* `relation_id` bigint, PK
* `from_unit_id` bigint
* `to_unit_id` bigint
* `relation_type` enum(
  `PRIMARY_PARENT`,
  `CROSS_LISTED_TO_COLLEGE`,
  `GROUP_BELONGS_TO_CLASS`
  )
* `created_at`
* `created_by`

用途：

* 课程挂多个学院
* 小组挂到班级下
* 未来扩展联合课程、实验组、项目组

---

## 4.3 用户与组织成员关系

### `user_org_membership`

描述某用户在某组织下的业务关系，不等同于角色。

字段建议：

* `membership_id` bigint, PK
* `user_id` bigint
* `unit_id` bigint
* `membership_type` enum(
  `ENROLLED`,
  `TEACHES`,
  `ASSISTS`,
  `MANAGES`,
  `BELONGS_TO_GROUP`
  )
* `status` enum(`ACTIVE`,`INACTIVE`,`COMPLETED`,`REMOVED`)
* `start_at`
* `end_at`
* `source_type` enum(`MANUAL`,`IMPORT`,`SYNC`,`SSO_BIND`)
* `created_at`
* `updated_at`

说明：

* 学生选课：`ENROLLED` 到 `COURSE/CLASS`
* 教师授课：`TEACHES` 到 `COURSE`
* 助教带班：`ASSISTS` 到 `COURSE/CLASS`
* 学生进组：`BELONGS_TO_GROUP` 到 `GROUP`

---

## 4.4 角色与权限

### `iam_role`

* `role_id`
* `role_code`
* `role_name`
* `role_category` enum(`GOVERNANCE`,`TEACHING`,`SYSTEM`)
* `description`
* `is_builtin`
* `created_at`

内置角色：

* `SCHOOL_ADMIN`
* `COLLEGE_ADMIN`
* `COURSE_ADMIN`
* `CLASS_ADMIN`
* `INSTRUCTOR`
* `TEACHING_ASSISTANT`
* `STUDENT`

### `iam_permission`

权限点采用 `resource:action` 命名。

示例：

* `user:read`
* `user:create`
* `user:update_status`
* `org:read_tree`
* `org:create`
* `course:publish_assignment`
* `experiment:start`
* `experiment:force_stop`
* `grade:override`
* `grade:read_self`
* `audit:read_scoped`

### `iam_role_permission`

* `role_id`
* `permission_code`

### `iam_user_role_binding`

角色绑定必须带作用域。

字段建议：

* `binding_id`
* `user_id`
* `role_code`
* `scope_unit_id`
* `scope_type`
* `status`
* `start_at`
* `end_at`
* `granted_by`
* `created_at`

作用域规则：

* 绑定在 `COURSE`，默认辐射 `CLASS` 和 `GROUP`
* 绑定在 `COLLEGE`，默认辐射该学院下课程/班级
* 绑定在 `CLASS`，仅限该班及其组
* 不跨平级节点自动扩散

---

## 4.5 认证与会话

### `auth_refresh_session`

用于 Refresh Token 轮转与注销。

字段建议：

* `session_id`
* `user_id`
* `refresh_token_hash`
* `issued_at`
* `expires_at`
* `revoked_at`
* `client_type`
* `user_agent`
* `ip`
* `last_seen_at`

### `auth_external_identity_binding`

* `binding_id`
* `user_id`
* `provider_code`
* `external_subject`
* `external_academic_id`
* `bind_status`
* `bound_at`

### `auth_pat_token`

PAT 只存摘要，不存明文。

字段建议：

* `pat_id`
* `user_id`
* `token_name`
* `token_prefix`
* `token_hash`
* `scope_json`
* `status`
* `last_used_at`
* `expires_at`
* `revoked_at`
* `created_at`

### `auth_login_failure`

* `username`
* `fail_count`
* `last_fail_at`
* `locked_until`

---

## 4.6 审计与事件

### `audit_log`

* `log_id`
* `operator_user_id`
* `operator_role_code`
* `scope_unit_id`
* `target_type`
* `target_id`
* `action_code`
* `result` enum(`SUCCESS`,`FAIL`)
* `before_json`
* `after_json`
* `extra_json`
* `request_id`
* `trace_id`
* `ip`
* `user_agent`
* `occurred_at`

### `domain_event_outbox`

虽然是模块化单体，仍建议加本地 outbox，提高稳定性。

字段建议：

* `event_id`
* `event_type`
* `aggregate_type`
* `aggregate_id`
* `payload_json`
* `status` enum(`NEW`,`PROCESSING`,`DONE`,`FAILED`)
* `retry_count`
* `created_at`
* `processed_at`

---

## 5. 状态机设计

## 5.1 用户账号状态机

推荐统一为：

* `PENDING_ACTIVATION`
* `ACTIVE`
* `LOCKED`
* `DISABLED`
* `EXPIRED`

流转规则：

* 新建本地账号：`PENDING_ACTIVATION -> ACTIVE`
* 登录连续失败 5 次：`ACTIVE -> LOCKED`
* 锁定到期自动恢复：`LOCKED -> ACTIVE`
* 管理员禁用：`ACTIVE/LOCKED -> DISABLED`
* 到期账号：`ACTIVE -> EXPIRED`
* 管理员恢复：`DISABLED/EXPIRED -> ACTIVE`

约束：

* `DISABLED` 用户不可登录，不可刷新 Token，不可启动实验
* `LOCKED` 用户不可登录，但不自动删除已有审计记录
* `EXPIRED` 常用于临时账号或学期结束自动过期账号

## 5.2 组织节点状态

* `ACTIVE`
* `ARCHIVED`
* `DISABLED`

规则：

* 课程归档后：

  * 禁止新建作业
  * 禁止新开实验
  * 允许查历史成绩、日志、提交记录
* 班级归档后：

  * 禁止新增成员
* 学校/学院一般不建议 `ARCHIVED`，仅可 `DISABLED`

---

## 6. 权限模型

## 6.1 RBAC 负责“谁可做什么”

RBAC 解决菜单、按钮、基础接口权限。

## 6.2 ABAC 负责“在什么条件下能做”

ABAC 解决实验/考试等动态场景限制。

## 6.3 推荐的权限矩阵

### 学校治理线

* `SCHOOL_ADMIN`
  平台全局配置、全量用户、全量组织、全量审计

* `COLLEGE_ADMIN`
  本学院及下属课程/班级用户与组织治理

* `COURSE_ADMIN`
  本课程节点元数据、课程成员治理

* `CLASS_ADMIN`
  本班级成员治理、班级内操作审查

### 教学线

* `INSTRUCTOR`

  * 发布/修改作业
  * 配置实验
  * 查看课程内所有提交
  * 人工改分
  * 查询课程作用域审计

* `TEACHING_ASSISTANT`

  * 查看并批改被授权范围的提交
  * 查看实验结果
  * 不可修改课程级核心配置
  * 默认不可最终发布成绩

* `STUDENT`

  * 查看本人课程/作业/实验
  * 启动本人实验容器
  * 提交作业/代码
  * 查看本人分数与反馈

---

## 7. ABAC 决策规则

ABAC 决策放在 `policy-module`，统一提供：

```java
PolicyDecision check(Subject subject, Resource resource, Action action, Context context)
```

### 7.1 决策顺序

1. 账号状态检查
2. RBAC 基础权限检查
3. 作用域检查
4. ABAC 规则检查
5. 输出允许/拒绝及原因码

### 7.2 首批落地规则

#### 规则 1：时间窗口

资源：实验启动、考试提交
条件：当前时间必须落在 `resource.start_at <= now <= resource.end_at`

拒绝码：

* `POLICY_TIME_WINDOW_DENIED`

#### 规则 2：IP 白名单

资源：考试级实验
条件：客户端 IP 命中课程或考试配置的 CIDR 白名单

拒绝码：

* `POLICY_IP_DENIED`

#### 规则 3：机时配额

资源：实验容器启动
条件：`quota_total - quota_used >= estimated_cost`

拒绝码：

* `POLICY_QUOTA_INSUFFICIENT`

#### 规则 4：本人数据隔离

资源：成绩、提交记录
条件：学生只能访问 `owner_user_id == current_user_id`

拒绝码：

* `IAM_PERMISSION_DENIED`

#### 规则 5：归档保护

资源：课程/班级归档后写操作
条件：若 `org.status == ARCHIVED`，所有修改类接口拒绝，仅开放只读

---

## 8. 接口设计

## 8.1 Auth 模块

### `POST /api/v1/auth/login`

请求：

```json
{
  "username": "20230001",
  "password": "******"
}
```

响应：

```json
{
  "code": "SUCCESS",
  "message": "ok",
  "data": {
    "accessToken": "jwt-token",
    "expiresIn": 7200,
    "user": {
      "userId": 1001,
      "username": "20230001",
      "realName": "张三",
      "accountStatus": "ACTIVE"
    },
    "roleBindings": [
      {
        "roleCode": "STUDENT",
        "scopeUnitId": 30001,
        "scopeType": "CLASS"
      }
    ]
  }
}
```

### `POST /api/v1/auth/refresh`

* 从 HTTP-Only Cookie 读取 Refresh Token
* 成功则轮转旧 Token，签发新 Access Token

### `POST /api/v1/auth/logout`

* 吊销当前会话对应 Refresh Session
* 前端清 Cookie

### `POST /api/v1/auth/change-password`

* 需校验旧密码
* 成功后提升 `password_version`
* 使全部旧会话失效

### `GET /api/v1/auth/oauth2/{provider}/login`

### `GET /api/v1/auth/oauth2/{provider}/callback`

* 首次登录走绑定逻辑
* 按 `academic_id` 静默匹配
* 若唯一匹配失败，返回绑定待确认状态

### `POST /api/v1/auth/pats`

创建 PAT
请求：

```json
{
  "tokenName": "cli-submit",
  "expiresAt": "2026-12-31T23:59:59",
  "scopes": ["submission:create", "submission:read_self"]
}
```

### `GET /api/v1/auth/pats`

查询本人 PAT 列表

### `DELETE /api/v1/auth/pats/{patId}`

撤销 PAT

---

## 8.2 用户模块

### `POST /api/v1/admin/users`

创建用户
支持本地用户或 SSO-only 用户

### `POST /api/v1/admin/users/import`

Excel/CSV 批量导入
响应必须逐行返回处理结果：

```json
{
  "successCount": 97,
  "failCount": 3,
  "errors": [
    {
      "rowNo": 12,
      "code": "USER_DUPLICATE_ACADEMIC_ID",
      "message": "学号重复"
    }
  ]
}
```

### `GET /api/v1/admin/users`

条件分页：

* username
* realName
* academicId
* identityType
* accountStatus
* unitId
* roleCode

### `GET /api/v1/admin/users/{userId}`

返回：

* 基础账号信息
* 教务画像
* 角色绑定
* 组织成员关系
* 最近登录信息

### `PATCH /api/v1/admin/users/{userId}/status`

请求：

```json
{
  "targetStatus": "DISABLED",
  "reason": "毕业后停用"
}
```

### `POST /api/v1/admin/users/{userId}/roles`

为用户授予角色绑定

### `DELETE /api/v1/admin/users/{userId}/roles/{bindingId}`

撤销角色绑定

---

## 8.3 组织模块

### `POST /api/v1/admin/org-units`

创建节点

请求：

```json
{
  "unitType": "CLASS",
  "unitCode": "SE2026-1",
  "unitName": "软件工程 1 班",
  "parentUnitId": 22001
}
```

强校验：

* 父子层级合法
* 编码唯一
* 当前操作者对父节点有治理权限

### `GET /api/v1/admin/org-units/tree`

按当前用户作用域裁剪返回局部树

### `POST /api/v1/admin/org-units/{unitId}/relations`

创建课程-学院跨挂、班级-小组关系

### `POST /api/v1/admin/org-units/{unitId}/members`

添加成员关系
请求：

```json
{
  "userId": 1001,
  "membershipType": "ENROLLED"
}
```

### `DELETE /api/v1/admin/org-units/{unitId}/members/{membershipId}`

移除成员关系

---

## 8.4 IAM 模块

### `GET /api/v1/admin/roles`

### `GET /api/v1/admin/permissions`

### `GET /api/v1/admin/users/{userId}/effective-permissions`

返回用户在指定 `scopeUnitId` 下的有效权限集合，供前端做按钮显隐和调试。

---

## 8.5 审计模块

### `GET /api/v1/admin/audit-logs`

查询条件：

* operatorUserId
* actionCode
* targetType
* result
* scopeUnitId
* startAt / endAt

权限：

* `SCHOOL_ADMIN` 查全量
* `INSTRUCTOR` 查本课程作用域
* `TA` 默认不可查全量人工改分日志，需额外授权

---

## 8.6 平台配置模块

### `GET /api/v1/admin/platform-config/current`

### `PUT /api/v1/admin/platform-config/current`

建议配置项：

* `ENABLE_PUBLIC_REGISTRATION`
* `GLOBAL_EXPERIMENT_MAINTENANCE_MODE`
* `DEFAULT_ACCESS_TOKEN_TTL_SECONDS`
* `DEFAULT_REFRESH_TOKEN_TTL_DAYS`
* `MAX_LOGIN_FAIL_COUNT`
* `ACCOUNT_LOCK_MINUTES`

---

## 9. 关键流程

## 9.1 登录流程

1. auth-controller 收到登录请求
2. auth-service 查 `sys_user`
3. 检查 `account_status`
4. 校验密码
5. 成功则清空失败计数，写登录审计
6. 生成 Access Token
7. 生成 Refresh Session 并写 Cookie
8. 返回用户基本信息与角色快照

## 9.2 用户导入流程

1. 上传 CSV/Excel
2. user-import-service 逐行校验
3. 合法行：

   * 创建/更新 `sys_user`
   * 创建/更新 `academic_profile`
   * 建立 `user_org_membership`
   * 建立 `iam_user_role_binding`
4. 每行结果落导入报告
5. 成功行发布 `UserEnrolledInClassEvent`

## 9.3 学生启动实验流程

1. 前端调用实验模块 `POST /experiments/{id}/start`
2. 实验模块调用 `AuthorizationFacade`
3. 鉴权依次检查：

   * 用户状态
   * 是否有 `experiment:start`
   * 是否属于该课程/班级
   * 时间窗口
   * IP 白名单
   * 配额余额
4. 通过后创建实验实例
5. 写 `EXPERIMENT_START` 审计
6. 更新配额占用

## 9.4 改分流程

1. 教师/助教调用 `PATCH /grades/{submissionId}`
2. 检查 `grade:override`
3. 若操作者为 TA，再检查是否被授予该班级/作业范围
4. 落成绩变更前后值
5. 写 `GRADE_OVERRIDE` 审计
6. 发布 `GradeOverriddenEvent`

---

## 10. 领域事件契约

建议统一事件头：

```json
{
  "eventId": "evt_xxx",
  "eventType": "UserEnrolledInClassEvent",
  "occurredAt": "2026-04-15T10:00:00",
  "operatorUserId": 9001,
  "aggregateId": 1001,
  "payload": {}
}
```

首批事件：

### `UserEnrolledInClassEvent`

payload：

* `userId`
* `classUnitId`
* `courseUnitId`
* `academicId`

下游：

* 实验模块预创建仓库/目录
* 作业模块初始化学生作业视图

### `UserStatusSuspendedEvent`

payload：

* `userId`
* `oldStatus`
* `newStatus`
* `reason`

下游：

* 销毁运行中实验
* 撤销活跃会话

### `CourseNodeArchivedEvent`

payload：

* `courseUnitId`
* `archivedBy`
* `archivedAt`

下游：

* 作业提交通道切只读
* 评测任务停止受理
* 实验镜像停止新实例分配

### `GradeOverriddenEvent`

payload：

* `submissionId`
* `oldScore`
* `newScore`
* `operatorUserId`

下游：

* 触发成绩统计重算
* 通知模块生成消息

---

## 11. 安全设计

### 11.1 Token 设计

* Access Token：JWT，2 小时
* Refresh Token：7 天，HTTP-Only Cookie
* PAT：仅摘要存库，可单独撤销

### 11.2 密码

* 推荐 Argon2id 或 bcrypt
* 密码修改后提升 `password_version`
* JWT 中带 `pwdv`，用于强制旧 Token 失效

### 11.3 防爆破

* 连续失败 5 次锁定 30 分钟
* 同时保留用户名维度和 IP 维度限流
* 失败事件必须写审计

### 11.4 数据隔离

* 所有查询必须带 `ScopeConstraint`
* Repository 层不得手写绕过作用域的查询
* 管理端查询统一通过 `ScopedQueryBuilder`

---

## 12. 非功能要求

### 12.1 性能

* 登录接口 P95 < 300ms
* 用户分页查询 P95 < 500ms
* 权限决策单次 < 50ms
* 审计查询支持分页与时间范围索引

### 12.2 稳定性

* 关键写操作必须事务化
* 领域事件采用 `afterCommit + outbox`
* 审计日志写入失败不得影响主业务成功，但需记录告警

### 12.3 可观测性

* 每次请求生成 `requestId`
* 全链路 `traceId`
* 安全事件告警：

  * 高频登录失败
  * 异常 PAT 使用
  * 短时间高频提交代码
  * 非白名单 IP 尝试考试实验

---

## 13. 开发顺序建议

### Phase 1：一期最小可用

* 用户、画像、组织树
* JWT 登录/刷新/登出
* 角色绑定与作用域裁剪
* 管理员用户管理
* 学生/教师基础登录与访问
* 审计基础能力

### Phase 2：教学增强

* SSO 对接
* PAT
* 批量导入与主数据同步
* ABAC 时间/IP/配额策略
* 课程作用域审计

### Phase 3：稳定性增强

* outbox
* 安全告警
* 配额精算
* 归档只读策略
* 更细粒度 TA 权限

---

## 14. 可以直接交给研发的拆分结果

后端可直接拆成这些任务：

* `AUTH-01` 登录/刷新/登出
* `AUTH-02` PAT 管理
* `AUTH-03` SSO 绑定
* `USER-01` 用户 CRUD
* `USER-02` 批量导入
* `ORG-01` 组织树 CRUD
* `ORG-02` 成员关系管理
* `IAM-01` 角色权限表初始化
* `IAM-02` 作用域鉴权中间层
* `POLICY-01` 时间/IP/配额策略
* `AUDIT-01` 审计日志写入
* `AUDIT-02` 审计查询
* `INTEGRATION-01` 用户入班事件
* `INTEGRATION-02` 用户冻结事件
* `INTEGRATION-03` 课程归档事件

前端可直接拆成：

* 登录页 / PAT 管理页
* 用户列表 / 详情页
* 批量导入结果页
* 组织树页
* 用户角色绑定弹窗
* 审计日志查询页
* 平台配置页

测试可直接写：

* 登录成功/失败/锁定
* 作用域越权
* PAT 吊销
* 学生数据隔离
* 课程归档只读
* TA 改分边界
* 审计完整性
