CREATE INDEX idx_users_primary_org_unit_created_at_id
    ON users (primary_org_unit_id, created_at DESC, id DESC);

CREATE INDEX idx_user_scope_roles_role_code_user_id
    ON user_scope_roles (role_code, user_id);

CREATE INDEX idx_course_members_user_offering_status_class
    ON course_members (user_id, offering_id, member_status, teaching_class_id);

CREATE INDEX idx_assignments_visible_open_at_id
    ON assignments (open_at ASC, id ASC)
    WHERE status <> 'DRAFT';
