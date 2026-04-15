# 项目 Skills

## 目的

本仓库在 `.agents/skills/` 下使用项目级 skills，用来强化 Spring Boot 开发、harness 工程、计划管理、验证流程与文档编写等可重复工程工作。

## 已安装的项目级 Skills

| Skill                              | 主要用途                                                         |
| ---------------------------------- | ---------------------------------------------------------------- |
| `springboot-patterns`              | Spring Boot 服务架构、分层组织、API 实现和后端编码模式           |
| `springboot-tdd`                   | 基于 JUnit、Mockito、MockMvc、Testcontainers 的测试驱动开发      |
| `springboot-security`              | Spring Security 默认策略、认证授权设计、安全端点模式和权限关注点 |
| `springboot-verification`          | 构建、测试、验证闭环与发布前检查                                 |
| `agent-harness-construction`       | 面向 harness 的仓库结构、评估循环和代理支持系统设计              |
| `planning-with-files`              | 基于 Markdown 文件的多步骤开发计划管理                           |
| `architecture-decision-records`    | 将架构决策与权衡记录为仓库资产                                   |
| `postgresql-table-design`          | PostgreSQL 表结构与模式设计指导                                  |
| `api-design-principles`            | API 契约、资源建模与接口设计指导                                 |
| `documentation-writer`             | 项目文档、harness 文档和技术说明编写                             |
| `github-actions-templates`         | GitHub Actions 工作流编写与 CI 脚手架                            |
| `gh-fix-ci`                        | 基于 GitHub CLI 排查和修复 GitHub Actions / PR 检查失败          |
| `prometheus-configuration`         | Prometheus 监控与指标采集配置指导                                |
| `benchmark`                        | 性能基线建立与基准测试流程                                       |
| `write-coding-standards-from-file` | 从仓库现有实现风格归纳编码规范                                   |
| `tech-debt`                        | 技术债识别、跟踪与优先级管理                                     |

## 能力覆盖矩阵

| 需求                     | 覆盖方式                                                                              |
| ------------------------ | ------------------------------------------------------------------------------------- |
| 设计项目架构             | `springboot-patterns`、`architecture-decision-records`、`agent-harness-construction`  |
| 规划开发工作             | `planning-with-files`、`architecture-decision-records`                                |
| 设计数据库结构           | `postgresql-table-design`、`springboot-patterns`                                      |
| 编写项目与 harness 文档  | `documentation-writer`、`agent-harness-construction`、`architecture-decision-records` |
| 编写项目代码             | `springboot-patterns`                                                                 |
| 测试驱动开发             | `springboot-tdd`                                                                      |
| 测试可靠性与边界         | `springboot-tdd`、`springboot-verification`、`benchmark`                              |
| 编写 API 文档            | `api-design-principles`、`documentation-writer`                                       |
| 调试 API                 | `springboot-patterns`、`springboot-verification`                                      |
| 配置与排查 CI/CD         | `github-actions-templates`、`gh-fix-ci`、`springboot-verification`                    |
| 建设监控与可观测体系     | `prometheus-configuration`，以及现有 Micrometer / Prometheus 基线                     |
| 调优性能与高可用         | `benchmark`、`springboot-patterns`、`prometheus-configuration`                        |
| 保护安全与权限治理       | `springboot-security`、`springboot-verification`                                      |
| 进行工程规范与技术债管理 | `write-coding-standards-from-file`、`tech-debt`、`architecture-decision-records`      |

## 推荐组合

1. 用户、认证、组织等后端功能开发：`springboot-tdd` + `springboot-patterns` + `springboot-security`
2. 多步骤中大型任务：`planning-with-files` + 对应领域 skill
3. 文档或规范重构：`documentation-writer` + `architecture-decision-records`
4. API 契约或接口评审：`api-design-principles` + `documentation-writer`
5. 发布前收口：`springboot-verification`
