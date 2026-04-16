# 质量评分

| 维度 | 评分 | 依据 | 下一步 |
| --- | --- | --- | --- |
| 架构清晰度 | 9/10 | 已将平台治理、课程第一切片以及 assignment / submission / grading / judge 当前切片收敛为模块优先的模块化单体结构，课程角色继续与平台治理身份分离建模，编程题工作区状态也保持在 `submission` 而非散落到 `judge`。 | 随实验、成绩继续新增模块时，保持 `course` 作为稳定上游边界。 |
| 可靠性基线 | 9/10 | 公开健康检查、Flyway 迁移、Testcontainers 和真实 Bearer 集成测试已经覆盖平台治理、课程第一切片以及 assignment / submission / grading / judge 当前切片；judge 已具备 go-judge 真实执行、RabbitMQ 队列第一阶段、详细评测报告与结果回写回归，在线 IDE 后端契约也有模板工作区、历史恢复和自定义试运行专项覆盖。 | 后续补充更大范围的跨模块验证，以及评测失败恢复、独立 worker、重试 / 死信治理和产物留存链路。 |
| 安全基线 | 9/10 | JWT、账号状态、治理作用域、课程教师/助教边界和课程成员导入均已具备服务端校验与测试证据。 | 后续补充刷新令牌、课程级细粒度 staff scope 和更细的审计视图。 |
| 产品定义 | 9/10 | 平台治理、课程系统以及 assignment / submission / grading / judge 当前切片规格已经形成稳定口径，课程成员来源、作业可见性、模板工作区、历史修订、自定义试运行、go-judge 评测规则、详细评测报告、成绩发布和成绩册边界清晰。 | 继续补充实验、资源正文和导出/统计规格。 |
| 文档适配度 | 9/10 | 架构、ADR、产品规格、数据库、目录结构说明和文档入口已经同步到当前作业主链路状态，并清理了重复入口和过期 active 计划；当前接手入口已经统一到 README、仓库结构说明、产品规格索引和 active 计划。 | 随实验/运行时继续补齐跨模块文档，并保持 active/completed 计划及时归档。 |

## 推荐阅读顺序

- 先看 [../README.md](../README.md)
- 再看 [../ARCHITECTURE.md](../ARCHITECTURE.md)
- 再看 [repository-structure.md](repository-structure.md)
- 然后阅读 [product-specs/index.md](product-specs/index.md)
