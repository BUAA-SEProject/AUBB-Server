package com.aubb.server.modules.notification.application;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationUnreadCountView(
        @Schema(description = "未读通知数") long unreadCount) {}
