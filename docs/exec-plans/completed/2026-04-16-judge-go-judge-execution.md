# 执行计划：judge go-judge 执行切片

## 目标

把 `judge` 从“评测作业入队骨架”推进到“可真实调用 go-judge 执行并回写聚合结果”的当前切片，使 assignment、submission、judge 主链路继续向 grading 靠拢。

## 范围

- assignment 增加脚本型自动评测配置
- go-judge 基础配置与本地运行时接入
- AFTER_COMMIT 异步执行与 `judge_jobs` 结果回写
- judge 专项集成测试
- 架构、规格、数据库、todo 与工作记忆同步

## 风险

| 风险 | 影响 | 缓解措施 |
| --- | --- | --- |
| 现有 submission 模型没有多文件工程语义 | 直接做多语言/多文件会过度设计 | 当前只做 `PYTHON3 + TEXT_BODY` |
| go-judge 网络故障阻断提交 | 提交链路不稳定 | 提交事务内只入队，执行放到 AFTER_COMMIT 异步线程 |
| 官方镜像缺少脚本运行时 | 本地无法复现 Python3 评测 | 仓库提供固定版本 `docker/go-judge/Dockerfile` |

## 决策记录

| 决策 | 原因 |
| --- | --- |
| 使用 `assignment_judge_profiles + assignment_judge_cases` 存评测配置 | 保持 assignment 作为 judge 上游边界 |
| 使用应用内异步执行而不是 RabbitMQ worker | 先完成主链路闭环，再引入更重的调度基础设施 |
| `judge_jobs.status` 表达流程结果，`verdict` 表达判题结论 | 区分“评测执行成功但答案错误”和“评测基础设施失败” |

## 验证路径

- `bash ./mvnw -Dtest=JudgeIntegrationTests test`
- `bash ./mvnw -Dtest=SubmissionIntegrationTests,AssignmentIntegrationTests test`
- `bash ./mvnw clean verify`

## 完成结果

- 已支持配置作业级 Python3 文本自动评测
- 已支持正式提交后异步调用 go-judge 并回写 verdict、得分、日志摘要、资源指标和错误信息
- 已支持教师手动重新评测
- 已同步架构、规格、数据库和 todo 文档
