package com.aubb.server.modules.notification.application;

import com.aubb.server.modules.notification.domain.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Map;

public record NotificationView(
        @Schema(description = "通知编号") Long id,
        @Schema(description = "通知类型") NotificationType type,
        @Schema(description = "通知标题") String title,
        @Schema(description = "通知正文") String body,
        @Schema(description = "通知发起人用户编号，系统事件可为空") Long actorUserId,
        @Schema(description = "目标资源类型") String targetType,
        @Schema(description = "目标资源编号") String targetId,
        @Schema(description = "开课编号") Long offeringId,
        @Schema(description = "教学班编号") Long teachingClassId,
        @Schema(description = "附加元数据") Map<String, Object> metadata,
        @Schema(description = "是否已读") boolean read,
        @Schema(description = "已读时间") OffsetDateTime readAt,
        @Schema(description = "创建时间") OffsetDateTime createdAt) {}
