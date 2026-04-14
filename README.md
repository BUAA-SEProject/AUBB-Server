# AUBB-Server

AUBB-Server is the backend baseline for the AUBB platform. It currently boots a Spring Boot 4 service with Java 25, actuator health checks, a security baseline, and infrastructure dependencies prepared for PostgreSQL, RabbitMQ, Redis, Flyway, and MyBatis-Plus.

The infrastructure stack is intentionally scaffolded before product modules exist. Database and Flyway auto-configuration are therefore deferred until the first real persistence slice lands, keeping the platform skeleton bootable and testable.

## Harness Engineering Baseline

This repository now includes a minimal harness-engineering system inspired by OpenAI's [Harness Engineering](https://openai.com/zh-Hans-CN/index/harness-engineering/) article.

- Agent workflow: [AGENTS.md](AGENTS.md)
- Architecture map: [ARCHITECTURE.md](ARCHITECTURE.md)
- Docs index: [docs/index.md](docs/index.md)
- Project skills: [docs/project-skills.md](docs/project-skills.md)
- Design rules: [docs/design-docs/index.md](docs/design-docs/index.md)
- Product baseline: [docs/product-specs/index.md](docs/product-specs/index.md)
- Quality dashboard: [docs/quality-score.md](docs/quality-score.md)
- Completed bootstrap plan: [docs/exec-plans/completed/2026-04-14-harness-engineering-bootstrap.md](docs/exec-plans/completed/2026-04-14-harness-engineering-bootstrap.md)

## Local Validation

- Windows: `.\mvnw.cmd verify`
- Linux/macOS: `./mvnw verify`

The harness validates:

- Spring context startup
- public `/actuator/health`
- repository knowledge-base structure
- local markdown links inside harness docs

## Current Scope

See [docs/product-sense.md](docs/product-sense.md), [docs/reliability.md](docs/reliability.md), and [docs/security.md](docs/security.md) for the current operating baseline and constraints.

The project-level skill inventory and capability coverage live in [docs/project-skills.md](docs/project-skills.md).
