package com.aubb.server.common.realtime;

import com.aubb.server.common.redis.RedisAvailabilityTracker;
import com.aubb.server.common.redis.RedisKeyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

@RequiredArgsConstructor
public class RedisRealtimeCoordinationService implements RealtimeCoordinationService {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory redisKeyFactory;
    private final RedisAvailabilityTracker availabilityTracker;

    @Override
    public void publish(String topic, String payload) {
        try {
            redisTemplate.convertAndSend(redisKeyFactory.realtimeChannel(topic), payload == null ? "" : payload);
            availabilityTracker.recordSuccess();
        } catch (Exception exception) {
            availabilityTracker.recordFailure("realtime.publish", exception);
        }
    }
}
