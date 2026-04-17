# 环境与发布运行手册

## 环境分层

- `staging`
  - 接近生产依赖拓扑，开启 OpenAPI 与更宽松的演练入口。
  - 用于联调、压测、监控与发布演练。
- `uat`
  - 关闭 Swagger / API docs，仅保留验收流量所需功能。
  - 用于业务回归、验收签字和回滚预演。
- `production`
  - 仅接受版本化镜像发布与回滚，不做 bootstrap。

模板文件：

- [deploy/.env.staging.example](../../deploy/.env.staging.example)
- [deploy/.env.uat.example](../../deploy/.env.uat.example)
- [deploy/.env.production.example](../../deploy/.env.production.example)

## 发布前检查

1. 渲染环境文件并确认 `AUBB_APP_IMAGE` 为版本化 tag。
2. 执行 `bash ops/preflight/check-runtime-deps.sh`。
3. 执行 `bash ops/release/backup-db.sh`。
4. 检查监控与日志采集是否正常。

## 标准发布流程

1. 触发 `.github/workflows/deploy.yml`。
2. 选择 `deploy / drill / rollback` 与目标环境。
3. 工作流会执行：
   - 运行时依赖预检
   - `docker compose pull app judge-worker`
   - `docker compose up -d app judge-worker`
   - readiness 检查
   - `bash ops/release/release-drill.sh`
   - `bash ops/openapi/export-static.sh`

## 备份与恢复

```bash
bash ops/release/backup-db.sh
AUBB_RESTORE_CONFIRM=I_UNDERSTAND bash ops/release/restore-db.sh backups/aubb-<timestamp>.sql.gz
```

## 回滚

优先通过 deploy workflow 重放上一个稳定镜像；如需主机手工回滚：

```bash
bash ops/release/rollback-release.sh ghcr.io/<owner>/<repo>:sha-previous
```

## 发布演练清单

1. 备份数据库。
2. 发布目标镜像。
3. 验证登录、课程主链路、judge 链路。
4. 导出静态 OpenAPI 产物。
5. 用上一版本镜像执行一次回滚。
