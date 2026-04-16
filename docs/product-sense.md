# 产品定位

## 仓库目的

当前的 AUBB-Server 已不再只是后端平台骨架。它现在的首要目标，是围绕 AUBB 教学主链路逐步落地真实业务能力。当前已完成平台治理基线、课程系统第一切片，并继续推进 assignment、submission、grading 与 judge：即时生效的平台配置、学校/学院/课程/班级组织、用户与多身份治理、账号状态、JWT 登录、基础审计，以及课程模板、开课实例、教学班、课程成员、班级功能开关、作业主数据、开课实例内题库、结构化试卷快照、正式提交受理和附件提交通道、分题答案与客观题自动评分摘要、教师 / 助教人工批改、assignment 级成绩发布、教师侧成绩册第一阶段、assignment 级自动评测配置、go-judge 异步执行与结果回写能力，以及结构化编程题题目级评测、目录树工作区快照保存和样例试运行闭环。

## 当前已经具备的内容

- 服务可以在 Java 25 上启动
- `/actuator/health` 可用于 smoke 验证
- 平台治理、课程第一切片以及 assignment / submission / grading / judge 当前切片的核心 API、数据库迁移和自动化测试已经就位
- 安全默认配置、JWT 鉴权、作用域授权与课程成员权限已落地
- 数据库和消息组件依赖已经完成第一批真实持久化接入，并为后续实验、作业、评测保留扩展位
- 仓库文档已经能够说明当前实现、开发入口和后续扩展方式
- 教师侧成绩册当前已支持按开课实例、教学班和单学生查看结构化作业成绩矩阵
- 学生侧成绩册当前已支持按开课实例查看自己的结构化作业成绩总览

## 下一步应该做什么

下一步面向产品的工作，不再是继续维持骨架，而是沿着课程主链路继续补齐这些缺口：

1. 在线 IDE 第二阶段：目录树交互、多文件编辑、文件操作和入口文件体验。
2. 多语言运行时稳定化：继续加固 `PYTHON3 / JAVA17 / CPP17` 的复杂工程布局、失败态和日志一致性。
3. 题库与组卷第二阶段：分类、试卷编辑体验和快照边界。
4. 成绩与反馈第二阶段：成绩导出、更完整统计和后续总评扩展位。
5. 判题日志与可复现性第二阶段：更完整执行元数据、日志与产物对象。

当前可直接参考：

- [product-specs/platform-governance-and-iam.md](product-specs/platform-governance-and-iam.md)
- [product-specs/course-system.md](product-specs/course-system.md)
- [product-specs/assignment-system.md](product-specs/assignment-system.md)
- [product-specs/submission-system.md](product-specs/submission-system.md)
- [product-specs/grading-system.md](product-specs/grading-system.md)
- [product-specs/judge-system.md](product-specs/judge-system.md)
- [repository-structure.md](repository-structure.md)
