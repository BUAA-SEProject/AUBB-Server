# 2026-04-16 教师侧成绩册第一阶段

## 目标

在不新增成绩表的前提下，为 `grading` 模块补齐教师侧成绩册第一阶段：支持按开课实例、教学班和单学生查看结构化作业成绩矩阵，并复用现有 `assignments / submissions / submission_answers` 作为读模型来源。

## 范围

- 教师 / 管理员按开课实例查看成绩册
- 教师 / 管理员 / 班级助教按教学班查看成绩册
- 教师 / 管理员查看单学生成绩册
- 默认按“每个学生每个作业最新一次正式提交”聚合
- 当前只覆盖结构化作业

## 不在范围

- 学生侧成绩册
- 多作业加权总评
- 导出与报表
- 学习分析、完成率和班级对比
- 新增成绩专用事实表

## 风险

- 课程公共作业和班级专属作业同时存在时，单元格适用性必须明确，不能错误把班级作业扩散给其他班学生。
- TA 只能查看自己负责教学班的成绩册，不能通过 offering 维度越权读取全课程成绩。
- 现有 legacy 文本作业没有 `submission_answers` 评分摘要，不能混入第一阶段成绩册。

## 验证路径

- `bash ./mvnw -Dtest=GradebookIntegrationTests test`
- `bash ./mvnw -Dtest=GradingIntegrationTests,SubmissionIntegrationTests,StructuredAssignmentIntegrationTests test`
- `bash ./mvnw clean verify`

## 决策记录

- 成绩册聚合放在 `grading`，不放进 `assignment` 或 `submission`。
- offering 级成绩册默认只对教师 / 管理员开放；班级成绩册额外向具备班级责任的 TA 开放。
- 第一阶段不新增成绩表，直接复用作业、提交、分题答案和课程成员读模型。
