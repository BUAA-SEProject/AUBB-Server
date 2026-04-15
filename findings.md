# 发现与决策

## 当前任务基线

- 本次任务目标不是新增业务功能，而是完成一次整仓质量治理：代码审查、问题修复、目录结构优化、文档清理和目录说明补齐。
- 仓库当前采用模块优先的模块化单体结构，核心模块包括 `identityaccess`、`organization`、`platformconfig`、`audit`、`course`。
- 代码、测试与文档已经经历多轮快速演进，存在“历史任务记录仍留在工作记忆文件中、仓库文档是否仍与当前实现一致需要复核”的明显风险。

## 初步观察

- `AGENTS.md`、`README.md`、`ARCHITECTURE.md` 已描述模块优先结构与当前功能范围，但是否与代码和 API 完全一致仍需逐项核对。
- `docs/exec-plans/active/README.md` 目前只有说明，没有当前任务执行计划文件；本轮应至少补齐一份执行计划。
- 工作树中存在大量 `.agents/skills/**` 修改，这些更像环境或技能资源的外部改动，不属于本次仓库治理范围，后续应避免误操作。

## 待确认问题方向

- 代码层：模块边界、命名、异常处理、测试缺口、配置与依赖声明是否存在冗余或错误。
- 结构层：目录索引是否清晰，是否存在文档与代码的“双源真相”或重复入口。
- 文档层：过时文档、弱价值文档、未被索引文档、与当前实现不一致的描述。

## 已确认问题

- 文档口径滞后：
  - `AGENTS.md`、`docs/product-sense.md`、`docs/product-specs/platform-baseline.md` 等位置仍残留“首个真实业务切片仅为平台治理”的旧说法，没有完整反映课程系统第一切片已落地。
- 文档入口重复或弱价值：
  - `docs/plans.md` 只是 `docs/plan.md` 的轻量转发，持续价值较弱。
  - `docs/references/openai-harness-engineering-notes.md` 偏外部来源摘记，对当前项目开发的直接帮助有限。
- 目录结构可读性不足：
  - 缺少一份专门解释代码、测试、文档、设计草稿和工作记忆文件边界的结构说明文档。
  - 集成测试位于 `src/test/java/com/aubb/server/api`，会让后续开发者误以为它属于生产 `api` 分层。
- 配置治理缺口：
  - OpenAPI/Swagger 的开发期开启策略缺少显式配置项，虽然当前可用，但生产关闭路径不够直观。
- 验证注意事项：
  - 在测试包迁移后，直接执行 `verify` 会受到 `target/test-classes` 中旧 class 残留影响，需要 `clean verify` 才能得到最终可信结果。

## 已执行修复

- 将集成测试迁移到 `src/test/java/com/aubb/server/integration`，并通过 `RepositoryStructureTests` 固化新约束。
- 新增 `docs/repository-structure.md`，明确顶层目录、代码放置规则、测试放置规则、文档边界和禁止事项。
- 新增 `docs/exec-plans/active/2026-04-15-repository-audit-remediation.md`，为本轮整仓治理建立正式执行计划。
- 更新 `README.md`、`AGENTS.md`、`ARCHITECTURE.md`、`docs/index.md`、`docs/design.md`、`docs/product-sense.md`、`docs/product-specs/platform-baseline.md`、`docs/security.md`、`docs/quality-score.md`、`docs/reliability.md` 和执行计划索引，使其与当前实现同步。
- 删除弱价值文档入口 `docs/plans.md` 与 `docs/references/openai-harness-engineering-notes.md`。
- 在 `application.yaml` 中显式加入 `AUBB_API_DOCS_ENABLED` 与 `AUBB_SWAGGER_UI_ENABLED` 开关，补齐 OpenAPI 端点的生产关闭路径。
- 将生产代码中 MyBatis-Plus 的 `selectBatchIds` 旧调用切换为 `selectByIds`，消除生产代码编译期弃用告警。
