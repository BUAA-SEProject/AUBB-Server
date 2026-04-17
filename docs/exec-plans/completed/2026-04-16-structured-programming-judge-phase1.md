# 2026-04-16 结构化编程题题目级评测第一阶段

## 目标

把当前 legacy assignment 级自动评测推进到结构化编程题 question-level judge 的第一阶段，实现正确的数据模型，而不是在现有 assignment 级 judge 上继续堆补丁。

## 范围

### 代码范围

- `modules.assignment`
- `modules.submission`
- `modules.judge`
- `modules.grading`
- Flyway migration

### 文档范围

- `todo.md`
- `docs/product-specs/{assignment,submission,judge,grading}-system.md`
- `docs/generated/db-schema.md`
- `ARCHITECTURE.md`

## 需要先解决的问题

1. 结构化编程题目前只有样例输入输出和脚本模式配置，没有题目级隐藏测试用例快照。
2. 当前 `judge_jobs` 仍以 assignment / submission 为主关联，尚未定位到 `submission_answer_id`。
3. 当前 go-judge 执行链路只覆盖 `PYTHON3 + TEXT_BODY`，没有题目级多语言、多文件装配模型。

## 分阶段方案

### Phase 1：题目级评测数据模型

- 为结构化编程题补充题目级测试用例和脚本快照模型
- 让已发布作业中的编程题拥有独立、不可变的评测配置
- 为题目级 judge 增加 `submission_answer_id` 级关联

### Phase 2：question-level judge 入队与结果回写

- 结构化提交后仅对编程题答案入队
- 结果回写到 `submission_answers`，并驱动 grading 汇总

### Phase 3：样例试运行与 IDE 扩展位

- 样例试运行 API
- 多语言 / 多文件代码装配
- 评测日志与逐 case 详情

## 风险

- 若先做样例试运行而不补隐藏测试用例模型，会把结构化编程题 judge 做成只能跑 sample 的半成品。
- 若继续复用 assignment 级 `judge_jobs` 语义，会导致 question-level 结果和 grading 聚合难以保持清晰边界。

## 验证路径

- 先补题目级 judge 数据模型集成测试
- 再补 question-level 评测执行与结果回写测试
- 最后执行 `bash ./mvnw clean verify`

## 决策记录

- 当前已完成 grading 第一阶段，因此下一优先级不再是“是否发布成绩”，而是“如何正确承接结构化编程题自动评测”。
- question-level judge 必须先把数据模型做对，再讨论在线 IDE 与工作区体验。

## 完成结果

- 已通过 `assignment_questions.config_json` 为编程题补充隐藏测试点、资源限制和题目级评测配置快照。
- 已为 `judge_jobs` 增加 `submission_answer_id / assignment_question_id / case_results_json`。
- 已实现结构化编程题提交后自动入队、答案级查询 / 重排队、go-judge 执行与结果回写到 `submission_answers`。
- 已保留 legacy assignment 级 judge，不破坏既有 submission 级查询与重排队语义。
- 已通过 `StructuredProgrammingJudgeIntegrationTests` 和关联回归验证。
