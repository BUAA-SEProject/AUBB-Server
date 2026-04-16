# 2026-04-16 人工批改与成绩发布第一阶段

## 目标

在结构化作业、分题提交和客观题自动评分底座之上，补齐非客观题人工批改、assignment 级成绩发布和学生可见性控制的第一阶段闭环。

## 范围

### 代码范围

- `modules.grading`
- `modules.submission`
- `modules.course`
- `modules.audit`
- Flyway migration

### 文档范围

- `README.md`
- `ARCHITECTURE.md`
- `docs/product-sense.md`
- `docs/product-specs/{assignment,submission,grading,judge}-system.md`
- `docs/generated/db-schema.md`
- `todo.md`

## 数据模型决策

1. `grading` 作为独立逻辑模块存在，但第一阶段不单独建成绩表。
2. assignment 级发布状态挂在 `assignments.grade_published_at / grade_published_by_user_id`。
3. 分题人工评分、反馈、批改人和批改时间继续挂在 `submission_answers`。
4. 学生在成绩发布前仍可看到客观题即时分，但看不到人工评分与反馈。
5. 成绩发布要求当前 assignment 下已有提交不存在待人工批改或待编程评测答案。

## 风险与取舍

- 当前助教权限仍依赖既有课程成员模型，因此只对“班级作业 + 该班助教”开放批改。
- 课程公共作业的细粒度助教分工尚未单独建模。
- 结构化编程题仍未接入题目级 judge，本轮只保留人工评分兜底。

## 验证路径

- 新增 `GradingIntegrationTests`
- `bash ./mvnw -Dtest=GradingIntegrationTests test`
- `bash ./mvnw -Dtest=AssignmentIntegrationTests,SubmissionIntegrationTests,JudgeIntegrationTests,StructuredAssignmentIntegrationTests,GradingIntegrationTests test`
- `bash ./mvnw clean verify`

## 结果

- 已支持教师 / 助教人工批改简答题、文件题和编程题
- 已支持 assignment 级成绩发布
- 已支持学生侧人工评分与反馈可见性控制
- 已补齐审计动作与跨模块回归测试
