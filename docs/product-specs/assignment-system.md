# 作业系统

## 目标

把 assignment 从“单体作业头”推进到“作业头 + 题库源数据 + 结构化试卷快照”的第一阶段实现。当前除了原有的课程级 / 班级级作业发布、状态流转和 assignment 级脚本型自动评测摘要外，还新增了题库建题、组卷和多题型结构化试卷能力，为 submission 的分题提交和后续 grading 链路提供正确上游。

## 覆盖范围

### 功能范围

- 教师在开课实例下创建作业
- 作业可绑定整个开课实例，也可绑定某个教学班
- 作业状态流转：`DRAFT -> PUBLISHED -> CLOSED`
- 教师可创建题库题目，并按开课实例查看题库列表 / 详情
- 教师可在创建作业时附带结构化试卷
- 结构化试卷支持多个大题，每个大题下挂多道题目
- 当前题型支持：
  - `SINGLE_CHOICE`
  - `MULTIPLE_CHOICE`
  - `SHORT_ANSWER`
  - `FILE_UPLOAD`
  - `PROGRAMMING`
- 结构化试卷支持直接内联建题，也支持引用题库题目并在发布时做快照
- 学生、助教、教师按课程成员关系查看自己有权访问的已发布作业
- 学生侧作业详情会返回结构化试卷，但不会暴露正确答案与敏感配置
- 关键状态变更与题库建题写入审计日志

### 不在范围

- 题库编辑、删除、标签体系和复杂组卷规则
- 结构化作业的复制、更新和发布后变更
- 人工批改与成绩发布逻辑本身
- `CUSTOM_SCRIPT` 真实执行和更完整的在线 IDE 工作区
- 课程资源、公告、讨论正文

## 核心业务规则

1. 作业必须挂在 `course_offerings` 下，不能直接挂到课程模板。
2. `teachingClassId` 为空表示课程公共作业；非空表示仅该教学班可见。
3. 只有具备课程教师侧管理权限的用户才能创建、发布、关闭作业以及管理题库。
4. 作业创建后默认是 `DRAFT`，草稿对学生不可见。
5. 只有 `DRAFT` 状态的作业可以发布。
6. 草稿不能直接关闭；已发布作业可以关闭。
7. 学生只能查看自己所在开课实例内、且班级范围匹配的非草稿作业。
8. 作业必须提供开放时间、截止时间和正整数提交次数上限，且截止时间不能早于开放时间。
9. 题库源数据和作业快照分离：引用题库建卷时，会把题目、选项和配置复制到 assignment 快照表，后续题库变化不会污染已创建作业。
10. 当前 assignment 级 `judgeConfig` 与结构化试卷互斥，不能在同一作业上同时使用。
11. 客观题必须配置合法选项：
   - 单选题必须且只能有一个正确选项
   - 多选题至少有一个正确选项
12. 文件题必须配置文件数量和大小限制；编程题必须配置支持语言和隐藏测试点，若选择 `CUSTOM_SCRIPT` 还必须提供脚本。
13. 编程题隐藏测试点分值之和必须等于题目分值。

## 核心数据模型

- `assignments`
  - 继续作为作业头，承载发布范围、状态、时间窗和最大提交次数
- `question_bank_questions`
  - 开课实例内可复用题目源数据
  - 当前按 `offering_id` 进行作用域隔离
- `question_bank_question_options`
  - 客观题源数据选项及正确性
- `assignment_sections`
  - 结构化试卷中的“大题”快照
- `assignment_questions`
  - 已发布作业的题目快照
  - `source_question_id` 可回指题库来源，但快照内容独立保存
- `assignment_question_options`
  - 作业快照中的客观题选项和正确答案
- `assignment_judge_profiles / assignment_judge_cases`
  - 继续保留 legacy assignment 级脚本型自动评测配置
- `audit_logs`
  - 记录 `QUESTION_BANK_QUESTION_CREATED / ASSIGNMENT_CREATED / ASSIGNMENT_PUBLISHED / ASSIGNMENT_CLOSED`

详细字段以 [../generated/db-schema.md](../generated/db-schema.md) 为准。

## 角色边界

### 学校管理员 / 学院管理员

- 当前不直接参与作业与题库管理
- 继续承担平台和课程主数据治理职责

### 教师

- 创建课程公共作业或教学班专属作业
- 创建题库题目并按开课实例管理题库
- 用内联建题或题库引用方式组卷
- 查看自己可管理课程下的作业列表与详情
- 发布和关闭作业

### 助教

- 当前可通过“我的作业”查看自己所在课程 / 教学班的已发布作业
- 当前不具备创建题库、组卷、发布、关闭权限

### 学生

- 通过“我的作业”查看自己有权访问的已发布作业
- 可读取结构化试卷的题面、选项和非敏感题型配置
- 不可查看草稿、正确答案和敏感编程配置

## API 边界

### 教师侧

- `POST /api/v1/teacher/course-offerings/{offeringId}/assignments`
- `GET /api/v1/teacher/course-offerings/{offeringId}/assignments`
- `GET /api/v1/teacher/assignments/{assignmentId}`
- `POST /api/v1/teacher/assignments/{assignmentId}/publish`
- `POST /api/v1/teacher/assignments/{assignmentId}/close`
- `POST /api/v1/teacher/course-offerings/{offeringId}/question-bank/questions`
- `GET /api/v1/teacher/course-offerings/{offeringId}/question-bank/questions`
- `GET /api/v1/teacher/question-bank/questions/{questionId}`

### 我的作业

- `GET /api/v1/me/assignments`
- `GET /api/v1/me/assignments/{assignmentId}`

## 当前实现边界

- assignment 已从“纯作业头”推进到“作业头 + 题库 + 试卷快照”，但仍未进入题库高级管理和人工批改阶段。
- assignment 已从“纯作业头”推进到“作业头 + 题库 + 试卷快照”；人工批改与成绩发布已转入 grading 模块。
- 结构化试卷已支持五种题型的建模与读取；其中编程题已支持题目级隐藏测试点、资源限制和多文件提交约束建模。
- assignment 级脚本型自动评测仍只适用于 legacy 文本作业，不适用于结构化试卷。
- 结构化编程题当前已接入 question-level judge，并已向学生侧暴露样例输入输出、工作区最小草稿能力和样例试运行入口。
- assignment 只负责提供编程题配置与快照，不直接承载工作区状态和试运行结果；对应状态分别落在 submission 与 judge 模块。
- `CUSTOM_SCRIPT` 真实执行和更完整的目录树 IDE 仍未落地。
- 教学班功能开关中的 `assignment_enabled` 目前只作为课程域配置位保留，尚未在 assignment 接口层强制拦截。
- 助教当前沿用课程成员可见性规则；批改权限已在 grading 模块对班级作业开放，更细的 staff scope 留待后续扩展。

## 验收标准

- 教师可创建课程公共作业和教学班专属作业。
- 教师可在开课实例内创建题库题目，并通过题库引用或内联方式组装结构化试卷。
- 教师可将草稿作业发布，并可关闭已发布作业。
- 学生只能看到自己有权访问的已发布作业，无法读取其他班级作业或草稿作业。
- 学生详情中不会暴露客观题正确答案和编程题敏感脚本配置。
- 学生无法调用教师侧作业与题库管理接口。
- `./mvnw verify` 提供自动化测试证据。
