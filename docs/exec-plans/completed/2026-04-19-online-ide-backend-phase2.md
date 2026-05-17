# 2026-04-19 在线 IDE 后端继续完善

## 目标

在不重写现有模型、不破坏 `assignment -> submission -> judge` 职责边界、且保持 `codeText` 兼容语义的前提下，把当前在线 IDE 后端从“已经能跑”推进到“可稳定支撑真实浏览器 IDE 联调与生产使用”的程度。当前轮优先处理工作区写入语义、自动保存、断线恢复元信息、路径安全和样例试运行稳定错误模型。

## 现状能力盘点

### 已有能力

- `submission` 已提供：
  - 模板工作区加载
  - 工作区完整目录树快照保存
  - `CREATE_FILE / UPDATE_FILE / CREATE_DIRECTORY / RENAME_PATH / DELETE_PATH`
  - 工作区修订历史、修订详情、历史恢复、模板重置
  - 最近一次 `stdin` 持久化
- `judge` 已提供：
  - 样例试运行创建 / 列表 / 详情
  - 显式快照、当前工作区、历史修订三种源码来源
  - 详细报告与源码快照对象化存储
  - `PYTHON3 / JAVA21 / CPP17 / GO122`
  - `compileArgs / runArgs / executionEnvironment`
- 安全与权限：
  - `ide.read / ide.save / ide.run`
  - assignment 可见性、归档只读、时间窗、成员状态约束
- 自动化验证：
  - `ProgrammingWorkspaceIntegrationTests`
  - `StructuredProgrammingJudgeIntegrationTests`
  - `SubmissionIntegrationTests`
  - `JudgeIntegrationTests`

### 当前缺口

#### P0

1. 工作区写入缺少并发冲突检测
- 现有 `PUT /workspace`、`POST /workspace/operations`、`restore`、`reset-to-template` 都没有 `baseRevisionId` 或等价前提条件。
- 多标签页 / 多设备并发写入时，后写覆盖前写，前端无法识别 stale write。

2. 自动保存缺少去噪
- `saveKind=AUTO` 当前只映射到 `AUTO_SAVE` revision kind。
- 每次自动保存都会追加完整快照 revision，缺少最小幂等或无变更跳过策略。

3. 工作区初始化元信息不足
- `GET /workspace` 能返回源码、目录、`lastStdinText`、`latestRevisionId/No`、`updatedAt`。
- 但不会明确告诉前端：
  - 当前是否可编辑
  - 当前是否可运行
  - 不可编辑 / 不可运行的原因
  - 最新修订种类

4. 路径安全仍缺关键边界
- 现有实现已经拦截绝对路径、`../`、反斜杠、双斜杠。
- 仍未覆盖：
  - 大小写冲突
  - 目录重命名到自身子路径
  - 只改大小写的重命名边界

#### P1

1. 样例试运行详情错误模型不稳定
- 详细报告对象缺失时，列表接口因为不加载 detail report 可以继续工作。
- 详情接口仍可能抛出未处理异常，前端拿不到稳定业务错误码。

2. 最小前端联调契约未收口
- 当前返回体可用，但还没正式表达“冲突写入 / 已过期 / 需重拉”的语义。
- 文档也还没把“哪些字段用于自动保存 / 断线恢复 / 冲突处理”写成权威事实。

#### P2

1. 保存 / 试运行性能与可观测性还可继续增强
- 当前尚未为工作区保存单独加限流策略。
- 也尚未增加保存冲突、自动保存去噪命中等可观测指标。

## 后端必须补的内容

- 保存 / 操作 / 恢复 / 模板重置的冲突检测
- 自动保存的最小去噪与幂等支持
- 工作区初始化元信息与稳定错误码
- 路径 / 目录树边界保护
- 样例试运行详情对象缺失兼容
- OpenAPI / README / stable-api / product specs / db-schema 同步

## 明确不在本仓库实现

- Monaco 编辑器
- 浏览器目录树组件
- 终端 UI
- 协同编辑 / presence / WebSocket 文本同步
- 浏览器端语法高亮、自动补全、格式化

## 实施顺序

### Phase A：盘点与计划

- [x] 核对代码、migration、集成测试与现有文档
- [x] 输出已有能力、缺口与优先级
- [x] 同步根目录 planning files

### Phase B：工作区写入语义

- [x] 先写失败测试：
  - stale `baseRevisionId` 写入返回 409
  - 自动保存无变更不新增 revision
  - 工作区读取返回编辑 / 运行元信息
- [x] 再做最小实现：
  - `PUT /workspace`、`POST /workspace/operations`、`restore`、`reset-to-template` 请求追加可选 `baseRevisionId`
  - 工作区返回体追加 `latestRevisionKind / editable / editBlockedReasonCode / runnable / runBlockedReasonCode`
  - `AUTO_SAVE` 在工作区无变更时直接回放当前状态，不再产生冗余修订

### Phase C：路径安全与稳定错误模型

- [x] 先写失败测试：
  - 大小写冲突
  - 重命名到自身子路径
  - 详细报告对象缺失时的稳定错误
- [x] 再做最小实现
  - 工作区与样例试运行显式快照统一拦截大小写冲突
  - 工作区目录重命名到自身子路径时返回稳定业务码 `PROGRAMMING_PATH_RENAME_DESCENDANT_INVALID`
  - 样例试运行详情在详细报告对象缺失时回退为摘要响应，并返回 `detailReportUnavailableReasonCode`

### Phase D：文档与契约

- [x] 同步 stable-api、README、submission / judge spec
- [x] 确认前端如何使用 `latestRevisionId` / `baseRevisionId`
- [x] 明确自动保存、冲突、恢复与错误码语义

### Phase E：验证

- [x] `bash ./mvnw spotless:apply`
- [x] `bash ./mvnw -Dtest=ProgrammingWorkspaceIntegrationTests test`
- [x] `bash ./mvnw -Dtest=ProgrammingWorkspaceIntegrationTests,StructuredProgrammingJudgeIntegrationTests,JudgeIntegrationTests test`
- [x] 修复 `course_members -> role_bindings` 生效窗口造成的交叉授权误拒
- [x] `bash ./mvnw -Dtest=ProgrammingWorkspaceIntegrationTests,StructuredProgrammingJudgeIntegrationTests,SubmissionIntegrationTests,JudgeIntegrationTests test`
- [x] 收口 `AssignmentIntegrationTests` / `GradebookIntegrationTests` 的教师 token 快照刷新时序抖动
- [x] `bash ./mvnw test`
- [x] `bash ./mvnw verify`

## 本轮结果

- 已完成：
  - 工作区写入冲突检测
  - 自动保存无变更去噪
  - 工作区前端初始化元信息
  - 路径大小写冲突与目录重命名硬边界
  - 样例试运行详情对象缺失回退
- 已验证通过：
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw -q -DskipTests compile`
  - `bash ./mvnw -Dtest=ProgrammingWorkspaceIntegrationTests test`
  - `bash ./mvnw -Dtest=CourseSystemIntegrationTests#loginAfterOfferingCreationShouldUseInstructorRoleBindingSnapshot+loginAfterClassMemberAddedShouldUseStudentRoleBindingSnapshot,JudgeIntegrationTests#syntaxErrorBecomesSuccessfulJudgeJobWithCompileFailureSummary,SubmissionIntegrationTests#classScopedStudentCanReadOwnSubmissionHistoryForOfferingWideAssignment test`
  - `bash ./mvnw -Dtest=ProgrammingWorkspaceIntegrationTests,StructuredProgrammingJudgeIntegrationTests,SubmissionIntegrationTests,JudgeIntegrationTests test`
  - `bash ./mvnw -Dtest=AssignmentIntegrationTests test`
  - `bash ./mvnw -Dtest=GradebookIntegrationTests test`
  - `bash ./mvnw -Dtest=AssignmentIntegrationTests,GradebookIntegrationTests test`
  - `bash ./mvnw test`
  - `bash ./mvnw verify`
- 交叉链路现状：
  - `JudgeIntegrationTests`、`StructuredProgrammingJudgeIntegrationTests`、`SubmissionIntegrationTests`、`ProgrammingWorkspaceIntegrationTests` 均通过
  - 之前的 403 根因已确认为教学成员同步出的 role binding 存在短暂未生效窗口，不是 `workspace/sample-run` 设计本身的行为缺陷
  - 2026-04-19 已通过 `V47__expand_legacy_binding_activation_tolerance_window.sql` 与应用层/查询层同步，把激活容忍窗口统一扩展到 `3 seconds`
  - 新增教师/学生登录快照回归后，`task.create` 与读写交叉链路已恢复稳定
  - `AssignmentIntegrationTests` 与 `GradebookIntegrationTests` 在全量 surefire / failsafe 下已稳定通过
  - 最新 fresh 全量 `test` 326/326 通过
  - 上一轮 `verify` 已通过；当前轮将在文档收口后重新执行

## 风险

- 如果直接改写 revision 记录来“合并自动保存”，会破坏样例试运行对 `workspaceRevisionId` 的历史引用稳定性。
- 如果把路径大小写冲突规则只改在 workspace，不同步考虑 sample run / formal submission，会出现链路语义分叉。
- 如果冲突错误只返回 400，前端无法把它和普通校验错误区分；应显式用 409。
- 课程成员同步出的 role binding 当前仍依赖 legacy member 数据源做事实来源；这轮只修了“即时生效”窗口，没有进一步清理 legacy 兼容读取面。

## 当前决策

- 冲突检测优先基于现有 `latestRevisionId` 做前提条件，不新起平行版本号体系。
- 自动保存先做“无变更跳过”和最小幂等，不在本轮引入复杂 patch / CRDT / 协同协议。
- 前端初始化元信息以追加字段方式落地，不破坏现有返回体兼容。
