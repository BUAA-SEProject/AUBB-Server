# 任务计划：作业模块能力重规划与后续推进

## 当前目标

基于当前代码基线，重新核对接手入口、活动计划和工作记忆，确保仓库入口文档能准确描述“平台治理 -> 课程 -> 作业 -> 提交 -> 评测 -> 批改 -> 成绩”的现状，并明确下一步继续开发的推荐阅读路径。当前仓库的后端主链路和在线 IDE 第二阶段后端契约已经落地，本轮重点是消除入口文档中的滞后表述，避免下一位开发者先读到旧状态。

## 当前阶段

Phases 15 / 16 / 17 / 18 / 19 in progress，Phases 20 / 21 / 22 / 23 / 24 completed

### Phase 24：接手文档与仓库入口二次收口

- [x] 复核 `ARCHITECTURE.md`、`docs/index.md`、`docs/repository-structure.md`、`docs/product-sense.md`、`docs/product-specs/index.md`、`docs/quality-score.md`
- [x] 修正 RabbitMQ 仍被描述为“未来扩展位”等口径漂移
- [x] 把评测队列、详细评测报告、学生侧成绩册和当前下一步优先级同步到入口文档
- [x] 保持 active 路线图只描述仍在推进的长期计划
- **Status:** completed

### Phase 26：仓库状态复核与入口文档三次收口

- [x] 复核 `README.md`、`AGENTS.md`、`docs/index.md`、`docs/repository-structure.md`、`docs/product-sense.md`、`docs/quality-score.md`
- [x] 修正“仍停留在课程第一切片 / 最小工作区”的旧表述
- [x] 明确在线 IDE 后端契约已具备模板工作区、修订历史、历史恢复和自定义试运行
- [x] 明确当前开发者应从 README、仓库结构说明、产品规格索引和 active 计划进入
- 后续如继续做后端开发，应把当前总计划进一步下钻到“多语言稳定化 / 题库分类 / 成绩导出”的下一张执行计划
- **Status:** completed

## Skills 选择

- `planning-with-files`：持续维护多阶段计划、发现和进度记录。
- `springboot-patterns`：保持 `assignment / submission / grading / judge` 的职责稳定。
- `documentation-writer`：把重规划结论同步到仓库文档与执行计划。
- `springboot-tdd`：后续每个切片继续用集成测试驱动实现。
- `springboot-verification`：维持专项测试与全量验证闭环。
- `postgresql-table-design`：后续目录树工作区和日志产物建模时约束表结构。
- `api-design-principles`：保持既有 REST API 兼容，只做追加式演进。

## 阶段

### 基线能力：结构化作业主链路

- [x] 课程级与班级级发布
- [x] 结构化试卷与多题型配置
- [x] 多次提交与版本记录
- [x] 客观题自动评分与人工批改
- [x] assignment 级成绩发布
- [x] question-level judge、样例试运行与最小工作区
- [x] 教师侧成绩册第一阶段
- **Status:** completed

### Phase 14：现状核对与路线重排

- [x] 对照最新需求盘点 `assignment / submission / grading / judge` 的真实边界
- [x] 划分“已完成 / 部分完成 / 缺口”
- [x] 冻结最佳模块职责拆分和兼容约束
- [x] 同步 `todo.md`、执行计划和工作记忆
- **Status:** completed

### Phase 15：在线 IDE 第二阶段

- [x] 把编程工作区升级为目录树快照与多文件编辑的数据模型
- [x] 支持入口文件选择，并让样例试运行与正式评测复用同一份工作区快照
- [x] 为工作区补充更稳定的项目快照语义
- [x] 提供工作区模板快照、模板重置和最近一次标准输入回填
- [x] 提供后端文件 / 目录操作、工作区修订历史、修订详情与恢复接口
- [x] 支持按当前工作区快照或历史修订发起自定义标准输入试运行
- [ ] 提供前端目录树体验、编辑器能力和更实时的草稿同步协议
- **Status:** in_progress

### Phase 16：多语言运行时稳定化

- [x] 补齐三种语言的样例试运行与正式评测集成测试
- [x] 固化 `PYTHON3 / JAVA21 / CPP17` 的源码装配与运行模板
- [x] 保留 `JAVA17` 作为兼容输入，统一映射到现有 Java 运行模板
- [x] 统一编译失败、运行失败与资源超限的日志摘要格式
- [ ] 明确当前 V1 支持矩阵与后续扩展语言入口
- **Status:** in_progress

### Phase 17：题库与组卷第二阶段

- [x] 为题库补齐更新、归档与更清晰的引用约束
- [x] 增加标签与标签检索第一阶段
- [x] 增加分类、分类列表与分类过滤第一阶段
- [x] 支持教师编辑草稿作业并整体替换结构化试卷
- [ ] 增强组卷能力，支持更稳定的题库选题与试卷编辑
- [ ] 保持 assignment 快照不可变，不直接引用运行中的题库实体
- **Status:** in_progress

### Phase 18：成绩与反馈第二阶段

- [x] 补齐学生侧成绩视图与已发布成绩面板
- [ ] 增加课程 / 班级成绩导出与报表第一阶段
- [ ] 为多作业聚合与加权总评预留扩展位
- [ ] 保持“发布前隐藏人工评分”的现有可见性边界
- **Status:** in_progress

### Phase 19：判题可复现性与日志第二阶段

- [x] 持久化测试点级详细评测日志与执行元数据到 `judge_jobs.detail_report_json`
- [x] 提供学生 / 教师详细评测报告 API，并按角色脱敏隐藏测试数据
- [x] 为正式评测补充 `compileArgs / runArgs` 与执行命令可见性
- [x] 为样例试运行补充 `detail_report_json`、输入模式与工作区修订引用
- [ ] 为正式评测和样例试运行补充更可回放的结果模型
- [ ] 在受控边界内扩展 `CUSTOM_SCRIPT` 的脚本打包与执行上下文
- **Status:** in_progress

### Phase 23：评测队列与详细报告第一阶段

- [x] 新增 RabbitMQ 驱动的评测入队与 consumer 执行链路
- [x] 保留队列关闭时的应用内异步回退路径
- [x] 为 legacy judge 和 question-level judge 持久化详细评测报告
- [x] 新增学生 / 教师详细评测报告接口与真实集成测试
- [x] 补齐 `compileArgs / runArgs` 与 C++ 多文件正式评测、样例试运行验证
- **Status:** completed

### Phase 20：仓库状态检查与文档整理

- [x] 盘点 README、文档索引、仓库结构说明和 active 计划是否与当前代码一致
- [x] 修正模块列表、成绩能力口径和下一步开发优先级
- [x] 归档已完成但仍留在 `docs/exec-plans/active/` 的旧计划
- [x] 同步根目录接手入口，方便后续直接继续开发
- **Status:** completed

### Phase 21：真实 go-judge 验证与 fake judge 清理

- [x] 用真实 go-judge Testcontainers 运行 legacy judge、question-level judge 和样例试运行
- [x] 清理测试中的 fake go-judge 服务器与旧标记断言
- [x] 修正真实 go-judge 状态映射与 HTTP 请求模型兼容问题
- [x] 将仓库文档和计划统一到 `JAVA21` 与真实引擎验证口径
- **Status:** completed

### Phase 22：接手入口与验证路径收口

- [x] 复核 `README.md`、`docs/index.md`、`docs/repository-structure.md` 与 active 计划入口是否仍反映当前状态
- [x] 统一主入口文档中的验证命令，显式说明当前 Unix 环境使用 `bash ./mvnw ...`
- [x] 把仓库状态检查与文档整理结论写回工作记忆，便于下一轮继续开发
- **Status:** completed

## 已做决策

| Decision | Rationale |
|----------|-----------|
| `assignment` 继续负责作业头、题库和试卷快照 | 这些内容共同定义了“作业长什么样”，不应拆散到执行或提交域 |
| `submission` 继续负责提交版本、分题答案、附件和工作区状态 | 这些都属于“学生提交了什么”的事实模型 |
| `judge` 只负责样例试运行、正式评测和日志产物 | 执行型系统应与题库、人工批改和成绩发布解耦 |
| `grading` 继续负责人工批改、成绩发布和成绩册 | 评分可见性、反馈和统计应集中在一个域内演进 |
| 下一优先级先补在线 IDE，再补多语言稳定化 | 当前最显著的产品缺口是“能评测但不像真正 IDE”，先补工作区模型最稳 |
| 多语言 V1 先收敛为 `PYTHON3 / JAVA21 / CPP17` | 代码和配置已具备这三种语言的基础能力，继续扩语言会放大验证成本；`JAVA17` 仅作为兼容输入保留 |
| 编译失败继续映射到 `RUNTIME_ERROR` 而不是新增 verdict | 现有 API 和存储枚举已被学生侧、教师侧与样例试运行复用，先稳定摘要口径比扩枚举更稳 |
| 题库生命周期与成绩导出排在 IDE / 运行时之后 | 这两块重要，但不会阻断学生完成编程题主链路 |
| 文档入口优先集中到 README / docs/index / repository-structure 三处 | 继续开发时应先解决入口失真，而不是继续新增重复说明文档 |
| 评测详细报告先落库到 `judge_jobs.detail_report_json`，暂不直接落对象存储 | 先稳定 API 与权限脱敏边界，再决定日志产物对象化策略 |
| RabbitMQ 队列先做单队列单 consumer 入口，并保留本地异步回退 | 先验证真实引擎与消息链路闭环，避免一次性引入独立 worker 与重试编排 |
| 在线 IDE 第二阶段先补后端工作区与试运行契约，不在当前仓库内实现浏览器编辑器能力 | 当前仓库是后端服务，优先保证模板、目录树、版本恢复、试运行与评测环境一致性 |

## 错误记录

| Error | Attempt | Resolution |
|-------|---------|------------|
| 暂无本轮实现错误 | 当前阶段以重规划与文档同步为主 | 后续进入实现切片时继续追加 |
| `./mvnw` 在当前环境返回 `Permission denied` | 1 | 改用 `bash ./mvnw ...` 完成格式化与验证，无需修改仓库权限位 |
| 真实 go-judge 返回 `Nonzero Exit Status` 未被正确映射 | 1 | 扩展 `mapEngineVerdict(...)` 兼容官方状态值，避免把用户代码失败误判为 `SYSTEM_ERROR` |
| go-judge 对 `files` 数组执行联合类型校验，`null` 字段会触发 400 | 1 | 将 `GoJudgeClient` 的 stdin/stdout/stderr 文件描述符拆成真实联合模型，并为空 stdin 发送空内容而不是 `null` |
| 评测任务在 RabbitMQ 路径下可能出现 `finished_at < started_at` 约束冲突 | 1 | 在 `JudgeExecutionService` 中将 `startedAt / finishedAt` 归一到不早于排队时间与开始时间，避免时序抖动触发表约束 |
| 工作区从历史修订恢复时，`last_stdin_text` 无法被清空 | 1 | 为 `ProgrammingWorkspaceEntity.lastStdinText` 增加 `FieldStrategy.ALWAYS`，确保恢复空值时也会写回数据库 |
