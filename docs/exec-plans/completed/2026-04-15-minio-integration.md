# 执行计划：MinIO 对象存储接入

## 目标

将 MinIO 接入当前仓库的运行时和测试体系，形成可复用的对象存储共享能力。

## 范围

- MinIO Java SDK 依赖与配置
- 对象存储共享服务接口与实现
- bucket 初始化和健康检查
- `compose.yaml` 本地开发依赖
- MinIO 集成测试
- 架构、可靠性、安全和对象存储文档同步

## 不在范围

- submission 文件上传 API
- 工程快照、附件索引和数据库元数据设计
- judge / grading 对象存储消费逻辑

## 风险

- 若默认启用 MinIO，可能破坏当前无对象存储环境下的启动体验。
- 若只补运行时配置、不补测试路径，后续文件上传会缺乏稳定回归。

## 验证路径

- MinIO 集成测试：对象上传、下载、删除、预签名 URL、健康检查
- `./mvnw spotless:apply`
- `./mvnw clean verify`

## 完成结果

- 已新增共享对象存储服务、MinIO 条件装配、bucket 自动创建和健康检查
- 已新增本地 compose 依赖和 MinIO 集成测试
- 已同步 README、架构、可靠性、安全、仓库结构和对象存储文档
- 已通过 `./mvnw spotless:apply`
- 已通过 `./mvnw -Dtest=MinioStorageIntegrationTests test`
- 已通过 `./mvnw clean verify`

## 决策记录

| 决策 | 原因 |
|------|------|
| MinIO 默认关闭 | 避免把新基础设施变成默认阻塞依赖 |
| 共享服务先落 `common/storage` | 为多个业务模块复用留出稳定入口 |
| compose 与 Testcontainers 同时接入 | 同时覆盖本地开发和自动化验证 |
