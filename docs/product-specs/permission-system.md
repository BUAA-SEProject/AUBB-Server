# AUBB 权限系统改造 Product Spec

- **文档版本**：v1.0
- **文档状态**：可实施
- **适用范围**：AUBB（Academic Unified Builder Bench）一体化在线教学与实验平台
- **主要读者**：产品经理、后端工程师、前端工程师、架构师、测试工程师、运维工程师
- **目标**：提供一套完整、统一、可扩展、可审计的权限系统设计，支持学校—学院—课程—开课实体—班级多层级结构，以及教师、学生、助教、管理员等多身份、多作用域、多状态的复杂教学场景

---

# 1. 背景与问题陈述

AUBB 是一体化在线教学与实验平台，覆盖课程管理、作业与实验、自动评测、在线 IDE、成绩管理等核心功能。本文档仅保留“学校”作为一级组织术语。现有系统在组织层级、成员角色、跨层级授权、评测敏感信息隔离等方面逐步复杂化，原有简单角色模型难以满足以下需求：

1. 用户可能同时拥有多种身份  
   例如：
   - 教师同时是学院管理员
   - 某课程学生同时是另一课程助教
   - 同一用户在不同开课实体中分别担任教师、助教、学生

2. 权限需要按层级和作用域生效  
   例如：
   - 学校管理员能管理本校数据
   - 学院管理员只能管理本学院资源
   - 开课级助教可访问本次开课下所有班级
   - 班级助教只能访问指定班级

3. 很多权限不仅由角色决定，还由资源状态、用户与资源关系、时间窗口决定  
   例如：
   - 学生只能看自己成绩，且必须在成绩发布后
   - 已归档开课实体默认只读
   - 退课学生不能继续提交
   - 助教默认不能查看隐藏测试点和评测脚本

4. 需要保证敏感教学数据与系统操作的可追踪、可审计、可扩展

因此，需要将权限系统升级为一套标准化、可配置、可实现、可测试的完整模型。

---

# 2. 产品目标

## 2.1 目标

本次权限系统改造需要实现以下目标：

1. 支持 **多层级组织结构** 的作用域授权
2. 支持 **多身份并存** 且权限互不污染
3. 支持 **角色模板 + 作用域绑定 + 属性约束** 的混合授权模型
4. 支持 **教学敏感数据保护**
5. 支持 **统一权限判定入口**
6. 支持 **列表、详情、导出、批量操作** 的统一授权
7. 支持 **角色变更实时生效**
8. 支持 **操作审计**
9. 支持后续扩展到：
   - 题库系统
   - 竞赛系统
   - 分组协作
   - 实验资源隔离
   - 多租户/多学校部署

## 2.2 非目标

本次改造不包含：

1. SSO/统一认证协议本身的改造
2. OAuth/OIDC 作为对外开放平台的完整接入
3. 通用策略语言（如自定义 DSL）的完整实现
4. 复杂审批流引擎
5. 跨平台组织同步系统的完整重构

---

# 3. 设计原则

1. **默认拒绝**  
   所有资源访问默认拒绝，必须显式授权。

2. **最小权限原则**  
   用户仅拥有完成工作所必须的权限。

3. **作用域优先**  
   任何角色都必须绑定作用域，禁止无作用域泛授权（平台超级管理员除外）。

4. **向下继承，不向上提升**  
   学校级角色可覆盖本学校下级资源；班级级角色不能反向获得开课级或学院级权限。

5. **组织管理权限与教学敏感权限分离**  
   学校/学院管理员默认不自动获得成绩修改、隐藏测试查看、评测脚本查看等权限。

6. **读写分离**  
   只读权限与写权限分开设计，不因为能看就能改。

7. **敏感操作必须留痕**  
   改分、导出成绩、角色分配、查看隐藏测试等操作必须审计。

8. **统一授权入口**  
   所有后端资源访问必须经过统一权限判定逻辑，禁止 Controller/Handler 内散落大量手写 if/else。

---

# 4. 术语定义

| 术语                        | 定义                                               |
| --------------------------- | -------------------------------------------------- |
| 学校（School）              | 一级组织                                           |
| 学院（College）             | 隶属于学校的二级组织                               |
| 课程（Course）              | 稳定的课程定义，如“数据结构”                       |
| 开课实体（Course Offering） | 某门课程在某学年/学期开设的一次实例                |
| 班级（Class）               | 开课实体下的具体教学班                             |
| 作用域（Scope）             | 某个角色生效的组织或业务范围                       |
| 角色（Role）                | 一组权限点的集合模板                               |
| 权限点（Permission）        | 某资源上的某动作，如 `task.publish`                |
| 角色绑定（Role Binding）    | 用户在某作用域上拥有某角色                         |
| 属性约束（ABAC Rule）       | 基于资源状态、关系、时间窗口等的附加访问条件       |
| 敏感资源                    | 隐藏测试、评测脚本、成绩草稿、源代码全文等高敏数据 |
| 审计日志                    | 记录高风险操作的日志实体                           |

---

# 5. 系统范围与组织模型

## 5.1 组织层级

权限系统支持以下组织层级：

```text
school
  └── college
     └── course
        └── offering
           └── class
````

## 5.2 作用域类型

系统必须支持以下作用域类型：

* `school`
* `college`
* `course`
* `offering`
* `class`

## 5.3 继承规则

### 允许

* `school` 可覆盖学校域下全部资源
* `college` 可覆盖学院下全部课程/开课/班级
* `course` 可覆盖课程下全部开课/班级
* `offering` 可覆盖开课实体下全部班级
* `class` 仅覆盖本班

### 禁止

* `class` 不能提升到 `offering`
* `offering` 不能提升到 `college`
* `college` 不能跨学校

---

# 6. 用户与身份模型

## 6.1 用户

系统中的用户统一使用 `User` 实体。

用户自身不直接存储单一全局角色，而是通过角色绑定获得权限。

## 6.2 支持的用户身份类别

系统需支持以下业务身份：

* 学校管理员
* 学院管理员
* 课程管理者
* 开课负责人
* 开课教师
* 班级教师
* 开课助教
* 班级助教
* 学生
* 评测管理员
* 审计员
* 阅卷员（可选）

## 6.3 多身份规则

系统必须支持同一用户同时拥有多个身份，且满足：

1. 权限按并集生效
2. 各身份只在各自绑定作用域内生效
3. 一个身份不会自动扩展到另一个身份的作用域
4. 身份变更后权限应实时收敛或扩展

### 示例

* 用户 A 是 `school_admin@school:S1`
* 同时也是 `offering_teacher@offering:O100`
* 也可能是 `student@class:C203`

判定时应分别按作用域求并集，而非将其视为一个全局超级角色。

---

# 7. 授权模型

## 7.1 模型选型

采用混合授权模型：

* **RBAC**：角色定义能力边界
* **作用域绑定**：限定角色生效范围
* **ABAC**：处理状态、关系、时间窗口、敏感信息等条件

## 7.2 判定公式

授权结果 = 角色权限命中 × 作用域命中 × 属性约束命中 × 资源状态允许

## 7.3 授权流程

后端每次请求统一执行：

1. 识别用户
2. 识别目标资源
3. 解析资源所属层级链路
4. 查询用户在相关祖先作用域上的角色绑定
5. 聚合角色对应的权限点
6. 判断目标动作是否被授权
7. 执行属性约束校验
8. 返回允许/拒绝
9. 对敏感操作写审计日志

---

# 8. 角色设计

## 8.1 内建角色列表

| 角色代码               | 名称       | 说明                         |
| ---------------------- | ---------- | ---------------------------- |
| `school_admin`         | 学校管理员 | 管理本学校域内组织与教学配置 |
| `college_admin`        | 学院管理员 | 管理本学院课程与开课         |
| `course_manager`       | 课程管理者 | 管理课程模板级资源           |
| `offering_coordinator` | 开课负责人 | 管理某次开课整体运行         |
| `offering_teacher`     | 开课教师   | 管理该开课下全部班级         |
| `class_teacher`        | 班级教师   | 仅管理某个班级               |
| `offering_ta`          | 开课助教   | 协助管理开课下所有班级       |
| `class_ta`             | 班级助教   | 协助管理某个班级             |
| `student`              | 学生       | 参与课程学习与提交           |
| `judge_admin`          | 评测管理员 | 管理评测环境与敏感评测配置   |
| `auditor`              | 审计员     | 查看审计数据与高风险操作记录 |
| `grader`               | 阅卷员     | 专门用于批改与评分（可选）   |

## 8.2 禁止使用的设计

* 禁止 `user.role = teacher` 这种单字段模型
* 禁止通过 `is_admin`、`is_ta` 等简单布尔字段承载完整权限
* 禁止“全局教师”“全局助教”等无作用域业务角色

---

# 9. 权限点设计

## 9.1 命名规则

权限点统一采用：

```text
<resource>.<action>
```

例如：

* `task.read`
* `task.publish`
* `submission.grade`
* `grade.override`

## 9.2 资源分类

系统中权限至少覆盖以下资源域：

* 组织资源
* 成员资源
* 任务/作业资源
* 题目/题库资源
* 提交资源
* 成绩资源
* 评测资源
* IDE 资源
* 报表资源
* 公告与资源文件
* 申诉与审计资源

## 9.3 权限点清单

### 9.3.1 组织类

```text
school.read
school.manage

college.read
college.manage

course.read
course.manage

offering.read
offering.manage
offering.archive

class.read
class.manage
```

### 9.3.2 成员管理类

```text
member.read
member.manage
member.import
member.export
role_binding.read
role_binding.manage
```

### 9.3.3 任务与作业类

```text
task.read
task.create
task.edit
task.delete
task.publish
task.close
task.archive
```

### 9.3.4 题目与题库类

```text
question.read
question.create
question.edit
question.delete

question_bank.read
question_bank.manage
paper.generate
```

### 9.3.5 提交与批改类

```text
submission.read
submission.download
submission.grade
submission.regrade
submission.comment
submission.export
submission.read_source
```

### 9.3.6 成绩类

```text
grade.read
grade.export
grade.publish
grade.override
grade.import
ranking.read
```

### 9.3.7 IDE 与评测类

```text
ide.read
ide.save
ide.run
ide.submit

judge.run
judge.rejudge
judge.read_log
judge.config
judge.view_hidden
judge.manage_environment
```

### 9.3.8 公告与教学资源类

```text
announcement.read
announcement.publish

resource.read
resource.upload
resource.manage
```

### 9.3.9 报表与统计类

```text
report.read
report.export
analytics.read
```

### 9.3.10 申诉与审计类

```text
appeal.create
appeal.read
appeal.review

audit.read
audit.export
```

---

# 10. 角色默认权限模板

> 以下为系统默认模板，允许后续按产品配置调整，但应有一份稳定默认值。

## 10.2 school_admin

### 默认拥有

* 学校及下级组织读写
* 课程、开课、班级管理
* 成员导入导出
* 学校级统计与报表查看

### 默认不拥有

* `submission.read_source`
* `judge.view_hidden`
* `judge.config`
* `grade.override`

---

## 10.3 college_admin

### 默认拥有

* 学院及下级课程/开课/班级管理
* 学院成员与统计管理

### 默认不拥有

* 改分
* 查看隐藏测试
* 查看评测脚本
* 学生源代码全文查看

---

## 10.4 course_manager

### 默认拥有

* 课程定义维护
* 课程模板资源管理
* 题库/题型模板配置（可选）

### 默认不拥有

* 开课运行期数据访问
* 提交批改
* 成绩管理

---

## 10.5 offering_coordinator / offering_teacher

### 默认拥有

* 本开课全部班级的任务管理
* 提交查看
* 批改
* 成绩查看与发布
* 报表查看
* 公告发布
* 成员查看

### 可选拥有

* `grade.override`
* `submission.export`
* `report.export`

### 默认不拥有

* `judge.view_hidden`
* `judge.config`

---

## 10.6 class_teacher

### 默认拥有

* 本班任务与作业管理
* 本班提交查看与批改
* 本班成绩管理
* 本班公告与资源管理

### 默认不拥有

* 跨班访问
* 开课级统一发布
* 全开课成绩导出
* 隐藏测试查看

---

## 10.7 offering_ta

### 默认拥有

* 本开课全部班级的提交读取
* 本开课全部班级的批改
* 反馈评论
* 任务读取

### 可选拥有

* `task.create`
* `task.publish`
* `grade.export`

### 默认不拥有

* `grade.override`
* `judge.view_hidden`
* `judge.config`

---

## 10.8 class_ta

### 默认拥有

* 本班提交读取
* 本班批改
* 本班反馈

### 默认不拥有

* 兄弟班级访问
* 整门课成绩导出
* 改分
* 评测隐藏配置访问

---

## 10.9 student

### 默认拥有

* 读取已发布任务
* 使用 IDE 进行试运行与提交
* 查看自己的提交
* 查看自己的已发布成绩
* 发起申诉
* 查看可见教学资源

### 默认不拥有

* 查看他人提交/成绩
* 查看隐藏测试/评测脚本
* 查看未发布成绩
* 导出任何班级/课程数据

---

## 10.10 judge_admin

### 默认拥有

* 评测环境配置
* 语言与镜像配置
* 隐藏测试与 SPJ 管理
* 评测日志查看
* 重判权限

### 默认不拥有

* 组织结构管理
* 成绩发布
* 成员管理

---

## 10.11 auditor

### 默认拥有

* 审计日志查看
* 高风险操作记录导出
* 某些只读调查能力

### 默认不拥有

* 修改数据
* 改分
* 发布任务

---

# 11. 属性约束（ABAC）规则

本系统不采用完全自由的 ABAC 策略引擎，而是定义一组固定的、可实现的产品规则。

## 11.1 用户与资源关系约束

### 学生查看提交

允许条件：

* 用户是提交本人

### 学生查看成绩

允许条件：

* 用户是成绩本人
* 成绩状态为 `published`

### 教师/助教查看提交

允许条件：

* 用户对该提交所属 `class/offering` 拥有 `submission.read`

### 助教批改

允许条件：

* 用户对目标 `class/offering` 拥有 `submission.grade`

---

## 11.2 资源状态约束

### 任务草稿

* 学生不可见
* 教学团队可见

### 成绩草稿

* 学生不可见
* 教师/助教可见

### 归档开课实体

* 默认只读
* 普通教师/助教不可继续写入
* 特殊纠错权限可例外

### 退课学生

* 不允许继续正式提交
* 可只读访问本人历史记录（建议）

---

## 11.3 时间窗口约束

### 提交

允许条件：

* 当前时间 <= 截止时间
* 或作业允许迟交

### 考试模式

允许条件：

* 请求发生在考试窗口内

---

## 11.4 敏感资源约束

### 隐藏测试

仅以下角色允许：

* `judge_admin`
* 显式拥有 `judge.view_hidden` 的教学人员

### 评测脚本/SPJ

默认仅：

* `judge_admin`
* 显式拥有 `judge.config`

### 源代码全文

默认仅：

* 对应班级/开课教学团队
* 显式拥有 `submission.read_source`

组织管理员默认不能自动读取学生代码全文。

---

# 12. 数据模型设计

## 12.1 基础表

### users

| 字段       | 类型        | 说明                     |
| ---------- | ----------- | ------------------------ |
| id         | bigint/uuid | 主键                     |
| name       | string      | 用户名                   |
| email      | string      | 邮箱                     |
| status     | enum        | `active/disabled/locked` |
| created_at | datetime    | 创建时间                 |
| updated_at | datetime    | 更新时间                 |

---

### roles

| 字段          | 类型        | 说明                          |
| ------------- | ----------- | ----------------------------- |
| id            | bigint/uuid | 主键                          |
| code          | string      | 角色代码，唯一                |
| name          | string      | 角色名                        |
| description   | text        | 描述                          |
| role_category | enum        | `system/org/teaching/special` |
| is_builtin    | bool        | 是否系统内建                  |

---

### permissions

| 字段          | 类型        | 说明           |
| ------------- | ----------- | -------------- |
| id            | bigint/uuid | 主键           |
| code          | string      | 权限代码，唯一 |
| resource_type | string      | 资源类型       |
| action        | string      | 动作           |
| description   | text        | 描述           |

---

### role_permissions

| 字段          | 类型 | 说明 |
| ------------- | ---- | ---- |
| role_id       | fk   | 角色 |
| permission_id | fk   | 权限 |

唯一索引：

* `(role_id, permission_id)`

---

### role_bindings

| 字段             | 类型              | 说明                                   |
| ---------------- | ----------------- | -------------------------------------- |
| id               | bigint/uuid       | 主键                                   |
| user_id          | fk                | 用户                                   |
| role_id          | fk                | 角色                                   |
| scope_type       | enum              | `school/college/course/offering/class` |
| scope_id         | string/bigint     | 作用域 ID                              |
| constraints_json | json              | 附加限制                               |
| status           | enum              | `active/inactive`                      |
| effective_from   | datetime nullable | 生效开始                               |
| effective_to     | datetime nullable | 生效结束                               |
| granted_by       | fk nullable       | 授权人                                 |
| created_at       | datetime          | 创建时间                               |
| updated_at       | datetime          | 更新时间                               |

唯一索引建议：

* `(user_id, role_id, scope_type, scope_id)`

查询索引建议：

* `(user_id, status)`
* `(scope_type, scope_id, status)`

---

## 12.2 组织与业务成员表

### schools

### colleges

### courses

### course_offerings

### teaching_classes

这些表为业务主表，权限系统需能够根据任意资源追溯所属组织链路。

---

### offering_members

| 字段        | 类型 | 说明                              |
| ----------- | ---- | --------------------------------- |
| id          | 主键 | 主键                              |
| offering_id | fk   | 开课实体                          |
| user_id     | fk   | 用户                              |
| member_role | enum | `coordinator/teacher/ta/observer` |
| status      | enum | `active/inactive`                 |

---

### class_members

| 字段        | 类型 | 说明                          |
| ----------- | ---- | ----------------------------- |
| id          | 主键 | 主键                          |
| class_id    | fk   | 班级                          |
| user_id     | fk   | 用户                          |
| member_role | enum | `teacher/ta/student/observer` |
| status      | enum | `active/inactive`             |

> 说明：
> `offering_members` / `class_members` 用于业务查询；
> `role_bindings` 用于权限判定。
> 两者可联动，但不建议互相替代。

---

## 12.3 审计表

### audit_logs

| 字段          | 类型     | 说明         |
| ------------- | -------- | ------------ |
| id            | 主键     | 主键         |
| user_id       | fk       | 操作者       |
| action        | string   | 动作         |
| resource_type | string   | 资源类型     |
| resource_id   | string   | 资源 ID      |
| scope_type    | string   | 作用域类型   |
| scope_id      | string   | 作用域 ID    |
| decision      | enum     | `allow/deny` |
| reason        | string   | 原因         |
| metadata_json | json     | 附加信息     |
| ip            | string   | IP           |
| user_agent    | string   | UA           |
| created_at    | datetime | 时间         |

必须记录的高风险操作：

* 角色绑定变更
* 改分
* 成绩导出
* 查看隐藏测试
* 修改评测配置
* 批量导入导出成员
* 查看源码全文（若为敏感策略）

---

# 13. 资源归属链解析要求

权限系统需要提供统一资源归属解析能力，即对于任意业务资源，系统能够解析出它所属的：

* school_id
* college_id
* course_id
* offering_id
* class_id
* owner_user_id（如果有）
* resource_status（如果有）

## 13.1 示例

### Submission

需要解析到：

* 所属 `task`
* 所属 `class`
* 所属 `offering`
* 所属 `course`
* 所属 `college`
* 所属 `school`
* 提交人 `student_id`

### Grade

需要解析到：

* 所属学生
* 所属任务
* 所属班级/开课实体
* 成绩状态

### Task

需要解析到：

* 作用域是 `offering` 还是 `class`
* 所属开课实体
* 所属班级（如果是班级任务）
* 发布状态

---

# 14. 授权判定服务设计

## 14.1 核心接口

后端需提供统一授权服务，例如：

```text
authorize(user, action, resource) -> allow / deny
```

推荐内部可扩展为：

```text
authorize(user_id, permission_code, resource_ref, context) -> AuthorizationResult
```

## 14.2 返回结构

建议返回：

```json
{
  "allowed": true,
  "matched_roles": ["offering_teacher"],
  "matched_scopes": [{"type": "offering", "id": "O100"}],
  "reason_code": "ALLOW_BY_SCOPE_ROLE",
  "need_audit": false
}
```

拒绝示例：

```json
{
  "allowed": false,
  "reason_code": "DENY_NOT_OWNER_OR_UNPUBLISHED",
  "matched_roles": ["student"]
}
```

## 14.3 必要能力

授权服务必须支持：

1. 单资源授权判断
2. 批量资源授权判断
3. 列表查询条件生成
4. 字段级脱敏判断
5. 敏感操作审计开关

---

# 15. 列表过滤与搜索授权

这是权限系统改造中的高优先级要求。

## 15.1 基本要求

权限校验不能只做在详情接口上。
以下接口必须自动做数据过滤：

* 列表接口
* 搜索接口
* 统计接口
* 导出接口
* 批量操作接口

## 15.2 规则

### 学生

只能查询：

* 自己所在班级中已发布内容
* 自己的提交
* 自己的成绩

### 班级助教

只能查询：

* 被授权班级的数据

### 开课教师/助教

可以查询：

* 被授权开课实体下的全部班级数据

### 学院管理员

可查询：

* 本学院组织与教学运行概览
  但默认不返回学生敏感教学内容正文

## 15.3 实现要求

后端必须支持在 Repository / Query Builder 层自动注入权限过滤条件，禁止先查全量再在业务层手动过滤。

---

# 16. 字段级访问控制

对于某些资源，存在“对象可访问，但对象中的敏感字段不可见”的情况。

## 16.1 必须支持字段级控制的资源

### Submission

敏感字段示例：

* 源代码全文
* 附件下载链接
* 编译日志全文
* 运行日志全文

### Grade

敏感字段示例：

* 草稿成绩
* 未发布评语
* 教师内部备注

### Task / Judge Config

敏感字段示例：

* 隐藏测试点
* SPJ 脚本
* 特殊判题逻辑

## 16.2 策略要求

* 对象级允许不代表字段级完全可见
* 字段级建议通过序列化层或 DTO 层统一裁剪
* 前端不得依赖后端返回后再本地隐藏敏感字段

---

# 17. 状态驱动权限规则

## 17.1 开课实体状态

建议状态：

* `draft`
* `active`
* `archived`

规则：

* `draft`：学生不可见，教学团队/管理员可见
* `active`：按正常权限逻辑
* `archived`：默认只读

## 17.2 任务状态

建议状态：

* `draft`
* `published`
* `closed`
* `archived`

规则：

* `draft`：学生不可见
* `published`：学生可按作用域读取
* `closed`：不可再提交，成绩可继续处理
* `archived`：默认只读

## 17.3 成绩状态

建议状态：

* `draft`
* `published`
* `frozen`

规则：

* `draft`：仅教学团队可见
* `published`：学生可见
* `frozen`：原则上只读，改动需高权限并留痕

## 17.4 成员状态

### 学生

* `active`
* `dropped`
* `transferred`

规则：

* `dropped`：不可再提交
* `transferred`：可只读历史记录，但不应访问旧班其他成员数据

---

# 18. 高风险操作与审计要求

## 18.1 必须审计的操作

1. 角色绑定新增/移除/变更
2. 改分
3. 发布成绩
4. 导出成绩
5. 批量导入成员
6. 查看隐藏测试
7. 修改评测配置
8. 查看学生源码全文（若策略定义为敏感）
9. 执行重判
10. 查看或导出审计数据

## 18.2 审计日志内容要求

每条审计日志至少记录：

* 操作者
* 时间
* 动作
* 资源类型
* 资源 ID
* 命中的角色
* 命中的作用域
* 决策结果
* 请求来源（IP / UA）
* 关键上下文（如改分前后分值）

---

# 19. 典型边界规则

## 19.1 教师同时是管理员

允许。
行为要求：

* 权限按并集生效
* 只在各自作用域内生效
* 管理员身份不自动解锁教学敏感资源

## 19.2 某课程学生同时是其他课程助教

允许。
行为要求：

* 在学生作用域仅有学生权限
* 在助教作用域仅有助教权限
* 两者严格隔离

## 19.3 开课级助教访问全部班级

允许。
前提：

* 绑定为 `offering_ta@offering:X`

## 19.4 班级助教只能访问指定班级

必须支持。
前提：

* 绑定为 `class_ta@class:Y`

## 19.5 学院管理员查看学生源码

默认拒绝。
除非显式授予 `submission.read_source`。

## 19.6 学校管理员直接改分

默认拒绝。
除非显式授予 `grade.override`，且必须审计。

## 19.7 同一用户在同一开课实体既是教师又是学生

产品建议：**禁止这种配置**。
如果业务必须允许，则系统必须额外保证：

* 禁止自批改
* 禁止自评分
* 排名与统计中不污染学生维度数据

---

# 20. API 需求

## 20.1 权限相关管理接口

### 角色查询

* `GET /roles`
* `GET /roles/{id}`

### 权限查询

* `GET /permissions`

### 角色绑定查询

* `GET /role-bindings`
* `GET /users/{id}/role-bindings`

### 角色绑定管理

* `POST /role-bindings`
* `PATCH /role-bindings/{id}`
* `DELETE /role-bindings/{id}`

### 审计日志查询

* `GET /audit-logs`

## 20.2 授权辅助接口（可选）

### 当前用户能力查询

* `GET /me/permissions`
* `GET /me/scopes`

### 对某资源的操作能力查询

* `POST /authorization/check`
* `POST /authorization/batch-check`

> 说明：
> 前端可用于按钮显隐；
> 但最终以后端实时授权结果为准。

---

# 21. 后端集成要求

## 21.1 中间件要求

权限系统需至少支持：

1. 身份认证中间件
2. 资源加载/资源归属解析器
3. 授权中间件
4. 字段裁剪/序列化安全层
5. 审计拦截器

## 21.2 代码架构要求

建议结构：

* `auth/identity`
* `auth/roles`
* `auth/permissions`
* `auth/scope`
* `auth/authorize`
* `auth/audit`

禁止在业务 Handler 中重复拼装权限规则。

## 21.3 Repository 要求

* 列表查询必须支持授权过滤条件下推
* 导出接口必须在数据源层过滤
* 批量查询必须支持 batch authorization

---

# 22. 性能要求

权限系统不能只正确，还要足够快。

## 22.1 性能目标

1. 单次授权判断应尽量在低毫秒级
2. 批量授权判断应支持列表场景
3. 角色绑定和角色模板可缓存
4. 资源归属链解析应避免重复数据库查询
5. 列表页不能因逐条授权判断导致 N+1 问题

## 22.2 推荐优化手段

1. 缓存角色权限模板
2. 缓存用户角色绑定
3. 对常见资源建立归属关系索引
4. 对列表接口使用批量归属解析
5. 对权限过滤生成 SQL 条件而非内存后过滤

---

# 23. 安全要求

1. 所有授权判断必须在服务端完成
2. 前端按钮隐藏不等于权限控制
3. 所有对象 ID 访问必须重新校验作用域
4. 导出、搜索、批量接口必须校验
5. 角色变更后权限应及时生效
6. 旧 token 不应长期保留已移除权限
7. 敏感字段不可由前端自行过滤

---

# 24. 数据迁移与改造方案

## 24.1 改造原则

1. 先引入新权限表结构
2. 从现有成员关系推导默认角色绑定
3. 保持旧系统可用，逐步切换到新授权服务
4. 最终移除旧的布尔角色/硬编码判断

## 24.2 迁移阶段

### 阶段一：建表与基础数据

* 新建 `roles`
* 新建 `permissions`
* 新建 `role_permissions`
* 新建 `role_bindings`
* 新建 `audit_logs`

### 阶段二：初始化内建角色与权限点

* 写初始化脚本
* 插入内建角色
* 插入权限点
* 插入角色-权限模板

### 阶段三：从业务成员表导入角色绑定

例如：

* `offering_members.teacher` -> `offering_teacher@offering`
* `class_members.ta` -> `class_ta@class`
* `class_members.student` -> `student@class`

### 阶段四：新增统一授权服务

* 新接口先走新权限系统
* 老接口灰度接入

### 阶段五：删除旧权限逻辑

* 移除 `is_admin`、`role == teacher` 等旧逻辑
* 全量切换

---

# 25. 测试要求

## 25.1 测试类型

必须覆盖：

1. 单元测试
2. 集成测试
3. 授权边界测试
4. 列表过滤测试
5. 字段级脱敏测试
6. 审计日志测试
7. 迁移回归测试

## 25.2 最小核心用例

### 组织隔离

* 学校管理员不能访问其他学校资源
* 学院管理员不能访问其他学院资源

### 班级与开课隔离

* 班级助教不能访问兄弟班级
* 开课助教可访问本开课所有班级

### 学生隔离

* 学生不能查看他人提交
* 学生不能查看未发布成绩

### 敏感信息

* 非 judge_admin 不能查看隐藏测试
* 非显式授权者不能看源码全文

### 状态驱动

* 已归档开课默认不可写
* 退课学生不可继续提交

### 高风险操作

* 改分必须产生日志
* 导出成绩必须产生日志

---

# 26. 产品验收标准

权限系统改造完成后，必须满足以下验收标准：

## 26.1 功能验收

1. 支持多层级作用域绑定
2. 支持多身份并存
3. 支持默认角色模板
4. 支持统一授权服务
5. 支持列表/搜索/导出授权过滤
6. 支持字段级敏感信息裁剪
7. 支持状态驱动访问控制
8. 支持高风险操作审计

## 26.2 安全验收

1. 任意详情接口不存在 IDOR 越权
2. 任意导出接口不存在越权导出
3. 学生无法读取他人数据
4. 助教无法默认访问未授权班级
5. 管理员无法默认访问隐藏测试/改分

## 26.3 可维护性验收

1. 新增角色不需要修改大量业务代码
2. 新增权限点可通过模板配置接入
3. 授权规则集中管理
4. 审计日志可追溯

---

# 27. 成功指标

权限系统上线后，建议跟踪以下指标：

1. 越权问题数量下降
2. 权限相关线上 bug 数量下降
3. 高风险操作审计覆盖率
4. 授权失败日志的可解释性
5. 角色配置与维护成本
6. 列表接口授权性能表现
7. 权限缓存命中率

---

# 28. 推荐的实施顺序

## 第一期：最小可用改造

* 角色、权限点、角色绑定表
* 统一授权服务
* 开课/班级作用域判断
* 学生/教师/助教/管理员基础模板

## 第二期：安全增强

* 字段级裁剪
* 列表过滤
* 审计日志
* 状态驱动规则

## 第三期：能力增强

* 批量授权
* 权限缓存
* 前端能力查询接口
* 更多专项角色（judge_admin、auditor、grader）

---

# 29. 附录 A：推荐初始角色绑定来源

| 业务成员关系 | 推荐角色绑定                    |
| ------------ | ------------------------------- |
| 学校管理员表 | `school_admin@school`           |
| 学院管理员表 | `college_admin@college`         |
| 开课负责人   | `offering_coordinator@offering` |
| 开课教师     | `offering_teacher@offering`     |
| 班级教师     | `class_teacher@class`           |
| 开课助教     | `offering_ta@offering`          |
| 班级助教     | `class_ta@class`                |
| 班级学生     | `student@class`                 |

---

# 30. 附录 B：推荐默认策略总结

1. 默认拒绝
2. 向下继承，不向上提升
3. 组织管理权不等于教学敏感权
4. 学生只能看自己
5. 成绩必须发布后学生才能看
6. 归档默认只读
7. 高风险操作必须审计
8. 列表/搜索/导出必须授权过滤

---

# 31. 最终结论

AUBB 的权限系统应采用：

**“作用域化 RBAC + 固定规则型 ABAC + 统一资源归属解析 + 全链路审计”**

这套方案可以同时满足：

* 学校—学院—课程—开课实体—班级的层级结构
* 教师、助教、学生、管理员的多身份并存
* 开课级与班级级权限隔离
* 敏感评测与成绩数据保护
* 后续业务扩展的可维护性

本规格文档可直接作为权限系统重构的产品与技术实施依据。
