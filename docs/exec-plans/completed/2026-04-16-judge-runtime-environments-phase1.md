# 2026-04-16 评测运行环境与语言模板第一阶段

## 目标

在不打破现有结构化编程题评测链路的前提下，继续增强真实 go-judge 运行时，使其具备：

- `GO` 语言第一阶段真实执行能力
- 题目级可配置评测运行环境
- 开课实例级可复用环境模板
- 更统一的多语言运行模板与执行元数据

当前切片聚焦“题目配置快照 + 课程内环境模板 + 真实执行 + 自动化验证”，不一次性展开平台级环境中心或分布式 worker 编排。

## 范围

- 扩展 `ProgrammingLanguage`，新增 Go 语言第一阶段
- 为结构化编程题配置新增评测运行环境快照，覆盖：
  - 环境标签 / 语言版本标签
  - 自定义编译命令 / 运行命令模板
  - 环境变量
  - 工作目录
  - 初始化脚本
  - 运行时支持文件
  - CPU 速率限制
- 让题库题目与作业题目都可携带上述环境快照，并继续以 assignment question snapshot 固定执行边界
- 新增开课实例级 `judge_environment_profiles`，允许教师在课程范围内维护可复用环境模板
- 新增 `languageExecutionEnvironments`，允许编程题按语言引用模板并做题目级覆盖
- 更新 `JudgeExecutionService` 与 `GoJudgeClient`，把环境配置真实映射到 go-judge `/run`
- 用真实 go-judge Testcontainers 补齐 Go 语言、环境模板和按语言环境选择专项集成测试

## 不在范围

- 平台级环境配置中心
- 评测 worker 水平扩展、重试策略和死信治理
- 对象存储化评测产物留存
- 浏览器前端 IDE 能力

## 风险

- go-judge `/run` 不支持按请求动态挂载宿主路径，所谓“挂载文件”只能在当前切片中收敛为受控 `copyIn` 支持文件
- 自定义编译 / 运行命令若过于自由，容易破坏结果一致性，因此必须保留默认模板并限制为模板化 shell 命令
- Go 语言多文件工程依赖 `go.mod` 与工作目录约定，默认模板需要对入口目录和模块根目录关系保持保守假设
- 环境模板一旦在题目发布后发生变化，不得反向污染既有 assignment snapshot，因此引用时必须先解析后固化

## 验证路径

- 先补失败的真实 go-judge 集成测试
- 执行 `bash ./mvnw spotless:apply`
- 执行与 judge / workspace 相关的定向测试
- 阶段收口执行 `bash ./mvnw clean verify`

## 决策记录

- 复用现有“题库题目配置 -> assignment question snapshot -> submission answer -> judge”链路，不新增平行业务模块
- 当前“挂载文件”实现为题目配置中的支持文件，通过 go-judge `copyIn` 注入到运行目录；未来若需要宿主目录挂载，再升级到专门基础设施配置
- 继续保留默认语言模板，同时允许题目级模板化命令覆盖，以兼顾一致性与扩展性
- 课程内可复用环境先收敛为开课实例级 `judge_environment_profiles`，不在本阶段额外引入平台级环境配置中心
- 题目引用环境模板时，平台在题库建题和 assignment 建卷环节就解析 `profileId / profileCode`，并把结果固化为 assignment question snapshot
- 对于 `JAVA21 / JAVA17 / CPP17 / GO122` 这类需要编译的语言，当前采用“两次 go-judge `/run` + `copyOut / copyIn` 回传编译产物”的方式拆分编译和运行阶段，避免第二阶段沙箱丢失编译结果

## 结果

- 已补齐题目级 `executionEnvironment` 快照模型，并打通题库题目、assignment question snapshot、正式评测和样例试运行两条链路
- 已补齐开课实例级 `judge_environment_profiles` 与题目级 `languageExecutionEnvironments`，支持教师在课程内复用语言环境模板，并在发布时固化为题目快照
- 已新增 `GO122`，并通过真实 go-judge Testcontainers 验证 Go 多文件工程 + `go.mod` + 工作目录 + 初始化脚本 + 环境变量覆盖场景
- 已通过真实 go-judge 集成测试验证“模板引用 -> assignment 快照 -> 正式评测 / 样例试运行”链路，不再依赖 fake judge
- 当前验证命令：
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw -Dtest=StructuredAssignmentIntegrationTests,StructuredProgrammingJudgeIntegrationTests test`
  - `bash ./mvnw -Dtest=ProgrammingWorkspaceIntegrationTests test`
  - `bash ./mvnw clean verify`
