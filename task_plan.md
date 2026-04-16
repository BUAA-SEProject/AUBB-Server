# 任务计划：结构化编程题评测继续推进到自定义脚本执行

## 当前目标

围绕 `todo.md` 的作业主链路，仓库已完成“题库 + 结构化试卷 + 分题提交 + 客观题自动评分 + 人工批改 + 成绩发布 + question-level judge + 样例试运行 + 工作区 + CUSTOM_SCRIPT 第一阶段”这一段。当前这一轮继续补齐“教师侧成绩册第一阶段”，并把下一优先级收敛为：多语言稳定运行、更完整 IDE / 目录树工作区，以及成绩册导出 / 学生侧成绩册。

## 当前阶段

Phase 6 in_progress

## Skills 选择

- `planning-with-files`：持续维护多阶段工作记忆和后续切片计划。
- `springboot-patterns`：保持 `assignment / submission / grading / judge` 的边界稳定。
- `springboot-tdd`：继续以集成测试驱动 question-level judge 的最小闭环。
- `springboot-verification`：收口专项测试与全量验证。
- `postgresql-table-design`：设计题目级隐藏测试用例、评测快照和结果回写约束。
- `api-design-principles`：保持现有 REST API 的追加式演进。

## 阶段

### Phase 1：结构化作业与批改闭环

- [x] 题库最小管理
- [x] 结构化试卷快照
- [x] 分题提交与客观题自动评分
- [x] 非客观题人工批改
- [x] assignment 级成绩发布
- **Status:** completed

### Phase 2：当前缺口建模

- [x] 审查现有 `judge` 仍是 assignment 级模型的边界
- [x] 确认结构化编程题缺少 question-level 隐藏测试用例与脚本快照模型
- [x] 确认在线 IDE / 样例试运行应建立在 question-level judge 之上，而不是反过来
- **Status:** completed

### Phase 3：题目级评测数据模型

- [x] 为结构化编程题补充题目级隐藏测试点快照
- [x] 为 judge job 增加 `submission_answer_id` 级定位
- [x] 保持 legacy assignment 级 judge 与 question-level judge 可并存
- **Status:** completed

### Phase 4：question-level judge 执行

- [x] 结构化提交后对编程题答案自动入队
- [x] go-judge 执行结果回写到 `submission_answers`
- [x] 与 grading 汇总和成绩发布衔接
- [x] 补充答案级查询与重排队 API
- **Status:** completed

### Phase 5：试运行与 IDE 扩展位

- [x] 样例试运行 API
- [x] 在线 IDE / 工作区模型
- [x] `CUSTOM_SCRIPT` 真实执行
- **Status:** completed

### Phase 6：成绩册与统计

- [x] assignment 级成绩册聚合
- [x] 课程 / 班级 / 学生维度统计（教师侧第一阶段）
- [ ] 导出与报表
- **Status:** in_progress

## 已做决策

| Decision | Rationale |
|----------|-----------|
| `grading` 独立成逻辑模块，但第一阶段不新建成绩表 | 当前最小闭环只需要 assignment 级发布与分题人工评分，不值得提前引入成绩册复杂度 |
| 学生成绩发布前只保留客观题即时分可见 | 兼容既有客观题即时反馈，又给非客观题评分和反馈留出正式发布入口 |
| question-level judge 必须先补题目级隐藏测试模型 | 只有样例输入输出不足以支撑真实编程题自动评测 |
| 样例试运行不能复用 `judge_jobs` | 否则会污染正式评测历史、成绩和审计语义 |
| 题目级隐藏测试点当前先挂在 `assignment_questions.config_json` | 先复用现有题目快照链路，避免提前引入额外表拆分；后续若需要更复杂查询再拆表 |
| 教师侧成绩册聚合放在 `grading` | 当前聚合依赖 assignment 成绩发布、submission 最新提交和 `submission_answers` 评分摘要，放在 grading 最符合职责边界 |
| 成绩册第一阶段只覆盖结构化作业 | legacy 文本作业没有分题评分摘要，强行混入会让聚合语义不稳定 |

## 错误记录

| Error | Attempt | Resolution |
|-------|---------|------------|
| `submission_answers.grading_status` 检查约束未包含 `MANUALLY_GRADED` | 首轮 grading 测试直接触发数据库约束失败 | 在 `V10__grading_first_slice.sql` 中显式重建 check constraint |
