# 2026-04-16 学生侧成绩册 CSV 导出第一阶段

## 目标

在不改变现有成绩发布与可见性边界的前提下，为学生侧个人成绩册补齐 CSV 导出能力，让“我的成绩册”从只可浏览推进到可下载、可留存和可复核。

## 范围

- 新增学生侧个人成绩册 CSV 导出接口
- 复用既有“我的成绩册”聚合结果，保持导出与页面语义一致
- 保持未发布人工分、人工反馈和人工部分总分的隐藏边界
- 补齐集成测试与相关文档

## 完成内容

- 新增 `GET /api/v1/me/course-offerings/{offeringId}/gradebook/export`
- 在 `GradebookApplicationService` 中补齐学生侧 CSV 渲染
- 固定导出内容为：
  - 个人成绩汇总
  - 按作业明细
- 保证未发布人工分在导出中继续隐藏
- 扩展 `GradebookIntegrationTests` 覆盖成功导出、未发布人工分隐藏和教师误用边界
- 同步 grading 规格、README、todo 与工作记忆

## 验证

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -Dtest=GradebookIntegrationTests,GradingIntegrationTests test`
- `bash ./mvnw clean verify`

## 结果

- 学生侧现在可以按开课实例导出个人成绩册 CSV
- 导出与页面使用同一聚合结果，不会额外泄露未发布人工分
- 下一步成绩域优先级切换为更复杂总评策略、更完整统计与更丰富报表
