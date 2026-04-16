package com.aubb.server.modules.notification.application;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.notification.domain.NotificationReceiptLifecyclePolicy;
import com.aubb.server.modules.notification.domain.NotificationType;
import com.aubb.server.modules.notification.infrastructure.NotificationEntity;
import com.aubb.server.modules.notification.infrastructure.NotificationMapper;
import com.aubb.server.modules.notification.infrastructure.NotificationReceiptEntity;
import com.aubb.server.modules.notification.infrastructure.NotificationReceiptMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationApplicationService {

    private final NotificationMapper notificationMapper;
    private final NotificationReceiptMapper notificationReceiptMapper;
    private final NotificationReceiptLifecyclePolicy receiptLifecyclePolicy = new NotificationReceiptLifecyclePolicy();

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> listMyNotifications(
            long page, long pageSize, AuthenticatedUserPrincipal principal) {
        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        Long total = notificationReceiptMapper.selectCount(Wrappers.<NotificationReceiptEntity>lambdaQuery()
                .eq(NotificationReceiptEntity::getRecipientUserId, principal.getUserId()));
        long offset = (safePage - 1) * safePageSize;
        List<NotificationReceiptEntity> receipts =
                notificationReceiptMapper.selectList(Wrappers.<NotificationReceiptEntity>lambdaQuery()
                        .eq(NotificationReceiptEntity::getRecipientUserId, principal.getUserId())
                        .orderByDesc(NotificationReceiptEntity::getCreatedAt)
                        .orderByDesc(NotificationReceiptEntity::getId)
                        .last("LIMIT " + safePageSize + " OFFSET " + offset));
        Map<Long, NotificationEntity> notificationIndex = loadNotificationIndex(receipts.stream()
                .map(NotificationReceiptEntity::getNotificationId)
                .toList());
        List<NotificationView> items = receipts.stream()
                .map(receipt -> toView(notificationIndex.get(receipt.getNotificationId()), receipt))
                .filter(Objects::nonNull)
                .toList();
        return new PageResponse<>(items, total == null ? 0 : total, safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountView getUnreadCount(AuthenticatedUserPrincipal principal) {
        Long unreadCount = notificationReceiptMapper.selectCount(Wrappers.<NotificationReceiptEntity>lambdaQuery()
                .eq(NotificationReceiptEntity::getRecipientUserId, principal.getUserId())
                .isNull(NotificationReceiptEntity::getReadAt));
        return new NotificationUnreadCountView(unreadCount == null ? 0 : unreadCount);
    }

    @Transactional
    public NotificationView markRead(Long notificationId, AuthenticatedUserPrincipal principal) {
        NotificationReceiptEntity receipt = requireReceipt(notificationId, principal.getUserId());
        OffsetDateTime resolvedReadAt = receiptLifecyclePolicy.markRead(receipt.getReadAt(), OffsetDateTime.now());
        if (!Objects.equals(receipt.getReadAt(), resolvedReadAt)) {
            receipt.setReadAt(resolvedReadAt);
            notificationReceiptMapper.updateById(receipt);
        }
        NotificationEntity notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "通知不存在");
        }
        return toView(notification, receipt);
    }

    @Transactional
    public NotificationReadAllResultView markAllRead(AuthenticatedUserPrincipal principal) {
        Long unreadCount = notificationReceiptMapper.selectCount(Wrappers.<NotificationReceiptEntity>lambdaQuery()
                .eq(NotificationReceiptEntity::getRecipientUserId, principal.getUserId())
                .isNull(NotificationReceiptEntity::getReadAt));
        long updatedCount = unreadCount == null ? 0 : unreadCount;
        if (updatedCount > 0) {
            NotificationReceiptEntity update = new NotificationReceiptEntity();
            update.setReadAt(OffsetDateTime.now());
            notificationReceiptMapper.update(
                    update,
                    Wrappers.<NotificationReceiptEntity>lambdaUpdate()
                            .eq(NotificationReceiptEntity::getRecipientUserId, principal.getUserId())
                            .isNull(NotificationReceiptEntity::getReadAt));
        }
        return new NotificationReadAllResultView(updatedCount, 0);
    }

    private NotificationReceiptEntity requireReceipt(Long notificationId, Long userId) {
        NotificationReceiptEntity receipt =
                notificationReceiptMapper.selectOne(Wrappers.<NotificationReceiptEntity>lambdaQuery()
                        .eq(NotificationReceiptEntity::getNotificationId, notificationId)
                        .eq(NotificationReceiptEntity::getRecipientUserId, userId)
                        .last("LIMIT 1"));
        if (receipt == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "通知不存在");
        }
        return receipt;
    }

    private Map<Long, NotificationEntity> loadNotificationIndex(Collection<Long> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return Map.of();
        }
        return notificationMapper.selectBatchIds(notificationIds).stream()
                .collect(Collectors.toMap(NotificationEntity::getId, Function.identity()));
    }

    private NotificationView toView(NotificationEntity notification, NotificationReceiptEntity receipt) {
        if (notification == null || receipt == null) {
            return null;
        }
        return new NotificationView(
                notification.getId(),
                NotificationType.valueOf(notification.getType()),
                notification.getTitle(),
                notification.getBody(),
                notification.getActorUserId(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getOfferingId(),
                notification.getTeachingClassId(),
                notification.getMetadata(),
                receipt.getReadAt() != null,
                receipt.getReadAt(),
                notification.getCreatedAt());
    }
}
