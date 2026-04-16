package com.aubb.server.modules.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class NotificationReceiptLifecyclePolicyTests {

    private final NotificationReceiptLifecyclePolicy policy = new NotificationReceiptLifecyclePolicy();

    @Test
    void markReadUsesRequestedTimeWhenReceiptIsUnread() {
        OffsetDateTime requestedReadAt = OffsetDateTime.parse("2026-04-17T10:15:30+08:00");

        assertThat(policy.markRead(null, requestedReadAt)).isEqualTo(requestedReadAt);
    }

    @Test
    void markReadKeepsOriginalReadTimeWhenReceiptAlreadyRead() {
        OffsetDateTime originalReadAt = OffsetDateTime.parse("2026-04-17T09:00:00+08:00");
        OffsetDateTime requestedReadAt = OffsetDateTime.parse("2026-04-17T10:15:30+08:00");

        assertThat(policy.markRead(originalReadAt, requestedReadAt)).isEqualTo(originalReadAt);
    }
}
