# 批改与成绩发布系统

## 目标

把结构化作业从“学生可提交、教师可查看”推进到“教师 / 助教可批改、学生按发布状态查看成绩与反馈”，并进一步补齐“教师侧成绩册第一阶段”和“学生侧成绩册第一阶段”。当前 grading 模块不接管提交原文，也不执行自动评测；它负责人工评分、反馈写回、assignment 级成绩发布，以及基于现有提交事实的只读成绩册聚合。

## 覆盖范围

### 功能范围

- 教师对结构化作业中的非客观题执行人工批改
- 助教对自己负责教学班内的班级作业执行人工批改
- 人工批改支持：
  - `SHORT_ANSWER`
  - `FILE_UPLOAD`
  - `PROGRAMMING`
- 人工批改写回分题得分、评分反馈、批改人和批改时间
- 教师在 assignment 维度发布成绩
- 教师 / 管理员按开课实例查看成绩册
- 教师 / 管理员 / 班级助教按教学班查看成绩册
- 教师 / 管理员按单个学生查看成绩册
- 学生按开课实例查看自己的成绩册
- 学生在成绩发布前只能看到客观题即时分与非客观题批改状态
- 学生成绩发布后可看到人工评分、总分和反馈
- 批改与成绩发布写入审计日志

### 不在范围

- 多作业加权成绩册
- 班级 / 课程成绩导出与报表
- 学习分析、完成率和班级对比
- 批量批改工作台
- 申诉、复评和成绩回滚
- 结构化编程题题目级自动评测执行本身

## 核心业务规则

1. 人工批改必须绑定到某次正式提交中的某一道题。
2. 当前只有非客观题允许人工批改；单选 / 多选题仍由 submission 模块自动判分。
3. 批改分数必须在 `0 ~ question.score` 范围内。
4. 教师可批改自己课程内的作业提交。
5. 助教当前只允许批改自己负责教学班内、且 assignment 本身绑定该教学班的作业提交。
6. assignment 级成绩发布前，学生仍可查看自己的提交与客观题即时分，但看不到人工评分、人工反馈和人工部分总分。
7. assignment 级成绩发布要求当前 assignment 下已有提交的所有分题答案都不处于 `PENDING_MANUAL / PENDING_PROGRAMMING_JUDGE`。
8. 成绩发布是 assignment 维度的全局开关；一旦发布，后续该 assignment 下的新批改结果也按已发布状态对学生可见。
9. 教师侧成绩册第一阶段默认按“每个学生、每个作业最新一次正式提交”聚合，不提供 `latestOnly=false` 的历史矩阵视图。
10. 教学班成绩册会同时显示课程公共作业和该教学班专属作业；其他班级专属作业不会混入该视图。
11. offering 级和单学生成绩册当前只对教师 / 管理员开放；具备班级责任的 TA 只可查看自己负责教学班的班级成绩册。
12. 当前成绩册只覆盖结构化作业；legacy 文本作业暂不进入第一阶段成绩册。

## 核心数据模型

- `assignments`
  - `grade_published_at`：assignment 级成绩发布时间
  - `grade_published_by_user_id`：成绩发布人
- `submission_answers`
  - `manual_score`：人工评分
  - `final_score`：当前分题最终得分
  - `grading_status`：`AUTO_GRADED / MANUALLY_GRADED / PROGRAMMING_JUDGED / PENDING_MANUAL / PENDING_PROGRAMMING_JUDGE`
  - `feedback_text`：分题反馈
  - `graded_by_user_id / graded_at`：最近批改人和时间
- `submissions`
  - 提供正式提交版本、`attempt_no` 和 `submitted_at`
- `course_members`
  - 提供课程 / 班级名册与 TA 作用域
- `audit_logs`
  - `SUBMISSION_ANSWER_GRADED`
  - `ASSIGNMENT_GRADES_PUBLISHED`

详细字段以 [../generated/db-schema.md](../generated/db-schema.md) 为准。

## 角色边界

### 教师

- 查看课程内提交详情和分题答案
- 批改非客观题
- 发布 assignment 成绩
- 查看开课实例、教学班和单学生成绩册

### 助教

- 当前可查看并批改自己负责教学班内、且 assignment 绑定该教学班的提交
- 当前不具备 assignment 级成绩发布权限
- 当前只可查看自己负责教学班的班级成绩册

### 学生

- 查看自己的提交详情和评分摘要
- 按开课实例查看自己的成绩册
- 成绩发布前只能看到客观题即时分与批改状态
- 成绩发布后可查看人工评分、总分与反馈

## API 边界

### 教师 / 助教侧

- `POST /api/v1/teacher/submissions/{submissionId}/answers/{answerId}/grade`

### 教师侧

- `POST /api/v1/teacher/assignments/{assignmentId}/grades/publish`
- `GET /api/v1/teacher/course-offerings/{offeringId}/gradebook`
- `GET /api/v1/teacher/teaching-classes/{teachingClassId}/gradebook`
- `GET /api/v1/teacher/course-offerings/{offeringId}/students/{userId}/gradebook`

### 学生侧

- `GET /api/v1/me/course-offerings/{offeringId}/gradebook`

## 当前实现边界

- 当前发布粒度是 assignment 级，而不是按提交、按学生或按班级的细粒度发布。
- 当前只支持单题人工批改，不支持批量批改工作流。
- 当前成绩册不新建成绩表，直接复用 `assignments / submissions / submission_answers / course_members` 读模型。
- 当前成绩册默认只按最新正式提交聚合，不提供历史版本并排比较。
- 当前成绩册的 offering / class / student / me 四类视图都只覆盖结构化作业。
- 学生侧成绩册当前只提供“我在某个开课实例下的成绩总览”，不提供班级横向对比或历史版本矩阵。
- 学生侧成绩册在未发布成绩时仍会返回作业和提交状态，但非客观题人工分、人工反馈和人工部分总分继续隐藏。
- 当前没有总评语、评分 rubric、打回重提和复评流程。
- 助教权限仍基于已有课程成员模型；对课程公共作业的细粒度分工未单独建模。
- 编程题虽然允许人工批改，但题目级自动评测已由 judge 模块先行写回；grading 当前不直接执行自动评测。

## 验收标准

- 教师或具备班级责任的助教可以对非客观题写入分数与反馈。
- 未发布成绩前，学生无法读取人工评分与反馈。
- 发布成绩后，学生可以读取人工评分、总分与反馈。
- 存在待批改或待编程评测答案时，assignment 成绩发布会被拒绝。
- 教师 / 管理员可查看开课实例和单学生成绩册；班级责任 TA 可查看本班成绩册。
- 学生可查看自己在开课实例下的成绩册，并且未发布的人工分不会在成绩册里提前泄露。
- 成绩册默认按最新正式提交聚合，并正确处理课程公共作业与班级专属作业的适用范围。
- `mvnd verify` 或 `bash ./mvnw verify` 提供自动化测试证据。
