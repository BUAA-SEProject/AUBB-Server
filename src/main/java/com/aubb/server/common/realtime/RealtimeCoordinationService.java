package com.aubb.server.common.realtime;

public interface RealtimeCoordinationService {

    void publish(String topic, String payload);
}
