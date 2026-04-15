# 任务计划：todo 驱动的开发推进（submission 附件 + judge 第一切片）

## 目标

根据 `todo.md` 盘点当前平台真实开发进度，更新仓库内执行计划与工作记忆，并沿主链路继续推进开发。本轮先完成 `submission` 附件切片，再继续落地 `judge` 第一切片，为后续 go-judge 执行器、grading 和实验链路提供稳定输入。

## 当前阶段

Phase 5 completed

## Skills 选择

- `planning-with-files`：本任务跨进度盘点、计划更新、代码实现、文档同步和验证，需要持续维护工作记忆。
- `springboot-patterns`：用于保持模块优先、层内分层的 Spring Boot 实现风格稳定。
- `springboot-tdd`：本轮新增提交附件能力，先补失败测试，再做最小实现。
- `springboot-verification`：用于收口格式化、专项测试和全量验证。
- `postgresql-table-design`：用于设计 `submission` 附件相关表结构、索引和约束。
- `api-design-principles`：用于设计上传、提交通道和下载接口，保证路径与语义一致。

## 阶段

### Phase 1：todo 进度盘点与计划更新

- [x] 读取 `todo.md`、架构、产品规格和模块实现
- [x] 输出当前已完成 / 部分完成 / 未开始的能力映射
- [x] 新增执行计划并锁定本轮开发切片
- **Status:** completed

### Phase 2：提交附件能力设计与实现

- [x] 为 submission 设计附件元数据表和对象键规则
- [x] 补充上传、正式提交关联、查询与下载接口
- [x] 保持学生 / 教师授权边界与审计链路正确
- **Status:** completed

### Phase 3：测试与文档同步

- [x] 新增或更新 submission 集成测试
- [x] 更新 `todo.md`、产品规格、架构、对象存储说明和数据库结构文档
- [x] 在仓库计划中记录当前实现边界与后续扩展位
- **Status:** completed

### Phase 4：judge 第一切片

- [x] 新增 `judge_jobs` 数据模型与评测状态枚举
- [x] 提交后自动创建评测作业
- [x] 补充学生/教师评测作业查询与教师重排队接口
- [x] 新增专项测试并通过
- [x] 同步 judge 规格、架构和数据库文档
- **Status:** completed

### Phase 5：验证与提交

- [x] 执行 `./mvnw spotless:apply`
- [x] 执行 `./mvnw clean verify`
- [ ] 做一次范围清晰的 git 提交
- **Status:** in_progress

## 已做决策

| Decision | Rationale |
|----------|-----------|
| 本轮优先推进 `submission` 附件切片，而不是直接上 judge | 当前仓库已具备 assignment、submission 和 MinIO 基础设施，补附件是最短主链路缺口 |
| 附件采用“先上传资产，再在正式提交时关联”的两阶段模型 | 便于支持多文件提交、版本留痕和后续工作区 / 草稿扩展 |
| 继续保持 `submission` 模块内部闭环，不新建临时共享业务目录 | 避免把业务语义误塞进 `common` 或 `config` |

## 错误记录

| Error | Attempt | Resolution |
|-------|---------|------------|
| `./mvnw` 在当前环境中不可直接执行 | 改用 `bash ./mvnw` | 已解决 |
| `/root/.m2` 元数据写入受限 | 改用离线执行与已缓存依赖 | 已解决 |
