# 执行计划：todo 驱动的开发推进与提交附件切片

## 目标

根据 `todo.md` 盘点当前平台真实开发进度，更新仓库级计划，并把 submission 从纯文本正式提交推进到支持代码 / 文件 / 报告附件的下一切片。

## 范围

- `todo.md` 的当前进度映射
- `modules.submission` 附件上传、正式提交关联、查询与下载
- `modules.audit` 的新增审计动作
- Flyway 迁移、数据库结构说明、产品规格和对象存储说明
- submission 相关集成测试

## 不在范围

- 在线工作区、草稿恢复、试运行
- judge、沙箱执行、重新评测
- 人工批改、成绩计算、成绩发布
- 课程资源、公告通知、消息中心

## 风险

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 附件上传与正式提交耦合不清 | 后续工作区 / judge 难扩展 | 使用“先上传资产，再关联正式提交”的两阶段模型 |
| 对象存储元数据没有业务归属 | 后续权限校验混乱 | 显式在 `submission_artifacts` 中记录 assignment / uploader / submission 归属 |
| 下载接口绕过授权 | 学生或无关教师越权访问附件 | 下载前复用 submission 与 assignment 授权校验 |

## 验证路径

- `./mvnw spotless:apply`
- `./mvnw -Dtest=SubmissionIntegrationTests,MinioStorageIntegrationTests test`
- `./mvnw clean verify`

## 决策记录

| 决策 | 原因 |
| --- | --- |
| 附件元数据进入 `submission` 模块 | 附件是正式提交的一部分，不应抽成过早共享业务目录 |
| 下载先走服务端鉴权读取对象存储 | 当前契约更稳定，避免本轮增加预签名 URL 生命周期管理复杂度 |
| `todo.md` 只更新真实已完成项和当前切片状态 | 避免把“扩展位”误标成已完成功能 |

## 当前结果

- 已新增 `submission_artifacts` 表和 `V6__submission_artifact_slice.sql`
- 已落地学生附件上传、正式提交关联、学生详情查看、教师详情查看、师生授权下载
- 已补充 `SUBMISSION_ARTIFACT_UPLOADED` 审计动作
- 已通过 `SubmissionIntegrationTests`
- 已通过 `clean verify`
- 下一步主链路优先转向 `judge` 第一切片
