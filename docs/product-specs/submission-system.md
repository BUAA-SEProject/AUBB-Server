# 提交系统

## 目标

交付 submission 第一切片，使平台具备学生对已发布作业进行正式提交、学生查看本人提交，以及教师按作业查看提交的能力，为后续 judge 和 grading 链路提供稳定输入。

## 覆盖范围

### 功能范围

- 学生对已发布作业发起正式提交
- 提交受 assignment 可见性、开放时间和最大次数约束
- 学生按作业查看自己的提交列表与详情
- 教师按作业查看提交列表与详情
- 提交受理写入审计日志

### 不在范围

- 在线工作区、草稿恢复、试运行会话
- 文件上传、工程快照、附件存储
- 自动评测、结果回写、人工批改、成绩计算
- 助教独立批改范围和更细粒度 staff scope

## 核心业务规则

1. 提交必须挂在 `assignments` 下，不能脱离作业单独存在。
2. 只有已发布且处于开放时间窗口内的作业允许创建新提交。
3. 只有学生可以创建正式提交。
4. 提交次数不能超过作业 `maxSubmissions`。
5. 学生只能查看自己的提交。
6. 教师可查看自己有权管理的作业提交。
7. 提交一旦受理，不提供删除能力。
8. 第一切片的提交内容只采用文本正文，不处理文件和工程快照。

## 核心数据模型

- `submissions`
  - `submission_no`：唯一提交编号
  - `assignment_id`：所属作业
  - `offering_id`：冗余所属开课实例，便于后续检索
  - `teaching_class_id`：可选，冗余作业班级范围
  - `submitter_user_id`：提交人
  - `attempt_no`：第几次正式提交
  - `status`：当前固定为 `SUBMITTED`
  - `content_text`：文本提交内容
  - `submitted_at`：提交时间
- `assignments`
  - 提供开放时间、截止时间和最大提交次数
- `audit_logs`
  - 记录 `SUBMISSION_CREATED`

详细字段以 [../generated/db-schema.md](../generated/db-schema.md) 为准。

## 角色边界

### 教师

- 查看自己课程内作业的提交列表与详情
- 当前不在 submission 第一切片内执行批改

### 助教

- 当前不具备独立提交查看入口
- 后续可在批改和 staff scope 切片中扩展

### 学生

- 对自己有权访问的已发布作业进行正式提交
- 查看自己的提交列表与详情
- 不可查看其他学生的提交

## API 边界

### 学生侧

- `POST /api/v1/me/assignments/{assignmentId}/submissions`
- `GET /api/v1/me/assignments/{assignmentId}/submissions`
- `GET /api/v1/me/submissions/{submissionId}`

### 教师侧

- `GET /api/v1/teacher/assignments/{assignmentId}/submissions`
  - 支持 `submitterUserId` 过滤单个学生
  - 支持 `latestOnly=true` 只查看每个学生最新一次提交
- `GET /api/v1/teacher/submissions/{submissionId}`

## 当前实现边界

- 第一切片只支持正式提交，不包含工作区、试运行和文件上传。
- 提交状态当前固定为 `SUBMITTED`，评测态和成绩态留待后续模块扩展。
- 提交内容当前只支持文本正文，不支持结构化文件清单和工程快照。
- 助教当前不具备单独提交查看入口。

## 验收标准

- 学生可对自己有权访问的已发布作业进行正式提交。
- 超过最大提交次数、早于开放时间或晚于截止时间的提交会被拒绝。
- 学生只能查看自己的提交。
- 教师可按作业查看提交列表与详情。
- `./mvnw verify` 提供自动化测试证据。
