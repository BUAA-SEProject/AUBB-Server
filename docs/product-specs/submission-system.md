# 提交系统

## 目标

在 submission 第一切片基础上，继续交付“附件提交切片”，使平台具备学生上传代码 / 文件 / 报告附件、正式提交时关联附件、学生查看本人提交与附件、教师按作业查看提交并下载已关联附件的能力，为后续 judge 和 grading 链路提供稳定输入。

## 覆盖范围

### 功能范围

- 学生对已发布作业发起正式提交
- 学生先上传附件，再在正式提交时关联附件
- 提交受 assignment 可见性、开放时间和最大次数约束
- 学生按作业查看自己的提交列表与详情
- 教师按作业查看提交列表与详情
- 学生下载自己的提交附件
- 教师下载已关联到正式提交的附件
- 提交受理写入审计日志
- 附件上传写入审计日志

### 不在范围

- 在线工作区、草稿恢复、试运行会话
- 工程快照、目录级工作区、草稿同步
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
8. 学生可以先上传附件，再创建正式提交；上传附件本身不计入提交次数。
9. 正式提交时，文本正文和附件不能同时为空。
10. 单个附件只能关联到一次正式提交，不能跨提交复用。
11. 教师只能下载已经关联到正式提交的附件，不读取学生暂存中的未关联附件。
12. 当前每次正式提交最多关联 10 个附件，每个附件最大 20MB。

## 核心数据模型

- `submissions`
  - `submission_no`：唯一提交编号
  - `assignment_id`：所属作业
  - `offering_id`：冗余所属开课实例，便于后续检索
  - `teaching_class_id`：可选，冗余作业班级范围
  - `submitter_user_id`：提交人
  - `attempt_no`：第几次正式提交
  - `status`：当前固定为 `SUBMITTED`
  - `content_text`：可选文本提交内容
  - `submitted_at`：提交时间
- `submission_artifacts`
  - `assignment_id`：所属作业
  - `submission_id`：关联的正式提交，可先为空后绑定
  - `uploader_user_id`：上传人
  - `object_key`：对象存储键
  - `original_filename` / `content_type` / `size_bytes`：原始元数据
  - `uploaded_at`：附件上传时间
- `assignments`
  - 提供开放时间、截止时间和最大提交次数
- `audit_logs`
  - 记录 `SUBMISSION_CREATED`
  - 记录 `SUBMISSION_ARTIFACT_UPLOADED`

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
- 上传并管理自己用于正式提交的附件
- 查看自己的提交列表与详情
- 不可查看其他学生的提交

## API 边界

### 学生侧

- `POST /api/v1/me/assignments/{assignmentId}/submissions`
- `POST /api/v1/me/assignments/{assignmentId}/submission-artifacts`
- `GET /api/v1/me/assignments/{assignmentId}/submissions`
- `GET /api/v1/me/submissions/{submissionId}`
- `GET /api/v1/me/submission-artifacts/{artifactId}/download`

### 教师侧

- `GET /api/v1/teacher/assignments/{assignmentId}/submissions`
  - 支持 `submitterUserId` 过滤单个学生
  - 支持 `latestOnly=true` 只查看每个学生最新一次提交
- `GET /api/v1/teacher/submissions/{submissionId}`
- `GET /api/v1/teacher/submission-artifacts/{artifactId}/download`

## 当前实现边界

- 当前切片支持文本与附件正式提交，不包含工作区、试运行和目录级工程快照。
- 提交状态当前固定为 `SUBMITTED`，评测态和成绩态留待后续模块扩展。
- 附件当前采用“先上传，再在正式提交时关联”的两阶段模型，不支持草稿恢复。
- 附件下载当前统一走服务端鉴权后再读取对象存储，不直接暴露预签名下载契约。
- 助教当前不具备单独提交查看入口。

## 验收标准

- 学生可对自己有权访问的已发布作业进行正式提交。
- 学生可上传附件并在正式提交时关联附件。
- 超过最大提交次数、早于开放时间或晚于截止时间的提交会被拒绝。
- 学生只能查看自己的提交。
- 教师可按作业查看提交列表与详情，并下载已关联附件。
- `./mvnw verify` 提供自动化测试证据。
