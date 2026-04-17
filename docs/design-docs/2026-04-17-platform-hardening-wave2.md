# 2026-04-17 平台第二轮硬化与交付深化设计

## 目标

围绕当前已经贯通的 “课程 -> 作业 -> 提交 -> 评测 -> 批改 -> 成绩 -> 通知” 主链路，完成第二轮面向生产化的深化改造，使系统从“功能闭环可联调”推进到“队列可治理、查询可扩展、授权可控、对象存储可预检、发布可演练、观测可接入、题库可扩展、评测可复现、通知可异步、接口文档可收口”的状态。

## 范围

本轮覆盖以下十类工作：

1. judge worker 独立化、重试 / DLQ / 排队治理与运维脚本
2. 成绩册 / 报表数据库侧分页与聚合
3. 组织范围授权去掉全量组织树内存计算
4. MinIO / 附件依赖环境校验与部署前检查
5. staging / UAT、备份恢复、发布回滚与发布演练流程
6. tracing、告警规则、Grafana 面板和日志聚合
7. 题库检索、批量组卷编辑、第二阶段组卷 API
8. 评测可复现性深化：固定产物包、环境版本与报告归档
9. 通知 fan-out 异步化与可选 WebSocket / SSE 推送层
10. stable API 文档漂移治理与静态 OpenAPI 决策

## 现状与主要问题

### 评测链路

- RabbitMQ 目前只有单队列第一阶段，没有独立 worker、延迟重试和 DLQ。
- 评测详细报告、源码快照和产物追踪已经对象化，但执行环境、编译产物包和归档策略还不够稳定。

### 成绩与授权

- `GradebookApplicationService` 仍先构建全量 `GradebookSnapshot` 再做分页，报表也复用全量快照。
- `GovernanceAuthorizationService` 仍通过 `selectList` 加载全量组织树并在内存里做祖先判断。

### 题库与通知

- 题库列表已支持标签 / 分类 / 关键词过滤，但仍是“全量查询 + Java skip/limit”。
- 通知 fan-out 目前在业务事务内同步写 `notifications + notification_receipts`。
- 实时推送还没有稳定承载层。

### 运维与交付

- MinIO / go-judge / RabbitMQ 已进入 readiness，但缺少更强的部署前检查和运行参数校验。
- 部署当前是最小 SSH + Compose 基线，还没有 staging/UAT、备份恢复、发布演练和外部告警配置。
- OpenAPI 目前以运行时 `/v3/api-docs` 为事实源，静态产物仍未形成明确策略。

## 设计分组

### A. 评测与对象存储硬化

- judge queue 拆成 `main / retry / dlq` 三段，并新增 `publisherEnabled / consumerEnabled / prefetch / maxAttempts / retryDelay` 配置。
- 同一可执行包支持两种角色：
  - web/app：发布评测任务，不消费队列
  - judge-worker：消费队列、调用 go-judge、写回结果
- 正式评测继续由 `judge_jobs` 作为事实来源，不新引入独立调度服务。
- 评测归档补 `reproducibility metadata`，至少包含：
  - 语言与语言版本
  - 运行环境快照
  - go-judge endpoint / engine 版本
  - 源码包摘要、编译产物包摘要、报告摘要

### B. 查询与授权收口

- 组织祖先判断改为数据库侧递归查询 / 受限 ancestor chain 查询，不再全量加载组织树。
- 成绩册分页改为两段式：
  - 先用数据库分页拿当前页 roster
  - 再只针对当前页 student ids 拉作业矩阵
- 成绩报表改为数据库聚合，页面矩阵和 report 拆开，避免 report 再依赖全量 page snapshot。

### C. 题库与通知第二阶段

- 题库列表改为数据库侧分页；在现有分类 / 标签过滤上扩展更强关键字检索字段。
- 组卷 API 增加批量编辑能力，允许前端一次提交整个 section / question patch 集合，而不是只能整份替换或单次大请求耦合。
- 通知 fan-out 改为 AFTER_COMMIT 异步派发。
- 推送层采用“持久化通知模型不变 + 可选实时层”的原则：
  - 默认仍保留列表轮询
  - 新增 SSE hub；WebSocket 作为设计保留，不强制本轮落地

### D. 运维、观测与文档

- 加入 Micrometer tracing / OTLP 配置开关，不改变业务 API。
- 仓库新增 Prometheus 告警规则、Grafana dashboard、Loki/Promtail 示例配置与 staging/UAT / backup / rollback runbook。
- OpenAPI 继续以运行时产物为事实源，同时补一个静态导出脚本 / CI 产物，作为发布辅助，而不是第二事实源。

## 关键设计决策

1. 不拆微服务；judge worker 仍与主应用共享代码和数据库，只在运行角色和消息消费责任上拆开。
2. 不引入 Redis；通知异步与 SSE registry 先用现有 Spring 能力和单实例基线实现。
3. 成绩册不新增独立事实表；继续复用 `assignments / submissions / submission_answers / course_members`。
4. 组织授权不改 role 模型，只改“祖先可见性计算方式”。
5. 静态 OpenAPI 只做发布辅助产物，不替代运行时 `/v3/api-docs`。

## 风险

- gradebook 改造需要同时保证分页语义、排序语义、排名语义和 CSV / report 一致。
- judge queue 重试与 worker 拆分会影响真实 RabbitMQ 集成测试，需要同步调整测试装配。
- 通知 fan-out 改为异步后，集成测试不能再假设同步入箱，需要显式等待或轮询。
- SSE 是增量能力，不能污染现有稳定 API 范围。

## 验证策略

- 先补失败测试，再做实现。
- 至少覆盖：
  - judge queue / DLQ / worker 配置测试
  - GovernanceAuthorizationService 祖先链路测试
  - GradebookIntegrationTests 与题库集成测试
  - NotificationCenterIntegrationTests 与 OpenAPI 契约测试
- 变更完成后执行定向测试，再做一次 `bash ./mvnw verify`。

