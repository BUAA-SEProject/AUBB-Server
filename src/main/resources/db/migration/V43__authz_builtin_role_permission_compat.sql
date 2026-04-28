INSERT INTO permissions (code, resource_type, action, description, sensitive)
SELECT seed.code, seed.resource_type, seed.action, seed.description, seed.sensitive
FROM (
    VALUES
        ('auth.group.manage', 'auth_group', 'manage', '管理授权组与授权组成员', TRUE),
        ('auth.explain.read', 'auth_explain', 'read', '查看权限解释结果', TRUE)
) AS seed(code, resource_type, action, description, sensitive)
WHERE NOT EXISTS (
    SELECT 1
    FROM permissions existing
    WHERE lower(existing.code) = lower(seed.code)
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM (
    VALUES
        ('school_admin', 'auth.group.manage'),
        ('school_admin', 'auth.explain.read'),
        ('offering_coordinator', 'judge.config'),
        ('offering_coordinator', 'judge.rejudge'),
        ('offering_teacher', 'grade.import'),
        ('offering_teacher', 'judge.config'),
        ('offering_teacher', 'judge.rejudge'),
        ('class_teacher', 'judge.config'),
        ('class_teacher', 'judge.rejudge')
) AS bindings(role_code, permission_code)
JOIN roles ON roles.code = bindings.role_code
JOIN permissions ON permissions.code = bindings.permission_code
LEFT JOIN role_permissions existing
        ON existing.role_id = roles.id
       AND existing.permission_id = permissions.id
WHERE existing.id IS NULL;
