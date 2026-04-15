# 执行计划：模块优先的模块化单体重构

## 目标

将当前 `AUBB-Server` 从“顶层按 `api / application / domain / infrastructure` 分层组织”增量重构为“按业务模块优先组织、模块内部分层”的模块化单体，实现首批治理模块的清晰边界，并保持现有接口与行为稳定。

## 范围

- `src/main/java` 下当前已落地治理代码的包结构与目录结构
- `src/test/java` 下与包结构相关的测试与 harness 约束
- `ARCHITECTURE.md`、`docs/` 下的架构/规范/设计文档
- `../docs` 中与当前包结构和工程规范直接相关的文档

## 不在范围

- 拆分 Maven 多模块
- 新增独立进程或远程服务
- 改动 API 路径、数据库表结构或治理业务规则
- 启动课程域、IDE、任务、提交、评测等后续业务模块实现

## 首批模块边界

- `identityaccess`
  - 登录、JWT、当前用户
  - 用户创建、导入、查询、详情、状态管理
  - 平台治理身份、作用域授权、密码与角色规则
- `organization`
  - 学校/学院/课程/班级组织树维护与组织摘要查询
- `platformconfig`
  - 单份即时生效的平台配置读取与更新
- `audit`
  - 审计记录写入与检索

共享顶层保留：

- `com.aubb.server.config`
- `com.aubb.server.common`
- `com.aubb.server.infrastructure.persistence`

## 计划动作

1. 先用仓库 harness 测试固化“业务代码进入 `modules/`”的结构约束。
2. 将首批治理代码迁移到 `com.aubb.server.modules.<module>.<layer>`。
3. 修复导包、测试和文档引用。
4. 新增 ADR，说明为何采用模块优先结构以及当前模块划分原则。
5. 执行格式化与全量验证。

## 风险

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 包迁移导致导包大量失效 | 编译中断 | 先固化模块映射，再批量迁移并立即跑测试 |
| 把强耦合能力拆成过细模块 | 产生大量跨模块直接依赖 | 首批合并为 `identityaccess` 模块，后续再细化 |
| 文档只更新仓库内而未同步系统级设计 | 架构口径失真 | 同步更新 `ARCHITECTURE.md`、ADR、工程规范和系统级详细设计 |

## 验证路径

- 仓库结构测试：
  - `RepositoryHarnessTests` 能识别模块优先目录并拒绝旧的业务顶层目录
- 编译与行为验证：
  - `./mvnw -Dtest=RepositoryHarnessTests test`
  - `./mvnw -Dtest=AuthApiIntegrationTests,PlatformGovernanceApiIntegrationTests test`
  - `./mvnw verify`

## 决策记录

- 当前实现先采用“模块优先、模块内分层”的单仓单应用结构，不引入多模块 Maven 或微服务边界。
- `identityaccess` 作为首批聚合模块，承接用户、认证与 IAM，避免过度早拆。

## 退出条件

- 首批治理能力已经完成到 `com.aubb.server.modules.<module>.<layer>` 的迁移
- 仓库级 harness 已能阻止旧的顶层业务目录重新承载代码
- 架构、开发流程、ADR 和系统级工程文档已经统一为模块优先口径
- `./mvnw verify` 通过

## 完成记录

- 2026-04-15：新增模块优先目录约束，要求业务代码进入 `src/main/java/com/aubb/server/modules/`，并将 ADR-0004 纳入必备设计文档。
- 2026-04-15：完成认证、用户、IAM、组织、平台配置与审计代码的迁包，保留 `common`、`config`、`infrastructure.persistence` 作为共享顶层。
- 2026-04-15：同步更新 `AGENTS.md`、`ARCHITECTURE.md`、`docs/` 与 `../docs` 中与包结构和工程规范相关的文档。
- 2026-04-15：执行 `./mvnw spotless:apply`、`./mvnw -Dtest=RepositoryHarnessTests test`、`./mvnw -Dtest=AuthApiIntegrationTests,PlatformGovernanceApiIntegrationTests test`、`./mvnw -Dtest=GovernanceRolePolicyTests,PasswordPolicyTests,OrganizationPolicyTests test` 与 `./mvnw verify`，验证通过。

## 结果说明

- 本次重构完成后，仓库已经从“按层分散组织”切换为“模块优先的模块化单体”；后续新增业务域可在同一结构下继续演进，而无需立即拆分 Maven 多模块或微服务。
