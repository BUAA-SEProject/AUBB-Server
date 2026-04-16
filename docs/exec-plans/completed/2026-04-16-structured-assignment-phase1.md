# 2026-04-16 结构化作业与题库第一阶段

## 目标

把当前“单体作业 + 整份提交 + 作业级 judge”推进到可承载完整作业系统的第一阶段底座。当前轮次聚焦：

- 题库最小管理
- 结构化试卷快照
- 分题提交骨架
- 客观题自动判分第一切片

不在本轮范围：

- 人工批改完整链路
- 成绩发布与成绩册
- 题目级 go-judge 执行
- 在线 IDE / 工作区 / 样例试运行完整实现

## 范围

### 代码范围

- `modules.assignment`
- `modules.submission`
- 必要的 `modules.audit`
- Flyway migration

### 文档范围

- `todo.md`
- `docs/product-specs/assignment-system.md`
- `docs/product-specs/submission-system.md`
- `docs/product-specs/judge-system.md`
- `docs/generated/db-schema.md`
- `ARCHITECTURE.md`

## 数据模型决策

1. 题库和已发布作业快照分离建模。
2. `assignments` 继续只保留作业头信息，不把整份题目结构塞进单列文本或 assignment JSON。
3. `submissions` 继续保留“整份提交头 + attempt 版本语义”；分题答案新增子表。
4. 单选 / 多选题自动判分在应用内完成，不引入 go-judge。
5. 编程题先落配置和答案模型，题目级自动评测留待下一阶段。

## 风险

- 结构化作业上线后，旧版 submission API 必须对新型作业做正确兼容或显式限制。
- 学生可见的 assignment / submission 详情不能暴露正确答案。
- 当前 judge 仍是 assignment 级模型，本轮不能误把题目级编程判题做成半成品耦合。

## 验证路径

- 题库建题 / 查题 / 列表集成测试
- 结构化作业创建与详情读取集成测试
- 结构化分题提交与客观题自动评分集成测试
- `./mvnw spotless:apply`
- `./mvnw clean verify`

## 决策记录

- 使用 `planning-with-files` 保持多阶段上下文和错误记录。
- 使用 `springboot-patterns` 维持模块优先、层内分层结构。
- 使用 `springboot-tdd` 先补失败测试，再做最小实现。
- 使用 `postgresql-table-design` 设计新增表、索引和约束。
- 使用 `api-design-principles` 保持对现有 REST 契约的追加式演进。
