# 提交系统

## 目标

把 submission 从“整份文本/附件提交”推进到“整份提交头 + 分题答案 + 客观题自动评分 + 编程题自动入队 + 工作区最小闭环”的当前阶段实现。当前既保留 legacy 文本与附件提交通道，也支持结构化作业的按题提交、题目级编程评测入队、编程题工作区草稿保存和评分摘要；人工批改与成绩发布已转入 grading 模块，submission 继续承担提交事实来源与学生自查入口。

## 覆盖范围

### 功能范围

- 学生对已发布作业发起正式提交
- 学生先上传附件，再在正式提交时关联附件
- 结构化作业支持按题提交答案
- 当前按题答案支持：
  - 单选题选项提交并自动判分
  - 多选题选项提交并自动判分
  - 简答题文本提交并进入待人工处理状态
  - 文件题附件提交并进入待人工处理状态
  - 编程题代码 / 附件 + 语言信息提交并进入待后续评测状态
- 提交受 assignment 可见性、开放时间和最大次数约束
- 学生按作业查看自己的提交列表与详情
- 教师按作业查看提交列表与详情
- 学生下载自己的提交附件
- 教师下载已关联到正式提交的附件
- 提交详情返回分题答案和评分摘要
- 学生按编程题保存和读取工作区草稿
- 提交受理写入审计日志
- 附件上传写入审计日志
- 工作区保存写入审计日志

### 不在范围

- 完整目录树工程快照和草稿同步
- 试运行执行本身与运行结果明细持久化（当前已由 judge 模块承担）
- 多作业成绩册
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
9. legacy 非结构化作业仍走 `contentText + artifactIds`，文本正文和附件不能同时为空。
10. 结构化作业必须通过 `answers` 按题提交，不能再使用 legacy 顶层内容字段。
11. 结构化作业当前要求一次提交覆盖试卷中的全部题目。
12. 单个附件只能关联到一次正式提交，不能跨提交复用。
13. 教师只能下载已经关联到正式提交的附件，不读取学生暂存中的未关联附件。
14. 当前每次正式提交最多关联 10 个附件，每个附件最大 20MB。
15. 客观题自动评分不走 go-judge：
    - 单选题要求且仅允许一个选项
    - 多选题采用与标准答案精确集合匹配
16. 简答题、文件题、编程题在 submission 中只负责进入待后续处理状态；人工终态评分由 grading 模块完成。
17. legacy assignment 已配置 assignment 级自动评测时，正式提交后会自动创建 submission 级 judge job。
18. 结构化作业中的编程题提交后会按 `submission_answer_id` 自动创建题目级 judge job。
19. assignment 成绩发布前，学生只能看到客观题即时分与非客观题批改状态；人工评分与反馈由 grading 发布控制。
20. 工作区按 `assignmentId + assignmentQuestionId + userId` 唯一保存，避免不同编程题之间的草稿相互污染。
21. 工作区只保存代码正文、语言和附件引用，不改变正式提交次数、正式提交版本和成绩。

## 核心数据模型

- `submissions`
  - `submission_no`：唯一提交编号
  - `assignment_id`：所属作业
  - `offering_id`：冗余所属开课实例，便于后续检索
  - `teaching_class_id`：可选，冗余作业班级范围
  - `submitter_user_id`：提交人
  - `attempt_no`：第几次正式提交
  - `status`：当前固定为 `SUBMITTED`
  - `content_text`：legacy 文本提交内容，可空
  - `submitted_at`：提交时间
- `submission_artifacts`
  - `assignment_id`：所属作业
  - `submission_id`：关联的正式提交，可先为空后绑定
  - `uploader_user_id`：上传人
  - `object_key`：对象存储键
  - `original_filename / content_type / size_bytes`：原始元数据
  - `uploaded_at`：附件上传时间
- `submission_answers`
  - `submission_id + assignment_question_id`：唯一定位一道题在一次提交中的答案
  - `answer_text`：文本答案或代码文本
  - `answer_payload_json`：选项、附件、编程语言等结构化载荷
  - `auto_score / manual_score / final_score`：分题得分
  - `grading_status`：`AUTO_GRADED / MANUALLY_GRADED / PROGRAMMING_JUDGED / PENDING_MANUAL / PENDING_PROGRAMMING_JUDGE`
  - `feedback_text`：当前阶段保留的评分反馈位
  - `graded_by_user_id / graded_at`：最近人工批改人和时间
- `assignments`
  - 提供开放时间、截止时间、最大提交次数、结构化试卷快照和成绩发布时间
- `programming_workspaces`
  - `assignment_id + assignment_question_id + user_id`：唯一定位一个学生在一道编程题上的工作区
  - `programming_language`：当前工作区语言
  - `code_text`：当前入口文件正文
  - `artifact_ids_json`：工作区引用的附件列表
- `audit_logs`
  - 记录 `SUBMISSION_CREATED`
  - 记录 `SUBMISSION_ARTIFACT_UPLOADED`
  - 记录 `PROGRAMMING_WORKSPACE_SAVED`

详细字段以 [../generated/db-schema.md](../generated/db-schema.md) 为准。

## 角色边界

### 教师

- 查看自己课程内作业的提交列表与详情
- 查看结构化提交中的分题答案和评分摘要
- 人工批改与成绩发布行为已转入 grading 模块

### 助教

- 当前可通过教师侧提交详情与 grading 接口处理自己负责教学班内的班级作业
- 课程公共作业的更细粒度助教范围仍待后续扩展

### 学生

- 对自己有权访问的已发布作业进行正式提交
- 上传并管理自己用于正式提交的附件
- 查看自己的提交列表、详情、分题答案和评分摘要
- 不可查看其他学生的提交

## API 边界

### 学生侧

- `POST /api/v1/me/assignments/{assignmentId}/submissions`
- `POST /api/v1/me/assignments/{assignmentId}/submission-artifacts`
- `GET /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace`
- `PUT /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace`
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

- 当前切片支持 legacy 文本 / 附件正式提交，也支持结构化作业的分题提交和编程题工作区最小草稿能力。
- 提交状态当前固定为 `SUBMITTED`，评测态通过 `judge_jobs` 独立表达；人工评分结果写回 `submission_answers`，学生可见性由 grading 模块控制。
- 人工评分与反馈已经可写回 `submission_answers`，但是否对学生可见由 grading 模块控制。
- submission 继续作为成绩册聚合的事实来源，提供“最新正式提交 + 分题评分摘要”读模型；跨作业成绩矩阵不在 submission 模块内计算。
- 附件当前采用“先上传，再在正式提交时关联”的两阶段模型，不支持草稿恢复。
- 附件下载当前统一走服务端鉴权后再读取对象存储，不直接暴露预签名下载契约。
- 编程题答案当前已接入题目级 go-judge，并支持代码正文和附件一起装配为评测输入。
- 当前工作区只保存“入口代码正文 + 附件引用 + 语言”三类状态，不提供目录树级文件操作和实时协同。

## 验收标准

- 学生可对自己有权访问的已发布作业进行正式提交。
- 学生可上传附件并在正式提交时关联附件。
- 结构化作业可按题提交，并返回分题答案和评分摘要。
- 单选 / 多选题会自动判分，简答题 / 文件题 / 编程题会进入后续处理状态。
- 超过最大提交次数、早于开放时间或晚于截止时间的提交会被拒绝。
- 学生只能查看自己的提交。
- 教师可按作业查看提交列表与详情，并下载已关联附件。
- `./mvnw verify` 提供自动化测试证据。
