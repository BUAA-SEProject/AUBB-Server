# 发现与决策

## 当前任务基线

- 当前模块级顶层结构是健康的，热点不在“模块拆分不够”，而在少数层内部文件过于平铺。
- 当前最拥挤的目录集中在：
  - `modules/course/application` 15 个文件
  - `modules/course/infrastructure` 12 个文件
  - `modules/course/domain` 12 个文件
  - `modules/identityaccess/domain` 11 个文件
  - `modules/identityaccess/application/user` 11 个文件
  - `modules/identityaccess/infrastructure` 10 个文件

## 接入建模结论

- `course` 模块的拥挤主要来自三类平铺：
  - `application` 中服务类和大量 `View/Command/Result` 记录类混放
  - `domain` 中多个子场景的枚举全部平铺
  - `infrastructure` 中多个聚合的 `Entity/Mapper` 对平铺
- `identityaccess` 模块的拥挤模式类似，尤其是 `application/user`、`domain` 和 `infrastructure`。
- 这类热点适合做“层内再分组”，例如：
  - `application/view`、`application/command`、`application/result`
  - `domain/account`、`domain/membership`、`domain/profile`、`domain/governance`
  - `infrastructure/user`、`infrastructure/profile`、`infrastructure/role`
- `config`、`common/storage`、测试目录当前文件数可接受，不需要为了凑整继续拆。

## 待特别验证的规则

- 拆分后仍需保持现有模块边界和职责语义清晰。
- 目录优化不能让 import 关系更混乱，不能制造循环依赖。
- 目录说明文档需要同步到“层内允许按职责细分子包”。

## 本轮落地结果

- `course/application` 仅保留应用服务，记录类拆到：
  - `application/view`
  - `application/command`
  - `application/result`
- `course/domain` 按子场景拆到：
  - `term`
  - `catalog`
  - `offering`
  - `member`
  - `teaching`
- `course/infrastructure` 按聚合拆到：
  - `term`
  - `catalog`
  - `offering`
  - `member`
  - `teaching`
- `identityaccess/application/user` 仅保留应用服务，记录类拆到：
  - `view`
  - `command`
  - `result`
- `identityaccess/domain` 按子场景拆到：
  - `account`
  - `profile`
  - `governance`
  - `membership`
- `identityaccess/infrastructure` 按聚合拆到：
  - `user`
  - `profile`
  - `membership`
  - `role`

## 新的仓库约束

- 对已经出现目录拥挤的模块，不再允许把大量 `View / Command / Result` 继续直接平铺回顶层 `application`。
- 对已经拆过的 `domain` 和 `infrastructure` 层，不再允许把多个子场景或聚合的类型重新塞回层根目录。
- 使用 `RepositoryStructureTests` 固化上述约束，避免目录退化。

## 后续建议

- 新增业务模块先从四层结构起步，不要一开始就过度细分。
- 当某层直接文件数明显升高并出现职责分化时，再增量拆出职责子目录。
- 继续优先沿用当前 `course` 和 `identityaccess` 的细分方式，降低全仓目录风格漂移。
