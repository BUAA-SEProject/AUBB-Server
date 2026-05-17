# AUBB Server 后端

详细工作流规则见 `AGENTS.md`，系统架构见 `ARCHITECTURE.md`。

## 技术栈

- Spring Boot 4 + Java 25
- PostgreSQL + RabbitMQ + Redis + MinIO + go-judge
- 持久化：Flyway + MyBatis-Plus
- 安全：Spring Security（OAuth2 Resource Server）
- API 文档：SpringDoc OpenAPI（`/v3/api-docs`）

## 快速验证

```bash
bash ./mvnw test        # 快速：单元测试
bash ./mvnw verify      # 完整：含集成测试
```

## 关键约束

- 环境变量 `AUBB_JWT_SECRET` 必须设置
- 稳定 API 契约见 `docs/stable-api.md`
- 代码格式化：Spotless（Palantir Java format），提交前自动检查
- 模块化单体架构：`modules/<module>/{api,application,domain,infrastructure}/`
