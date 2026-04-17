# AUBB 性能压测说明

本目录提供面向 AUBB 后端核心链路的压测脚本与建议场景，重点覆盖：

- 登录鉴权
- 我的课程列表
- 我的作业列表
- 我的提交列表
- 评测结果轮询
- 成绩查询
- 通知未读数

## 前置准备

1. 准备一套真实或准真实测试数据：
   - 至少 1 个管理员、1 个教师、200+ 学生
   - 每门课至少 2 个教学班
   - 每门课至少 10 个作业
   - 每个学生至少有 5 条提交与 3 条评测任务
2. 准备压测账号：
   - `studentUsername` / `studentPassword`
   - `teacherUsername` / `teacherPassword`
3. 记录要压测的目标资源：
   - `offeringId`
   - `assignmentId`
   - `submissionId`
   - `judgeJobId`
4. 安装 `k6`

## 运行方式

```bash
BASE_URL=http://localhost:8080 \
STUDENT_USERNAME=student-a \
STUDENT_PASSWORD=student-pass \
TEACHER_USERNAME=teacher-a \
TEACHER_PASSWORD=teacher-pass \
OFFERING_ID=1 \
ASSIGNMENT_ID=1 \
SUBMISSION_ID=1 \
JUDGE_JOB_ID=1 \
k6 run ops/perf/k6-core-apis.js
```

如果本机没有 `k6`，可以直接使用仓库内置的 Python 脚本完成“造数 + 压测 + 采样”：

```bash
BASE_URL=http://localhost:18084 \
ADMIN_USERNAME=perf-admin \
ADMIN_PASSWORD=PerfAdmin!123 \
python3 ops/perf/setup_perf_data.py

PERF_MANIFEST=/tmp/aubb-perf-20260418/manifest.json \
COMPOSE_PREFIX=aubb-perf-20260418 \
python3 ops/perf/run_perf_suite.py
```

其中：

- `setup_perf_data.py` 会创建学院、教师、学生、课程、教学班、客观题作业、编程作业，并写出 `manifest.json`
- `run_perf_suite.py` 会基于 `manifest.json` 运行阶梯压测，并输出 `perf_results.json` 与 `resource_samples.json`

## 建议压测场景

### 场景 1：课程查询高峰

- 目标：模拟大量学生同时查看课程、作业和通知
- 压测接口：
  - `POST /api/v1/auth/login`
  - `GET /api/v1/me/courses`
  - `GET /api/v1/me/assignments?offeringId={offeringId}`
  - `GET /api/v1/me/notifications/unread-count`
- 建议并发：
  - 稳态 1000 VUs 起步
  - 峰值按 3000 到 4000 RPS 做阶梯增长

### 场景 2：提交后评测轮询高峰

- 目标：模拟学生提交后频繁查看提交记录和评测结果
- 压测接口：
  - `GET /api/v1/me/assignments/{assignmentId}/submissions`
  - `GET /api/v1/me/submissions/{submissionId}/judge-jobs`
  - `GET /api/v1/me/judge-jobs/{judgeJobId}/report`
- 风险观察：
  - 数据库连接池占用
  - JWT 鉴权链路 QPS
  - 评测结果查询 RT 与 P99

### 场景 3：教师批量查看

- 目标：模拟教师高频查看作业、提交与成绩册
- 压测接口：
  - `GET /api/v1/teacher/course-offerings/{offeringId}/assignments`
  - `GET /api/v1/teacher/assignments/{assignmentId}/submissions`
  - `GET /api/v1/teacher/course-offerings/{offeringId}/gradebook`

## 关键指标

- 吞吐量：RPS
- 平均响应时间：Avg
- 长尾延迟：P95 / P99
- 错误率：HTTP 4xx / 5xx 占比
- 资源占用：
  - App CPU / 内存
  - PostgreSQL CPU / 活跃连接数
  - Redis CPU / ops
  - RabbitMQ 队列深度（评测场景）

## 建议阈值

- 登录：P95 < 250ms，错误率 < 0.5%
- 我的课程 / 我的作业：P95 < 200ms
- 我的提交 / 评测轮询：P95 < 300ms
- 成绩查询：P95 < 300ms
- 5xx：< 0.1%

## 优化前后对比方式

1. 同一批数据、同一部署规格、同一压测脚本
2. 先跑预热 3 到 5 分钟
3. 再跑稳态 10 分钟
4. 记录优化前后：
   - RPS
   - Avg / P95 / P99
   - DB 活跃连接数
   - JVM 堆使用与 Full GC 次数
5. 至少保留一份原始压测结果 JSON
