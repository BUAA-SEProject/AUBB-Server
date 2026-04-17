# AUBB-Server 状态台账

> 基线日期：2026-04-17  
> 判定口径：以当前代码、测试、部署资产和 `docs/exec-plans/completed/` 完成记录为准。  
> 说明：本台账中的“已完成”是指**当前 V1 计划口径已完成**，不等于未来没有增强空间。

## 状态定义

- `未开始`：当前计划范围内尚未启动
- `进行中`：已经有实现或计划，但仍缺核心闭环
- `部分完成`：主路径已落地，但按当前计划口径仍有明确缺口
- `已完成`：按当前计划口径已闭环，剩余仅是后续增强项

## 优先级事项

| 项目项 | 状态 | 当前证据文件 | 剩余缺口 | 是否阻塞上线 | 下一动作 |
| --- | --- | --- | --- | --- | --- |
| 1. judge 死锁与终态超时修复 | 已完成 | [完成记录](completed/2026-04-17-judge-deadlock-and-terminal-timeout-fix.md)、[StructuredProgrammingJudgeIntegrationTests](../../src/test/java/com/aubb/server/integration/StructuredProgrammingJudgeIntegrationTests.java) | 后续可继续增强失败重试 / 死信治理 | 否 | 仅在后续评测 worker 独立化时再扩展 |
| 2. 去掉 JWT 默认密钥 | 已完成 | [完成记录](completed/2026-04-17-jwt-secret-governance-baseline.md)、[JwtSecurityProperties](../../src/main/java/com/aubb/server/config/JwtSecurityProperties.java) | 后续可做更完整密钥轮换流程 | 否 | 后续如接 KMS 再补密钥轮换 runbook |
| 3. refresh token / revoke / 强制失效 | 已完成 | [完成记录](completed/2026-04-17-refresh-revoke-session-invalidation.md)、[AuthApiIntegrationTests](../../src/test/java/com/aubb/server/integration/AuthApiIntegrationTests.java) | 当前未提供多设备会话自助管理 | 否 | 作为后续 IAM 增强项规划 |
| 4. bootstrap 初始化闭环 | 已完成 | [完成记录](completed/2026-04-17-bootstrap-initialization-closure.md)、[BootstrapInitializationIntegrationTests](../../src/test/java/com/aubb/server/integration/BootstrapInitializationIntegrationTests.java) | 当前只覆盖单学校单根节点场景 | 否 | 若未来支持多租户 / 多学校再重规划 |
| 5. Dockerfile / CI / deploy 基线 | 已完成 | [完成记录](completed/2026-04-17-delivery-pipeline-baseline.md)、[ci.yml](../../.github/workflows/ci.yml)、[deploy.yml](../../.github/workflows/deploy.yml) | 当前仍是最小 SSH + Compose 部署，不含蓝绿 / 自动回滚 | 否 | 后续按真实运维规模增强 |
| 6. judge 详细产物对象化存储 | 已完成 | [phase1](completed/2026-04-17-judge-artifact-object-storage-phase1.md)、[phase2](completed/2026-04-17-judge-artifact-object-storage-phase2.md) | 编译产物 bundle、镜像 digest、对象版本化仍属增强项 | 否 | 后续若做强审计 / 完全可复现再扩展 |
| 7. lab / report MVP | 已完成 | [完成记录](completed/2026-04-17-lab-report-mvp.md)、[LabReportIntegrationTests](../../src/test/java/com/aubb/server/integration/LabReportIntegrationTests.java) | 未引入历史版本、批量评阅和更复杂流转 | 否 | 后续按教学反馈迭代 |
| 8. 通知 / 消息中心 MVP | 已完成 | [完成记录](completed/2026-04-17-notification-center-mvp.md)、[NotificationCenterIntegrationTests](../../src/test/java/com/aubb/server/integration/NotificationCenterIntegrationTests.java) | 当前只做轮询式站内通知，无 WebSocket / 多渠道 | 否 | 后续按实时性需求扩展 |
| 9. 热点列表 DB 分页权限过滤 | 已完成 | [完成记录](completed/2026-04-17-db-paginated-permission-filtering.md)、[PlatformGovernanceApiIntegrationTests](../../src/test/java/com/aubb/server/integration/PlatformGovernanceApiIntegrationTests.java)、[AssignmentIntegrationTests](../../src/test/java/com/aubb/server/integration/AssignmentIntegrationTests.java) | 仅收口 `listUsers`、`listMyAssignments` 两个热点，其他列表未必都已优化 | 否 | 后续按性能热点继续治理 |
| 10. 文档漂移与 OpenAPI / 稳定接口清单 | 已完成 | [完成记录](completed/2026-04-17-doc-drift-openapi-stable-api.md)、[docs/stable-api.md](../stable-api.md)、[OpenApiContractIntegrationTests](../../src/test/java/com/aubb/server/OpenApiContractIntegrationTests.java) | 当前仍以运行时 OpenAPI 为事实入口，没有额外静态导出流水线 | 否 | 后续如有外部发布需求再补静态产物 |

## 里程碑状态

| 项目项 | 状态 | 当前证据文件 | 剩余缺口 | 是否阻塞上线 | 下一动作 |
| --- | --- | --- | --- | --- | --- |
| M0 核心链路稳定性与安全基线 | 已完成 | [最终统一验收](completed/2026-04-17-final-verification.md)、[JWT 密钥治理](completed/2026-04-17-jwt-secret-governance-baseline.md)、[refresh/revoke](completed/2026-04-17-refresh-revoke-session-invalidation.md)、[bootstrap](completed/2026-04-17-bootstrap-initialization-closure.md) | 后续仍可增强密码管理 / KMS / 更复杂运维策略 | 否 | 作为 V1.1 安全增强处理 |
| M1 交付与部署基线 | 已完成 | [交付基线](completed/2026-04-17-delivery-pipeline-baseline.md)、[最终统一验收](completed/2026-04-17-final-verification.md) | 当前部署仍偏最小，不含复杂发布策略 | 否 | 结合真实环境再补发布策略 |
| M2 Judge / Grading 主链路硬化 | 已完成 | [judge phase2](completed/2026-04-17-judge-artifact-object-storage-phase2.md)、[成绩发布快照](completed/2026-04-17-grade-publish-snapshots-v1.md) | 回滚 / 冻结、编译产物归档与更强可复现能力仍属增强项 | 否 | 后续按评测审计需求继续细化 |
| M3 实验 / 实验报告 MVP | 已完成 | [完成记录](completed/2026-04-17-lab-report-mvp.md) | 历史版本、Rubric、批量评阅尚未纳入 | 否 | 进入教学侧反馈迭代阶段 |
| M4 通知中心与异步体验 | 已完成 | [完成记录](completed/2026-04-17-notification-center-mvp.md) | WebSocket / 多渠道通知不在当前 V1 范围 | 否 | 后续按前端体验和运维能力扩展 |
| M5 性能、可观测与治理收尾 | 已完成 | [健康检查](completed/2026-04-17-health-check-closure.md)、[业务指标](completed/2026-04-17-business-metrics-baseline.md)、[Redis 去留](completed/2026-04-17-redis-decision-closure.md) | 告警规则、Grafana 面板、外部监控接入仍属环境级后续动作 | 否 | 在上线准备阶段与运维环境一起落地 |

## 交付判断

| 判断项 | 结论 | 依据 |
| --- | --- | --- |
| 可继续联调 | 是 | 统一验收、OpenAPI、compose、Dockerfile、deploy workflow 已全部落地 |
| 可进入内测 | 是 | 主教学链路、权限边界、评测、批改、实验、通知、状态台账已闭环 |
| 可进入上线准备 | 是 | 代码、测试、交付、健康检查、指标、文档和 Redis 去留已收口；仍建议补环境级演练 |
| 可直接无准备上线 | 否 | 仍建议先完成 staging/UAT、备份恢复演练、外部告警接入和发布演练 |

## 仍需跟踪但不阻塞本轮交付的事项

1. judge 更强可复现能力：编译产物 bundle、镜像 digest、对象版本化
2. grade publish 快照后续增强：冻结、完整回滚、读模型切换
3. 通知中心实时化：WebSocket / 多渠道通知
4. 指标后续增强：Prometheus 采集规则、告警、Grafana 面板
5. 更多列表查询的数据库分页权限过滤
