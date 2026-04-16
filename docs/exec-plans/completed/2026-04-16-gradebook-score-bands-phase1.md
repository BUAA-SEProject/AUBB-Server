# 2026-04-16 统计报告五档成绩分布第一阶段

## 目标

在不新增统计表、不改变既有权限边界和 API 路径的前提下，为教师侧成绩册统计报告补齐可直接用于教学诊断的五档成绩分布。

## 范围

- 在现有 report 返回结构中补齐总体成绩分布
- 在按作业统计中补齐已提交学生的成绩分布
- 在按班级统计中补齐班级总评成绩分布
- 保持既有导出接口、权限边界和分页接口不变

## 完成内容

- 新增统一的 `EXCELLENT / GOOD / MEDIUM / PASS / FAIL` 五档分布结构
- 总体与班级分布按当前总评得分率统计：
  - 优先使用加权得分率
  - 若当前无加权权重，则回退到总分得分率
- 作业分布只统计已提交学生
- offering / class report 的现有 JSON 结构保持兼容追加式演进
- 扩展集成测试，覆盖总体、作业和班级分布断言
- 同步 grading 规格、README、todo 与工作记忆

## 验证

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -Dtest=GradebookIntegrationTests,GradingIntegrationTests test`
- `bash ./mvnw clean verify`

## 结果

- 教师 / 管理员 / 班级责任 TA 现在可直接从 report 中看到成绩分布
- 现有成绩册统计从“只有均值和完成率”推进到“可快速诊断分布”
- 下一步统计方向收敛为更复杂总评策略、更深入学习分析和更丰富报表
