package com.aubb.server.common.realtime;

public class NoOpRealtimeCoordinationService implements RealtimeCoordinationService {

    @Override
    public void publish(String topic, String payload) {
        // 未来多实例实时协调关闭时保持 no-op。
    }
}
