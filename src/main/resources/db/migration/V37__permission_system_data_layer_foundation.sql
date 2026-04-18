CREATE TABLE roles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code TEXT NOT NULL CHECK (length(code) <= 64),
    name TEXT NOT NULL CHECK (length(name) <= 128),
    description TEXT NOT NULL CHECK (length(description) <= 256),
    role_category TEXT NOT NULL CHECK (role_category IN ('SYSTEM', 'ORG', 'TEACHING', 'SPECIAL')),
    scope_type TEXT NOT NULL CHECK (scope_type IN ('platform', 'school', 'college', 'course', 'offering', 'class')),
    is_builtin BOOLEAN NOT NULL DEFAULT FALSE,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_roles_code_lower ON roles (lower(code));
CREATE INDEX ix_roles_scope_type_status ON roles (scope_type, status, id);

CREATE TABLE permissions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code TEXT NOT NULL CHECK (length(code) <= 96),
    resource_type TEXT NOT NULL CHECK (length(resource_type) <= 64),
    action TEXT NOT NULL CHECK (length(action) <= 64),
    description TEXT NOT NULL CHECK (length(description) <= 256),
    sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_permissions_code_lower ON permissions (lower(code));
CREATE INDEX ix_permissions_resource_action ON permissions (resource_type, action, id);

CREATE TABLE role_permissions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_id BIGINT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_role_permissions_role_permission ON role_permissions (role_id, permission_id);
CREATE INDEX ix_role_permissions_permission_id ON role_permissions (permission_id);

CREATE TABLE role_bindings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    scope_type TEXT NOT NULL CHECK (scope_type IN ('platform', 'school', 'college', 'course', 'offering', 'class')),
    scope_id BIGINT NOT NULL,
    constraints_json JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(constraints_json) = 'object'),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    effective_from TIMESTAMPTZ,
    effective_to TIMESTAMPTZ,
    granted_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    source_type TEXT NOT NULL CHECK (source_type IN (
        'MANUAL',
        'IMPORT',
        'SYNC',
        'LEGACY_GOVERNANCE',
        'LEGACY_COURSE_MEMBER',
        'LEGACY_AUTHZ_GROUP'
    )),
    source_ref_id BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)
);

CREATE UNIQUE INDEX ux_role_bindings_source_scope
    ON role_bindings (user_id, role_id, scope_type, scope_id, source_type, source_ref_id);
CREATE INDEX ix_role_bindings_user_status
    ON role_bindings (user_id, status, scope_type, scope_id, role_id);
CREATE INDEX ix_role_bindings_scope_status
    ON role_bindings (scope_type, scope_id, status, user_id, role_id);
CREATE INDEX ix_role_bindings_role_status
    ON role_bindings (role_id, status, scope_type, scope_id, user_id);

ALTER TABLE audit_logs
    ADD COLUMN user_id BIGINT GENERATED ALWAYS AS (actor_user_id) STORED,
    ADD COLUMN resource_type TEXT GENERATED ALWAYS AS (target_type) STORED,
    ADD COLUMN resource_id TEXT GENERATED ALWAYS AS (target_id) STORED,
    ADD COLUMN metadata_json JSONB GENERATED ALWAYS AS (metadata) STORED,
    ADD COLUMN scope_type TEXT CHECK (scope_type IN ('platform', 'school', 'college', 'course', 'offering', 'class')),
    ADD COLUMN scope_id BIGINT,
    ADD COLUMN decision TEXT CHECK (decision IN ('ALLOW', 'DENY')),
    ADD COLUMN reason TEXT GENERATED ALWAYS AS ((metadata ->> 'reason')) STORED,
    ADD COLUMN user_agent TEXT CHECK (user_agent IS NULL OR length(user_agent) <= 512);

CREATE INDEX ix_audit_logs_user_id_created_at ON audit_logs (user_id, created_at DESC);
CREATE INDEX ix_audit_logs_scope_created_at ON audit_logs (scope_type, scope_id, created_at DESC);
CREATE INDEX ix_audit_logs_resource_created_at ON audit_logs (resource_type, resource_id, created_at DESC);
CREATE INDEX ix_audit_logs_decision_created_at ON audit_logs (decision, created_at DESC);

CREATE TABLE offering_members (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    member_role TEXT NOT NULL CHECK (member_role IN ('COORDINATOR', 'TEACHER', 'TA', 'OBSERVER')),
    member_status TEXT NOT NULL CHECK (member_status IN ('PENDING', 'ACTIVE', 'DROPPED', 'COMPLETED', 'REMOVED', 'INACTIVE')),
    source_type TEXT NOT NULL CHECK (source_type IN ('MANUAL', 'IMPORT', 'SYNC')),
    legacy_course_member_id BIGINT UNIQUE REFERENCES course_members (id) ON DELETE CASCADE,
    remark TEXT,
    joined_at TIMESTAMPTZ,
    left_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (left_at IS NULL OR joined_at IS NULL OR left_at >= joined_at)
);

CREATE UNIQUE INDEX ux_offering_members_unique ON offering_members (offering_id, user_id, member_role);
CREATE INDEX ix_offering_members_offering_status
    ON offering_members (offering_id, member_status, member_role, user_id);
CREATE INDEX ix_offering_members_user_status
    ON offering_members (user_id, member_status, offering_id);

CREATE TABLE class_members (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    class_id BIGINT NOT NULL REFERENCES teaching_classes (id) ON DELETE CASCADE,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    member_role TEXT NOT NULL CHECK (member_role IN ('TEACHER', 'TA', 'STUDENT', 'OBSERVER')),
    member_status TEXT NOT NULL CHECK (member_status IN ('PENDING', 'ACTIVE', 'DROPPED', 'COMPLETED', 'REMOVED', 'INACTIVE')),
    source_type TEXT NOT NULL CHECK (source_type IN ('MANUAL', 'IMPORT', 'SYNC')),
    legacy_course_member_id BIGINT UNIQUE REFERENCES course_members (id) ON DELETE CASCADE,
    remark TEXT,
    joined_at TIMESTAMPTZ,
    left_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (left_at IS NULL OR joined_at IS NULL OR left_at >= joined_at)
);

CREATE UNIQUE INDEX ux_class_members_unique ON class_members (class_id, user_id, member_role);
CREATE INDEX ix_class_members_class_status
    ON class_members (class_id, member_status, member_role, user_id);
CREATE INDEX ix_class_members_user_status
    ON class_members (user_id, member_status, class_id);
CREATE INDEX ix_class_members_offering_id ON class_members (offering_id, class_id, user_id);

INSERT INTO roles (code, name, description, role_category, scope_type, is_builtin, status)
VALUES
    ('school_admin', '学校管理员', '管理学校级组织、成员绑定与运行配置', 'ORG', 'school', TRUE, 'ACTIVE'),
    ('college_admin', '学院管理员', '管理学院级课程、开课与成员范围', 'ORG', 'college', TRUE, 'ACTIVE'),
    ('course_manager', '课程管理者', '维护课程模板、题库与课程级资源', 'ORG', 'course', TRUE, 'ACTIVE'),
    ('offering_coordinator', '开课负责人', '管理某次开课整体教学运行', 'TEACHING', 'offering', TRUE, 'ACTIVE'),
    ('offering_teacher', '开课教师', '管理某次开课下全部班级的教学任务', 'TEACHING', 'offering', TRUE, 'ACTIVE'),
    ('class_teacher', '班级教师', '管理指定教学班的教学任务与成绩', 'TEACHING', 'class', TRUE, 'ACTIVE'),
    ('offering_ta', '开课助教', '协助管理开课下全部班级', 'TEACHING', 'offering', TRUE, 'ACTIVE'),
    ('class_ta', '班级助教', '协助管理指定教学班', 'TEACHING', 'class', TRUE, 'ACTIVE'),
    ('student', '学生', '参与课程学习、IDE 运行与提交', 'TEACHING', 'class', TRUE, 'ACTIVE'),
    ('judge_admin', '评测管理员', '管理评测环境与敏感评测配置', 'SPECIAL', 'school', TRUE, 'ACTIVE'),
    ('auditor', '审计员', '查看审计日志与高风险操作记录', 'SPECIAL', 'school', TRUE, 'ACTIVE'),
    ('grader', '阅卷员', '专门用于批改与成绩修订', 'SPECIAL', 'offering', TRUE, 'ACTIVE'),
    ('class_admin', '班级管理员（兼容）', '兼容旧治理模型中的班级管理员角色', 'ORG', 'class', TRUE, 'ACTIVE'),
    ('observer', '观察者（兼容）', '兼容旧教学观察者角色，只提供只读访问', 'TEACHING', 'offering', TRUE, 'ACTIVE');

INSERT INTO permissions (code, resource_type, action, description, sensitive)
VALUES
    ('school.read', 'school', 'read', '查看学校信息', FALSE),
    ('school.manage', 'school', 'manage', '管理学校配置', FALSE),
    ('college.read', 'college', 'read', '查看学院信息', FALSE),
    ('college.manage', 'college', 'manage', '管理学院配置', FALSE),
    ('course.read', 'course', 'read', '查看课程信息', FALSE),
    ('course.manage', 'course', 'manage', '管理课程配置', FALSE),
    ('offering.read', 'offering', 'read', '查看开课实体信息', FALSE),
    ('offering.manage', 'offering', 'manage', '管理开课实体', FALSE),
    ('offering.archive', 'offering', 'archive', '归档开课实体', TRUE),
    ('class.read', 'class', 'read', '查看教学班信息', FALSE),
    ('class.manage', 'class', 'manage', '管理教学班', FALSE),
    ('member.read', 'member', 'read', '查看成员信息', FALSE),
    ('member.manage', 'member', 'manage', '维护成员信息', FALSE),
    ('member.import', 'member', 'import', '批量导入成员', TRUE),
    ('member.export', 'member', 'export', '批量导出成员', TRUE),
    ('role_binding.read', 'role_binding', 'read', '查看角色绑定', TRUE),
    ('role_binding.manage', 'role_binding', 'manage', '维护角色绑定', TRUE),
    ('task.read', 'task', 'read', '查看任务或作业', FALSE),
    ('task.create', 'task', 'create', '创建任务或作业', FALSE),
    ('task.edit', 'task', 'edit', '编辑任务或作业', FALSE),
    ('task.delete', 'task', 'delete', '删除任务或作业', TRUE),
    ('task.publish', 'task', 'publish', '发布任务或作业', FALSE),
    ('task.close', 'task', 'close', '关闭任务或作业', FALSE),
    ('task.archive', 'task', 'archive', '归档任务或作业', TRUE),
    ('question.read', 'question', 'read', '查看题目', FALSE),
    ('question.create', 'question', 'create', '创建题目', FALSE),
    ('question.edit', 'question', 'edit', '编辑题目', FALSE),
    ('question.delete', 'question', 'delete', '删除题目', TRUE),
    ('question_bank.read', 'question_bank', 'read', '查看题库', FALSE),
    ('question_bank.manage', 'question_bank', 'manage', '管理题库', FALSE),
    ('paper.generate', 'paper', 'generate', '生成试卷或任务纸面结构', FALSE),
    ('submission.read', 'submission', 'read', '查看提交', TRUE),
    ('submission.download', 'submission', 'download', '下载提交附件', TRUE),
    ('submission.grade', 'submission', 'grade', '批改提交', TRUE),
    ('submission.regrade', 'submission', 'regrade', '重新批改提交', TRUE),
    ('submission.comment', 'submission', 'comment', '评论或反馈提交', TRUE),
    ('submission.export', 'submission', 'export', '导出提交数据', TRUE),
    ('submission.read_source', 'submission', 'read_source', '查看源码全文与敏感日志', TRUE),
    ('grade.read', 'grade', 'read', '查看成绩', FALSE),
    ('grade.export', 'grade', 'export', '导出成绩', TRUE),
    ('grade.publish', 'grade', 'publish', '发布成绩', TRUE),
    ('grade.override', 'grade', 'override', '覆盖成绩', TRUE),
    ('grade.import', 'grade', 'import', '导入成绩', TRUE),
    ('ranking.read', 'ranking', 'read', '查看排名', TRUE),
    ('ide.read', 'ide', 'read', '查看 IDE 工作区', FALSE),
    ('ide.save', 'ide', 'save', '保存 IDE 工作区', FALSE),
    ('ide.run', 'ide', 'run', '运行 IDE 代码', FALSE),
    ('ide.submit', 'ide', 'submit', '从 IDE 提交', FALSE),
    ('judge.run', 'judge', 'run', '触发评测运行', TRUE),
    ('judge.rejudge', 'judge', 'rejudge', '触发重判', TRUE),
    ('judge.read_log', 'judge', 'read_log', '查看评测日志', TRUE),
    ('judge.config', 'judge', 'config', '管理评测配置', TRUE),
    ('judge.view_hidden', 'judge', 'view_hidden', '查看隐藏测试与评测脚本', TRUE),
    ('judge.manage_environment', 'judge', 'manage_environment', '管理评测环境', TRUE),
    ('announcement.read', 'announcement', 'read', '查看公告', FALSE),
    ('announcement.publish', 'announcement', 'publish', '发布公告', FALSE),
    ('resource.read', 'resource', 'read', '查看教学资源', FALSE),
    ('resource.upload', 'resource', 'upload', '上传教学资源', FALSE),
    ('resource.manage', 'resource', 'manage', '管理教学资源', FALSE),
    ('report.read', 'report', 'read', '查看报表', TRUE),
    ('report.export', 'report', 'export', '导出报表', TRUE),
    ('analytics.read', 'analytics', 'read', '查看统计分析', TRUE),
    ('appeal.create', 'appeal', 'create', '发起申诉', FALSE),
    ('appeal.read', 'appeal', 'read', '查看申诉', TRUE),
    ('appeal.review', 'appeal', 'review', '处理申诉', TRUE),
    ('audit.read', 'audit', 'read', '查看审计日志', TRUE),
    ('audit.export', 'audit', 'export', '导出审计日志', TRUE);

INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM (
    VALUES
        ('school_admin', 'school.read'),
        ('school_admin', 'school.manage'),
        ('school_admin', 'college.read'),
        ('school_admin', 'college.manage'),
        ('school_admin', 'course.read'),
        ('school_admin', 'course.manage'),
        ('school_admin', 'offering.read'),
        ('school_admin', 'offering.manage'),
        ('school_admin', 'offering.archive'),
        ('school_admin', 'class.read'),
        ('school_admin', 'class.manage'),
        ('school_admin', 'member.read'),
        ('school_admin', 'member.manage'),
        ('school_admin', 'member.import'),
        ('school_admin', 'member.export'),
        ('school_admin', 'role_binding.read'),
        ('school_admin', 'role_binding.manage'),
        ('school_admin', 'report.read'),
        ('school_admin', 'report.export'),
        ('school_admin', 'analytics.read'),

        ('college_admin', 'college.read'),
        ('college_admin', 'college.manage'),
        ('college_admin', 'course.read'),
        ('college_admin', 'course.manage'),
        ('college_admin', 'offering.read'),
        ('college_admin', 'offering.manage'),
        ('college_admin', 'offering.archive'),
        ('college_admin', 'class.read'),
        ('college_admin', 'class.manage'),
        ('college_admin', 'member.read'),
        ('college_admin', 'member.manage'),
        ('college_admin', 'member.import'),
        ('college_admin', 'member.export'),
        ('college_admin', 'role_binding.read'),
        ('college_admin', 'report.read'),
        ('college_admin', 'analytics.read'),

        ('course_manager', 'course.read'),
        ('course_manager', 'course.manage'),
        ('course_manager', 'question.read'),
        ('course_manager', 'question.create'),
        ('course_manager', 'question.edit'),
        ('course_manager', 'question.delete'),
        ('course_manager', 'question_bank.read'),
        ('course_manager', 'question_bank.manage'),
        ('course_manager', 'paper.generate'),

        ('offering_coordinator', 'offering.read'),
        ('offering_coordinator', 'offering.manage'),
        ('offering_coordinator', 'offering.archive'),
        ('offering_coordinator', 'class.read'),
        ('offering_coordinator', 'class.manage'),
        ('offering_coordinator', 'member.read'),
        ('offering_coordinator', 'member.manage'),
        ('offering_coordinator', 'task.read'),
        ('offering_coordinator', 'task.create'),
        ('offering_coordinator', 'task.edit'),
        ('offering_coordinator', 'task.delete'),
        ('offering_coordinator', 'task.publish'),
        ('offering_coordinator', 'task.close'),
        ('offering_coordinator', 'task.archive'),
        ('offering_coordinator', 'submission.read'),
        ('offering_coordinator', 'submission.download'),
        ('offering_coordinator', 'submission.grade'),
        ('offering_coordinator', 'submission.regrade'),
        ('offering_coordinator', 'submission.comment'),
        ('offering_coordinator', 'submission.export'),
        ('offering_coordinator', 'submission.read_source'),
        ('offering_coordinator', 'grade.read'),
        ('offering_coordinator', 'grade.export'),
        ('offering_coordinator', 'grade.publish'),
        ('offering_coordinator', 'grade.override'),
        ('offering_coordinator', 'grade.import'),
        ('offering_coordinator', 'ranking.read'),
        ('offering_coordinator', 'announcement.read'),
        ('offering_coordinator', 'announcement.publish'),
        ('offering_coordinator', 'resource.read'),
        ('offering_coordinator', 'resource.upload'),
        ('offering_coordinator', 'resource.manage'),
        ('offering_coordinator', 'report.read'),
        ('offering_coordinator', 'report.export'),
        ('offering_coordinator', 'appeal.read'),
        ('offering_coordinator', 'appeal.review'),

        ('offering_teacher', 'offering.read'),
        ('offering_teacher', 'offering.manage'),
        ('offering_teacher', 'class.read'),
        ('offering_teacher', 'class.manage'),
        ('offering_teacher', 'member.read'),
        ('offering_teacher', 'task.read'),
        ('offering_teacher', 'task.create'),
        ('offering_teacher', 'task.edit'),
        ('offering_teacher', 'task.delete'),
        ('offering_teacher', 'task.publish'),
        ('offering_teacher', 'task.close'),
        ('offering_teacher', 'task.archive'),
        ('offering_teacher', 'submission.read'),
        ('offering_teacher', 'submission.download'),
        ('offering_teacher', 'submission.grade'),
        ('offering_teacher', 'submission.regrade'),
        ('offering_teacher', 'submission.comment'),
        ('offering_teacher', 'submission.export'),
        ('offering_teacher', 'submission.read_source'),
        ('offering_teacher', 'grade.read'),
        ('offering_teacher', 'grade.export'),
        ('offering_teacher', 'grade.publish'),
        ('offering_teacher', 'grade.override'),
        ('offering_teacher', 'ranking.read'),
        ('offering_teacher', 'announcement.read'),
        ('offering_teacher', 'announcement.publish'),
        ('offering_teacher', 'resource.read'),
        ('offering_teacher', 'resource.upload'),
        ('offering_teacher', 'resource.manage'),
        ('offering_teacher', 'report.read'),
        ('offering_teacher', 'report.export'),
        ('offering_teacher', 'appeal.read'),
        ('offering_teacher', 'appeal.review'),

        ('class_teacher', 'class.read'),
        ('class_teacher', 'class.manage'),
        ('class_teacher', 'member.read'),
        ('class_teacher', 'member.manage'),
        ('class_teacher', 'task.read'),
        ('class_teacher', 'task.create'),
        ('class_teacher', 'task.edit'),
        ('class_teacher', 'task.delete'),
        ('class_teacher', 'task.publish'),
        ('class_teacher', 'task.close'),
        ('class_teacher', 'task.archive'),
        ('class_teacher', 'submission.read'),
        ('class_teacher', 'submission.download'),
        ('class_teacher', 'submission.grade'),
        ('class_teacher', 'submission.regrade'),
        ('class_teacher', 'submission.comment'),
        ('class_teacher', 'submission.export'),
        ('class_teacher', 'submission.read_source'),
        ('class_teacher', 'grade.read'),
        ('class_teacher', 'grade.export'),
        ('class_teacher', 'grade.publish'),
        ('class_teacher', 'grade.override'),
        ('class_teacher', 'grade.import'),
        ('class_teacher', 'ranking.read'),
        ('class_teacher', 'announcement.read'),
        ('class_teacher', 'announcement.publish'),
        ('class_teacher', 'resource.read'),
        ('class_teacher', 'resource.upload'),
        ('class_teacher', 'resource.manage'),
        ('class_teacher', 'appeal.read'),
        ('class_teacher', 'appeal.review'),

        ('offering_ta', 'offering.read'),
        ('offering_ta', 'class.read'),
        ('offering_ta', 'member.read'),
        ('offering_ta', 'task.read'),
        ('offering_ta', 'submission.read'),
        ('offering_ta', 'submission.grade'),
        ('offering_ta', 'submission.comment'),
        ('offering_ta', 'submission.read_source'),
        ('offering_ta', 'grade.read'),
        ('offering_ta', 'appeal.read'),

        ('class_ta', 'class.read'),
        ('class_ta', 'member.read'),
        ('class_ta', 'task.read'),
        ('class_ta', 'submission.read'),
        ('class_ta', 'submission.grade'),
        ('class_ta', 'submission.comment'),
        ('class_ta', 'submission.read_source'),
        ('class_ta', 'grade.read'),
        ('class_ta', 'appeal.read'),

        ('student', 'class.read'),
        ('student', 'task.read'),
        ('student', 'submission.read'),
        ('student', 'grade.read'),
        ('student', 'ide.read'),
        ('student', 'ide.save'),
        ('student', 'ide.run'),
        ('student', 'ide.submit'),
        ('student', 'announcement.read'),
        ('student', 'resource.read'),
        ('student', 'appeal.create'),
        ('student', 'appeal.read'),

        ('judge_admin', 'judge.run'),
        ('judge_admin', 'judge.rejudge'),
        ('judge_admin', 'judge.read_log'),
        ('judge_admin', 'judge.config'),
        ('judge_admin', 'judge.view_hidden'),
        ('judge_admin', 'judge.manage_environment'),

        ('auditor', 'audit.read'),
        ('auditor', 'audit.export'),
        ('auditor', 'report.read'),

        ('grader', 'submission.read'),
        ('grader', 'submission.grade'),
        ('grader', 'submission.comment'),
        ('grader', 'submission.read_source'),
        ('grader', 'grade.read'),
        ('grader', 'grade.override'),
        ('grader', 'grade.import'),
        ('grader', 'appeal.read'),
        ('grader', 'appeal.review'),

        ('class_admin', 'class.read'),
        ('class_admin', 'class.manage'),
        ('class_admin', 'member.read'),
        ('class_admin', 'member.manage'),
        ('class_admin', 'member.import'),
        ('class_admin', 'member.export'),
        ('class_admin', 'role_binding.read'),
        ('class_admin', 'role_binding.manage'),

        ('observer', 'offering.read'),
        ('observer', 'class.read'),
        ('observer', 'task.read'),
        ('observer', 'announcement.read'),
        ('observer', 'resource.read')
) AS bindings(role_code, permission_code)
JOIN roles ON roles.code = bindings.role_code
JOIN permissions ON permissions.code = bindings.permission_code;

INSERT INTO offering_members (
    offering_id,
    user_id,
    member_role,
    member_status,
    source_type,
    legacy_course_member_id,
    remark,
    joined_at,
    left_at,
    created_at,
    updated_at
)
SELECT
    course_members.offering_id,
    course_members.user_id,
    CASE course_members.member_role
        WHEN 'INSTRUCTOR' THEN 'TEACHER'
        WHEN 'OFFERING_TA' THEN 'TA'
        WHEN 'OBSERVER' THEN 'OBSERVER'
    END,
    course_members.member_status,
    course_members.source_type,
    course_members.id,
    course_members.remark,
    course_members.joined_at,
    course_members.left_at,
    course_members.created_at,
    course_members.updated_at
FROM course_members
WHERE course_members.member_role IN ('INSTRUCTOR', 'OFFERING_TA', 'OBSERVER');

INSERT INTO class_members (
    class_id,
    offering_id,
    user_id,
    member_role,
    member_status,
    source_type,
    legacy_course_member_id,
    remark,
    joined_at,
    left_at,
    created_at,
    updated_at
)
SELECT
    course_members.teaching_class_id,
    course_members.offering_id,
    course_members.user_id,
    CASE course_members.member_role
        WHEN 'CLASS_INSTRUCTOR' THEN 'TEACHER'
        WHEN 'TA' THEN 'TA'
        WHEN 'STUDENT' THEN 'STUDENT'
    END,
    course_members.member_status,
    course_members.source_type,
    course_members.id,
    course_members.remark,
    course_members.joined_at,
    course_members.left_at,
    course_members.created_at,
    course_members.updated_at
FROM course_members
WHERE course_members.member_role IN ('CLASS_INSTRUCTOR', 'TA', 'STUDENT')
  AND course_members.teaching_class_id IS NOT NULL;

INSERT INTO role_bindings (
    user_id,
    role_id,
    scope_type,
    scope_id,
    constraints_json,
    status,
    effective_from,
    effective_to,
    granted_by,
    source_type,
    source_ref_id,
    created_at,
    updated_at
)
SELECT
    user_scope_roles.user_id,
    roles.id,
    lower(org_units.type),
    user_scope_roles.scope_org_unit_id,
    '{}'::jsonb,
    'ACTIVE',
    user_scope_roles.created_at,
    NULL,
    NULL,
    'LEGACY_GOVERNANCE',
    user_scope_roles.id,
    user_scope_roles.created_at,
    user_scope_roles.created_at
FROM user_scope_roles
JOIN org_units ON org_units.id = user_scope_roles.scope_org_unit_id
JOIN roles ON roles.code = CASE user_scope_roles.role_code
    WHEN 'SCHOOL_ADMIN' THEN 'school_admin'
    WHEN 'COLLEGE_ADMIN' THEN 'college_admin'
    WHEN 'COURSE_ADMIN' THEN 'course_manager'
    WHEN 'CLASS_ADMIN' THEN 'class_admin'
END;

INSERT INTO role_bindings (
    user_id,
    role_id,
    scope_type,
    scope_id,
    constraints_json,
    status,
    effective_from,
    effective_to,
    granted_by,
    source_type,
    source_ref_id,
    created_at,
    updated_at
)
SELECT
    course_members.user_id,
    roles.id,
    CASE
        WHEN course_members.member_role IN ('INSTRUCTOR', 'OFFERING_TA', 'OBSERVER') THEN 'offering'
        ELSE 'class'
    END,
    CASE
        WHEN course_members.member_role IN ('INSTRUCTOR', 'OFFERING_TA', 'OBSERVER') THEN course_members.offering_id
        ELSE course_members.teaching_class_id
    END,
    '{}'::jsonb,
    CASE WHEN course_members.member_status = 'ACTIVE' THEN 'ACTIVE' ELSE 'INACTIVE' END,
    course_members.joined_at,
    course_members.left_at,
    NULL,
    'LEGACY_COURSE_MEMBER',
    course_members.id,
    course_members.created_at,
    course_members.updated_at
FROM course_members
JOIN roles ON roles.code = CASE course_members.member_role
    WHEN 'INSTRUCTOR' THEN 'offering_teacher'
    WHEN 'CLASS_INSTRUCTOR' THEN 'class_teacher'
    WHEN 'OFFERING_TA' THEN 'offering_ta'
    WHEN 'TA' THEN 'class_ta'
    WHEN 'STUDENT' THEN 'student'
    WHEN 'OBSERVER' THEN 'observer'
END
WHERE (
        course_members.member_role IN ('INSTRUCTOR', 'OFFERING_TA', 'OBSERVER')
        AND course_members.offering_id IS NOT NULL
    )
   OR (
        course_members.member_role IN ('CLASS_INSTRUCTOR', 'TA', 'STUDENT')
        AND course_members.teaching_class_id IS NOT NULL
    );

INSERT INTO role_bindings (
    user_id,
    role_id,
    scope_type,
    scope_id,
    constraints_json,
    status,
    effective_from,
    effective_to,
    granted_by,
    source_type,
    source_ref_id,
    created_at,
    updated_at
)
SELECT
    auth_group_members.user_id,
    roles.id,
    lower(auth_groups.scope_type),
    auth_groups.scope_ref_id,
    '{}'::jsonb,
    CASE
        WHEN auth_groups.status = 'ACTIVE'
            AND (auth_group_members.expires_at IS NULL OR auth_group_members.expires_at > now())
            THEN 'ACTIVE'
        ELSE 'INACTIVE'
    END,
    auth_group_members.joined_at,
    auth_group_members.expires_at,
    NULL,
    'LEGACY_AUTHZ_GROUP',
    auth_group_members.id,
    auth_group_members.created_at,
    auth_group_members.updated_at
FROM auth_group_members
JOIN auth_groups ON auth_groups.id = auth_group_members.group_id
JOIN auth_group_templates ON auth_group_templates.id = auth_groups.template_id
JOIN roles ON roles.code = CASE auth_group_templates.code
    WHEN 'school-admin' THEN 'school_admin'
    WHEN 'college-admin' THEN 'college_admin'
    WHEN 'course-admin' THEN 'course_manager'
    WHEN 'class-admin' THEN 'class_admin'
    WHEN 'offering-instructor' THEN 'offering_teacher'
    WHEN 'class-instructor' THEN 'class_teacher'
    WHEN 'offering-ta' THEN 'offering_ta'
    WHEN 'class-ta' THEN 'class_ta'
    WHEN 'student' THEN 'student'
    WHEN 'observer' THEN 'observer'
    WHEN 'audit-readonly' THEN 'auditor'
    WHEN 'grade-corrector' THEN 'grader'
END;

CREATE OR REPLACE FUNCTION sync_member_compatibility_tables() RETURNS TRIGGER AS $$
BEGIN
    DELETE FROM offering_members
    WHERE legacy_course_member_id = COALESCE(NEW.id, OLD.id);

    DELETE FROM class_members
    WHERE legacy_course_member_id = COALESCE(NEW.id, OLD.id);

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    IF NEW.member_role IN ('INSTRUCTOR', 'OFFERING_TA', 'OBSERVER') THEN
        INSERT INTO offering_members (
            offering_id,
            user_id,
            member_role,
            member_status,
            source_type,
            legacy_course_member_id,
            remark,
            joined_at,
            left_at,
            created_at,
            updated_at
        )
        VALUES (
            NEW.offering_id,
            NEW.user_id,
            CASE NEW.member_role
                WHEN 'INSTRUCTOR' THEN 'TEACHER'
                WHEN 'OFFERING_TA' THEN 'TA'
                WHEN 'OBSERVER' THEN 'OBSERVER'
            END,
            NEW.member_status,
            NEW.source_type,
            NEW.id,
            NEW.remark,
            NEW.joined_at,
            NEW.left_at,
            NEW.created_at,
            NEW.updated_at
        );
    ELSIF NEW.member_role IN ('CLASS_INSTRUCTOR', 'TA', 'STUDENT') AND NEW.teaching_class_id IS NOT NULL THEN
        INSERT INTO class_members (
            class_id,
            offering_id,
            user_id,
            member_role,
            member_status,
            source_type,
            legacy_course_member_id,
            remark,
            joined_at,
            left_at,
            created_at,
            updated_at
        )
        VALUES (
            NEW.teaching_class_id,
            NEW.offering_id,
            NEW.user_id,
            CASE NEW.member_role
                WHEN 'CLASS_INSTRUCTOR' THEN 'TEACHER'
                WHEN 'TA' THEN 'TA'
                WHEN 'STUDENT' THEN 'STUDENT'
            END,
            NEW.member_status,
            NEW.source_type,
            NEW.id,
            NEW.remark,
            NEW.joined_at,
            NEW.left_at,
            NEW.created_at,
            NEW.updated_at
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sync_role_bindings_from_user_scope_roles() RETURNS TRIGGER AS $$
DECLARE
    binding_role_code TEXT;
    binding_scope_type TEXT;
    binding_created_at TIMESTAMPTZ;
BEGIN
    DELETE FROM role_bindings
    WHERE source_type = 'LEGACY_GOVERNANCE'
      AND source_ref_id = COALESCE(NEW.id, OLD.id);

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    binding_role_code := CASE NEW.role_code
        WHEN 'SCHOOL_ADMIN' THEN 'school_admin'
        WHEN 'COLLEGE_ADMIN' THEN 'college_admin'
        WHEN 'COURSE_ADMIN' THEN 'course_manager'
        WHEN 'CLASS_ADMIN' THEN 'class_admin'
    END;
    binding_created_at := NEW.created_at;

    SELECT lower(org_units.type)
    INTO binding_scope_type
    FROM org_units
    WHERE org_units.id = NEW.scope_org_unit_id;

    IF binding_role_code IS NOT NULL AND binding_scope_type IS NOT NULL THEN
        INSERT INTO role_bindings (
            user_id,
            role_id,
            scope_type,
            scope_id,
            constraints_json,
            status,
            effective_from,
            effective_to,
            granted_by,
            source_type,
            source_ref_id,
            created_at,
            updated_at
        )
        SELECT
            NEW.user_id,
            roles.id,
            binding_scope_type,
            NEW.scope_org_unit_id,
            '{}'::jsonb,
            'ACTIVE',
            binding_created_at,
            NULL,
            NULL,
            'LEGACY_GOVERNANCE',
            NEW.id,
            binding_created_at,
            binding_created_at
        FROM roles
        WHERE roles.code = binding_role_code;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sync_role_bindings_from_course_members() RETURNS TRIGGER AS $$
DECLARE
    binding_role_code TEXT;
    binding_scope_type TEXT;
    binding_scope_id BIGINT;
BEGIN
    DELETE FROM role_bindings
    WHERE source_type = 'LEGACY_COURSE_MEMBER'
      AND source_ref_id = COALESCE(NEW.id, OLD.id);

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    binding_role_code := CASE NEW.member_role
        WHEN 'INSTRUCTOR' THEN 'offering_teacher'
        WHEN 'CLASS_INSTRUCTOR' THEN 'class_teacher'
        WHEN 'OFFERING_TA' THEN 'offering_ta'
        WHEN 'TA' THEN 'class_ta'
        WHEN 'STUDENT' THEN 'student'
        WHEN 'OBSERVER' THEN 'observer'
    END;

    binding_scope_type := CASE
        WHEN NEW.member_role IN ('INSTRUCTOR', 'OFFERING_TA', 'OBSERVER') THEN 'offering'
        ELSE 'class'
    END;

    binding_scope_id := CASE
        WHEN NEW.member_role IN ('INSTRUCTOR', 'OFFERING_TA', 'OBSERVER') THEN NEW.offering_id
        ELSE NEW.teaching_class_id
    END;

    IF binding_role_code IS NOT NULL AND binding_scope_id IS NOT NULL THEN
        INSERT INTO role_bindings (
            user_id,
            role_id,
            scope_type,
            scope_id,
            constraints_json,
            status,
            effective_from,
            effective_to,
            granted_by,
            source_type,
            source_ref_id,
            created_at,
            updated_at
        )
        SELECT
            NEW.user_id,
            roles.id,
            binding_scope_type,
            binding_scope_id,
            '{}'::jsonb,
            CASE WHEN NEW.member_status = 'ACTIVE' THEN 'ACTIVE' ELSE 'INACTIVE' END,
            NEW.joined_at,
            NEW.left_at,
            NULL,
            'LEGACY_COURSE_MEMBER',
            NEW.id,
            NEW.created_at,
            NEW.updated_at
        FROM roles
        WHERE roles.code = binding_role_code;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sync_role_bindings_from_auth_group_members() RETURNS TRIGGER AS $$
DECLARE
    binding_role_code TEXT;
    binding_scope_type TEXT;
    binding_scope_id BIGINT;
    binding_status TEXT;
BEGIN
    DELETE FROM role_bindings
    WHERE source_type = 'LEGACY_AUTHZ_GROUP'
      AND source_ref_id = COALESCE(NEW.id, OLD.id);

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    SELECT
        CASE auth_group_templates.code
            WHEN 'school-admin' THEN 'school_admin'
            WHEN 'college-admin' THEN 'college_admin'
            WHEN 'course-admin' THEN 'course_manager'
            WHEN 'class-admin' THEN 'class_admin'
            WHEN 'offering-instructor' THEN 'offering_teacher'
            WHEN 'class-instructor' THEN 'class_teacher'
            WHEN 'offering-ta' THEN 'offering_ta'
            WHEN 'class-ta' THEN 'class_ta'
            WHEN 'student' THEN 'student'
            WHEN 'observer' THEN 'observer'
            WHEN 'audit-readonly' THEN 'auditor'
            WHEN 'grade-corrector' THEN 'grader'
        END,
        lower(auth_groups.scope_type),
        auth_groups.scope_ref_id,
        CASE
            WHEN auth_groups.status = 'ACTIVE'
                AND (NEW.expires_at IS NULL OR NEW.expires_at > now())
                THEN 'ACTIVE'
            ELSE 'INACTIVE'
        END
    INTO binding_role_code, binding_scope_type, binding_scope_id, binding_status
    FROM auth_groups
    JOIN auth_group_templates ON auth_group_templates.id = auth_groups.template_id
    WHERE auth_groups.id = NEW.group_id;

    IF binding_role_code IS NOT NULL AND binding_scope_id IS NOT NULL THEN
        INSERT INTO role_bindings (
            user_id,
            role_id,
            scope_type,
            scope_id,
            constraints_json,
            status,
            effective_from,
            effective_to,
            granted_by,
            source_type,
            source_ref_id,
            created_at,
            updated_at
        )
        SELECT
            NEW.user_id,
            roles.id,
            binding_scope_type,
            binding_scope_id,
            '{}'::jsonb,
            binding_status,
            NEW.joined_at,
            NEW.expires_at,
            NULL,
            'LEGACY_AUTHZ_GROUP',
            NEW.id,
            NEW.created_at,
            NEW.updated_at
        FROM roles
        WHERE roles.code = binding_role_code;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sync_member_compatibility_tables
AFTER INSERT OR UPDATE OR DELETE ON course_members
FOR EACH ROW EXECUTE FUNCTION sync_member_compatibility_tables();

CREATE TRIGGER trg_sync_role_bindings_from_user_scope_roles
AFTER INSERT OR UPDATE OR DELETE ON user_scope_roles
FOR EACH ROW EXECUTE FUNCTION sync_role_bindings_from_user_scope_roles();

CREATE TRIGGER trg_sync_role_bindings_from_course_members
AFTER INSERT OR UPDATE OR DELETE ON course_members
FOR EACH ROW EXECUTE FUNCTION sync_role_bindings_from_course_members();

CREATE TRIGGER trg_sync_role_bindings_from_auth_group_members
AFTER INSERT OR UPDATE OR DELETE ON auth_group_members
FOR EACH ROW EXECUTE FUNCTION sync_role_bindings_from_auth_group_members();
