# 2026-04-16 assignment 权重与加权总评第一阶段

## 目标

在既有“最新正式提交”成绩册聚合之上，为 assignment 补齐稳定的成绩权重模型，让教师 / 学生成绩册、课程 / 班级 CSV 导出和统计报告都能直接表达基础总评语义，而不引入新的独立总评表或破坏现有可见性边界。

## 范围

- 为 `assignments` 新增 assignment 级 `grade_weight`
- 允许教师在创建 / 编辑草稿作业时设置权重
- 为教师 / 学生成绩册补齐加权总分、权重合计和加权得分率
- 为课程 / 班级 CSV 导出补齐作业权重和每格加权得分
- 为统计报告补齐加权总分与作业权重第一阶段

## 实现摘要

- 迁移 `V20__assignment_grade_weights_phase1.sql` 为 `assignments` 增加 `grade_weight`
- `AssignmentApplicationService` 新增 `gradeWeight` 规范化与校验，默认 `100`，当前允许范围 `1 ~ 1000`
- 教师侧创建 / 更新草稿作业接口新增 `gradeWeight`
- `GradebookApplicationService` 新增：
  - 行级 `totalWeightedScore / totalWeight / weightedScoreRate`
  - 单格 `weightedScore`
  - 统计报告级加权总分与作业权重
- `AssignmentIntegrationTests`、`StructuredAssignmentIntegrationTests`、`GradebookIntegrationTests` 新增或扩展权重断言

## 验证

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -Dtest=AssignmentIntegrationTests,StructuredAssignmentIntegrationTests,GradebookIntegrationTests test`
- `bash ./mvnw clean verify`

## 结果

- assignment 已支持 assignment 级权重
- 教师 / 学生成绩册、CSV 导出和统计报告已补齐加权总分第一阶段
- 后续“更复杂的总评策略”可以继续在 `grading` 模块内追加，而不需要回退本次模型
