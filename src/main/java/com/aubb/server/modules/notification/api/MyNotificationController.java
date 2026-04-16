package com.aubb.server.modules.notification.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.notification.application.NotificationApplicationService;
import com.aubb.server.modules.notification.application.NotificationReadAllResultView;
import com.aubb.server.modules.notification.application.NotificationUnreadCountView;
import com.aubb.server.modules.notification.application.NotificationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/me/notifications")
@Tag(name = "My Notifications")
public class MyNotificationController {

    private final NotificationApplicationService notificationApplicationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "分页查看我的站内通知")
    public PageResponse<NotificationView> list(
            @RequestParam(defaultValue = "1") @Positive long page,
            @RequestParam(defaultValue = "20") @Positive long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return notificationApplicationService.listMyNotifications(page, pageSize, principal);
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "查看我的未读通知数")
    public NotificationUnreadCountView unreadCount(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return notificationApplicationService.getUnreadCount(principal);
    }

    @PostMapping("/{notificationId}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "将单条通知标记为已读")
    public NotificationView markRead(
            @PathVariable Long notificationId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return notificationApplicationService.markRead(notificationId, principal);
    }

    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "将我的全部通知标记为已读")
    public NotificationReadAllResultView markAllRead(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return notificationApplicationService.markAllRead(principal);
    }
}
