# 质量评分

| 维度 | 评分 | 依据 | 下一步 |
| --- | --- | --- | --- |
| 架构清晰度 | 9/10 | 已将平台治理、课程第一切片以及 assignment / submission / judge 当前切片收敛为模块优先的模块化单体结构，课程角色继续与平台治理身份分离建模。 | 随实验、成绩继续新增模块时，保持 `course` 作为稳定上游边界。 |
| 可靠性基线 | 9/10 | 公开健康检查、Flyway 迁移、Testcontainers 和真实 Bearer 集成测试已经覆盖平台治理、课程第一切片以及 assignment / submission / judge 当前切片；judge 已具备 go-judge 异步执行与结果回写回归。 | 后续补充更大范围的跨模块验证，以及评测失败恢复、消息队列 worker 和产物留存链路。 |
| 安全基线 | 9/10 | JWT、账号状态、治理作用域、课程教师/助教边界和课程成员导入均已具备服务端校验与测试证据。 | 后续补充刷新令牌、课程级细粒度 staff scope 和更细的审计视图。 |
| 产品定义 | 9/10 | 平台治理、课程系统以及 assignment / submission / judge 当前切片规格已经形成稳定口径，课程成员来源、作业可见性、正式提交与 go-judge 评测规则清晰。 | 继续补充实验、资源正文和成绩规格。 |
| 文档适配度 | 9/10 | 架构、ADR、产品规格、数据库和目录结构说明已经同步到 go-judge 执行切片，并清理了弱价值入口。 | 随实验/成绩模块继续补齐跨模块文档。 |

## 推荐阅读顺序

- 先看 [../README.md](../README.md)
- 再看 [../ARCHITECTURE.md](../ARCHITECTURE.md)
- 再看 [repository-structure.md](repository-structure.md)
- 然后阅读 [product-specs/index.md](product-specs/index.md)
