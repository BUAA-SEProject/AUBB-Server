# 一体化在线教学与实验平台

# 课程系统详细设计（可开发版，模块化单体）

## 1. 目标与范围

课程系统是平台的教学主干模块，负责承载课程从创建、开课、排班、成员管理、教学资源组织，到结课归档的全生命周期管理，为作业、实验、自动评测、成绩统计等下游模块提供统一的课程主数据与业务边界。

本设计覆盖：

* 课程主数据管理
* 课程开设与学期实例管理
* 教学班与分组组织
* 教师/助教/学生的课程成员关系
* 课程资源目录与发布控制
* 课程设置、时间窗口与教学规则
* 课程状态流转与归档
* 对作业、实验、评测、成绩模块的集成接口与事件

本设计**不直接实现**：

* 作业题目与提交
* 在线实验运行时
* 自动评测判题
* 成绩计算明细

但会为这些模块提供统一的课程边界、规则配置和可追踪事件。

---

## 2. 设计目标

课程系统应满足以下目标：

1. **一课多开**
   支持同一门课程在不同学期、多学院、多教学班重复开设。

2. **教务真实映射**
   支持课程模板、开课实例、教学班、课程分组的层级关系，适配真实高校教学组织方式。

3. **课程是业务核心边界**
   作业、实验、评测、成绩等都应绑定到课程开设实例，而不是直接绑定课程模板。

4. **配置可控**
   课程支持设置选课规则、实验开放时间、资源可见性、归档策略、助教授权边界等。

5. **支持模块化单体演进**
   当前单体部署，但模块边界清晰，后续可平滑拆分。

---

## 3. 架构选型：模块化单体

## 3.1 模块划分

课程系统建议拆为以下子模块：

1. **course-catalog-module**
   课程模板、课程编码、课程元数据

2. **course-offering-module**
   学期开课实例、教学安排、课程状态流转

3. **course-class-module**
   教学班、课程分组、小组治理

4. **course-member-module**
   教师、助教、学生的课程成员关系管理

5. **course-content-module**
   课程公告、教学资源目录、课程首页配置

6. **course-rule-module**
   时间窗口、选课规则、归档规则、课程可见性规则

7. **course-integration-module**
   对作业、实验、评测、成绩模块的接口与事件适配

8. **course-query-module**
   面向教师端、学生端、管理端的课程聚合查询

## 3.2 对外依赖

课程系统依赖以下基础模块：

* `user-module`：用户基础信息
* `org-module`：学院、课程、班级组织映射
* `iam-module`：角色与作用域权限
* `policy-module`：ABAC 规则执行
* `audit-module`：审计写入
* `platform-config-module`：平台级配置

---

## 4. 核心领域模型

课程系统必须区分三个层级：

* **课程模板（Course Catalog）**：如《数据结构》
* **课程开设实例（Course Offering）**：如“2026 春 数据结构 A 班”
* **教学班/课程分组（Teaching Class / Group）**：某个开设实例下的班级与分组

这是课程系统能否支撑真实开发的关键。

---

## 5. 数据模型设计

## 5.1 课程模板

### `course_catalog`

用于表达“课程本体”。

字段建议：

* `catalog_id` bigint, PK
* `course_code` varchar(64), unique
  如 `CS101`
* `course_name` varchar(128)
* `course_name_en` varchar(128), nullable
* `course_type` enum(`REQUIRED`,`ELECTIVE`,`GENERAL`,`PRACTICE`)
* `credit` decimal(4,1)
* `total_hours` int
* `department_unit_id` bigint
  主属学院/系
* `description` text
* `status` enum(`ACTIVE`,`DISABLED`)
* `created_at`
* `updated_at`

说明：

* 这是课程定义，不含学期、教师、学生、具体开课信息
* 同一门课跨学期复用同一 `catalog_id`

---

## 5.2 学期主数据

### `academic_term`

字段建议：

* `term_id` bigint, PK
* `term_code` varchar(32), unique
  如 `2026-SPRING`
* `term_name` varchar(64)
* `school_year` varchar(16)
  如 `2025-2026`
* `semester` enum(`SPRING`,`SUMMER`,`AUTUMN`,`WINTER`)
* `start_date` date
* `end_date` date
* `status` enum(`PLANNING`,`ONGOING`,`ENDED`,`ARCHIVED`)
* `created_at`
* `updated_at`

---

## 5.3 课程开设实例

### `course_offering`

这是课程系统最核心的业务表。

字段建议：

* `offering_id` bigint, PK
* `catalog_id` bigint
* `term_id` bigint
* `offering_code` varchar(64), unique
  如 `CS101-2026SP-01`
* `offering_name` varchar(128)
  如 `数据结构（2026春）`
* `primary_college_unit_id` bigint
* `org_course_unit_id` bigint
  对应组织模块中的 COURSE 节点
* `delivery_mode` enum(`ONLINE`,`OFFLINE`,`HYBRID`)
* `language` enum(`ZH`,`EN`,`BILINGUAL`)
* `capacity` int
* `selected_count` int
* `cover_image_url` varchar(255), nullable
* `intro` text, nullable
* `status` enum(
  `DRAFT`,
  `PUBLISHED`,
  `ENROLLING`,
  `ONGOING`,
  `FROZEN`,
  `ENDED`,
  `ARCHIVED`
  )
* `publish_at` datetime, nullable
* `start_at` datetime, nullable
* `end_at` datetime, nullable
* `archived_at` datetime, nullable
* `created_by`
* `created_at`
* `updated_at`

说明：

* 一门课每学期开一次或多次，每次对应一条 `course_offering`
* 下游作业/实验/成绩都应绑定 `offering_id`

---

## 5.4 课程跨学院映射

### `course_offering_college_map`

字段建议：

* `id`
* `offering_id`
* `college_unit_id`
* `relation_type` enum(`PRIMARY`,`SECONDARY`,`CROSS_LISTED`)
* `created_at`

用于：

* 通识课挂多个学院
* 联合授课课程的学院归属展示与治理

---

## 5.5 教学班

### `teaching_class`

字段建议：

* `teaching_class_id` bigint, PK
* `offering_id` bigint
* `class_code` varchar(64)
* `class_name` varchar(128)
* `org_class_unit_id` bigint
  对应组织模块 CLASS 节点
* `capacity` int
* `status` enum(`ACTIVE`,`FROZEN`,`ARCHIVED`)
* `schedule_summary` varchar(255), nullable
* `created_at`
* `updated_at`

唯一约束建议：

* `(offering_id, class_code)` unique

说明：

* 一门课程开设实例可包含多个教学班
* 学生默认归属于具体教学班，而不是只归属课程实例

---

## 5.6 课程分组

### `course_group`

字段建议：

* `group_id`
* `offering_id`
* `teaching_class_id`, nullable
* `group_code`
* `group_name`
* `group_type` enum(`PROJECT`,`LAB`,`DISCUSSION`)
* `leader_user_id`, nullable
* `max_members`
* `status` enum(`ACTIVE`,`FROZEN`,`ARCHIVED`)
* `created_at`
* `updated_at`

说明：

* 用于团队实验、课程项目、小组讨论
* 可挂在教学班下，也可直接挂在课程实例下

---

## 5.7 课程成员关系

### `course_member`

这是课程系统自己的业务成员表，不直接复用通用 `user_org_membership`，但需与之保持同步。

字段建议：

* `course_member_id`
* `offering_id`
* `teaching_class_id`, nullable
* `group_id`, nullable
* `user_id`
* `member_role` enum(`INSTRUCTOR`,`TA`,`STUDENT`,`OBSERVER`)
* `member_status` enum(`PENDING`,`ACTIVE`,`DROPPED`,`COMPLETED`,`REMOVED`)
* `joined_at`
* `left_at`, nullable
* `source_type` enum(`MANUAL`,`IMPORT`,`SYNC`,`SELF_ENROLL`)
* `remark`, nullable
* `created_at`
* `updated_at`

唯一约束建议：

* 对教师/助教：`(offering_id, user_id, member_role)` unique
* 对学生：`(offering_id, user_id)` unique

说明：

* 课程系统需要独立成员表，便于高频查询“某课程有哪些学生/老师”
* 同时要同步到组织与 IAM

---

## 5.8 课程教师授权

### `course_staff_scope`

用于表达教师/助教的细粒度授权范围。

字段建议：

* `staff_scope_id`
* `offering_id`
* `user_id`
* `staff_role` enum(`INSTRUCTOR`,`TA`)
* `scope_type` enum(`ALL`,`CLASS`,`GROUP`,`RESOURCE`,`ASSIGNMENT`)
* `scope_ref_id`
* `permissions_json`
* `created_by`
* `created_at`

用途：

* 某助教只能看某个班
* 某助教只能批改某部分作业
* 某教师只能负责资源管理，不可发布最终成绩

---

## 5.9 课程内容与资源

### `course_content_item`

课程内容统一抽象表。

字段建议：

* `content_id`
* `offering_id`
* `content_type` enum(`ANNOUNCEMENT`,`RESOURCE`,`LINK`,`PAGE`)
* `title`
* `summary`
* `body_text`, nullable
* `file_ref_id`, nullable
* `external_url`, nullable
* `visibility` enum(`DRAFT`,`TEACHERS_ONLY`,`COURSE_MEMBERS`,`PUBLIC`)
* `publish_at`, nullable
* `sort_order`
* `created_by`
* `created_at`
* `updated_at`

### `course_resource_folder`

用于课程资源树目录。

字段建议：

* `folder_id`
* `offering_id`
* `parent_folder_id`, nullable
* `folder_name`
* `visibility`
* `sort_order`
* `created_by`
* `created_at`

---

## 5.10 课程规则与配置

### `course_offering_config`

课程实例配置表。

字段建议：

* `config_id`
* `offering_id`, unique
* `allow_self_enroll` boolean
* `allow_drop_course` boolean
* `allow_group_switch` boolean
* `require_real_name` boolean
* `allow_student_view_classmates` boolean
* `default_resource_visibility` enum
* `enable_course_homepage` boolean
* `enable_announcement_push` boolean
* `enable_attendance` boolean
* `enable_lab` boolean
* `enable_assignment` boolean
* `enable_auto_grading` boolean
* `created_at`
* `updated_at`

### `course_time_window`

课程级时间窗口统一表。

字段建议：

* `window_id`
* `offering_id`
* `window_type` enum(
  `COURSE_VISIBLE`,
  `ENROLLMENT`,
  `RESOURCE_VISIBLE`,
  `LAB_OPEN`,
  `SUBMISSION_OPEN`,
  `GRADE_VISIBLE`
  )
* `start_at`
* `end_at`
* `status` enum(`ACTIVE`,`DISABLED`)
* `remark`
* `created_by`
* `created_at`

---

## 6. 状态机设计

## 6.1 课程开设实例状态机

推荐状态：

* `DRAFT`
* `PUBLISHED`
* `ENROLLING`
* `ONGOING`
* `FROZEN`
* `ENDED`
* `ARCHIVED`

流转规则：

* 新建课程：`DRAFT`
* 对外发布课程主页与信息：`DRAFT -> PUBLISHED`
* 开始选课：`PUBLISHED -> ENROLLING`
* 开课后：`ENROLLING/PUBLISHED -> ONGOING`
* 临时停课或冻结提交：`ONGOING -> FROZEN`
* 学期结束：`ONGOING/FROZEN -> ENDED`
* 完成归档：`ENDED -> ARCHIVED`

约束：

* `DRAFT` 不可见给学生
* `PUBLISHED` 可见但未必可选
* `ENROLLING` 才允许选课/导入学生
* `ONGOING` 才允许课程教学业务全面开启
* `FROZEN` 下禁止提交类写操作
* `ARCHIVED` 只读

## 6.2 教学班状态

* `ACTIVE`
* `FROZEN`
* `ARCHIVED`

## 6.3 课程成员状态

* `PENDING`
* `ACTIVE`
* `DROPPED`
* `COMPLETED`
* `REMOVED`

规则：

* 自主选课可先为 `PENDING`，审核通过后 `ACTIVE`
* 退课为 `DROPPED`
* 结课后学生自动转 `COMPLETED`

---

## 7. 权限模型

课程系统权限继续沿用**RBAC + Scoped ABAC**。

## 7.1 课程系统核心权限点

建议权限命名：

* `course_catalog:create`
* `course_catalog:update`
* `course_catalog:read`
* `course_offering:create`
* `course_offering:update`
* `course_offering:publish`
* `course_offering:archive`
* `course_offering:read`
* `course_member:add`
* `course_member:remove`
* `course_member:import`
* `course_member:read`
* `course_class:create`
* `course_class:update`
* `course_group:create`
* `course_group:update`
* `course_content:create`
* `course_content:update`
* `course_content:publish`
* `course_content:delete`
* `course_config:update`
* `course_audit:read_scoped`

## 7.2 角色与能力边界

### SCHOOL_ADMIN

* 全局课程模板与开课治理
* 学期管理
* 全量课程审计

### COLLEGE_ADMIN

* 本学院课程模板与开课实例治理
* 可查看学院范围课程成员与课程状态

### COURSE_ADMIN

* 本课程节点治理
* 课程信息维护、班级开设、成员导入

### INSTRUCTOR

* 本课程实例教学负责人
* 可管理课程内容、公告、资源、助教、教学班
* 可配置课程规则、时间窗口
* 可发布课程主页与教学资源

### TEACHING_ASSISTANT

* 默认只读课程配置
* 可在授权范围内查看学生名单、发布班级公告、维护部分资源
* 不可归档课程
* 不可修改课程核心元数据，除非被额外授权

### STUDENT

* 查看已加入课程
* 查看课程资源、公告、教学安排
* 在时间窗口内加入/退课、选组
* 不可修改课程配置

---

## 8. ABAC 规则设计

课程系统首批 ABAC 规则：

### 规则 1：课程可见性

资源：课程主页、资源目录、公告
判断：

* `DRAFT`：仅教师/管理者可见
* `PUBLISHED`：按 `COURSE_VISIBLE` 时间窗口与 visibility 控制
* `ARCHIVED`：仅课程成员与管理员可查看历史内容

### 规则 2：选课时间窗

资源：加入课程
条件：

* 当前课程状态为 `ENROLLING`
* 当前时间命中 `ENROLLMENT` 窗口
* 未超过容量上限

拒绝码：

* `COURSE_ENROLLMENT_CLOSED`
* `COURSE_CAPACITY_EXCEEDED`

### 规则 3：归档保护

资源：所有写操作
条件：

* `ARCHIVED` 下全部拒绝
* `ENDED` 下仅允许少量收尾操作

### 规则 4：助教授权边界

资源：班级、资源、学生列表
条件：

* 助教必须命中 `course_staff_scope`
* 否则只允许查看基础课程信息，不允许进入班级/成员/资源写接口

### 规则 5：学生数据隔离

学生查看课程成员时，若配置 `allow_student_view_classmates = false`，则只可看到教师列表与本人信息，不能看到完整学生名册。

---

## 9. 接口设计

---

## 9.1 课程模板接口

### `POST /api/v1/admin/course-catalogs`

创建课程模板

请求：

```json
{
  "courseCode": "CS101",
  "courseName": "数据结构",
  "courseType": "REQUIRED",
  "credit": 3.0,
  "totalHours": 48,
  "departmentUnitId": 2001,
  "description": "面向本科一年级的数据结构基础课程"
}
```

### `GET /api/v1/admin/course-catalogs`

分页查询：

* courseCode
* courseName
* courseType
* departmentUnitId
* status

### `GET /api/v1/admin/course-catalogs/{catalogId}`

### `PUT /api/v1/admin/course-catalogs/{catalogId}`

### `PATCH /api/v1/admin/course-catalogs/{catalogId}/status`

---

## 9.2 学期接口

### `POST /api/v1/admin/academic-terms`

### `GET /api/v1/admin/academic-terms`

### `GET /api/v1/admin/academic-terms/{termId}`

### `PUT /api/v1/admin/academic-terms/{termId}`

---

## 9.3 课程开设实例接口

### `POST /api/v1/admin/course-offerings`

创建课程开设实例

请求：

```json
{
  "catalogId": 101,
  "termId": 202601,
  "offeringCode": "CS101-2026SP-01",
  "offeringName": "数据结构（2026春）",
  "primaryCollegeUnitId": 2001,
  "deliveryMode": "HYBRID",
  "language": "ZH",
  "capacity": 120,
  "startAt": "2026-02-20T08:00:00",
  "endAt": "2026-06-30T23:59:59"
}
```

响应：

```json
{
  "code": "SUCCESS",
  "message": "ok",
  "data": {
    "offeringId": 30001,
    "status": "DRAFT"
  }
}
```

### `GET /api/v1/admin/course-offerings`

查询条件：

* catalogId
* termId
* primaryCollegeUnitId
* status
* keyword

### `GET /api/v1/admin/course-offerings/{offeringId}`

### `PUT /api/v1/admin/course-offerings/{offeringId}`

可更新：

* 名称
* 介绍
* 容量
* 封面
* 起止时间
* 授课方式
* 语言

### `PATCH /api/v1/admin/course-offerings/{offeringId}/publish`

将课程从 `DRAFT` 置为 `PUBLISHED`

### `PATCH /api/v1/admin/course-offerings/{offeringId}/start-enrollment`

置为 `ENROLLING`

### `PATCH /api/v1/admin/course-offerings/{offeringId}/start`

置为 `ONGOING`

### `PATCH /api/v1/admin/course-offerings/{offeringId}/freeze`

置为 `FROZEN`

### `PATCH /api/v1/admin/course-offerings/{offeringId}/end`

置为 `ENDED`

### `PATCH /api/v1/admin/course-offerings/{offeringId}/archive`

置为 `ARCHIVED`

---

## 9.4 教学班接口

### `POST /api/v1/admin/course-offerings/{offeringId}/classes`

创建教学班

请求：

```json
{
  "classCode": "A01",
  "className": "A 班",
  "capacity": 60,
  "scheduleSummary": "周二 1-2 节，周四 3-4 节"
}
```

### `GET /api/v1/admin/course-offerings/{offeringId}/classes`

### `PUT /api/v1/admin/course-classes/{teachingClassId}`

### `PATCH /api/v1/admin/course-classes/{teachingClassId}/status`

---

## 9.5 课程成员接口

### `POST /api/v1/admin/course-offerings/{offeringId}/members`

手动添加成员

请求：

```json
{
  "userId": 10001,
  "memberRole": "STUDENT",
  "teachingClassId": 40001
}
```

### `POST /api/v1/admin/course-offerings/{offeringId}/members/import`

批量导入成员

支持字段：

* academicId
* realName
* memberRole
* classCode

返回逐行处理结果。

### `GET /api/v1/admin/course-offerings/{offeringId}/members`

条件：

* role
* status
* classId
* keyword

### `DELETE /api/v1/admin/course-members/{courseMemberId}`

移除成员

### `PATCH /api/v1/admin/course-members/{courseMemberId}/status`

修改成员状态

### `POST /api/v1/admin/course-staff-scopes`

配置助教/教师细粒度授权

---

## 9.6 学生侧选课接口

### `GET /api/v1/student/course-offerings/available`

查询当前可加入课程

过滤：

* termId
* keyword
* collegeUnitId

### `POST /api/v1/student/course-offerings/{offeringId}/enroll`

学生自主选课

### `POST /api/v1/student/course-offerings/{offeringId}/drop`

退课

### `POST /api/v1/student/course-groups/{groupId}/join`

加入课程小组

### `POST /api/v1/student/course-groups/{groupId}/quit`

退出课程小组

---

## 9.7 课程内容接口

### `POST /api/v1/teacher/course-offerings/{offeringId}/contents`

创建课程内容

请求：

```json
{
  "contentType": "ANNOUNCEMENT",
  "title": "第一次上课通知",
  "bodyText": "请提前安装实验环境",
  "visibility": "COURSE_MEMBERS",
  "publishAt": "2026-02-19T18:00:00"
}
```

### `GET /api/v1/course-offerings/{offeringId}/contents`

根据当前用户身份自动裁剪可见内容

### `PUT /api/v1/teacher/course-contents/{contentId}`

### `PATCH /api/v1/teacher/course-contents/{contentId}/publish`

### `DELETE /api/v1/teacher/course-contents/{contentId}`

---

## 9.8 课程配置接口

### `GET /api/v1/admin/course-offerings/{offeringId}/config`

### `PUT /api/v1/admin/course-offerings/{offeringId}/config`

### `GET /api/v1/admin/course-offerings/{offeringId}/time-windows`

### `POST /api/v1/admin/course-offerings/{offeringId}/time-windows`

### `PUT /api/v1/admin/course-time-windows/{windowId}`

### `DELETE /api/v1/admin/course-time-windows/{windowId}`

---

## 9.9 聚合查询接口

### `GET /api/v1/me/courses`

返回当前用户参与的课程列表
按用户角色自动聚合：

* 教师：我教授的课程
* 助教：我协助的课程
* 学生：我已选课程

### `GET /api/v1/course-offerings/{offeringId}/dashboard`

根据角色返回课程首页聚合数据：

教师端：

* 学生人数
* 班级数
* 未发布资源数
* 即将开始时间窗
* 最近公告

学生端：

* 课程简介
* 课程老师
* 公告
* 资源目录
* 自己所属班级/小组

---

## 10. 关键流程

## 10.1 创建开课实例流程

1. 管理员选择课程模板与学期
2. 创建 `course_offering`
3. 同步创建 `org_course_unit_id`
4. 写审计日志 `COURSE_OFFERING_CREATED`
5. 发布 `CourseOfferingCreatedEvent`

## 10.2 发布课程流程

1. 教师/管理员补齐课程信息
2. 设置课程可见时间窗
3. 调用发布接口
4. 状态变为 `PUBLISHED`
5. 写审计日志
6. 学生侧课程大厅开始可见

## 10.3 成员导入流程

1. 上传名单
2. 逐行校验
3. 建立 `course_member`
4. 同步：

   * `user_org_membership`
   * `iam_user_role_binding`
5. 发布：

   * `StudentJoinedCourseEvent`
   * `TeacherAssignedToCourseEvent`

## 10.4 学生选课流程

1. 检查课程状态是否 `ENROLLING`
2. 检查选课时间窗
3. 检查容量
4. 检查是否重复选课
5. 创建 `course_member`
6. `selected_count + 1`
7. 写审计日志 `COURSE_ENROLLED`

## 10.5 课程归档流程

1. 检查课程是否 `ENDED`
2. 课程转 `ARCHIVED`
3. 冻结所有课程写操作
4. 发布 `CourseOfferingArchivedEvent`
5. 下游作业/实验/评测/成绩模块切换为只读历史态

---

## 11. 领域事件设计

统一事件头：

```json
{
  "eventId": "evt_xxx",
  "eventType": "CourseOfferingCreatedEvent",
  "occurredAt": "2026-04-15T12:00:00",
  "operatorUserId": 9001,
  "aggregateId": 30001,
  "payload": {}
}
```

首批事件如下：

### `CourseOfferingCreatedEvent`

payload：

* `offeringId`
* `catalogId`
* `termId`
* `orgCourseUnitId`

下游：

* 初始化课程空间
* 初始化课程默认配置

### `CourseOfferingPublishedEvent`

payload：

* `offeringId`
* `publishedAt`

下游：

* 学生端课程大厅可见
* 通知模块可发订阅消息

### `StudentJoinedCourseEvent`

payload：

* `offeringId`
* `teachingClassId`
* `userId`

下游：

* 作业模块初始化学生作业视图
* 实验模块初始化实验工作区
* 成绩模块初始化成绩记录骨架

### `TeachingAssistantAssignedEvent`

payload：

* `offeringId`
* `userId`
* `scopeJson`

下游：

* 作业/实验模块刷新助教授权

### `CourseOfferingArchivedEvent`

payload：

* `offeringId`
* `archivedAt`

下游：

* 作业模块停止提交
* 实验模块禁止新建实验
* 成绩模块冻结成绩统计

---

## 12. 审计设计

课程系统核心审计事件：

* `COURSE_CATALOG_CREATED`
* `COURSE_CATALOG_UPDATED`
* `COURSE_OFFERING_CREATED`
* `COURSE_OFFERING_PUBLISHED`
* `COURSE_OFFERING_STATUS_CHANGED`
* `COURSE_CLASS_CREATED`
* `COURSE_MEMBER_IMPORTED`
* `COURSE_MEMBER_ADDED`
* `COURSE_MEMBER_REMOVED`
* `COURSE_ENROLLED`
* `COURSE_DROPPED`
* `COURSE_GROUP_CREATED`
* `COURSE_CONFIG_UPDATED`
* `COURSE_CONTENT_CREATED`
* `COURSE_CONTENT_PUBLISHED`
* `COURSE_OFFERING_ARCHIVED`

审计字段建议继承统一 `audit_log` 结构，并补 `extra_json`：

```json
{
  "offeringId": 30001,
  "termId": 202601,
  "memberRole": "STUDENT",
  "classId": 40001
}
```

---

## 13. 与用户系统的协作边界

课程系统不重复造基础能力，应遵循：

### 用户系统负责

* 登录认证
* 用户基础画像
* 通用角色绑定
* 组织节点治理
* 全局安全审计基线

### 课程系统负责

* 课程模板与开课实例
* 教学班与课程成员关系
* 课程资源与规则配置
* 课程状态流转
* 课程维度事件发布

### 协作方式

* 通过应用服务接口查询用户信息
* 通过组织模块创建/关联 COURSE、CLASS、GROUP 节点
* 通过 IAM 生成课程作用域角色绑定
* 通过 audit-module 写统一审计
* 通过 integration-module 发布课程事件

---

## 14. 非功能要求

### 性能

* 我的课程列表 P95 < 300ms
* 课程详情页 P95 < 500ms
* 成员分页查询 P95 < 500ms
* 导入 1000 行学生名单应支持异步处理，10 秒内返回任务结果入口

### 稳定性

* 状态流转必须事务化
* 成员导入采用逐行容错，不全量回滚
* 事件采用 `afterCommit + outbox`

### 可观测性

* 所有状态变更带 `requestId`
* 重要事件打业务指标：

  * 新开课程数
  * 当前进行中课程数
  * 本学期选课人数
  * 归档课程数

---

## 15. 开发顺序建议

## Phase 1：最小可用课程骨架

* 课程模板
* 学期管理
* 课程开设实例
* 教学班
* 课程成员 CRUD
* 我的课程列表
* 课程详情页基础数据

## Phase 2：教学可运营

* 课程内容与公告
* 课程配置与时间窗口
* 学生选课/退课
* 小组管理
* 课程首页聚合

## Phase 3：深度集成

* 与作业、实验、评测、成绩模块联动
* 细粒度助教授权
* 课程归档联动冻结
* 课程统计看板

---

## 16. 研发拆分建议

后端任务：

* `COURSE-CATALOG-01` 课程模板 CRUD
* `COURSE-TERM-01` 学期管理
* `COURSE-OFFERING-01` 开课实例 CRUD
* `COURSE-OFFERING-02` 状态流转
* `COURSE-CLASS-01` 教学班管理
* `COURSE-MEMBER-01` 成员管理
* `COURSE-MEMBER-02` 成员导入
* `COURSE-ENROLL-01` 学生选退课
* `COURSE-CONTENT-01` 公告与资源
* `COURSE-CONFIG-01` 配置与时间窗
* `COURSE-INTEGRATION-01` 课程事件
* `COURSE-QUERY-01` 我的课程 / 课程首页聚合

前端任务：

* 课程模板管理页
* 学期管理页
* 开课实例列表/详情页
* 教学班管理页
* 课程成员管理页
* 批量导入结果页
* 我的课程页
* 课程首页
* 课程设置页
* 公告与资源页

测试任务：

* 课程状态流转合法性
* 选课时间窗校验
* 容量边界
* 助教越权访问
* 课程归档只读
* 导入部分失败不影响成功行
* 学生名单隔离规则

---

## 17. 最终判断

按这版设计，课程系统已经从“概念设计”变成了**可直接指导开发的详细设计**。
它已经补齐了研发最需要的几类信息：

* 课程领域分层
* 表结构与主从关系
* 状态机
* 权限边界
* ABAC 规则
* 接口契约
* 关键时序
* 与用户系统的协作边界
* 事件联动方式
* 开发拆分建议

下一步最自然的顺序不是再回头补课程概念，而是继续补下面四份中的一份，使教学主链路完整起来：

1. **作业系统详细设计**
2. **在线实验系统详细设计**
3. **自动评测系统详细设计**
4. **成绩系统详细设计**

我建议优先写 **作业系统**，因为它正好位于课程系统和评测/成绩系统之间。
