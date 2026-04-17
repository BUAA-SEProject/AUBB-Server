package com.aubb.server.modules.notification.application;

import com.aubb.server.modules.notification.domain.NotificationType;
import java.util.List;
import java.util.Map;

record NotificationFanoutCommand(
        NotificationType type,
        String title,
        String body,
        Long actorUserId,
        String targetType,
        String targetId,
        Long offeringId,
        Long teachingClassId,
        Map<String, Object> metadata,
        List<Long> recipientUserIds) {}
