ALTER TABLE course_members
    DROP CONSTRAINT IF EXISTS course_members_member_status_check;

ALTER TABLE course_members
    ADD CONSTRAINT course_members_member_status_check CHECK (
        member_status IN ('PENDING', 'ACTIVE', 'DROPPED', 'TRANSFERRED', 'COMPLETED', 'REMOVED')
    );

ALTER TABLE offering_members
    DROP CONSTRAINT IF EXISTS offering_members_member_status_check;

ALTER TABLE offering_members
    ADD CONSTRAINT offering_members_member_status_check CHECK (
        member_status IN ('PENDING', 'ACTIVE', 'DROPPED', 'TRANSFERRED', 'COMPLETED', 'REMOVED', 'INACTIVE')
    );

ALTER TABLE class_members
    DROP CONSTRAINT IF EXISTS class_members_member_status_check;

ALTER TABLE class_members
    ADD CONSTRAINT class_members_member_status_check CHECK (
        member_status IN ('PENDING', 'ACTIVE', 'DROPPED', 'TRANSFERRED', 'COMPLETED', 'REMOVED', 'INACTIVE')
    );
