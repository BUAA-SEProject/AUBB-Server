# 执行计划：代码目录结构优化

## 目标

检查当前仓库代码目录结构，识别文件过于密集的目录，在不改变模块边界和业务语义的前提下优化目录编排，让热点目录更易阅读、跳转和继续开发。

## 范围

- `src/main/java/com/aubb/server/modules/course`
- `src/main/java/com/aubb/server/modules/identityaccess`
- 与目录结构直接相关的测试和说明文档

## 不在范围

- 新增业务能力
- 改动 API 路径或数据库结构
- 重命名现有业务模块
- 把单仓应用拆成 Maven 多模块

## 调整原则

1. 保持 `modules.<module>.api / application / domain / infrastructure` 大边界不变。
2. 只对真正拥挤的目录做层内细分，不为了形式统一制造额外层级。
3. 优先按职责拆分 `View / Command / Result`、领域子场景、`Entity / Mapper` 聚合。
4. 通过结构测试和文档说明固化结果，避免目录很快回退到平铺状态。

## 风险

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 文件移动后包声明和 import 大量失效 | 编译中断 | 先批量改包名，再用编译器兜底修正显式 import |
| 只改目录不改文档 | 后续接手者继续按旧习惯平铺 | 同步更新架构和仓库结构说明 |
| 目录重排后缺少约束 | 结构很快退化 | 在 `RepositoryStructureTests` 中加入直接文件数与子目录约束 |

## 验证路径

- `./mvnw spotless:apply`
- `./mvnw clean verify`

## 完成记录

- 2026-04-15：审查 `src/main/java` 的目录密度，确认热点集中在 `course` 与 `identityaccess` 的层内平铺目录。
- 2026-04-15：将 `course` 的 `application`、`domain`、`infrastructure` 按职责和聚合拆分为更细子目录。
- 2026-04-15：将 `identityaccess` 的 `application/user`、`domain`、`infrastructure` 按职责和聚合拆分为更细子目录。
- 2026-04-15：批量修正包声明与 import，并补齐应用服务对新子包类型的显式 import。
- 2026-04-15：更新 `ARCHITECTURE.md`、`docs/repository-structure.md` 和 `RepositoryStructureTests`，将本次目录优化沉淀为仓库约束。

## 结果说明

本次优化没有改变业务模块边界，但显著降低了热点目录的平铺密度。后续开发应继续沿用“模块优先，层内按职责增量细分”的方式，而不是把记录类、枚举和 `Entity / Mapper` 重新堆回层根目录。
