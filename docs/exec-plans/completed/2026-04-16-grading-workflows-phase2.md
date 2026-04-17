# 2026-04-16 成绩系统第二阶段：排名、申诉复核与批量调整收口

## 目标

把 `grading` 从“单题人工批改 + 成绩册查看”继续推进到更接近真实教学管理使用的状态，补齐以下能力：

- 成绩册排名与通过率第一阶段
- 成绩申诉与复核第一阶段
- assignment 级批量成绩调整与 CSV 导入导出第一阶段

## 已完成

- 教师侧成绩册和单学生视图新增 `offeringRank / teachingClassRank`
- 统计报告新增 `passedStudentCount / passRate`
- 新增 `grade_appeals` 迁移、学生侧申诉创建 / 列表，以及教师 / 责任助教 assignment 维度的申诉列表 / 复核
- 新增 assignment 级批量成绩 CSV 模板导出和 CSV 导入接口，保留既有 JSON `batch-adjust`
- 申诉接受与批量导入都继续复用单题人工批改语义写回 `submission_answers`
- `README`、产品定位、grading 规格、数据库结构、`todo.md` 和工作记忆已同步到当前状态

## 验证

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -Dtest=GradingIntegrationTests test`
- `bash ./mvnw clean verify`

结果：

- `BUILD SUCCESS`
- 全量 `92` 个测试通过

## 后续缺口

- 更复杂的总评策略、权重版本化和分组权重
- 更丰富的成绩报表、学习分析和趋势统计
- 更完整的申诉仲裁、回滚、附件材料与 SLA 管理
- 更细粒度的成绩生命周期控制，例如延迟发布与冻结
