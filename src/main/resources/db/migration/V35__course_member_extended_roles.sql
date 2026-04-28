ALTER TABLE course_members
    DROP CONSTRAINT IF EXISTS course_members_member_role_check;

ALTER TABLE course_members
    ADD CONSTRAINT course_members_member_role_check CHECK (member_role IN (
        'INSTRUCTOR',
        'CLASS_INSTRUCTOR',
        'OFFERING_TA',
        'TA',
        'STUDENT',
        'OBSERVER'
    ));

CREATE UNIQUE INDEX IF NOT EXISTS ux_course_members_class_instructor_unique
    ON course_members (offering_id, user_id, teaching_class_id)
    WHERE member_role = 'CLASS_INSTRUCTOR';

CREATE UNIQUE INDEX IF NOT EXISTS ux_course_members_offering_ta_unique
    ON course_members (offering_id, user_id)
    WHERE member_role = 'OFFERING_TA';
