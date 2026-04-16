package com.aubb.server.modules.notification.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

public class NotificationReceiptLifecyclePolicy {

    public OffsetDateTime markRead(OffsetDateTime currentReadAt, OffsetDateTime requestedReadAt) {
        if (currentReadAt != null) {
            return currentReadAt;
        }
        return Objects.requireNonNull(requestedReadAt, "requestedReadAt");
    }
}
