# 产品定位

## 仓库目的

当前的 AUBB-Server 已不再只是后端平台骨架。它现在的首要目标，是围绕 AUBB 教学主链路逐步落地真实业务能力。当前已完成平台治理基线、课程系统第一切片，并进入 assignment 与 submission 第一切片：即时生效的平台配置、学校/学院/课程/班级组织、用户与多身份治理、账号状态、JWT 登录、基础审计，以及课程模板、开课实例、教学班、课程成员、班级功能开关、作业主数据和正式提交受理能力。

## 当前已经具备的内容

- 服务可以在 Java 25 上启动
- `/actuator/health` 可用于 smoke 验证
- 平台治理、课程第一切片以及 assignment / submission 第一切片的核心 API、数据库迁移和自动化测试已经就位
- 安全默认配置、JWT 鉴权、作用域授权与课程成员权限已落地
- 数据库和消息组件依赖已经完成第一批真实持久化接入，并为后续实验、作业、评测保留扩展位
- 仓库文档已经能够说明当前实现、开发入口和后续扩展方式

## 下一步应该做什么

下一步面向产品的工作，不再是继续维持骨架，而是沿着课程主链路继续补齐 judge、实验和成绩等后续模块，并保持当前治理、课程、assignment 与 submission 边界稳定。当前可直接参考：

- [product-specs/platform-governance-and-iam.md](product-specs/platform-governance-and-iam.md)
- [product-specs/course-system.md](product-specs/course-system.md)
- [product-specs/assignment-system.md](product-specs/assignment-system.md)
- [product-specs/submission-system.md](product-specs/submission-system.md)
- [repository-structure.md](repository-structure.md)
