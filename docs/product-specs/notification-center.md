# 通知中心系统

## 目标

补齐站内通知最小闭环，承接当前教学主链路里的关键事件，同时保持模块化单体、数据库持久化和现有课程成员权限边界不变。

当前版本只做“站内通知 v1”：

- 持久化通知内容
- 支持未读 / 已读
- 支持未读数
- 提供可选的 HTTP SSE 增强订阅入口
- 支持关键教学事件触发

## 覆盖范围

### 功能范围

- 用户分页查看自己的站内通知
- 用户查看自己的未读通知数
- 用户以 HTTP SSE 方式订阅自己的通知增量事件
- 用户将单条通知标记为已读
- 用户将全部通知标记为已读
- 业务事件触发通知 fan-out

### 首批事件

- 作业发布
- 评测完成
- 成绩发布
- 申诉处理完成
- 实验发布
- 实验报告评语发布

为了保证教师 / 助教也有真实待办型通知，当前实现还额外接入：

- 实验报告提交后通知教师 / 助教

### 不在范围

- 不做 WebSocket 实时推送主链路
- 不做多节点可靠推送、跨节点会话协调或 exactly-once 投递保证
- 不做 Redis 推送通道或消息总线前置依赖
- 不做系统公告中心
- 不做短信、邮件、企业微信等多渠道通知
- 不做通知模板后台、用户通知偏好和免打扰策略

## 为什么当前先做轮询 + 未读数

1. 站内通知的核心问题是“通知内容 + 收件人 + 已读状态”的持久化，而不是先选某种推送协议。
2. 当前仓库还没有稳定的 Redis / WebSocket 运行基线；如果先把实时推送前置，会把最关键的持久化和补拉问题后置。
3. `GET /api/v1/me/notifications` + `GET /api/v1/me/notifications/unread-count` 已经足够支撑前端轮询、角标更新和列表回放；当前 `/stream` 只是增强通道，不是唯一事实来源。
4. 后续若需要 v1.1 实时能力，应在不改变 `notifications / notification_receipts` 语义的前提下，演进当前 SSE 或额外增加 WebSocket 推送层，而不是重新设计通知模型。

## 核心业务规则

1. 通知内容与收件状态分离：
   - `notifications` 保存通知正文、类型、目标资源和上下文元数据
   - `notification_receipts` 保存面向具体用户的收件记录和 `read_at`
2. 通知必须绑定到业务上已经“真正对用户可见”的状态切换点，不能在草稿保存、排队中、教师内部评阅中提前通知。
3. 同一条通知可 fan-out 给多个收件人；收件人之间的已读状态彼此独立。
4. `POST /api/v1/me/notifications/{id}/read` 只允许标记当前用户自己的通知；越权访问按“通知不存在”处理。
5. `POST /api/v1/me/notifications/read-all` 只会影响当前用户未读通知，不会改动其他用户的收件状态。
6. 当前通知排序按最新创建时间倒序。
7. 第一阶段的去重主要依赖业务状态机本身：
   - 作业 / 实验只能从草稿发布一次
   - 成绩只能首次发布时触发通知
   - 申诉处理完成只在 `ACCEPTED / REJECTED` 终态触发
   - 实验报告评语只在 `publishReport` 时触发
   - 评测通知只在终态完成时触发

## 数据模型

- `notifications`
  - 保存通知类型、标题、正文、目标资源、上下文元数据
  - 可按一条内容 fan-out 给多个用户
- `notification_receipts`
  - 保存 `notification_id + recipient_user_id`
  - 通过 `read_at` 表达已读状态

详细列说明以 [../generated/db-schema.md](../generated/db-schema.md) 为准。

## API 边界

### 学生 / 教师统一个人侧

- `GET /api/v1/me/notifications`
- `GET /api/v1/me/notifications/unread-count`
- `GET /api/v1/me/notifications/stream`
- `POST /api/v1/me/notifications/{id}/read`
- `POST /api/v1/me/notifications/read-all`

## 当前实现边界

- 当前只做站内通知，不承载公告正文、多渠道消息中心或独立实时总线。
- 当前通知正文由后端按事件类型直接生成，不引入通知模板中心。
- 当前只按用户收件箱维度查询，不支持课程、类型、是否已读等更复杂过滤。
- 当前通知 fan-out 仍在业务事务内同步入库；如果后续事件量继续扩大，再考虑事件总线或异步派发。
- `GET /api/v1/me/notifications/stream` 当前已经存在，但仅提供单实例内存态 HTTP SSE best-effort 推送：
  - 不保证多节点会话迁移后的无缝续传
  - 不保证断线期间事件逐条补发
  - 客户端断连或 stream 不可用时必须回退 `GET /api/v1/me/notifications` 与 `GET /api/v1/me/notifications/unread-count`
