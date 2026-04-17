# 2026-04-17 成绩发布快照 v1

## 背景

当前 assignment 成绩发布链路已经能完成“校验是否全部批改完毕 -> 设置 `assignments.grade_published_at` -> 向学生开放人工分与反馈”，但缺少“发布当时到底向学生展示了什么”的可追踪历史。后续若要做冻结、回滚、批次审计或发布差异比对，必须先补齐最小快照模型。

## 本轮目标

- 在 assignment 成绩发布时生成可追踪快照
- 保留当前学生读取链路，不重写 gradebook / submission 主链路
- 为后续冻结 / 回滚预留数据基础，但本轮不实现完整回滚

## 实现摘要

- 新增 `grade_publish_snapshot_batches`
  - 记录 assignment 每次发布的批次头、序号、发布时间、是否首次发布与快照数量
- 新增 `grade_publish_snapshots`
  - 记录某个发布批次下，每个学生最新正式提交对应的已发布成绩快照
- `POST /api/v1/teacher/assignments/{assignmentId}/grades/publish`
  - 每次调用都会新建一个快照批次
  - 只有第一次发布会写入 `assignments.grade_published_at / grade_published_by_user_id`
  - 只有第一次发布会发送“成绩首次发布”通知
- 新增教师追踪接口
  - `GET /api/v1/teacher/assignments/{assignmentId}/grade-publish-batches`
  - `GET /api/v1/teacher/assignments/{assignmentId}/grade-publish-batches/{batchId}`

## 快照内容

每条学生快照当前保存：

- assignment / offering / teaching class 关联键
- 学生快照：`userId / username / displayName / classCode / className`
- 提交快照：`submissionId / submissionNo / attemptNo / submittedAt`
- 分数摘要：`finalScore / maxScore / autoScoredScore / manualScoredScore / fullyGraded`
- 分题批改视图：题目标题、题型、自动分、人工分、最终分、批改状态、反馈、批改人、批改时间

## 兼容策略

- 学生读取链路仍以 `assignments.grade_published_at != null` 作为发布开关
- 快照是附加追踪模型，不替代现有成绩册和提交详情读取
- 不回填历史发布数据；旧 assignment 没有快照批次仍可正常使用

## 验证

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -Dtest=GradingIntegrationTests,GradebookIntegrationTests test`
- `git diff --check`

## 当前边界

- v1 只支持发布时生成快照与教师侧追踪，不支持完整回滚
- 快照只覆盖“已有最新正式提交”的学生，不为未提交学生补空白记录
- 当前仍未引入总评冻结批次或成绩版本切换能力
