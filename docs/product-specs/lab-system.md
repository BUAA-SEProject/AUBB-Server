# 实验与实验报告系统

## 目标

补齐课程主链路中长期缺失的实验与实验报告域，在不复用 assignment/submission 语义的前提下，交付教学班级级别的 lab/report MVP。

## 覆盖范围

### 功能范围

- 教师创建实验草稿、更新实验草稿、发布实验、关闭实验
- 教师分页查看实验列表与实验详情
- 学生按教学班查看实验列表与实验详情
- 学生上传实验报告附件
- 学生保存实验报告草稿或正式提交
- 教师分页查看实验报告列表与单份报告详情
- 教师填写批注 / 评语并发布评阅结果
- 学生查看自己的实验报告与已发布评语
- 教师与学生都可按权限下载实验报告附件

### 不在范围

- 不把 lab/report 建模成 assignment/submission 的别名
- 不做实验分组协作、多人共同提交
- 不做实验报告版本历史、多次尝试历史、撤回提交
- 不做前端富文本批注、文件内行级批注、批量评阅
- 不在实验域内部直接承载通知、站内信或实时推送能力；相关事件统一交给通知中心消费

## 核心业务规则

1. 当前实验 `lab` 仅支持教学班级级别发布，`teachingClassId` 必填；暂不支持开课实例级公共实验。
2. 实验定义状态为 `DRAFT / PUBLISHED / CLOSED`：
   - `DRAFT` 可编辑，不对学生开放
   - `PUBLISHED` 对教学班成员开放查看与报告提交
   - `CLOSED` 保留查看与评阅，不再允许学生继续提交或上传附件
3. 实验报告状态为 `DRAFT / SUBMITTED / REVIEWED / PUBLISHED`：
   - 学生保存草稿得到 `DRAFT`
   - 学生正式提交得到 `SUBMITTED`
   - 教师填写批注 / 评语后进入 `REVIEWED`
   - 教师显式发布评阅结果后进入 `PUBLISHED`
4. 当前每个学生在每个实验下只保留一份当前实验报告，唯一键为 `(lab_id, student_user_id)`。
5. 学生只能在实验为 `PUBLISHED` 且报告状态仍是 `DRAFT / SUBMITTED` 时继续修改报告；进入教师评阅后不再允许学生覆盖。
6. 教师当前只能对 `SUBMITTED / REVIEWED` 状态的报告继续评阅；发布评阅结果前至少要有批注或评语之一。
7. 学生在 `REVIEWED` 阶段只能看到自己的报告与状态，教师批注 / 评语要到 `PUBLISHED` 后才对学生可见。
8. 实验附件复用现有对象存储能力，数据库只保存附件元数据和对象引用，不直接保存二进制内容。
9. 当前实验报告正文长度上限为 `20000` 字符，教师批注 / 评语上限为 `5000` 字符；单份报告最多 `10` 个附件，每个附件最大 `20MB`。
10. `labEnabled=false` 时，实验定义、实验列表、实验详情、附件上传、报告查看 / 提交、教师评阅等相关入口都要在后端真实拦截，不能只靠前端隐藏。
11. 权限完全沿用课程成员边界：
   - 实验定义管理复用开课实例管理权限
   - 实验报告评阅复用“可批改提交”的教师 / 助教权限
   - 学生访问基于教学班成员关系判断

## 数据模型

- `labs`
  - 实验定义主表
  - 关键字段：`offering_id`、`teaching_class_id`、`title`、`description`、`status`
- `lab_reports`
  - 学生当前实验报告
  - 关键字段：`lab_id`、`student_user_id`、`status`、`report_content_text`
  - 教师评阅字段：`teacher_annotation_text`、`teacher_comment_text`、`reviewer_user_id`
- `lab_report_attachments`
  - 实验报告附件元数据
  - 关键字段：`lab_id`、`lab_report_id`、`uploader_user_id`、`object_key`

详细列说明以 [../generated/db-schema.md](../generated/db-schema.md) 为准。

## API 边界

### 教师侧

- `POST /api/v1/teacher/course-offerings/{offeringId}/labs`
- `PUT /api/v1/teacher/labs/{labId}`
- `POST /api/v1/teacher/labs/{labId}/publish`
- `POST /api/v1/teacher/labs/{labId}/close`
- `GET /api/v1/teacher/course-offerings/{offeringId}/labs`
- `GET /api/v1/teacher/labs/{labId}`
- `GET /api/v1/teacher/labs/{labId}/reports`
- `GET /api/v1/teacher/lab-reports/{reportId}`
- `PUT /api/v1/teacher/lab-reports/{reportId}/review`
- `POST /api/v1/teacher/lab-reports/{reportId}/publish`
- `GET /api/v1/teacher/lab-report-attachments/{attachmentId}/download`

### 学生侧

- `GET /api/v1/me/course-classes/{teachingClassId}/labs`
- `GET /api/v1/me/labs/{labId}`
- `POST /api/v1/me/labs/{labId}/attachments`
- `PUT /api/v1/me/labs/{labId}/report`
- `GET /api/v1/me/labs/{labId}/report`
- `GET /api/v1/me/lab-report-attachments/{attachmentId}/download`

## 当前实现边界

- 当前实验只按教学班发布，这样 `labEnabled` 功能开关有唯一、稳定的后端判断来源。
- 教师定义实验仍由开课实例管理者负责；实验报告评阅则放宽到对应教学班助教，与已有 grading 语义对齐。
- 当前不做实验报告版本表；后续若需要历史版本，应该新增版本化结构，而不是覆盖当前表语义。
- 当前附件先上传、再在保存 / 提交实验报告时绑定到报告；未绑定附件仍保留元数据，但只能被同一学生在同一实验内引用。
- 当前学生实验列表只返回 `PUBLISHED / CLOSED` 实验，草稿实验仅教师可见。
