# 产品定位

## 仓库目的

当前的 AUBB-Server 已不再只是后端平台骨架。它现在的首要目标，是围绕 AUBB 教学主链路逐步落地真实业务能力。当前已完成平台治理基线、课程系统第一切片，并继续推进 assignment、submission、grading 与 judge：即时生效的平台配置、学校/学院/课程/班级组织、用户与多身份治理、账号状态、JWT 登录、基础审计，以及课程模板、开课实例、教学班、课程成员、班级功能开关、作业主数据、开课实例内题库、题库标签与分类、结构化试卷快照与草稿编辑、正式提交受理和附件提交通道、分题答案与客观题自动评分摘要、教师 / 助教人工批改、assignment 级成绩发布、教师与学生成绩册第一阶段、教师侧成绩册 CSV 导出与统计报告第一阶段、多作业权重与加权总评第一阶段、学生侧成绩册 CSV 导出第一阶段、assignment 级自动评测配置、go-judge 真实执行与结果回写能力、开课实例级评测环境模板、题目级运行环境快照、RabbitMQ 队列第一阶段、详细评测报告，以及结构化编程题题目级评测、模板工作区、目录树工作区快照、工作区修订历史、历史恢复和自定义标准输入试运行闭环。

## 当前已经具备的内容

- 服务可以在 Java 25 上启动
- `/actuator/health` 可用于 smoke 验证
- 平台治理、课程第一切片以及 assignment / submission / grading / judge 当前切片的核心 API、数据库迁移和自动化测试已经就位
- 安全默认配置、JWT 鉴权、作用域授权与课程成员权限已落地
- 数据库和消息组件依赖已经完成第一批真实持久化接入，并为后续实验、作业、评测保留扩展位
- 仓库文档已经能够说明当前实现、开发入口和后续扩展方式
- 教师侧成绩册当前已支持按开课实例、教学班和单学生查看结构化作业成绩矩阵
- 教师侧成绩册当前已支持按开课实例 / 教学班导出 CSV，并提供总体摘要、按作业统计、按班级对比和五档成绩分布的统计报告第一阶段
- 作业当前已支持 assignment 级权重配置，教师 / 学生成绩册、CSV 导出和统计报告都会同步返回加权总分、权重合计和加权得分率第一阶段
- 学生侧当前已支持导出自己在开课实例下的成绩册 CSV，且未发布人工分在导出里会继续按页面可见性规则隐藏
- 学生侧成绩册当前已支持按开课实例查看自己的结构化作业成绩总览
- 在线 IDE 的后端契约已经具备模板工作区、目录树快照、工作区修订历史、历史恢复和自定义标准输入试运行
- 当前最近一次全量验证结果为 `BUILD SUCCESS`，共 `87` 个测试通过

## 下一步应该做什么

下一步面向产品的工作，不再是继续维持骨架，而是沿着课程主链路继续补齐这些缺口：

1. 在线 IDE 前端阶段：目录树交互、多文件编辑、语法高亮、自动补全、格式化，以及更实时自动保存体验。
2. 多语言运行时稳定化：继续加固 `PYTHON3 / JAVA21 / CPP17 / GO122` 的复杂工程布局、失败态和日志一致性；`JAVA17` 仅作为兼容输入保留。
3. 题库与组卷第二阶段：更稳定的试卷编辑体验、题库选题与快照边界。
4. 成绩与反馈第二阶段：更复杂的总评策略、更深入学习分析和学生侧更丰富导出 / 报表扩展位。
5. 判题日志与可复现性第二阶段：更完整执行元数据、日志、产物对象和队列稳定化。

当前可直接参考：

- [product-specs/platform-governance-and-iam.md](product-specs/platform-governance-and-iam.md)
- [product-specs/course-system.md](product-specs/course-system.md)
- [product-specs/assignment-system.md](product-specs/assignment-system.md)
- [product-specs/submission-system.md](product-specs/submission-system.md)
- [product-specs/grading-system.md](product-specs/grading-system.md)
- [product-specs/judge-system.md](product-specs/judge-system.md)
- [repository-structure.md](repository-structure.md)
