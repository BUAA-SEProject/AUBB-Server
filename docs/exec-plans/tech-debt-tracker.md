# 技术债跟踪

## 当前待处理项

- 当前稳定接口已经通过运行时 OpenAPI 和 `docs/stable-api.md` 收敛，但还没有独立静态 OpenAPI 产物生成链路。
- judge 详细产物对象化当前仍处于第一阶段，旧 JSON / 文本列继续保留为兼容回退，后续仍需评估回填与清理计划。
- 非热点列表仍可能保留内存过滤或 Java 侧分页；优先级 9 只处理了 `listUsers` 与 `listMyAssignments`。
- 通知中心当前只覆盖轮询式站内通知，不包含 WebSocket、邮件、短信或更复杂的 fan-out / dead-letter 治理。
- 在线 IDE、题库组卷第二阶段、多语言复杂工程布局稳定化和更复杂总评策略仍是当前主线增量开发项。
