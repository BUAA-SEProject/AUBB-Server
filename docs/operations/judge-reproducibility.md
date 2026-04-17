# Judge 可复现性与归档策略

## 当前归档内容

正式评测当前会归档：

- `detail_report`
- `source_snapshot`
- `artifact_manifest`
- `artifact_trace_json`

## 可复现性基线

`executionMetadata` 需要固定以下关键字段：

- `programmingLanguage`
- `entryFilePath`
- `sourceFiles / supportFiles`
- `compileArgs / runArgs`
- `executionEnvironment.languageVersion`
- `timeLimitMs / memoryLimitMb / outputLimitKb`
- `submissionId / assignmentId / assignmentQuestionId / submissionAnswerId`

结构化编程题的可复用编译产物包固定文件名为 `_aubb_compiled_bundle.b64`。

## 归档策略

1. 完整报告优先写入对象存储，数据库只保留摘要与指针。
2. `artifact_manifest` 作为审计入口。
3. 静态 OpenAPI 产物只作为发布辅助，不替代运行时 `/v3/api-docs`。
4. 每个发布批次至少保留一份 `staging` 与 `uat` 的回放样例。
