DROP INDEX IF EXISTS ux_course_members_student_unique;

CREATE UNIQUE INDEX IF NOT EXISTS ux_course_members_student_active_unique
    ON course_members (offering_id, user_id)
    WHERE member_role = 'STUDENT' AND member_status = 'ACTIVE';
