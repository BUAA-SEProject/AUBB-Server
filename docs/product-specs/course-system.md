# 课程系统

## 目标

交付课程域第一切片，使平台具备课程模板、学期、开课实例、教学班、课程成员和班级功能开关能力，能够支撑教师组织课程、维护教学班和批量管理课程成员。

## 覆盖范围

### 功能范围

- 学期创建与查询
- 课程模板创建与查询
- 开课实例创建、查询与跨学院共同管理
- 开课实例自动绑定 COURSE 组织节点
- 教学班创建与查询
- 教学班自动绑定 CLASS 组织节点
- 教师批量添加课程成员
- 教师通过 CSV 导入既有系统用户为课程成员
- 课程成员同步到 `user_org_memberships`
- 班级功能开关：公告、讨论区、资源、实验、作业
- “我的课程”聚合查询

### 不在范围

- 课程公告正文、讨论帖、资源正文、课程首页装修
- 学生自主选课、邀请码加课、退课、选组
- 任务、提交、实验、评测、成绩
- 助教细粒度授权范围自定义与通用 ABAC 引擎

## 核心业务规则

1. 学校管理员负责创建学院与学期。
2. 学院管理员负责创建课程模板和开课实例。
3. 开课实例可关联多个学院共同管理，但必须属于同一学校。
4. 教师可管理自己负责的课程与教学班。
5. 助教可查看被授权教学班成员，但不能修改课程核心信息、成员或班级功能开关。
6. 同一用户可在一个教学班中是学生、在另一个教学班中是助教。
7. 学生不能自主选课；课程成员只能由教师批量添加或导入既有系统用户。
8. 一个开课实例可包含不同年份的多个教学班，例如 2024 级和 2025 级。

## 核心数据模型

- `academic_terms`
- `course_catalogs`
- `course_offerings`
- `course_offering_college_maps`
- `teaching_classes`
- `course_members`

## 角色边界

### 学校管理员

- 创建学期
- 查看全量课程
- 管理学校级课程治理

### 学院管理员

- 创建课程模板
- 创建本学院开课实例
- 配置跨学院共同管理映射
- 查看自己学院参与管理的开课实例

### 教师

- 负责课程教学组织
- 创建教学班
- 批量添加和导入课程成员
- 开关班级功能

### 助教

- 查看自己协助教学班的成员列表
- 协助教学运行
- 不可批量加人
- 不可修改班级功能开关

### 学生

- 查看自己参与的课程与班级
- 当前版本不提供自主选课入口

## API 边界

### 管理端

- `POST /api/v1/admin/academic-terms`
- `GET /api/v1/admin/academic-terms`
- `POST /api/v1/admin/course-catalogs`
- `GET /api/v1/admin/course-catalogs`
- `POST /api/v1/admin/course-offerings`
- `GET /api/v1/admin/course-offerings`
- `GET /api/v1/admin/course-offerings/{offeringId}`

### 教师侧

- `POST /api/v1/teacher/course-offerings/{offeringId}/classes`
- `GET /api/v1/teacher/course-offerings/{offeringId}/classes`
- `PUT /api/v1/teacher/course-classes/{teachingClassId}/features`
- `POST /api/v1/teacher/course-offerings/{offeringId}/members/batch`
- `POST /api/v1/teacher/course-offerings/{offeringId}/members/import`
- `GET /api/v1/teacher/course-offerings/{offeringId}/members`

### 我的课程

- `GET /api/v1/me/courses`

## 验收标准

- 学院管理员可创建开课实例，并将课程关联到其他学院共同管理。
- 教师可创建 2024 级、2025 级等多个教学班。
- 教师可批量添加和导入既有系统用户为课程成员。
- 同一用户可在一个班级中是学生，在另一个班级中是助教。
- 助教在被授权班级内可查看成员，但不能修改成员和班级功能。
- `./mvnw verify` 提供自动化测试证据。
