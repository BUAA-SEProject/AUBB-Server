CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    actor_user_id BIGINT NULL REFERENCES users (id) ON DELETE SET NULL,
    target_type VARCHAR(64),
    target_id VARCHAR(64),
    offering_id BIGINT NULL REFERENCES course_offerings (id) ON DELETE SET NULL,
    teaching_class_id BIGINT NULL REFERENCES teaching_classes (id) ON DELETE SET NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notification_receipts (
    id BIGSERIAL PRIMARY KEY,
    notification_id BIGINT NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    recipient_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    read_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_notification_receipts_notification_recipient UNIQUE (notification_id, recipient_user_id)
);

CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC, id DESC);
CREATE INDEX idx_notifications_target ON notifications (target_type, target_id);
CREATE INDEX idx_notification_receipts_recipient_created
    ON notification_receipts (recipient_user_id, created_at DESC, id DESC);
CREATE INDEX idx_notification_receipts_recipient_unread
    ON notification_receipts (recipient_user_id)
    WHERE read_at IS NULL;
