# 作业系统

## 目标

交付 assignment 第一切片，使课程系统具备教师创建、发布、关闭作业，以及学生按课程/教学班查看已发布作业的能力，为后续 submission、judge 和 grading 链路提供稳定上游。

## 覆盖范围

### 功能范围

- 教师在开课实例下创建作业
- 作业可绑定整个开课实例，也可绑定某个教学班
- 作业状态流转：`DRAFT -> PUBLISHED -> CLOSED`
- 教师按开课实例查询作业列表与详情
- 学生、助教、教师按课程成员关系查看自己有权访问的已发布作业
- 关键状态变更写入审计日志

### 不在范围

- 作业附件、题目文件、Rubric、评分项
- 重新提交策略扩展、提交查重
- 自动评测编排、实验环境联动、成绩回填
- 教师修改已创建作业正文与规则
- 课程资源、公告、讨论正文

## 核心业务规则

1. 作业必须挂在 `course_offerings` 下，不能直接挂到课程模板。
2. `teachingClassId` 为空表示课程公共作业；非空表示仅该教学班可见。
3. 只有具备课程教师侧管理权限的用户才能创建、发布、关闭作业。
4. 作业创建后默认是 `DRAFT`，草稿对学生不可见。
5. 只有 `DRAFT` 状态的作业可以发布。
6. 草稿不能直接关闭；已发布作业可以关闭。
7. 学生只能查看自己所在开课实例内、且班级范围匹配的非草稿作业。
8. 作业必须提供开放时间、截止时间和正整数提交次数上限，且截止时间不能早于开放时间。

## 核心数据模型

- `assignments`
  - `offering_id`：所属开课实例
  - `teaching_class_id`：可选，限定作业可见范围
  - `status`：`DRAFT / PUBLISHED / CLOSED`
  - `open_at / due_at`：开放与截止时间
  - `max_submissions`：提交次数上限
  - `published_at / closed_at`：状态时间戳
- `course_members`
  - 继续作为课程域授权来源
- `audit_logs`
  - 记录 `ASSIGNMENT_CREATED / ASSIGNMENT_PUBLISHED / ASSIGNMENT_CLOSED`

详细字段以 [../generated/db-schema.md](../generated/db-schema.md) 为准。

## 角色边界

### 学校管理员 / 学院管理员

- 当前不直接参与作业管理
- 继续承担平台和课程主数据治理职责

### 教师

- 创建课程公共作业或教学班专属作业
- 查看自己可管理课程下的作业列表与详情
- 发布和关闭作业

### 助教

- 当前可通过“我的作业”查看自己所在课程/教学班的已发布作业
- 当前不具备创建、发布、关闭作业权限

### 学生

- 通过“我的作业”查看自己有权访问的已发布作业
- 不可查看草稿，不可管理作业

## API 边界

### 教师侧

- `POST /api/v1/teacher/course-offerings/{offeringId}/assignments`
- `GET /api/v1/teacher/course-offerings/{offeringId}/assignments`
- `GET /api/v1/teacher/assignments/{assignmentId}`
- `POST /api/v1/teacher/assignments/{assignmentId}/publish`
- `POST /api/v1/teacher/assignments/{assignmentId}/close`

### 我的作业

- `GET /api/v1/me/assignments`
- `GET /api/v1/me/assignments/{assignmentId}`

## 当前实现边界

- assignment 仍是第一切片，暂不支持更新、删除和复制作业。
- 学生正式提交已由 [submission-system.md](submission-system.md) 承接，assignment 保持作业主数据上游边界。
- 教学班功能开关中的 `assignment_enabled` 目前只作为课程域配置位保留，尚未在 assignment 接口层强制拦截。
- 助教当前沿用课程成员可见性规则，但不具备教师管理能力；更细的 staff scope 留待后续扩展。

## 验收标准

- 教师可创建课程公共作业和教学班专属作业。
- 教师可将草稿作业发布，并可关闭已发布作业。
- 学生只能看到自己有权访问的已发布作业，无法读取其他班级作业或草稿作业。
- 学生无法调用教师侧作业管理接口。
- `./mvnw verify` 提供自动化测试证据。
