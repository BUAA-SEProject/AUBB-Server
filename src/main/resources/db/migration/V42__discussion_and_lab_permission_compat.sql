INSERT INTO permissions (code, resource_type, action, description, sensitive)
SELECT seed.code, seed.resource_type, seed.action, seed.description, seed.sensitive
FROM (
    VALUES
        ('discussion.participate', 'discussion', 'participate', '参与课程讨论', FALSE),
        ('discussion.manage', 'discussion', 'manage', '管理课程讨论', FALSE),
        ('lab.read', 'lab', 'read', '查看实验与实验报告', FALSE),
        ('lab.manage', 'lab', 'manage', '创建、发布和关闭实验', FALSE),
        ('lab.report.review', 'lab', 'review', '评审和发布实验报告', TRUE)
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
        ('offering_coordinator', 'discussion.manage'),
        ('offering_coordinator', 'lab.read'),
        ('offering_coordinator', 'lab.manage'),
        ('offering_coordinator', 'lab.report.review'),
        ('offering_teacher', 'discussion.manage'),
        ('offering_teacher', 'lab.read'),
        ('offering_teacher', 'lab.manage'),
        ('offering_teacher', 'lab.report.review'),
        ('class_teacher', 'discussion.manage'),
        ('class_teacher', 'lab.read'),
        ('class_teacher', 'lab.manage'),
        ('class_teacher', 'lab.report.review'),
        ('offering_ta', 'announcement.read'),
        ('offering_ta', 'resource.read'),
        ('offering_ta', 'discussion.participate'),
        ('offering_ta', 'lab.read'),
        ('offering_ta', 'lab.report.review'),
        ('class_ta', 'announcement.read'),
        ('class_ta', 'resource.read'),
        ('class_ta', 'discussion.participate'),
        ('class_ta', 'lab.read'),
        ('class_ta', 'lab.report.review'),
        ('student', 'discussion.participate'),
        ('student', 'lab.read')
) AS bindings(role_code, permission_code)
JOIN roles ON roles.code = bindings.role_code
JOIN permissions ON permissions.code = bindings.permission_code
LEFT JOIN role_permissions existing
        ON existing.role_id = roles.id
       AND existing.permission_id = permissions.id
WHERE existing.id IS NULL;
