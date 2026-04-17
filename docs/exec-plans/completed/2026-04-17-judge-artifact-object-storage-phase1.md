# 2026-04-17 judge 详细产物对象化存储第一阶段

## 目标

把 judge 大体积评测产物从数据库主存迁移到对象存储，数据库只保留状态、摘要和对象引用，同时保持现有报告查询 API 与样例试运行查询链路兼容。

## 范围

- `judge_jobs` 详细报告对象化
- `programming_sample_runs` 详细报告对象化
- `programming_sample_runs` 源码快照对象化
- 兼容读取、最小 Flyway migration、MinIO 真实链路测试和文档同步

## 非目标

- 不拆分独立 judge 产物元数据表
- 不引入对象版本化、冷热分层或归档生命周期管理
- 不持久化 go-judge 镜像 digest、编译产物 bundle 或其他更重的可复现实体

## 根因

1. `judge_jobs.detail_report_json` 和 `programming_sample_runs.detail_report_json` 已经承载完整日志、执行命令和测试点明细，行体积持续增大。
2. `programming_sample_runs` 还直接保存 `code_text / source_files_json / source_directories_json / stdout_text / stderr_text`，导致样例试运行行宽进一步膨胀。
3. 现有 API 直接读取这些内联字段，缺少清晰的“摘要在 DB、完整产物在对象存储”的边界。
4. 正式评测和样例试运行在可复现性上需求不同：正式评测已有 `submission_answer_id` 稳定锚点，样例试运行则需要单独保留源码快照。

## 最小实现方案

1. 保留 `judge_jobs.case_results_json` 作为列表摘要，避免影响热点查询和权限过滤路径。
2. 为 `judge_jobs` 新增 `detail_report_object_key`，把完整详细报告优先写入对象存储。
3. 为 `programming_sample_runs` 新增：
   - `detail_report_object_key`
   - `source_snapshot_object_key`
4. 读路径统一改为“对象优先、旧列兼容回退”，保证已有 API 无需变更。
5. 正式评测的可复现第一阶段依赖：
   - `submission_id / submission_answer_id / assignment_question_id`
   - `executionMetadata.sourceSnapshotRef`
   - `compileArgs / runArgs / executionEnvironment`
   - 对象化后的详细报告
6. 样例试运行额外保留本次运行源码快照对象，覆盖源码回放需求。

## 实施结果

1. 已新增 `V25__judge_artifact_object_storage_phase1.sql`
2. 已新增 `JudgeArtifactStorageService`，统一处理 judge 详细报告和样例试运行源码快照的对象化读写
3. `JudgeExecutionService` 现在会优先把正式评测详细报告写入对象存储，并在 `executionMetadata` 中补充 `sourceSnapshotRef`
4. `JudgeApplicationService` 现在按对象引用回放正式评测报告，`detailReportAvailable` 也切换为对象优先判定
5. `ProgrammingSampleRunApplicationService` 现在会优先把详细报告和源码快照对象化，并在返回视图时自动回放
6. 已补齐对象化单元测试和 MinIO 真实链路集成测试
7. 已同步更新 README、judge 规格、对象存储文档、可靠性文档和数据库结构说明

## 验证结果

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -Dtest=JudgeArtifactStorageServiceTests,StructuredProgrammingJudgeIntegrationTests,ProgrammingWorkspaceIntegrationTests,MinioStorageIntegrationTests test`
- 结果：`BUILD SUCCESS`，定向 `22` 个测试通过

## 风险

- 当前对象化仍是单 bucket、单对象 key 方案，后续若要做归档分层，需要补独立生命周期策略。
- 正式评测的源码可复现第一阶段依赖 `submission_answers` 与附件引用链，而不是独立源码对象；后续若要做长周期归档，仍需评估是否补正式源码快照对象。
- 当前只验证了最小必要 judge / MinIO 测试集，未执行全量 `bash ./mvnw verify`。

## 下一步

- 进入优先级 7：定义并落地 lab/report MVP。
