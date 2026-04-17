package com.aubb.server.modules.notification.application;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotificationRealtimeService {

    private static final long SSE_TIMEOUT_MILLIS = 30L * 60L * 1000L;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        emittersByUserId
                .computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>())
                .add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(error -> removeEmitter(userId, emitter));
        send(emitter, "connected", Map.of("timestamp", OffsetDateTime.now().toString()));
        return emitter;
    }

    public void publish(Map<Long, NotificationView> viewsByRecipient) {
        if (viewsByRecipient == null || viewsByRecipient.isEmpty()) {
            return;
        }
        viewsByRecipient.forEach((userId, view) -> {
            List<SseEmitter> emitters = emittersByUserId.get(userId);
            if (emitters == null || emitters.isEmpty()) {
                return;
            }
            emitters.forEach(emitter -> send(emitter, "notification", view));
        });
    }

    private void send(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException | IllegalStateException exception) {
            emitter.complete();
        }
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUserId.remove(userId);
        }
    }
}
