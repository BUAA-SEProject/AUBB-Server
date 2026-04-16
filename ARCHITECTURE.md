# 架构

## 运行时基线

AUBB-Server 是一个运行在 Java 25 上的 Spring Boot 4 后端服务。当前基线包含 Spring Security、Actuator、Web MVC、WebSocket、Flyway、MyBatis-Plus、PostgreSQL、RabbitMQ、Redis、MinIO 兼容对象存储、Prometheus 指标以及 OpenAPI 支持。

## 当前状态

仓库当前已经从平台治理推进到课程、assignment、submission、grading 与 judge 的首个真实教学切片，当前已收敛为：

- JWT Bearer Token 认证
- 学校 / 学院 / 课程 / 班级四层组织
- 按组织作用域分配的多身份管理员模型
- 用户列表与详情查询能力
- 用户教务画像与组织成员关系建模
- 课程模板、开课实例、教学班、课程成员与班级功能开关
- 作业主数据、课程公共作业 / 教学班专属作业、作业状态流转
- 开课实例内题库、结构化试卷快照、五种题型建模与作业详情聚合
- 正式提交受理、分题答案、客观题自动评分摘要、学生自查提交、教师按作业查看提交与附件下载
- 教师 / 助教人工批改、assignment 级成绩发布、学生侧人工评分可见性控制
- 教师侧成绩册第一阶段：开课实例 / 教学班 / 单学生视图，默认按最新正式提交聚合
- assignment 级脚本型自动评测配置（当前支持 Python3 文本提交）
- 评测作业自动入队、AFTER_COMMIT 异步执行、结果回写、学生/教师按提交查看评测作业、教师手动重排队
- 共享对象存储服务与 MinIO 预签名 URL 能力
- 单份即时生效的平台配置
- 基础审计与公开健康检查

## 包结构

当前仓库采用模块优先的模块化单体结构：业务代码进入 `com.aubb.server.modules.<module>`，模块内部再细分 `api / application / domain / infrastructure` 四层。某一层文件数增长并出现明显职责分化后，允许继续按职责细分子包，而不是把 `View`、`Command`、枚举、`Entity / Mapper` 长期平铺在同一目录。

测试代码与生产分层分开组织：跨模块和 HTTP 链路回归放在 `src/test/java/com/aubb/server/integration`，领域规则测试继续挂在 `src/test/java/com/aubb/server/modules/<module>/domain`。

当前已落地的首批模块如下：

- `modules.identityaccess`
  - 登录、JWT、当前用户
  - 用户创建、导入、查询、详情、状态管理
  - 教务画像与组织成员关系
  - 平台治理身份、密码策略、作用域授权
  - 当前已在层内细分为 `application/user/view|command|result`、`domain/<子场景>`、`infrastructure/<聚合>`
- `modules.course`
  - 学期、课程模板、开课实例
  - 跨学院共同管理映射
  - 教学班、课程成员、班级功能开关
  - 教师 / 助教 / 学生课程级权限
  - 当前已在层内细分为 `application/view|command|result`、`domain/<子场景>`、`infrastructure/<聚合>`
- `modules.assignment`
  - 作业创建、列表、详情
  - 草稿、发布、关闭状态流转
  - 题库管理、结构化试卷快照与多题型题面
  - 脚本型自动评测配置摘要
  - 我的作业聚合查询
- `modules.submission`
  - 正式提交受理
  - 分题答案与评分摘要
  - 提交附件上传、关联与下载
  - 我的提交列表与详情
  - 教师按作业查看提交
- `modules.grading`
  - 非客观题人工批改
  - assignment 级成绩发布
  - 学生侧人工评分可见性控制
  - 教师侧成绩册第一阶段
- `modules.judge`
  - 提交后的评测作业入队
  - go-judge 适配层与 AFTER_COMMIT 异步执行
  - 评测结果回写、评分摘要与错误留痕
  - 学生 / 教师查看评测作业
  - 教师重新排队
- `modules.organization`
  - 学校 / 学院 / 课程 / 班级组织树
  - 组织层级校验和组织摘要查询
- `modules.platformconfig`
  - 单份即时生效的平台配置读取与更新
- `modules.audit`
  - 审计记录写入与分页检索

共享顶层包如下：

- `config`：安全、持久化、序列化等跨模块框架配置
- `common`：分页、错误模型、请求上下文、对象存储等共享能力
- `infrastructure.persistence`：跨模块复用的持久化适配

## 基础设施边界

- PostgreSQL 是关系型事实来源
- Redis 保留为缓存与短生命周期协调状态扩展位，本切片不再承担登录态
- RabbitMQ 保留给后续异步流程
- MinIO 承接共享对象存储，当前由 `common.storage` 对外暴露统一接口
- go-judge 承接当前脚本型自动评测执行，运行时通过 `config.GoJudgeConfiguration` 装配
- Flyway 负责数据库模式演进
- MyBatis-Plus 负责主数据持久化

当前治理批次的核心表为：

- `platform_configs`
- `org_units`
- `users`
- `academic_profiles`
- `user_org_memberships`
- `user_scope_roles`
- `academic_terms`
- `course_catalogs`
- `course_offerings`
- `course_offering_college_maps`
- `teaching_classes`
- `course_members`
- `assignments`
- `question_bank_questions`
- `question_bank_question_options`
- `assignment_sections`
- `assignment_questions`
- `assignment_question_options`
- `assignment_judge_profiles`
- `assignment_judge_cases`
- `submissions`
- `submission_artifacts`
- `submission_answers`
- `grading`（逻辑模块，数据仍挂在 `assignments / submission_answers`）
- `judge_jobs`
- `audit_logs`

## 请求流向

请求先进入对应模块的 `api` 层，再沿模块内的 `application -> domain -> infrastructure` 向内流动。认证信息先由安全配置解出 JWT，再由模块应用层和领域层执行业务级授权与作用域校验。

## 认证与授权

- 认证方式：JWT Bearer Token，服务端签发，服务端无状态校验。
- 令牌内容：用户标识、账号状态、默认组织、画像快照、身份列表和授权所需的 authority。
- 平台治理身份：
  - `SCHOOL_ADMIN`
  - `COLLEGE_ADMIN`
  - `COURSE_ADMIN`
  - `CLASS_ADMIN`
- 身份分配模型：一个用户可拥有多个作用域身份，每个身份都绑定一个组织节点。
- 当前实现边界：
  - 学校管理员拥有平台配置与全局审计能力
  - 学院 / 课程 / 班级管理员只管理各自作用域内的组织和用户
  - 教师、助教、学生的课程权限由 `course_members` 在业务层独立建模

## 组织与治理模型

- 组织树固定为四层：`SCHOOL -> COLLEGE -> COURSE -> CLASS`
- 不再支持任意类型的自由树形扩展
- 教师在平台治理阶段等价于班级管理员身份
- 用户系统拆为三个层次：
  - 用户基础资料：登录标识、显示名、邮箱、默认归属组织、账号状态与生命周期字段
  - 教务画像：学号/工号、真实姓名、画像状态与身份类型，存放于 `academic_profiles`
  - 组织成员关系：课程/班级等业务成员归属，存放于 `user_org_memberships`
  - 平台治理身份：作用域管理员身份，存放于 `user_scope_roles`
  - 课程成员角色：教师 / 助教 / 学员等课程域角色，存放于 `course_members`
- 用户主组织与管理身份解耦：
  - `users.primary_org_unit_id` 表示默认归属
  - `user_org_memberships` 表示业务成员归属
  - `user_scope_roles` 表示实际治理权限来源

## 课程系统模型

- 课程模板：`course_catalogs`
- 开课实例：`course_offerings`
- 跨学院治理：`course_offering_college_maps`
- 教学班：`teaching_classes`
- 课程成员：`course_members`
- 作业：`assignments`
- 题库题目：`question_bank_questions`
- 作业试卷大题：`assignment_sections`
- 作业试卷题目：`assignment_questions`
- 提交：`submissions`
- 分题答案：`submission_answers`
- 提交附件：`submission_artifacts`
- 编程题工作区：`programming_workspaces`
- 批改与发布：继续挂接在 `assignments / submission_answers`
- 成绩册聚合：继续挂接在 `assignments / submissions / submission_answers / course_members`，当前不引入独立成绩表
- 评测作业：`judge_jobs`
- 样例试运行：`programming_sample_runs`
- 当前实现明确禁止学生自主选课；课程成员仅由教师批量添加或导入既有系统用户。
- assignment 当前除了作业主数据外，还提供题库和结构化试卷快照，以及 legacy assignment 级脚本型自动评测配置。
- submission 当前已支持正式提交头、附件、分题答案与客观题自动评分摘要；非客观题最终评分与可见性控制已下沉到 grading 模块。
- grading 当前已支持教师 / 助教人工批改、assignment 级成绩发布和学生侧人工评分掩码。
- grading 当前已支持教师侧成绩册第一阶段：
  - offering 级成绩册只对教师 / 管理员开放
  - class 级成绩册额外对具备班级责任的 TA 开放
  - 默认按每个学生每个作业最新正式提交聚合
  - 当前只覆盖结构化作业
- judge 当前已同时覆盖 legacy assignment 级脚本评测、结构化编程题题目级评测，以及样例试运行最小闭环：
  - legacy 作业继续走 `assignment_judge_profiles / assignment_judge_cases`
  - 结构化编程题通过 `assignment_questions.config_json` 挂隐藏测试点，并把 job 下沉到 `submission_answer_id`
  - 工作区状态通过 `programming_workspaces` 保存入口文件、目录树源码快照、语言和附件引用，并兼容 legacy `codeText`
  - 样例试运行通过 `programming_sample_runs` 独立建模，避免污染正式 `judge_jobs` 与成绩语义
  - `CUSTOM_SCRIPT` 当前通过固定 Python checker + 保留文件名上下文真实执行，不把教师输入当 shell 命令直接拼接执行
  - 前端目录树 IDE 交互、更完整的评测产物对象存储仍待后续阶段

## 平台配置模型

- 平台配置采用单份实时配置，不再存在草稿、发布、回退和历史版本
- 修改配置后立即成为当前生效配置
- 若后续需要历史追溯，应通过审计扩展或独立配置快照表实现，而不是恢复版本化工作流

## 审计与可观测性

- 关键动作必须在事务边界内写入审计记录，至少覆盖：
  - 登录成功 / 失败
  - 平台配置更新
  - 用户导入
  - 身份变更
  - 账号状态变更
  - 组织节点创建
- 请求链路保留 `requestId`
- `/actuator/health` 继续保持公开读取

## Harness 约束

- 架构变更必须同步更新 [docs/quality-score.md](docs/quality-score.md)
- 设计决策应记录到 [docs/design-docs](docs/design-docs/index.md)
- 较大改动应在 [docs/exec-plans](docs/exec-plans/active/README.md) 下留下执行轨迹
