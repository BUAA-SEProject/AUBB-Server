# Judge 详细产物对象化存储 Phase 2

## 背景

`V25__judge_artifact_object_storage_phase1.sql` 已经把正式评测详细报告和样例试运行详细报告 / 源码快照优先转为对象存储，但正式评测仍存在三个交付缺口：

- 缺少正式评测报告下载能力
- 缺少正式评测源码快照和归档清单的对象化归档
- 缺少 submission / answer / judge job 三个维度的产物追踪摘要

## 本次目标

在不重构 judge 主链路的前提下，把 phase 1 补成最小闭环：

- 正式评测支持学生 / 教师下载详细报告
- 正式评测补齐 `source_snapshot_object_key / artifact_manifest_object_key / artifact_trace_json`
- 旧数据继续兼容 `detail_report_json`，不做批量回填

## 方案

1. 在 `judge_jobs` 上新增三列：
   - `source_snapshot_object_key`
   - `artifact_manifest_object_key`
   - `artifact_trace_json`
2. 扩展 `JudgeArtifactStorageService`：
   - 为正式评测详细报告、正式评测源码快照、归档清单统一生成对象键、大小、内容类型和 `SHA-256`
3. 扩展 `JudgeExecutionService`：
   - 在终态持久化时同步写入详细报告、源码快照、归档清单与追踪摘要
4. 扩展 `JudgeApplicationService` 与 controller：
   - 新增学生 / 教师侧报告下载接口
   - 报告查询返回 `artifactTrace`
5. 历史兼容策略：
   - 旧 `detail_report_json` 继续保留
   - 查询 / 下载走“对象优先，旧 JSON 回退”
   - 不做批量 backfill

## 验证

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -q -DskipTests compile`
- `bash ./mvnw -Dtest=JudgeArtifactStorageServiceTests,StructuredProgrammingJudgeIntegrationTests,ProgrammingWorkspaceIntegrationTests test`
- `git diff --check`

## 当前边界

- 本次只补正式评测下载、追踪和归档元数据，不覆盖编译产物 bundle、对象版本化和镜像 digest。
- 成绩发布快照仍属于下一步独立切片。
