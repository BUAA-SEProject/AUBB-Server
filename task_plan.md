# 任务计划：代码目录结构优化

## 目标

检查当前仓库代码目录结构，识别文件过于密集的目录，在不破坏既有模块边界和业务语义的前提下优化目录编排，让单个目录下的文件数更均衡、更易继续开发。

## 当前阶段

Phase 3 completed

## Skills 选择

- `planning-with-files`：本任务涉及结构审查、批量移动、导入修正、文档同步和验证，需要持续记录阶段和决策。
- `springboot-patterns`：用于约束 Spring Boot 模块内部的服务、DTO、领域和基础设施分组，避免为了“拆目录”而破坏分层。
- `springboot-verification`：用于约束格式化和全量验证。
- `documentation-writer`：用于同步架构、可靠性、安全和对象存储说明。

## 阶段

### Phase 1：热点目录审查与拆分方案

- [x] 统计当前各目录文件数
- [x] 确认真正需要拆分的热点目录
- [x] 制定最小必要的目录重组方案
- **Status:** completed

### Phase 2：目录重组与导入修正

- [x] 移动拥挤目录中的文件到更细职责分组
- [x] 修正包声明与 import
- [x] 保持现有模块边界和测试结构不变
- **Status:** completed

### Phase 3：文档同步与收尾

- [x] 更新架构和目录结构说明
- [x] 执行 `./mvnw spotless:apply`
- [x] 执行 `./mvnw clean verify`
- [x] 在合适时机做 git 提交
- **Status:** completed

## 已做决策

| Decision | Rationale |
|----------|-----------|
| 只拆真正拥挤的目录 | 避免为了形式统一而制造无意义层级 |
| 不改变 `modules.<module>.api/application/domain/infrastructure` 大边界 | 保持仓库现有模块优先结构稳定 |
| 优先把平铺的记录类、枚举和实体/Mapper 按职责成组 | 这类文件天然成组，拆分风险最低 |

## 错误记录

| Error | Attempt | Resolution |
|-------|---------|------------|
| 包重排后 `spotless` 先于编译阶段失败 | 先直接编译确认 | 改为先执行 `./mvnw spotless:apply`，再继续编译和验证 |
| 子目录重排后同包类型丢失隐式可见性 | 用 `./mvnw -q -DskipTests compile` 找出遗漏 import | 补齐 `course` 与 `identityaccess` 的 application service 显式 import |
| 增量编译未覆盖到全部漏项，`clean verify` 才暴露剩余符号错误 | 改为使用 `clean verify` 做最终收口 | 补齐 `CourseTeachingApplicationService`、`UserAdministrationApplicationService` 和两个领域测试的剩余导入/包路径 |
