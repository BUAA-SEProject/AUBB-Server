# 2026-04-17 首个学校 / 管理员 bootstrap 初始化闭环

## 目标

在不引入额外外部初始化系统的前提下，为空库环境补齐可重复执行的首个学校、学校管理员和必要平台配置初始化入口。

## 范围

- `identityaccess / organization / platformconfig` 相关初始化逻辑
- 启动期 bootstrap 配置与入口
- 必要的 schema 约束补充
- 配置校验测试、启动式集成测试和文档同步

## 非目标

- 不引入独立 installer、外部脚本引擎或单独初始化服务
- 不把平台级 bootstrap 扩展成课程 / 学期 / 题库全量初始化
- 不改造现有平台治理 HTTP API 为匿名 bootstrap API

## 根因

1. 当前所有平台治理入口都要求先有 `SCHOOL_ADMIN`，新环境无法通过现有管理 API 自举第一位管理员。
2. 代码库缺少业务级 `CommandLineRunner / ApplicationRunner / seed` 入口，文档已写“部署初始化或种子数据”，但实现仍为空白。
3. 现有 `org_units` 只有应用层根节点校验，没有数据库层单一学校根节点保护，不利于首个学校 bootstrap 的幂等与并发安全。

## 最小实现方案

1. 新增默认关闭的 bootstrap 配置 `aubb.bootstrap.*`
2. 以 `ApplicationRunner` 作为启动期入口，启用时 fail-fast 校验必填配置
3. 新增幂等 bootstrap application service：
   - 先创建或复用唯一学校根节点
   - 再创建或复用首个学校管理员
   - 最后创建或更新单份平台配置
4. 重复执行默认不重置现有管理员密码，但会补齐缺失的学校管理员身份和必要平台配置
5. 视需要为单一学校根节点补约束 migration

## 实施结果

1. 已新增 `PlatformBootstrapProperties`、`PlatformBootstrapConfiguration` 和 `PlatformBootstrapApplicationService`
2. 已补齐首个学校根节点、学校管理员、`SCHOOL_ADMIN` 作用域角色、学工画像和平台配置初始化
3. 已固定幂等语义为“只创建缺失项，不重置既有管理员密码，不覆盖既有平台配置”
4. 已新增 `V24__single_school_root_guard.sql`，为单一学校根节点补齐数据库层保护
5. 已扩展平台治理路径，显式拒绝创建第二个学校根节点

## 验证结果

- `bash ./mvnw spotless:apply`
- `git diff --check`
- `bash ./mvnw -Dtest=PlatformBootstrapPropertiesValidationTests,BootstrapInitializationIntegrationTests test`
- `bash ./mvnw -Dtest=PlatformBootstrapPropertiesValidationTests,BootstrapInitializationIntegrationTests,PlatformGovernanceApiIntegrationTests,AubbServerApplicationTests,HarnessHealthSmokeTests test`
- 结果：`BUILD SUCCESS`，定向 `19` 个测试通过

## 风险

- 如果启动期开关误开，可能在非空环境触发不期望的初始化动作，因此配置必须默认关闭。
- 并发多实例首次启动时，需要避免写出第二个学校根节点或重复管理员角色。
- 如果复用已有管理员时覆盖过多字段，容易造成“重复执行可用，但副作用过大”。

## 验证路径

- `bash ./mvnw -Dtest=BootstrapPropertiesValidationTests,BootstrapInitializationIntegrationTests test`
- 提交前执行 `bash ./mvnw spotless:apply`
