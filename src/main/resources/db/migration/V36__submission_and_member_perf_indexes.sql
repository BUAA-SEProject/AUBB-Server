CREATE INDEX IF NOT EXISTS idx_submissions_assignment_submitter_submitted_id
    ON submissions (assignment_id, submitter_user_id, submitted_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_submissions_assignment_submitted_id
    ON submissions (assignment_id, submitted_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_course_members_offering_class_role_status_user
    ON course_members (offering_id, teaching_class_id, member_role, member_status, user_id);
