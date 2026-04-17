# 2026-04-16 编程题运行时与试运行第二阶段

## 目标

在 question-level judge 第一阶段基础上，继续补齐结构化编程题的样例试运行、在线 IDE / 工作区和 `CUSTOM_SCRIPT` 真实执行能力。

## 范围

### 代码范围

- `modules.assignment`
- `modules.submission`
- `modules.judge`
- 共享对象存储与 go-judge 运行时配置

### 文档范围

- `todo.md`
- `docs/product-specs/{assignment,submission,judge}-system.md`
- `docs/generated/db-schema.md`
- `README.md`
- `ARCHITECTURE.md`

## 当前已具备的前置能力

1. 结构化编程题已具备题目级隐藏测试点、资源限制和语言配置。
2. 结构化提交已能按 `submission_answer_id` 自动创建题目级评测作业。
3. `judge_jobs` 已具备逐测试点摘要回写位，`submission_answers` 已能承接自动评测得分。

## 下一阶段重点

### Phase 1：样例试运行

- [x] 增加学生侧样例试运行 API
- [x] 不占用正式提交次数
- [x] 与正式评测作业分开建模为 `programming_sample_runs`

### Phase 2：在线 IDE / 工作区

- [x] 设计代码正文与附件的统一工作区视图
- [x] 支持多文件工程编辑与回填（当前为“代码正文 + 附件引用”最小模型）
- [x] 为前端提供最小可接手的读写接口

### Phase 3：`CUSTOM_SCRIPT` 执行

- [x] 定义脚本输入输出约定
- [x] 区分标准输出对比与自定义判定结果
- [x] 评估日志与产物留存策略

## 风险

- 若把样例试运行与正式评测混为一体，会污染成绩与审计语义。
- 若过早把工作区持久化为复杂文件树模型，会拖慢正式提交闭环演进。
- `CUSTOM_SCRIPT` 当前已改为固定 Python checker + 保留文件名上下文，后续仍需继续评估更复杂脚本能力的安全边界。

## 验证路径

- 先补试运行专项集成测试
- 再补工作区与代码装配测试
- 最后执行 `bash ./mvnw clean verify`

## 决策记录

- 当前第二阶段优先做“试运行和工作区最小闭环”，而不是直接做成绩册统计。
- `CUSTOM_SCRIPT` 真实执行要建立在试运行 / 代码装配稳定之后，避免一次性引入过多变量。
- 试运行和正式评测拆成两类记录：正式评测继续使用 `judge_jobs`，样例试运行单独落 `programming_sample_runs`。
- `CUSTOM_SCRIPT` 当前固定解释为“脚本内容”，由平台写成 Python checker 执行，不支持教师直接提供命令串。
