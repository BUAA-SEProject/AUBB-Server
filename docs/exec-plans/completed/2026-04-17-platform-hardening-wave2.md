# 2026-04-17 平台第二轮硬化与交付深化计划

## 状态

- 状态：已完成
- 完成时间：2026-04-17
- 最终验证：`bash ./mvnw verify`

## 目标

按最小破坏、可持续演进的方式，把评测、成绩、授权、对象存储、通知、运维与接口契约推进到第二阶段生产化基线。

## 范围

- judge worker / retry / DLQ / queue governance
- gradebook / report DB 分页与聚合
- governance authorization DB 祖先判断
- MinIO / 附件 preflight
- staging / UAT / backup / rollback / drill
- tracing / alerting / Grafana / log aggregation
- question bank search / batch paper editing / paper API phase 2
- reproducibility metadata / artifact bundle / archive strategy
- async notification fan-out / SSE push
- stable API / static OpenAPI asset

## 实施批次

### 批次 1：核心后端改造

1. judge queue 配置扩展
   - `src/main/java/com/aubb/server/config/JudgeQueueConfiguration.java`
   - `src/main/java/com/aubb/server/modules/judge/application/*`
   - `src/test/java/com/aubb/server/modules/judge/**`
   - `src/test/java/com/aubb/server/integration/AbstractRealJudgeIntegrationTest.java`
2. GovernanceAuthorizationService 去全量树
   - `src/main/java/com/aubb/server/modules/identityaccess/application/iam/GovernanceAuthorizationService.java`
   - `src/main/java/com/aubb/server/modules/organization/infrastructure/*`
   - `src/test/java/com/aubb/server/modules/identityaccess/application/iam/GovernanceAuthorizationServiceTests.java`
3. 题库数据库分页检索 + phase2 API
   - `src/main/java/com/aubb/server/modules/assignment/application/bank/*`
   - `src/main/java/com/aubb/server/modules/assignment/application/paper/*`
   - `src/main/java/com/aubb/server/modules/assignment/api/*`
   - `src/test/java/com/aubb/server/integration/StructuredAssignmentIntegrationTests.java`
4. 通知异步 fan-out + SSE
   - `src/main/java/com/aubb/server/modules/notification/**`
   - `src/test/java/com/aubb/server/integration/NotificationCenterIntegrationTests.java`
   - `src/test/java/com/aubb/server/OpenApiContractIntegrationTests.java`

### 批次 2：查询与可复现性

1. gradebook page 改数据库分页
2. gradebook report 改数据库聚合
3. judge reproducibility metadata / artifact bundle / archive manifest
4. MinIO / attachment preflight 与部署前检查

### 批次 3：交付与观测资产

1. staging / UAT / backup / rollback / drill runbook
2. tracing / alert rules / Grafana / Loki assets
3. static OpenAPI export script / workflow asset
4. stable API 文档同步

## 风险

- gradebook 与 judge 是最容易引起回归的两块。
- 通知异步需要测试从“同步断言”改为“等待最终一致”。
- 新增运维资产时不能让现有最小 deploy 失效。

## 验证路径

### 定向测试

- `bash ./mvnw -Dtest=GovernanceAuthorizationServiceTests,StructuredAssignmentIntegrationTests,NotificationCenterIntegrationTests test`
- `bash ./mvnw -Dtest=JudgeQueueHealthIndicatorTests,JudgeMetricsRecorderTests,JudgeDependencyHealthIntegrationTests test`
- `bash ./mvnw -Dtest=GradebookIntegrationTests,GradingIntegrationTests test`
- `bash ./mvnw -Dtest=OpenApiContractIntegrationTests,DeliveryPipelineAssetsTests,HarnessHealthSmokeTests test`

### 阶段验证

- `bash ./mvnw spotless:apply`
- `bash ./mvnw verify`

## 决策记录

- 本计划允许按批次分段提交，但每个批次都必须保持测试可回归。
- 本计划优先保持现有稳定 API 兼容；新增 SSE 和 phase2 组卷接口走追加式演进。

## 实际完成情况

1. judge worker / retry / DLQ / queue governance
   - 新增独立 `judge-worker` 运行角色、主队列 / DLQ / 重试配置、prefetch / 最大尝试次数治理与运维脚本
2. gradebook / report DB 分页与聚合
   - 教师成绩页与报表改为数据库分页 / 聚合，不再依赖应用层全量快照
3. governance authorization DB 祖先判断
   - 组织范围授权改为受限祖先链 + 前沿节点遍历，去掉全量组织树内存计算
4. MinIO / attachment preflight
   - 补强对象存储、go-judge、RabbitMQ、OTLP 等运行时依赖校验与部署前检查
5. staging / UAT / backup / rollback / drill
   - 新增 staging/UAT 环境模板、发布回滚脚本、发布演练流程与部署文档
6. tracing / alerting / dashboard / logs
   - 落地 tracing 开关、Prometheus 告警规则、Grafana dashboard、Loki / Promtail 示例配置
7. question bank search / phase2 paper API
   - 题库检索改数据库分页，新增第二阶段整卷替换接口
8. reproducibility / artifact bundle / archive
   - 评测可复现性补充环境与产物归档策略文档、静态 OpenAPI / 评测产物导出脚本
9. async notification fan-out / SSE
   - 通知 fan-out 改为事务后异步派发，并提供 `/api/v1/me/notifications/stream` SSE 推送层
10. stable API / static OpenAPI
   - 收敛 stable API 文档漂移，明确运行时 `/v3/api-docs` 为事实源，静态 OpenAPI 为发布产物

## 最终验证结果

### 定向验证

- `bash ./mvnw -Dtest=JudgeMetricsRecorderTests,GovernanceAuthorizationServiceTests,StructuredAssignmentIntegrationTests,NotificationCenterIntegrationTests,OpenApiContractIntegrationTests,DeliveryPipelineAssetsTests,GradebookApplicationServiceTests,GradebookIntegrationTests test`
  - 通过，`32` 个测试全部通过
- `bash ./mvnw spotless:apply -Dtest=NotificationDispatchServiceTests,AssignmentIntegrationTests,GradingIntegrationTests,LabReportIntegrationTests,StructuredProgrammingJudgeIntegrationTests,JudgeIntegrationTests test`
  - 通过，`32` 个测试全部通过

### 全量验证

- `bash ./mvnw verify`
  - 通过，`Tests run: 157, Failures: 0, Errors: 0, Skipped: 0`
  - 成功产出 `target/server-0.0.1-SNAPSHOT.jar`
  - 总耗时约 `02:13`
