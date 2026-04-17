# 对象存储

## 目标

为 AUBB-Server 提供一个可复用的共享对象存储基础设施，使后续 submission 附件、实验工程快照、评测产物等能力都能在统一接口上接入 MinIO，而不是在各业务模块里重复直连 SDK。

## 当前实现

- 运行时提供 `aubb.storage.minio.*` 配置。
- 默认关闭 MinIO 集成；只有显式设置 `AUBB_MINIO_ENABLED=true` 才会创建对象存储 Bean。
- 启用后会注册共享服务 `ObjectStorageService`，当前由 `MinioObjectStorageService` 实现。
- `submission` 模块已经开始消费该共享服务，并通过 `submission_artifacts` 显式保存业务元数据。
- `judge` 模块当前也开始消费该共享服务，用于保存正式评测详细报告和样例试运行的详细报告 / 源码快照。
- 当前共享服务支持：
  - 直接上传对象
  - 读取对象
  - 删除对象
  - 判断对象是否存在
  - 生成预签名 `GET` / `PUT` 链接
- 可选开启 `AUBB_MINIO_AUTO_CREATE_BUCKET=true`，在启动时自动创建 bucket。
- Actuator 会暴露 `minioStorage` 健康检查组件；当 MinIO 已启用时，bucket 不存在或不可访问会反映到整体健康状态。

## 配置项

| 配置项 | 环境变量 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `aubb.storage.minio.enabled` | `AUBB_MINIO_ENABLED` | `false` | 是否启用 MinIO 集成 |
| `aubb.storage.minio.endpoint` | `AUBB_MINIO_ENDPOINT` | `http://localhost:9000` | MinIO/S3 兼容入口 |
| `aubb.storage.minio.access-key` | `AUBB_MINIO_ACCESS_KEY` | 空 | 访问账号 |
| `aubb.storage.minio.secret-key` | `AUBB_MINIO_SECRET_KEY` | 空 | 访问密钥 |
| `aubb.storage.minio.bucket` | `AUBB_MINIO_BUCKET` | `aubb-assets` | 默认业务 bucket |
| `aubb.storage.minio.auto-create-bucket` | `AUBB_MINIO_AUTO_CREATE_BUCKET` | `false` | 启动时自动建 bucket |

## 本地运行

仓库根目录 `compose.yaml` 已加入 MinIO，本地默认使用：

- API 端口：`9000`
- Console 端口：`9001`
- 默认开发账号：`aubbminio`
- 默认开发密码：`aubbminio-secret`

本地启用示例：

```bash
export AUBB_MINIO_ENABLED=true
export AUBB_MINIO_ACCESS_KEY=aubbminio
export AUBB_MINIO_SECRET_KEY=aubbminio-secret
export AUBB_MINIO_BUCKET=aubb-assets
export AUBB_MINIO_AUTO_CREATE_BUCKET=true
bash ./mvnw spring-boot:run
```

## 代码入口

- 配置：`src/main/java/com/aubb/server/config/MinioStorageConfiguration.java`
- 配置属性：`src/main/java/com/aubb/server/config/MinioStorageProperties.java`
- 共享接口：`src/main/java/com/aubb/server/common/storage/ObjectStorageService.java`
- MinIO 实现：`src/main/java/com/aubb/server/common/storage/MinioObjectStorageService.java`

## 当前实现边界

- 当前没有“通用匿名上传 API”；附件上传只通过业务模块暴露，并必须显式做授权。
- `submission` 当前已落地附件元数据表与上传/下载接口；其他业务模块接入时仍应在自己的领域模型中显式落元数据。
- `judge` 当前约定对象 key：
  - `judge-jobs/{judgeJobId}/detail-report.json`
  - `programming-sample-runs/{sampleRunId}/detail-report.json`
  - `programming-sample-runs/{sampleRunId}/source-snapshot.json`
- 当前默认 bucket 是单一 bucket，后续是否拆分为 `submission-artifacts`、`judge-outputs` 等专用 bucket，再由具体业务决定。
- 当前预签名 URL 仍保留在共享服务中，但 `submission` 下载先走服务端鉴权读取，不直接暴露预签名契约。
- judge 第一阶段当前只对象化详细报告、case outputs、运行日志和样例试运行源码快照；正式评测源码正文仍通过 `submission_answers` 与附件引用链复原，不额外复制大对象。
