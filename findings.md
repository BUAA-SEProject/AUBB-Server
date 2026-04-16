# 发现与决策

## 2026-04-16 重规划结论

### 需求覆盖矩阵

#### 已完成

- 作业已支持按课程实例或教学班发布，并具备 `openAt / dueAt / maxSubmissions`
- 结构化试卷已支持多个大题与以下题型：
  - `SINGLE_CHOICE`
  - `MULTIPLE_CHOICE`
  - `SHORT_ANSWER`
  - `FILE_UPLOAD`
  - `PROGRAMMING`
- 已支持开课实例内题库创建、列表、详情与引用组卷
- 已支持正式提交、多次提交版本记录、附件上传、分题答案和客观题自动评分摘要
- 已支持教师 / 助教人工批改、assignment 级成绩发布与教师侧成绩册第一阶段
- 已支持 go-judge 驱动的样例试运行、question-level judge、`STANDARD_IO` 和第一阶段 `CUSTOM_SCRIPT`

#### 部分完成

- 题库管理只有最小生命周期，缺更新、删除、标签、检索与更完整组卷体验
- 编程题后端已支持 `entryFilePath + files + artifactIds` 的目录树快照、样例试运行和正式评测复用，但前端目录树交互与逐文件操作尚未补齐
- 多语言运行时已有 `PYTHON3 / JAVA17 / CPP17` 的模型与正式 / 样例两条执行链路，自动化验证已覆盖这三种语言，但日志一致性与更复杂工程布局仍不足
- 成绩发布、教师侧成绩册和学生侧成绩册已完成第一阶段，但成绩导出和多作业聚合未完成
- 评测结果已有摘要、`stdout/stderr` 和用例级结果，但完整日志与可复现元数据仍不足

#### 当前缺口

- 目录树工作区、多文件编辑、入口文件选择和工作区快照
- `PYTHON3 / JAVA17 / CPP17` 三语言完整验证矩阵
- 题库生命周期与更稳定的组卷编辑能力
- 成绩导出和后续多作业总评
- 更可回放的评测日志、执行元数据与产物对象

### 最佳实现拆分

- `assignment`
  - 负责作业头、题库、试卷快照和题目配置
  - 保持题目快照不可变，不直接依赖运行中的题库题目实体
- `submission`
  - 负责提交版本头、分题答案、附件和工作区状态
  - 继续维持 `submissionNo / attemptNo` 的版本语义
- `judge`
  - 负责样例试运行、正式评测、队列状态和日志产物
  - 不承载题库或人工批改语义
- `grading`
  - 负责人工批改、成绩发布、教师 / 学生成绩册和后续统计能力
  - 不直接承接代码执行逻辑

### 重新排序后的优先级

1. 在线 IDE 第二阶段
2. 多语言运行时稳定化
3. 题库与组卷第二阶段
4. 成绩与反馈第二阶段
5. 判题日志与可复现性第二阶段

## 当前仓库真实边界

### 已落地能力

- 平台治理、课程、assignment、submission、grading、judge 以及 MinIO / go-judge 基础设施已形成最小主链路。
- assignment 仍是“单体作业头”模型：
  - `assignments` 只描述发布范围、状态、时间窗、最大提交次数
  - 可选 assignment 级 `judgeConfig` 只支持 `PYTHON3 + TEXT_BODY`
- submission 仍是“整份提交头”模型：
  - `submissions` 表示一个学生对某作业的一次正式提交版本
  - `submission_artifacts` 负责附件资产
- grading 已作为独立逻辑模块落地：
  - `assignments` 挂 assignment 级成绩发布时间
  - `submission_answers` 挂人工评分、反馈、批改人和批改时间
- judge 当前同时包含两条执行链：
  - legacy assignment 级执行任务继续依赖 `assignment_judge_profiles / cases`
  - 结构化编程题 question-level judge 通过 `judge_jobs.submission_answer_id` 下沉到答案级

### 当前最关键的缺口

- 已补上 `CUSTOM_SCRIPT` 第一阶段真实执行；当前仍缺更完整的目录树 IDE
- 没有成绩导出与多作业总评
- 教师侧与学生侧成绩册第一阶段已补齐，但仍没有导出和多作业加权总评

## 子代理结论汇总

### 最佳演进路径

- `assignment` 继续负责题库、组卷和作业快照，不把这些职责提前拆到平级新模块。
- `submission` 继续负责提交版本头和分题作答，不推翻现有 `submissionNo / attemptNo` 语义。
- `judge` 继续只承接执行型评测，客观题自动判分不先走 go-judge。
- 工作区状态应放在 `submission`，样例试运行历史应放在 `judge`，避免正式提交与试运行混模。

### 需要严格保持兼容的契约

- 教师侧 assignment API：
  - `POST /api/v1/teacher/course-offerings/{offeringId}/assignments`
  - `GET /api/v1/teacher/course-offerings/{offeringId}/assignments`
  - `GET /api/v1/teacher/assignments/{assignmentId}`
- 学生侧 assignment API：
  - `GET /api/v1/me/assignments`
  - `GET /api/v1/me/assignments/{assignmentId}`
- 学生侧 submission API：
  - `POST /api/v1/me/assignments/{assignmentId}/submissions`
  - 现有 `contentText` / `artifactIds` 语义不能被破坏，只能追加结构化字段
- 编程题工作区、样例试运行和正式编程答案当前已追加 `entryFilePath / files`，旧 `codeText` 仍保留兼容语义
- 教师侧 submission / judge 查询 API 继续按 `assignmentId` 和 `submissionId` 聚合，不打散已有查询模型

## 第一阶段最合理的交付边界

- 题库最小管理：
  - 题目增删查列表中的“增 / 查 / 列表”先落地
  - 题型先覆盖 `SINGLE_CHOICE / MULTIPLE_CHOICE / SHORT_ANSWER / FILE_UPLOAD / PROGRAMMING`
- 结构化作业：
  - assignment 继续保留头信息
  - 新增大题和题目快照，不把整份结构塞进 `description` 或单个 assignment JSON 字段
- 分题提交：
  - 在保持 `submissions` 为整份提交头的前提下，新增 `submission_answers`
  - 单选 / 多选题应用内自动判分
  - 简答 / 文件上传 / 编程题先进入待后续处理状态
- legacy 兼容：
  - 旧版简单作业继续可用
  - 结构化作业走新增字段和新子模型，不回写破坏旧数据

## 本轮数据模型草案

- 题库：
  - `question_bank_questions`
  - `question_bank_question_options`
- 结构化作业快照：
  - `assignment_sections`
  - `assignment_questions`
  - `assignment_question_options`
- 分题作答：
  - `submission_answers`
- 批改与发布：
  - `assignments.grade_published_at / grade_published_by_user_id`
  - `submission_answers.manual_score / feedback_text / graded_by_user_id / graded_at`

## 风险记录

- 现有 `assignment_judge_profiles` 是 legacy assignment 级配置；结构化编程题当前已通过 `assignment_questions.config_json` 挂题目级隐藏测试点。
- `judge_jobs` 已能同时表达 submission 级 legacy job 和 `submission_answer_id` 级 question-level job，并保存逐测试点摘要；完整日志与产物对象仍未持久化。
- `programming_workspaces` 已支持 `entryFilePath + sourceFilesJson + artifactIdsJson` 的目录树快照，并兼容 legacy `codeText` 语义；前端目录树交互和逐文件操作仍未实现。
- `programming_sample_runs` 已支持保存样例试运行时的入口文件与文件树快照，并已支持 `CUSTOM_SCRIPT`；正式评测与试运行的评测产物对象存储仍未落地。
- 结构化作业一旦落地，旧版“整份文本提交”不能误用于新型作业，必须在业务层显式区分。
- 学生详情接口不能泄露题库正确答案或 assignment 快照中的 `isCorrect` 信息。
- assignment 级成绩发布当前是全局开关，后续若需要按班级或按学生分批发布，需要单独建模。
- Serena 目录级 symbol overview 在当前仓库环境下不稳定，继续以文件级符号查询和 `rg` 为主。
- 教师侧成绩册第一阶段的最稳边界是：
  - 放在 `grading` 模块
  - offering 级与单学生视图只对教师 / 管理员开放
  - class 级视图额外允许具备班级责任的 TA
  - 默认按每个学生每个作业最新正式提交聚合
  - 第一阶段只覆盖结构化作业
