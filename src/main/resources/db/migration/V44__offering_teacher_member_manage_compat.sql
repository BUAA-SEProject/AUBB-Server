INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM (
    VALUES
        ('offering_teacher', 'member.manage'),
        ('offering_teacher', 'member.import')
) AS bindings(role_code, permission_code)
JOIN roles ON roles.code = bindings.role_code
JOIN permissions ON permissions.code = bindings.permission_code
LEFT JOIN role_permissions existing
       ON existing.role_id = roles.id
      AND existing.permission_id = permissions.id
WHERE existing.id IS NULL;
