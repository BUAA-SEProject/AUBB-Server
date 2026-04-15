# 任务计划：模块优先的模块化单体重构

## 目标

围绕 AUBB-Server 当前已落地的 Phase 2 平台治理能力，对后端仓库进行一次可落地的模块化单体重构，使代码组织从“顶层分层优先”升级为“模块优先、模块内分层”，同时保持行为、接口和验证基线稳定。

## 当前阶段

Completed

## 阶段

### Phase 1：范围确认与模块映射

- [x] 读取当前架构、工程规范、系统级设计和已有工作记忆
- [x] 盘点现有包结构、代码依赖和测试分布
- [x] 确定首批模块边界与迁移映射
- **Status:** complete

### Phase 2：测试先行固化模块化约束

- [x] 在仓库 harness 测试中增加模块优先目录约束
- [x] 明确哪些顶层共享包允许继续存在，哪些业务代码必须迁入 `modules/`
- **Status:** complete

### Phase 3：代码迁包与依赖修复

- [x] 将业务代码迁移到 `com.aubb.server.modules.<module>.<layer>`
- [x] 修复 Spring 组件扫描、导入引用与测试编译错误
- [x] 保持 API 路径、数据库结构和业务行为不变
- **Status:** complete

### Phase 4：架构文档与 ADR 同步

- [x] 更新 `ARCHITECTURE.md`、`docs/design.md`、`docs/development-workflow.md`
- [x] 新增模块化单体 ADR，记录模块边界、共享包约束与后续扩展位
- [x] 同步更新 `../docs` 中与包结构和工程规范直接相关的文档
- **Status:** complete

### Phase 5：格式化、验证与归档

- [x] 执行格式化与针对性测试
- [x] 执行 `./mvnw verify`
- [x] 更新 `findings.md`、`progress.md` 和执行计划状态
- **Status:** complete

## 关键问题

1. 当前仓库是否应拆成多个细模块，还是先把强耦合的认证、用户、身份合并为一个 `identityaccess` 模块？
2. 模块化单体重构要做到什么深度，才能既建立清晰边界，又不引入过多非功能性改动？
3. 哪些共享能力应留在顶层，例如 `config`、`common` 和持久化公共适配器？

## 已做决策

| Decision | Rationale |
|----------|-----------|
| 首批模块采用 `identityaccess`、`organization`、`platformconfig`、`audit` 四个模块 | 能覆盖当前已落地治理能力，同时减少把强耦合的用户/认证/IAM 人为拆散带来的跨模块依赖 |
| `common`、`config` 与共享持久化适配保留在顶层 | 这些属于跨模块基础设施，不应被误建模为业务模块 |
| 本轮重构只调整包结构、目录约束和文档，不改变 API 路径和数据库结构 | 控制风险，确保这是一次架构内聚化重构而不是需求扩展 |

## 错误记录

| Error | Attempt | Resolution |
|-------|---------|------------|
| `./mvnw spotless:apply` 在沙箱内因 Maven 本地仓库写入受限失败 | 先在默认沙箱执行格式化 | 提升权限后重跑，格式化完成 |
| Testcontainers 相关集成测试在沙箱内无法访问 Docker | 先执行 `./mvnw -Dtest=AuthApiIntegrationTests,PlatformGovernanceApiIntegrationTests test` | 提升权限后重跑，14 个测试通过 |
| 首次执行 `./mvnw verify` 时旧包路径下的领域测试因迁包失效 | 根据失败栈定位旧测试类与包名 | 将领域测试迁移到 `src/test/java/com/aubb/server/modules/...` 并更新包声明，随后全量验证通过 |
