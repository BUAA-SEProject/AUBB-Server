# 发现与决策

## 2026-04-16 入口文档四次收口补充发现

- 当前主入口文档整体已基本同步，但活动计划 `2026-04-16-assignment-module-replan.md` 仍保留“题库仍缺分类”的旧表述，需要纠正为“分类第一阶段已完成，剩余缺口是更强的组卷编辑体验”。
- `README.md`、`docs/index.md`、`docs/product-sense.md` 和 `docs/quality-score.md` 需要显式提到“题库标签与分类”“结构化作业草稿编辑”以及最近一次 `BUILD SUCCESS / 78` 项测试通过的验证基线，避免下一位开发者先读到落后状态。

## 2026-04-16 入口文档五次收口补充发现

- `docs/product-sense.md` 和 `docs/quality-score.md` 仍停留在 `BUILD SUCCESS / 78` 项测试通过的旧基线，需要更新到最近一次 `82` 项测试通过。
- `README.md`、`todo.md` 和 `docs/product-sense.md` 的“多语言运行时稳定化”优先级表述仍写成 `PYTHON3 / JAVA21 / CPP17`，需要同步到当前四语言矩阵 `PYTHON3 / JAVA21 / CPP17 / GO122`。
- `docs/index.md`、`docs/repository-structure.md`、`docs/product-specs/index.md` 和 `AGENTS.md` 需要把“开课实例级评测环境模板 + 题目级运行环境快照”纳入接手入口，否则开发者很容易只看到 go-judge 执行链路，看不到课程内环境复用边界。

## 2026-04-16 成绩册导出与统计报告第一阶段补充发现

- 教师侧成绩册第一阶段如果只停留在页面浏览，实际教学管理价值不够；CSV 导出和统计报告是让该能力从“能看”推进到“能用”的最小收口。
- offering / class 导出与统计必须严格复用既有成绩册最新正式提交矩阵，否则页面与下载结果不一致会直接破坏教师信任。
- 完成教师侧课程 / 班级导出与统计后，成绩域下一步最自然的缺口已经收敛为多作业加权总评、学生侧导出和更完整统计，而不再是“是否先补一个导出入口”。

## 2026-04-16 assignment 权重与加权总评第一阶段补充发现

- 当前成绩册、CSV 导出和统计报告都已经稳定建立在“每个学生每个作业最新一次正式提交”之上，继续补 assignment 级权重比直接引入更复杂的总评策略更稳。
- 把权重建模在 `assignments.grade_weight` 而不是额外的独立总评表，可以先保持作业定义、成绩展示和导出三者的一致性，避免在第一阶段引入版本漂移。
- 第一阶段最稳的加权语义是 `finalScore / assignmentMaxScore * gradeWeight`，并且只有在某作业存在最新正式提交时才计入当前 `totalWeight`；这能避免“未提交作业是否算零分”在总评里被过早锁死。

## 2026-04-16 学生侧成绩册 CSV 导出第一阶段补充发现

- 学生侧导出最稳的做法不是新写一套查询，而是直接复用 `getMyGradebook(...)` 的聚合逻辑，再单独渲染 CSV；这样页面和导出不会在可见性规则上漂移。
- 学生个人导出第一阶段只需要“个人汇总 + 按作业明细”两段即可满足留存、申诉准备和课后复盘，不必过早引入更多报表维度。
- 未发布人工分的隐藏规则必须和页面完全一致；如果导出比页面多暴露任何人工分、人工反馈或人工部分总分，就会直接破坏当前成绩发布边界。

## 2026-04-16 统计报告五档成绩分布第一阶段补充发现

- 教师侧 report 已经有总体、作业和班级维度，如果继续拆新接口做分布统计，会平白增加权限、缓存和文档复杂度；最稳的做法是在现有 report 结构上追加派生统计字段。
- 五档分布先固定为 `EXCELLENT / GOOD / MEDIUM / PASS / FAIL`，足够覆盖教学场景里的快速诊断；更复杂的自定义分档和趋势分析可以延后。
- 总评分布优先按当前加权得分率统计，只有在学生尚无加权权重时才回退到总分得分率；这能和现有加权总评第一阶段保持一致。

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
- 已支持 go-judge 驱动的样例试运行、question-level judge、`STANDARD_IO`、第一阶段 `CUSTOM_SCRIPT`、RabbitMQ 队列第一阶段和详细评测报告 API

#### 部分完成

- 题库管理已补齐更新、归档、标签、标签精确检索，以及分类 / 分类过滤第一阶段；结构化作业也已补齐草稿编辑第一阶段，但仍缺更完整组卷体验
- 编程题后端已支持 `entryFilePath + files + directories + artifactIds` 的目录树快照、模板工作区、工作区目录操作、历史修订、模板重置、最近标准输入回填，以及样例试运行 / 正式评测复用；当前剩余缺口主要是前端目录树交互、编辑器能力和更实时同步协议
- 多语言运行时已有 `PYTHON3 / JAVA21 / CPP17 / GO122` 的模型与正式 / 样例两条执行链路，自动化验证已覆盖这四种语言，并已支持 `compileArgs / runArgs`、题目级 `executionEnvironment`、开课实例级 `judge_environment_profiles`、按语言 `languageExecutionEnvironments` 与 C++ / Go 多文件工程，但日志一致性与更复杂工程布局仍不足；`JAVA17` 当前仅作为兼容输入保留
- 编译失败、运行失败和资源超限的摘要口径已在 legacy judge、question-level judge 与样例试运行三条链路上完成第一阶段统一，但更复杂工程布局下的完整执行日志仍不足
- `JAVA21` 运行模板已补齐为“编译全部 `.java` 文件 + 按 package 解析启动类”，目录树中的嵌套路径和 package 化入口已不再退化成 `WRONG_ANSWER`
- 成绩发布、教师侧成绩册和学生侧成绩册已完成第一阶段，但成绩导出和多作业聚合未完成
- 成绩发布、教师侧成绩册和学生侧成绩册已完成第一阶段；教师侧课程 / 班级成绩册现已补齐 CSV 导出与统计报告第一阶段，但多作业聚合与加权总评仍未完成
- 成绩发布、教师侧成绩册和学生侧成绩册当前都已补齐第一阶段导出能力：教师侧支持课程 / 班级 CSV 与统计，学生侧支持个人成绩册 CSV；剩余缺口是更复杂的总评策略和更完整统计
- 成绩统计当前已补齐五档成绩分布第一阶段；剩余缺口进一步收敛为更复杂总评策略、更深入学习分析和学生侧更丰富报表
- 评测结果现已补齐测试点级完整日志、执行命令、`compileArgs / runArgs` 和执行元数据持久化，但评测产物对象化与完整重放仍不足

#### 当前缺口

- 浏览器侧目录树 IDE、语法高亮 / 自动补全 / 格式化，以及更实时的自动保存协议
- `PYTHON3 / JAVA21 / CPP17 / GO122` 四语言完整验证矩阵
- 更稳定的组卷编辑能力与题库选题体验
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
- judge 当前已支持 RabbitMQ 队列第一阶段与本地异步回退，详细报告先持久化到 `judge_jobs.detail_report_json`
- 题库题目当前已支持更新与软归档：
  - 默认列表只展示未归档题目
  - 已归档题目仍可按详情回溯，但不能继续被新的 assignment 引用
- 题库标签当前已支持：
  - 开课实例内标签去重与复用
  - 创建 / 更新题目时写入标签
  - 题库列表按重复 `tag` 参数执行“全部命中”过滤
- 题库分类当前已支持：
  - 开课实例内分类字典自动创建与列表读取
  - 创建 / 更新题目时设置主分类
  - 题库列表按 `category` 参数过滤
- 草稿作业当前已支持：
  - 教师在发布前更新标题、范围、时间窗和最大提交次数
  - 整体替换结构化试卷或 legacy assignment 级 judge 配置
  - 发布后保持不可编辑，避免污染提交与批改基线

### 当前最关键的缺口

- 已补上 `CUSTOM_SCRIPT` 第一阶段真实执行；当前仍缺更完整的目录树 IDE
- 没有成绩导出与多作业总评
- 教师侧与学生侧成绩册第一阶段已补齐，但仍没有导出和多作业加权总评
- 教师侧成绩册已补齐导出与统计报告第一阶段，但学生侧仍无导出能力，且多作业加权总评仍未建模

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
- `judge_jobs` 已能同时表达 submission 级 legacy job 和 `submission_answer_id` 级 question-level job，并保存逐测试点摘要、详细报告和执行元数据；完整评测产物对象仍未持久化。
- `programming_workspaces` 已支持 `entryFilePath + sourceFilesJson + sourceDirectoriesJson + artifactIdsJson` 的目录树快照，并通过 `programming_workspace_revisions` 保留工作区历史版本、模板重置和恢复点；当前仍未覆盖前端目录树交互和协同协议。
- `programming_sample_runs` 已支持保存样例试运行时的目录树快照、目录列表、输入模式、详细报告和工作区修订引用，并已支持从当前工作区或历史修订发起自定义标准输入试运行；正式评测与试运行的评测产物对象存储仍未落地。
- 真实 go-judge 集成测试已经替换 fake judge：legacy judge、question-level judge 和样例试运行都改为通过 Testcontainers 启动真实引擎验证。
- 真实 go-judge 集成测试当前已同时覆盖 RabbitMQ 队列路径，不再只验证应用内事件直连执行。
- 真实 go-judge `/run` 会返回 `Nonzero Exit Status`，不能再沿用 fake judge 时代的 `Non Zero Exit Status` 字符串。
- 真实 go-judge 对 `files` 使用联合类型校验，stdin/stdout/stderr 需要按真实对象类型序列化，不能发送带 `null` 字段的混合描述符。
- 正式评测与样例试运行现在都支持 `compileArgs / runArgs`，并已经由真实 go-judge Testcontainers 覆盖到 C++ 多文件工程。
- 真实 go-judge 的每次 `/run` 都是全新沙箱；若要拆成“编译 -> 运行”两阶段，必须通过 `copyOut / copyIn` 回传编译产物，不能假设第一次执行生成的二进制会自动保留到第二次调用。
- 题目级 `executionEnvironment` 目前最稳的边界是 assignment question snapshot：通过题库题目复用、在作业发布时固化，并在执行时只允许映射到受控的 `workingDirectory / initScript / environmentVariables / supportFiles / compileCommand / runCommand / cpuRateLimit`，不额外引入独立环境管理中心。
- 课程内最稳的环境复用边界是开课实例级 `judge_environment_profiles`：教师可先维护语言模板，再由题库题目或作业题目按 `profileId / profileCode` 引用；平台解析后只保留环境快照，不在正式评测阶段回查运行中的模板实体。
- `judge_jobs.detail_report_json` 当前包含测试点级 `stdin / expectedStdout / stdout / stderr / compileCommand / runCommand` 和执行元数据；学生侧报告会脱敏隐藏测试输入输出。
- 编译失败当前继续映射到 `RUNTIME_ERROR`，通过稳定中文摘要区分“编译失败”和“程序运行失败”；如后续要新增独立 verdict，需要评估 API 兼容和历史数据迁移。
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

## 2026-04-16 仓库整理补充发现

- `README.md`、`docs/index.md`、`docs/repository-structure.md` 是当前最关键的接手入口；其中任一口径漂移，后续开发者都很容易先读到过期状态。
- `docs/repository-structure.md` 原先的模块列表少了 `grading / judge`，不利于快速定位作业主链路的完整代码入口。
- `docs/index.md` 原先存在重复入口，且对当前状态的概括少了 `grading` 与学生侧成绩能力。
- `docs/exec-plans/active/` 中保留已完成计划会误导下一轮开发优先级；活动计划目录应只保留仍在推进的路线图。
- 当前仓库 Unix 环境下 `./mvnw` 没有稳定执行位，主入口文档应统一显式给出 `bash ./mvnw ...`，否则接手者会在第一步验证上遇到 `Permission denied`。
- 已完成的 `docs/exec-plans/completed/` 历史计划保留原命令记录即可；真正需要对齐的是 README、AGENTS、开发流程、当前规格和 active 计划这类“面向下一位开发者”的入口文档。
- `ARCHITECTURE.md` 在上一轮评测队列落地后仍把 RabbitMQ 描述为“未来异步扩展位”，这是当前最容易误导下一位开发者的口径漂移。
- `docs/repository-structure.md` 需要明确 `application.yaml` 已包含 go-judge 与 RabbitMQ 队列开关，以及真实 judge 集成测试会拉起 go-judge / MinIO / RabbitMQ 三类容器。
- `docs/product-specs/index.md`、`docs/product-sense.md` 和 `docs/quality-score.md` 应继续只做入口层概括，不复制细节，但必须覆盖学生侧成绩册 / 导出、评测队列和详细评测报告这类会影响“下一步开发从哪里开始看”的变化。

## 2026-04-16 仓库状态复核补充发现

- `AGENTS.md` 中“当前真实业务进度”若继续停留在“平台治理已完成，并已进入课程系统第一切片”，会直接误导后续任务的范围判断；它需要同步到当前主链路状态和下一步优先级。
- `docs/product-sense.md` 和 `docs/quality-score.md` 是最容易被忽略、但又最容易在接手时先扫一眼的入口页；它们必须至少覆盖模板工作区、修订历史、自定义试运行和真实 go-judge / RabbitMQ 基线。
- 全仓 `git diff --check` 在当前工作区并不适合作为收尾信号，因为 `.agents/skills/**`、`design/**` 和 `pom.xml` 已有与本任务无关的既有脏改动；仓库整理任务应改为对“本轮改动文件”做定向 diff 检查。
- 当前最合适的接手顺序已经比较稳定：
  1. `README.md`
  2. `docs/repository-structure.md`
  3. `docs/product-specs/index.md`
  4. `docs/exec-plans/active/README.md`
  5. `todo.md`
