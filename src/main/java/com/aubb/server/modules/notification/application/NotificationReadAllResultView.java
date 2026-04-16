package com.aubb.server.modules.notification.application;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationReadAllResultView(
        @Schema(description = "本次标记已读数量") long updatedCount,
        @Schema(description = "操作完成后的未读数量") long unreadCount) {}
