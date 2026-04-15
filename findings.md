# 发现与决策

## 当前任务基线

- assignment 第一切片已经具备教师创建、发布、关闭作业，以及学生按课程/教学班查看已发布作业能力。
- 当前最自然的后续主链路是 `submission -> judge -> grading`，其中 `submission` 是评测和成绩的直接上游。
- 现有课程权限已经具备本轮所需的三类主体：
  - 教师 / 课程管理员：可以管理开课实例和作业
  - 学生：可以按课程成员关系查看并参与自己有权访问的作业
  - 助教：当前可见但不承担提交通道主体

## 第一切片建模结论

- 提交作为独立 `submission` 模块落地，而不是继续塞进 `assignment` 模块。
- 提交主归属为 `assignment`，并冗余记录：
  - `offering_id`
  - 可选 `teaching_class_id`
  - `submitter_user_id`
  便于后续按课程、作业、用户维度检索。
- 第一切片状态只保留：
  - `SUBMITTED`
- 第一切片只实现以下字段：
  - 唯一提交编号
  - 所属作业
  - 所属开课实例
  - 可选所属教学班
  - 提交人
  - 尝试次数
  - 文本提交内容
  - 提交时间

## API 初步边界

- 学生侧：
  - `POST /api/v1/me/assignments/{assignmentId}/submissions`
  - `GET /api/v1/me/assignments/{assignmentId}/submissions`
  - `GET /api/v1/me/submissions/{submissionId}`
- 教师侧：
  - `GET /api/v1/teacher/assignments/{assignmentId}/submissions`
  - `GET /api/v1/teacher/submissions/{submissionId}`

## 待特别验证的规则

- 只有学生可以正式提交。
- 只有已发布且处于开放时间窗口内的作业允许提交。
- 提交次数不能超过作业 `maxSubmissions`。
- 学生只能看到自己的提交；教师可以看到课程内自己有权限的作业提交。
- 作业关闭后仍可查看历史提交，但不能再创建新提交。
