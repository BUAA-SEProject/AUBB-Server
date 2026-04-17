package com.aubb.server.modules.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
class NotificationFanoutListener {

    private final NotificationDispatchService notificationDispatchService;

    @Async("notificationFanoutTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationFanoutRequested(NotificationFanoutRequestedEvent event) {
        try {
            notificationDispatchService.persistFanout(event.command());
        } catch (RuntimeException exception) {
            log.error("Notification fan-out failed, event={}", event, exception);
            throw exception;
        }
    }
}
