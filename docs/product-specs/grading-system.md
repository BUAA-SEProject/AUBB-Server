# 批改与成绩发布系统

## 目标

把结构化作业从“学生可提交、教师可查看”推进到“教师 / 助教可批改、学生按发布状态查看成绩与反馈”，并进一步补齐“教师侧成绩册第一阶段”“教师侧课程 / 班级成绩册 CSV 导出与统计报告第一阶段”“统计报告五档成绩分布与通过率第一阶段”“多作业权重与加权总评第一阶段”“学生侧成绩册第一阶段”“学生侧成绩册 CSV 导出第一阶段”“成绩申诉与复核第一阶段”以及“assignment 级批量成绩调整与 CSV 导入导出第一阶段”。当前 grading 模块不接管提交原文，也不执行自动评测；它负责人工评分、反馈写回、assignment 级成绩发布，以及基于现有提交事实的只读成绩册聚合、导出、统计报告和申诉复核。

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
- 教师侧成绩册和单学生视图返回开课实例排名与教学班排名第一阶段结果
- 教师 / 管理员按开课实例导出 CSV 成绩册并查看统计报告
- 教师 / 管理员 / 班级助教按教学班导出 CSV 成绩册并查看统计报告
- 教师 / 管理员 / 班级助教查看五档成绩分布与通过率统计
- 学生按开课实例查看自己的成绩册
- 学生按开课实例导出自己的成绩册 CSV
- 学生在成绩发布前只能看到客观题即时分与非客观题批改状态
- 学生成绩发布后可看到人工评分、总分和反馈
- 学生可对已发布的非客观题成绩发起申诉
- 学生可按开课实例查看自己的成绩申诉列表
- 教师 / 具备班级责任的助教可按 assignment 查看申诉并执行复核
- 教师可按 assignment 执行 batch-adjust 和 CSV 导入 / 导出第一阶段
- 批改与成绩发布写入审计日志

### 不在范围

- 更复杂的总评策略与多套权重方案
- 更复杂的多维报表、学习分析和长期趋势统计
- 批量批改工作台
- 更完整的申诉仲裁、成绩回滚、附件化申诉材料与 SLA 管理
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
10. 当前成绩册按 assignment 级 `gradeWeight` 计算加权总分，默认公式为 `finalScore / assignmentMaxScore * gradeWeight`；若某作业尚无最新正式提交，则不计入学生当前 `totalWeight`。
11. 教学班成绩册会同时显示课程公共作业和该教学班专属作业；其他班级专属作业不会混入该视图。
12. offering 级和单学生成绩册当前只对教师 / 管理员开放；具备班级责任的 TA 只可查看自己负责教学班的班级成绩册。
13. 当前成绩册只覆盖结构化作业；legacy 文本作业暂不进入第一阶段成绩册。
14. 教师侧 CSV 导出必须复用与成绩册视图相同的聚合结果，不允许导出与页面语义不一致的历史版本或额外作业。
15. 教师侧统计报告当前复用成绩册最新正式提交矩阵，只提供总体摘要、按作业统计、按班级对比和五档成绩分布；班级视图不返回额外班级对比列表。
16. 班级责任 TA 当前只可导出和查看自己负责教学班的班级成绩册与统计报告，不能通过 offering 维度越权读取全课程导出或统计结果。
17. 学生侧 CSV 导出必须与“我的成绩册”返回完全一致的可见性边界，未发布人工分、人工反馈和人工部分总分不得因为导出接口被提前泄露。
18. 当前成绩分布固定为五档：`EXCELLENT / GOOD / MEDIUM / PASS / FAIL`；总评分布按学生当前加权得分率统计，若学生尚无加权权重则回退到总分得分率；作业分布只统计已提交学生。
19. 成绩册中的 `offeringRank / teachingClassRank` 当前都是只读派生结果，不做持久化存储。
20. 统计报告当前会在总体、按作业和按班级三个层级返回 `passedStudentCount` 与 `passRate`。
21. 成绩申诉当前只支持非客观题，且必须在 assignment 成绩已发布后才能发起；同一答案同一时间只允许一个 `PENDING / IN_REVIEW` 申诉。
22. 申诉复核在 `ACCEPTED` 时会复用单题人工批改写回最终分数与反馈，若未提供新分数则维持当前分数。
23. assignment 级 batch-adjust 与 CSV 导入当前单次最多处理 `100` 条调整项，且所有调整都必须属于同一个 assignment。

## 核心数据模型

- `assignments`
  - `grade_published_at`：assignment 级成绩发布时间
  - `grade_published_by_user_id`：成绩发布人
  - `grade_weight`：assignment 级成绩权重，默认 `100`
- `submission_answers`
  - `manual_score`：人工评分
  - `final_score`：当前分题最终得分
  - `grading_status`：`AUTO_GRADED / MANUALLY_GRADED / PROGRAMMING_JUDGED / PENDING_MANUAL / PENDING_PROGRAMMING_JUDGE`
  - `feedback_text`：分题反馈
  - `graded_by_user_id / graded_at`：最近批改人和时间
- `submissions`
  - 提供正式提交版本、`attempt_no` 和 `submitted_at`
- `grade_appeals`
  - 保存学生围绕非客观题答案发起的成绩申诉、处理状态与复核结果
- `course_members`
  - 提供课程 / 班级名册与 TA 作用域
- `audit_logs`
  - `SUBMISSION_ANSWER_GRADED`
  - `ASSIGNMENT_GRADES_PUBLISHED`
  - `ASSIGNMENT_GRADES_IMPORTED`
  - `GRADE_APPEAL_CREATED`
  - `GRADE_APPEAL_REVIEWED`

详细字段以 [../generated/db-schema.md](../generated/db-schema.md) 为准。

## 角色边界

### 教师

- 查看课程内提交详情和分题答案
- 批改非客观题
- 发布 assignment 成绩
- 查看开课实例、教学班和单学生成绩册
- 导出开课实例 / 教学班 CSV 成绩册，并查看统计报告
- 导出 assignment 级批量调分 CSV 模板，并导入批量调分结果
- 查看并复核 assignment 下的成绩申诉

### 助教

- 当前可查看并批改自己负责教学班内、且 assignment 绑定该教学班的提交
- 当前不具备 assignment 级成绩发布权限
- 当前只可查看并导出自己负责教学班的班级成绩册与统计报告
- 当前可在已有批改权限范围内查看并复核成绩申诉

### 学生

- 查看自己的提交详情和评分摘要
- 按开课实例查看自己的成绩册
- 成绩发布前只能看到客观题即时分与批改状态
- 成绩发布后可查看人工评分、总分与反馈
- 当前可对已发布的非客观题答案发起成绩申诉，并查看自己的申诉列表

## API 边界

### 教师 / 助教侧

- `POST /api/v1/teacher/submissions/{submissionId}/answers/{answerId}/grade`

### 教师侧

- `GET /api/v1/teacher/assignments/{assignmentId}/grades/import-template`
- `POST /api/v1/teacher/assignments/{assignmentId}/grades/import`
- `POST /api/v1/teacher/assignments/{assignmentId}/grades/batch-adjust`
- `POST /api/v1/teacher/assignments/{assignmentId}/grades/publish`
- `GET /api/v1/teacher/assignments/{assignmentId}/grade-appeals`
- `POST /api/v1/teacher/grade-appeals/{appealId}/review`
- `GET /api/v1/teacher/course-offerings/{offeringId}/gradebook`
- `GET /api/v1/teacher/course-offerings/{offeringId}/gradebook/export`
- `GET /api/v1/teacher/course-offerings/{offeringId}/gradebook/report`
- `GET /api/v1/teacher/teaching-classes/{teachingClassId}/gradebook`
- `GET /api/v1/teacher/teaching-classes/{teachingClassId}/gradebook/export`
- `GET /api/v1/teacher/teaching-classes/{teachingClassId}/gradebook/report`
- `GET /api/v1/teacher/course-offerings/{offeringId}/students/{userId}/gradebook`

### 学生侧

- `POST /api/v1/me/submissions/{submissionId}/answers/{answerId}/appeals`
- `GET /api/v1/me/course-offerings/{offeringId}/grade-appeals`
- `GET /api/v1/me/course-offerings/{offeringId}/gradebook`
- `GET /api/v1/me/course-offerings/{offeringId}/gradebook/export`

## 当前实现边界

- 当前发布粒度是 assignment 级，而不是按提交、按学生或按班级的细粒度发布。
- 当前单题人工批改、JSON batch-adjust 和 CSV 导入 / 导出都已实现第一阶段，但仍没有独立的批量批改工作台。
- 当前成绩册不新建成绩表，直接复用 `assignments / submissions / submission_answers / course_members` 读模型。
- 当前成绩册默认只按最新正式提交聚合，不提供历史版本并排比较。
- 当前成绩册已补齐 assignment 级权重、加权总分、权重合计和加权得分率第一阶段；更复杂的总评策略、权重版本化和分组权重仍未实现。
- 当前成绩册的 offering / class / student / me 四类视图都只覆盖结构化作业。
- 当前教师侧 CSV 导出只覆盖 offering / class 两类成绩册，且默认继续按最新正式提交聚合；assignment 级 CSV 只用于 batch-adjust 模板导入导出，不表达完整成绩册。
- 当前教师侧统计报告当前已补齐加权总分、加权得分率、作业权重和五档成绩分布第一阶段，但仍未进入更复杂的总评策略和长期趋势分析。
- 当前教师侧成绩册与单学生视图已补齐 `offeringRank / teachingClassRank`，统计报告已补齐通过率第一阶段，但这些都仍是读模型派生值。
- 学生侧成绩册当前已补齐“我在某个开课实例下的成绩总览”和 CSV 导出第一阶段，但不提供班级横向对比或历史版本矩阵。
- 学生侧成绩册在未发布成绩时仍会返回作业和提交状态，但非客观题人工分、人工反馈和人工部分总分继续隐藏。
- 当前已补齐成绩申诉与复核第一阶段，但仍没有更完整的申诉回滚、仲裁与 SLA 流程，也没有总评语、评分 rubric 和打回重提。
- 助教权限仍基于已有课程成员模型；对课程公共作业的细粒度分工未单独建模。
- 编程题虽然允许人工批改，但题目级自动评测已由 judge 模块先行写回；grading 当前不直接执行自动评测。

## 验收标准

- 教师或具备班级责任的助教可以对非客观题写入分数与反馈。
- 未发布成绩前，学生无法读取人工评分与反馈。
- 发布成绩后，学生可以读取人工评分、总分与反馈。
- 存在待批改或待编程评测答案时，assignment 成绩发布会被拒绝。
- 教师 / 管理员可查看开课实例和单学生成绩册；班级责任 TA 可查看本班成绩册。
- 教师 / 管理员可导出开课实例 / 教学班 CSV 成绩册，并读取与页面聚合语义一致的统计报告；班级责任 TA 只能访问自己负责教学班的导出与统计接口。
- 教师侧成绩册与单学生视图当前会返回开课实例排名 / 教学班排名，统计报告当前会返回通过人数与通过率第一阶段结果。
- 教师 / 管理员 / 班级责任 TA 读取统计报告时，可看到总体、按作业和按班级的五档成绩分布第一阶段结果。
- 教师 / 管理员 / 学生读取成绩册时，当前都会看到与 assignment `gradeWeight` 一致的加权总分、权重合计和加权得分率第一阶段结果。
- 学生可查看自己在开课实例下的成绩册，并且未发布的人工分不会在成绩册里提前泄露。
- 学生可导出自己在开课实例下的成绩册 CSV，且导出内容与页面可见性边界保持一致。
- 成绩册默认按最新正式提交聚合，并正确处理课程公共作业与班级专属作业的适用范围。
- 学生可对已发布的非客观题成绩发起申诉，教师 / 责任助教可在 assignment 维度查看并复核申诉。
- 教师可通过 JSON batch-adjust 或 CSV 模板导入 / 导出，对 assignment 下的分题成绩执行第一阶段批量调整。
- `mvnd verify` 或 `bash ./mvnw verify` 提供自动化测试证据。
