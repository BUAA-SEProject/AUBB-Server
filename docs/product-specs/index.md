# 产品规格

- [平台基线](platform-baseline.md)
- [平台治理与 IAM](platform-governance-and-iam.md)
- [权限系统](permission-system.md)
- [课程系统](course-system.md)
- [作业系统](assignment-system.md)
- [提交系统](submission-system.md)
- [批改与成绩发布系统](grading-system.md)
- [评测系统](judge-system.md)
- [实验与实验报告系统](lab-system.md)
- [通知中心系统](notification-center.md)

当前后端已从“平台治理与 IAM”推进到课程系统第一切片，并继续落地 assignment、submission、grading、judge、lab/report、notification 六个后续切片；当前 assignment / submission 已进入“题库 + 结构化试卷 + 分题提交”的第一阶段，题库已补齐更新 / 归档 / 标签 / 分类第一阶段，结构化作业已补齐草稿编辑第一阶段，submission 已补齐模板工作区、目录树快照、工作区修订历史和恢复接口；grading 当前保留人工批改、成绩发布、教师侧成绩册、CSV 导出、统计报告、五档成绩分布、通过率、成绩册排名、assignment 级批量成绩调整与 CSV 导入导出，以及学生侧成绩册与 CSV 导出，V48 已移除 assignment 级成绩权重、加权总评、成绩申诉和成绩发布快照批次；judge 已同时覆盖 legacy assignment 级自动评测、结构化编程题题目级评测、样例试运行、自定义标准输入试运行、`CUSTOM_SCRIPT` 第一阶段、开课实例级评测环境模板、题目级 `languageExecutionEnvironments / executionEnvironment` 运行环境快照、RabbitMQ 队列第一阶段和详细评测报告；lab/report 当前已补齐教学班级实验、实验报告、附件上传、教师评阅和 `labEnabled` 后端拦截 MVP；notification 当前已补齐站内通知、未读数、已读状态和关键教学事件入箱。后续新增规格时，继续在这里补充索引。

当前稳定接口范围与 OpenAPI / Swagger 事实入口，见 [../stable-api.md](../stable-api.md)。
