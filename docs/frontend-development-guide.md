# 前端全生命周期开发指南

## 1. 结论与适用范围

当前后端 API 已经能够支撑一个“单学校、一体化在线教学与实验平台”前端的完整开发与联调，范围覆盖：

- 平台治理与 IAM
- 课程 / 开课 / 教学班
- 课程公告 / 教学资源 / 课程讨论
- 作业 / 题库 / 结构化试卷
- 提交 / 附件 / 编程工作区 / 样例试运行
- 自动评测 / 评测环境 / 评测报告
- 批改 / 成绩发布 / 成绩册 / 申诉
- 实验 / 实验报告 / 实验附件
- 站内通知 / 未读数 / SSE 订阅

本指南用于指导前端从 0 到联调、回归、内测上线前的完整实施。若本文与运行时 OpenAPI 冲突，以 `GET /v3/api-docs` 为准；若本文与业务稳定面冲突，以 [stable-api.md](stable-api.md) 为准。

## 2. 当前边界与非目标

前端必须按当前后端范围开发，不要等待并不存在的能力：

- 单学校模型：当前不是多学校 / 多租户。
- 通知实时基线：默认采用“列表轮询 + 未读数”，SSE 只作为增强通道。
- 在线 IDE：后端已支持模板工作区、目录树快照、修订历史、模板重置、样例试运行；当前不支持多人实时协作、WebSocket 编辑协议。
- 课程讨论：当前支持主题、回复、锁帖，不支持删除、置顶、富文本审核流。
- 教学资源：当前支持上传、列表、下载，不支持目录树、版本历史、外链分享。
- 实验报告：当前每个学生每个实验只有一份当前报告，不支持版本历史。
- 用户认证：当前不提供注册、找回密码、自助会话管理、SSO/OIDC。
- 管理后台：当前提供平台配置、组织树、用户治理、审计查询；不提供组织节点编辑 / 删除。

前端若需要超出以上范围的交互，必须先和后端确认，不要自行假设有隐藏接口。

## 3. 前端统一约定

### 3.1 基础地址

- 业务 API：`/api/v1/**`
- OpenAPI：`GET /v3/api-docs`
- Swagger UI：`GET /swagger-ui/index.html`
- 健康检查：`GET /actuator/health/readiness`

### 3.2 认证模型

- 登录：`POST /api/v1/auth/login`
- 当前用户：`GET /api/v1/auth/me`
- 刷新 access token：`POST /api/v1/auth/refresh`
- 登出当前会话：`POST /api/v1/auth/logout`
- 主动撤销 refresh token：`POST /api/v1/auth/revoke`

前端要求：

- access token 统一走 `Authorization: Bearer <token>`
- refresh token 不自动暴露给各业务模块，由统一 auth client 管理
- 任意业务接口返回 `401` 时，先尝试一次 refresh；refresh 失败则清空本地会话并跳回登录页
- `/api/v1/auth/me` 是登录后初始化用户与路由权限的唯一事实来源

### 3.3 通用响应约定

- 分页接口统一返回 `PageResponse<T>`，关键字段为：
  - `items`
  - `total`
  - `page`
  - `pageSize`
- 业务错误统一走 JSON 错误响应，前端至少要解析：
  - `code`
  - `message`
- 时间字段统一按 ISO 8601 解析
- 下载接口返回二进制内容和 `Content-Disposition`
- 上传接口需要显式使用 `multipart/form-data`

### 3.4 前端必须识别的核心错误码

- `FORBIDDEN`
- `INVALID_CREDENTIALS`
- `INVALID_REFRESH_TOKEN`
- `ORG_HIERARCHY_INVALID`
- `ANNOUNCEMENT_DISABLED`
- `RESOURCE_DISABLED`
- `DISCUSSION_DISABLED`
- `DISCUSSION_LOCKED`
- `COURSE_DISCUSSION_NOT_FOUND`
- `COURSE_ANNOUNCEMENT_NOT_FOUND`
- `COURSE_RESOURCE_NOT_FOUND`
- `SUBMISSION_WINDOW_INVALID`
- `SUBMISSION_ASSIGNMENT_UNAVAILABLE`
- `LAB_STATUS_INVALID`

建议做统一错误字典层，不要把后端原始 message 直接散落到页面组件中。

## 4. 角色与导航信息架构

### 4.1 角色来源

前端不要自行推导角色，统一基于：

- `/api/v1/auth/me`
- `/api/v1/me/courses`
- 各业务列表接口的返回内容

### 4.2 建议导航层级

| 角色 | 一级导航 | 二级导航建议 |
| --- | --- | --- |
| 学校管理员 | 平台治理 | 平台配置、组织树、用户管理、审计日志、学期、课程目录、开课 |
| 学院管理员 | 教学治理 | 课程目录、开课、用户范围治理、审计查询 |
| 教师 | 我的教学 | 我的课程、班级、公告、资源、讨论、作业、提交、评测、成绩、实验、通知 |
| 助教 | 协助教学 | 我的课程、成员范围、提交查看、班级成绩册、通知 |
| 学生 | 我的学习 | 我的课程、公告、资源、讨论、作业、提交、IDE、成绩、实验、通知 |

## 5. 页面与 API 对照

### 5.1 登录与会话

| 页面/能力 | 接口 |
| --- | --- |
| 登录页 | `POST /api/v1/auth/login` |
| 初始化当前用户 | `GET /api/v1/auth/me` |
| 自动续期 | `POST /api/v1/auth/refresh` |
| 主动退出 | `POST /api/v1/auth/logout` |
| 强制撤销会话后的兜底 | `POST /api/v1/auth/revoke` |

前端状态建议：

- `authStore`: `accessToken / refreshToken / currentUser / initialized`
- `authGuard`: 基于 `currentUser` 和角色能力做路由守卫

### 5.2 平台治理后台

| 页面 | 接口 |
| --- | --- |
| 平台配置 | `GET/PUT /api/v1/admin/platform-config/current` |
| 组织树 | `GET /api/v1/admin/org-units/tree` |
| 创建组织节点 | `POST /api/v1/admin/org-units` |
| 用户列表 | `GET /api/v1/admin/users` |
| 用户详情 | `GET /api/v1/admin/users/{userId}` |
| 创建用户 | `POST /api/v1/admin/users` |
| 批量导入用户 | `POST /api/v1/admin/users/import` |
| 更新治理身份 | `PUT /api/v1/admin/users/{userId}/identities` |
| 更新画像 | `PUT /api/v1/admin/users/{userId}/profile` |
| 更新组织成员关系 | `PUT /api/v1/admin/users/{userId}/memberships` |
| 禁用/启用账号 | `PATCH /api/v1/admin/users/{userId}/status` |
| 强制用户下线 | `POST /api/v1/admin/users/{userId}/sessions/revoke` |
| 审计日志 | `GET /api/v1/admin/audit-logs` |

前端要求：

- 组织树页支持 scope 感知，不要假设任意管理员都能看到整棵树
- 用户导入页必须展示行级失败信息
- 用户详情页必须能展示 identities 与 memberships

### 5.3 课程主数据与教学组织

| 页面 | 接口 |
| --- | --- |
| 学期管理 | `POST/GET /api/v1/admin/academic-terms` |
| 课程目录管理 | `POST/GET /api/v1/admin/course-catalogs` |
| 开课管理 | `POST/GET /api/v1/admin/course-offerings` |
| 开课详情 | `GET /api/v1/admin/course-offerings/{offeringId}` |
| 我的课程 | `GET /api/v1/me/courses` |
| 教学班创建/列表 | `POST/GET /api/v1/teacher/course-offerings/{offeringId}/classes` |
| 教学班功能开关 | `PUT /api/v1/teacher/course-classes/{teachingClassId}/features` |
| 成员批量导入/添加 | `POST /api/v1/teacher/course-offerings/{offeringId}/members/batch`、`POST /api/v1/teacher/course-offerings/{offeringId}/members/import` |
| 成员列表 | `GET /api/v1/teacher/course-offerings/{offeringId}/members` |

前端要求：

- `我的课程` 是教师、助教、学生的统一课程入口
- 班级功能开关直接影响公告、资源、讨论、实验、作业入口显隐与禁用态
- 功能开关只能作为 UI 提示，最终仍以业务接口返回为准

### 5.4 课程公告 / 资源 / 讨论

#### 公告

| 页面 | 接口 |
| --- | --- |
| 教师创建公告 | `POST /api/v1/teacher/course-offerings/{offeringId}/announcements` |
| 教师公告列表 | `GET /api/v1/teacher/course-offerings/{offeringId}/announcements` |
| 学生班级公告列表 | `GET /api/v1/me/course-classes/{teachingClassId}/announcements` |
| 学生公告详情 | `GET /api/v1/me/announcements/{announcementId}` |

#### 资源

| 页面 | 接口 |
| --- | --- |
| 教师上传资源 | `POST /api/v1/teacher/course-offerings/{offeringId}/resources` |
| 教师资源列表 | `GET /api/v1/teacher/course-offerings/{offeringId}/resources` |
| 教师下载资源 | `GET /api/v1/teacher/course-resources/{resourceId}/download` |
| 学生资源列表 | `GET /api/v1/me/course-classes/{teachingClassId}/resources` |
| 学生下载资源 | `GET /api/v1/me/course-resources/{resourceId}/download` |

资源上传要求：

- 表单字段必须包含 `title`
- 文件字段名固定为 `file`

#### 讨论

| 页面 | 接口 |
| --- | --- |
| 教师创建讨论主题 | `POST /api/v1/teacher/course-offerings/{offeringId}/discussions` |
| 教师讨论列表 | `GET /api/v1/teacher/course-offerings/{offeringId}/discussions` |
| 教师讨论详情 | `GET /api/v1/teacher/discussions/{discussionId}` |
| 教师回复 | `POST /api/v1/teacher/discussions/{discussionId}/replies` |
| 教师锁帖/解锁 | `PUT /api/v1/teacher/discussions/{discussionId}/lock-state` |
| 学生创建班级讨论 | `POST /api/v1/me/course-classes/{teachingClassId}/discussions` |
| 学生讨论列表 | `GET /api/v1/me/course-classes/{teachingClassId}/discussions` |
| 学生讨论详情 | `GET /api/v1/me/discussions/{discussionId}` |
| 学生回复 | `POST /api/v1/me/discussions/{discussionId}/replies` |

讨论页要求：

- 支持“开课级公共讨论 + 班级讨论”混合展示
- 锁帖后输入框必须禁用
- 返回 `DISCUSSION_DISABLED` 时，整块讨论区域切到功能关闭态
- 班级讨论跨班访问返回 `403`，前端不要把它当成“加载失败重试”

### 5.5 作业与题库

| 页面 | 接口 |
| --- | --- |
| 教师作业列表/创建 | `POST/GET /api/v1/teacher/course-offerings/{offeringId}/assignments` |
| 作业详情/编辑 | `GET/PUT /api/v1/teacher/assignments/{assignmentId}` |
| 发布/关闭作业 | `POST /api/v1/teacher/assignments/{assignmentId}/publish`、`POST /api/v1/teacher/assignments/{assignmentId}/close` |
| 题库题目列表/创建 | `POST/GET /api/v1/teacher/course-offerings/{offeringId}/question-bank/questions` |
| 分类/标签 | `GET /api/v1/teacher/course-offerings/{offeringId}/question-bank/categories` |
| 题目详情/编辑/归档 | `GET/PUT /api/v1/teacher/question-bank/questions/{questionId}`、`POST /api/v1/teacher/question-bank/questions/{questionId}/archive` |
| 学生作业列表/详情 | `GET /api/v1/me/assignments`、`GET /api/v1/me/assignments/{assignmentId}` |

前端要求：

- 作业编辑页要按题型拆组件，但提交数据仍以题目配置整体发送
- 学生作业详情页不能假设一定包含所有正确答案字段

### 5.6 提交、附件、编程工作区

| 页面 | 接口 |
| --- | --- |
| 学生正式提交 | `POST /api/v1/me/assignments/{assignmentId}/submissions` |
| 附件上传 | `POST /api/v1/me/assignments/{assignmentId}/submission-artifacts` |
| 我的提交列表 | `GET /api/v1/me/assignments/{assignmentId}/submissions` |
| 我的提交详情 | `GET /api/v1/me/submissions/{submissionId}` |
| 我的附件下载 | `GET /api/v1/me/submission-artifacts/{artifactId}/download` |
| 教师提交列表/详情 | `GET /api/v1/teacher/assignments/{assignmentId}/submissions`、`GET /api/v1/teacher/submissions/{submissionId}` |
| 教师附件下载 | `GET /api/v1/teacher/submission-artifacts/{artifactId}/download` |
| 工作区读取/保存 | `GET/PUT /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace` |
| 工作区目录操作 | `POST /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace/operations` |
| 修订历史/详情 | `GET /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace/revisions`、`GET /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace/revisions/{revisionId}` |
| 恢复修订 | `POST /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace/revisions/{revisionId}/restore` |
| 重置模板 | `POST /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace/reset-to-template` |

前端要求：

- IDE 页面必须把“工作区保存”和“正式提交”分成两个动作
- 目录树操作不要本地自造 ID，直接以服务端回写的数据为准
- 修订恢复后立即刷新工作区快照

### 5.7 样例试运行与自动评测

| 页面 | 接口 |
| --- | --- |
| 样例试运行创建/列表/详情 | `POST/GET /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs`、`GET /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs/{sampleRunId}` |
| 学生查看评测任务 | `GET /api/v1/me/submissions/{submissionId}/judge-jobs`、`GET /api/v1/me/submission-answers/{answerId}/judge-jobs` |
| 学生查看/下载评测报告 | `GET /api/v1/me/judge-jobs/{judgeJobId}/report`、`GET /api/v1/me/judge-jobs/{judgeJobId}/report/download` |
| 教师查看/下载评测报告 | `GET /api/v1/teacher/judge-jobs/{judgeJobId}/report`、`GET /api/v1/teacher/judge-jobs/{judgeJobId}/report/download` |
| 教师重判 | `POST /api/v1/teacher/submissions/{submissionId}/judge-jobs/requeue`、`POST /api/v1/teacher/submission-answers/{answerId}/judge-jobs/requeue` |
| 评测环境模板管理 | `POST/GET /api/v1/teacher/course-offerings/{offeringId}/judge-environment-profiles`、`GET/PUT /api/v1/teacher/judge-environment-profiles/{profileId}`、`POST /api/v1/teacher/judge-environment-profiles/{profileId}/archive` |

前端要求：

- 评测页需要支持“轮询直到状态终止”
- 学生视图默认不要展示隐藏测试明文
- 失败状态要区分“评测失败”和“仍在排队”

### 5.8 批改、成绩发布、成绩册、申诉

| 页面 | 接口 |
| --- | --- |
| 教师人工批改 | `POST /api/v1/teacher/submissions/{submissionId}/answers/{answerId}/grade` |
| 批量调分 | `POST /api/v1/teacher/assignments/{assignmentId}/grades/batch-adjust` |
| 成绩导入模板/导入 | `GET /api/v1/teacher/assignments/{assignmentId}/grades/import-template`、`POST /api/v1/teacher/assignments/{assignmentId}/grades/import` |
| 成绩发布与发布批次 | `POST /api/v1/teacher/assignments/{assignmentId}/grades/publish`、`GET /api/v1/teacher/assignments/{assignmentId}/grade-publish-batches`、`GET /api/v1/teacher/assignments/{assignmentId}/grade-publish-batches/{batchId}` |
| 教师成绩册 | `GET /api/v1/teacher/course-offerings/{offeringId}/gradebook`、`GET /api/v1/teacher/teaching-classes/{teachingClassId}/gradebook` |
| 教师成绩导出/统计 | `GET /api/v1/teacher/course-offerings/{offeringId}/gradebook/export`、`GET /api/v1/teacher/course-offerings/{offeringId}/gradebook/report`、`GET /api/v1/teacher/teaching-classes/{teachingClassId}/gradebook/export`、`GET /api/v1/teacher/teaching-classes/{teachingClassId}/gradebook/report` |
| 单学生成绩册 | `GET /api/v1/teacher/course-offerings/{offeringId}/students/{studentUserId}/gradebook` |
| 学生成绩册/导出 | `GET /api/v1/me/course-offerings/{offeringId}/gradebook`、`GET /api/v1/me/course-offerings/{offeringId}/gradebook/export` |
| 学生申诉 | `POST /api/v1/me/submissions/{submissionId}/answers/{answerId}/appeals` |
| 学生申诉列表 | `GET /api/v1/me/course-offerings/{offeringId}/grade-appeals` |
| 教师申诉处理 | `GET /api/v1/teacher/assignments/{assignmentId}/grade-appeals`、`POST /api/v1/teacher/grade-appeals/{appealId}/review` |

前端要求：

- 成绩册导出是文件下载，不要按 JSON 处理
- 学生成绩册只在成绩发布后展示最终可见结果
- 申诉页需要把状态流转明确显示为时间线

### 5.9 实验与实验报告

| 页面 | 接口 |
| --- | --- |
| 教师实验列表/创建 | `POST/GET /api/v1/teacher/course-offerings/{offeringId}/labs` |
| 教师实验详情/编辑 | `GET/PUT /api/v1/teacher/labs/{labId}` |
| 教师发布/关闭实验 | `POST /api/v1/teacher/labs/{labId}/publish`、`POST /api/v1/teacher/labs/{labId}/close` |
| 教师实验报告列表/详情 | `GET /api/v1/teacher/labs/{labId}/reports`、`GET /api/v1/teacher/lab-reports/{reportId}` |
| 教师评阅/发布评语 | `PUT /api/v1/teacher/lab-reports/{reportId}/review`、`POST /api/v1/teacher/lab-reports/{reportId}/publish` |
| 教师下载实验附件 | `GET /api/v1/teacher/lab-report-attachments/{attachmentId}/download` |
| 学生实验列表/详情 | `GET /api/v1/me/course-classes/{teachingClassId}/labs`、`GET /api/v1/me/labs/{labId}` |
| 学生上传附件 | `POST /api/v1/me/labs/{labId}/attachments` |
| 学生保存/提交报告 | `PUT /api/v1/me/labs/{labId}/report` |
| 学生查看报告 | `GET /api/v1/me/labs/{labId}/report` |
| 学生下载附件 | `GET /api/v1/me/lab-report-attachments/{attachmentId}/download` |

### 5.10 通知中心

| 页面 | 接口 |
| --- | --- |
| 通知列表 | `GET /api/v1/me/notifications` |
| 未读角标 | `GET /api/v1/me/notifications/unread-count` |
| 单条已读 | `POST /api/v1/me/notifications/{notificationId}/read` |
| 全部已读 | `POST /api/v1/me/notifications/read-all` |
| SSE 增强订阅 | `GET /api/v1/me/notifications/stream` |

前端集成建议：

- 默认每 30 到 60 秒轮询一次 `list + unread-count`
- 若项目已接入 SSE，可增量接入 `/stream`
- SSE 断连后必须自动退回轮询，不要把通知能力绑定在单一实时通道上

## 6. 前端开发顺序

建议严格按下面顺序推进：

1. 认证壳层
2. 路由守卫与角色导航
3. 管理后台最小闭环：平台配置、组织树、用户
4. 我的课程与课程详情框架
5. 课程内容域：公告、资源、讨论
6. 作业列表与作业详情
7. 提交与 IDE
8. 评测结果与报告
9. 成绩册与申诉
10. 实验与实验报告
11. 通知中心

原因：

- 认证与角色导航是所有页面的入口
- 课程内容域是教师端与学生端的共同基座
- IDE、评测、成绩、实验都依赖课程和作业基础页面先跑通

## 7. 前端状态管理建议

建议按业务域拆 store / query key：

- `auth`
- `platform`
- `users`
- `courses`
- `courseContent`：公告、资源、讨论
- `assignments`
- `submissions`
- `workspace`
- `judge`
- `grading`
- `labs`
- `notifications`

缓存策略建议：

- 列表页：以分页参数作为 query key
- 详情页：以实体 `id` 作为 query key
- 成功变更后优先失效相关列表，再局部回写详情
- 文件下载不进入全局 store

## 8. 联调与回归清单

### 8.1 最小 smoke

前端每次大版本联调至少走通一次：

1. 登录
2. 初始化 `/api/v1/auth/me`
3. 进入我的课程
4. 查看课程公告 / 资源 / 讨论
5. 学生查看作业并进入提交页
6. 编程题保存工作区并发起样例试运行
7. 发起正式提交并轮询评测结果
8. 教师进入提交详情并批改 / 发布成绩
9. 学生查看成绩册并发起申诉
10. 学生查看实验并提交实验报告
11. 打开通知中心，确认未读数和列表回放正常

### 8.2 负向用例

前端联调时必须覆盖：

- 未登录访问受保护页
- 学生访问教师页
- 教师访问平台治理页
- 跨班访问讨论 / 资源 / 提交
- 班级功能关闭后的禁用态
- 锁帖后回复禁用态
- 作业关闭后提交失败态
- 实验关闭后报告提交失败态

## 9. 页面级 UX 最小要求

- 所有列表页都要支持空态、加载态、错误态
- 所有创建 / 上传 / 发布动作都要有明确成功反馈
- 下载按钮要支持二进制流下载失败的兜底提示
- IDE 页面必须把“已保存”和“未保存”状态显式展示
- 讨论页要区分主题正文与回复，不要把首帖当成普通回帖
- 成绩发布前后，学生侧页面要有明显状态差异

## 10. 前端开发时不要犯的误判

- 不要把班级功能开关当作前端本地布尔常量，它会被教师实时修改
- 不要假设任意教师都能看到任意班级讨论、资源、提交
- 不要把 `403` 全都处理成“无权限弹窗后重试”，很多场景应直接转空态或禁用态
- 不要把 SSE 当成通知唯一事实来源
- 不要等待不存在的删除 / 置顶 / 版本历史接口
- 不要把在线 IDE 当成多人实时协作编辑器

## 11. 开发交付建议

前端完成每个业务域后，应同步产出：

- 页面路由清单
- API client 封装
- query key / store 约定
- 错误码映射表
- smoke 用例

建议把 `GET /v3/api-docs` 直接接入前端本地 mock / type generation 流程，但生成后的类型只作为辅助，不替代业务层 DTO 适配。

## 12. 文档与事实入口

- 运行时 OpenAPI：`GET /v3/api-docs`
- Swagger UI：`GET /swagger-ui/index.html`
- 稳定接口范围：[stable-api.md](stable-api.md)
- 课程系统规格：[product-specs/course-system.md](product-specs/course-system.md)
- 作业系统规格：[product-specs/assignment-system.md](product-specs/assignment-system.md)
- 提交系统规格：[product-specs/submission-system.md](product-specs/submission-system.md)
- 评测系统规格：[product-specs/judge-system.md](product-specs/judge-system.md)
- 成绩系统规格：[product-specs/grading-system.md](product-specs/grading-system.md)
- 实验系统规格：[product-specs/lab-system.md](product-specs/lab-system.md)
- 通知中心规格：[product-specs/notification-center.md](product-specs/notification-center.md)

前端开发若严格遵循本文和以上事实入口，当前后端能力已经足够支撑完整的页面开发、联调、回归和内测交付。
