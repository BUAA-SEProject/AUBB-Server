# 发现与决策

## 需求

- 用户要求基于 `D:\Code\SEproject\docs\02-process-docs\software-requirements-specification.md` 完成 `docs/plan.md`。
- 计划需要覆盖“开发全部流程”，不能只列功能模块，应包括阶段、依赖、测试、验收、上线准备和后续增强。
- 仓库规则要求：非简单改动前先参考 `ARCHITECTURE.md`、`docs/plans.md`、`docs/product-specs/index.md`。
- 当前仓库是 Spring Boot 4 + Java 25 的后端骨架，尚未落地真实业务模块。

## 调研发现

- SRS v4.2 明确了 V1 范围、角色、核心链路、FR/NFR、数据实体、接口边界、验收基线和 V1.1 / 后续版本候选。
- V1 基线应覆盖全部 MUST 级需求，主链路从“平台初始化”贯通到“成绩发布与查看”。
- 当前后端仓库适合优先承接以下主线：平台配置/IAM、课程、任务、提交、评测编排、批改成绩、通知、审计、可观测性。
- 在线 IDE、浏览器交互和部分运营界面属于系统级能力，但在当前仓库内应主要体现为后端接口、工作区会话、运行会话和状态流转支持。

## 技术决策

| Decision | Rationale |
|----------|-----------|
| 计划按“阶段交付 + 横切保障 + 标准切片流程”组织 | 这样既能指导开发顺序，也能约束质量门槛 |
| 以 SRS 的 MUST/SHOULD/COULD 作为 V1/V1.1/VNext 分层依据 | 与 SRS 原始优先级一致，避免二次歪曲范围 |
| 将数据设计、API、测试、观测、安全作为每个阶段的必选输出 | 防止出现功能开发完成后再补技术债的模式 |

## 遇到的问题

| Issue | Resolution |
|-------|------------|
| SRS 内容很长，终端输出被截断 | 通过按章节与编号定向检索补全关键功能段落 |

## 资源

- SRS：`D:\Code\SEproject\docs\02-process-docs\software-requirements-specification.md`
- 架构基线：`D:\Code\SEproject\AUBB-Server\ARCHITECTURE.md`
- 文档计划入口：`D:\Code\SEproject\AUBB-Server\docs\plans.md`
- 产品规格入口：`D:\Code\SEproject\AUBB-Server\docs\product-specs\index.md`

## 可视/检索结论

- SRS 的功能分组完整覆盖 `FR-CFG / IAM / CRS / TSK / SUB / JDG / REV / NTF / OPS`。
- 非功能分组完整覆盖易用性、性能、可靠性、安全隐私、兼容性、可扩展性和可观测性。
- SRS 还给出了三项未决事项：容量指标、统一认证纳入范围、成绩保留年限，这些应进入计划前置门槛。
