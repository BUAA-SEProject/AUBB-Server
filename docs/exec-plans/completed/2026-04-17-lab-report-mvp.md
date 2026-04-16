# 2026-04-17 lab / report MVP

## 目标

在现有课程成员权限边界和模块化单体结构上，补齐教学班级级别的实验与实验报告 MVP。

## 范围

- `modules/lab` 新模块
- `db/migration` 中新增实验、实验报告和附件表
- 课程授权与 `labEnabled` 功能开关接线
- 教师 / 学生实验 API
- MinIO 附件集成测试与文档同步

## 非目标

- 不把实验报告重用为 assignment/submission
- 不做开课实例级公共实验
- 不做实验报告版本历史、多人协作、通知联动

## 根因

1. 当前 `labEnabled` 只是教学班 feature 开关，没有下游业务真正消费。
2. 仓库没有 `labs / lab_reports / lab_report_attachments` 的真实模型和状态流转。
3. 如果直接复用 assignment/submission，会把实验报告的草稿、评阅和发布语义混进作业域。

## 最小实现方案

1. 新增 `modules/lab`，保持 `api / application / domain / infrastructure` 分层
2. 实验先收敛为教学班级级别，`teachingClassId` 必填
3. 实验状态：`DRAFT / PUBLISHED / CLOSED`
4. 报告状态：`DRAFT / SUBMITTED / REVIEWED / PUBLISHED`
5. 附件内容走现有对象存储，数据库只保存元数据和对象引用
6. `CourseAuthorizationService` 新增 lab 相关授权与 `labEnabled` 断言入口

## 风险

- 当前每学生每实验只保留一份当前报告，不覆盖历史版本场景
- 教学班关闭实验功能后，历史实验与报告入口会被统一拦截
- 报告附件采用“先上传后绑定”，需要严格限制同一学生同一实验内引用

## 验证路径

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -Dtest=LabReportLifecyclePolicyTests,LabReportIntegrationTests test`

## 实施结果

1. 新增 `labs / lab_reports / lab_report_attachments` 与 `V26__lab_report_mvp.sql`
2. 教师侧补齐实验创建、更新、发布、关闭、报告列表、报告详情、批注 / 评语、评阅发布
3. 学生侧补齐实验列表 / 详情、附件上传、报告草稿 / 提交、我的报告与附件下载
4. `labEnabled=false` 已进入真实后端拦截链路
5. 附件通过 MinIO 真链路读写验证，报告查询与评语发布回放正常

## 验证结果

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -Dtest=LabReportLifecyclePolicyTests,LabReportIntegrationTests test`
- 结果：`BUILD SUCCESS`，定向 `6` 个测试通过
