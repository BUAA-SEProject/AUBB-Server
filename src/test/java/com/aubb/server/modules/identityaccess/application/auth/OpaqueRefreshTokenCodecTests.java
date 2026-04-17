package com.aubb.server.modules.identityaccess.application.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpaqueRefreshTokenCodecTests {

    private final OpaqueRefreshTokenCodec codec = new OpaqueRefreshTokenCodec();

    @Test
    void issuesOpaqueRefreshTokenWithStableSessionPrefix() {
        String token = codec.issue("session-123");
        OpaqueRefreshTokenCodec.RefreshTokenPayload payload = codec.parse(token);

        assertThat(payload.sessionId()).isEqualTo("session-123");
        assertThat(payload.secret()).isNotBlank();
        assertThat(codec.matches(token, codec.hash(token))).isTrue();
    }

    @Test
    void differentRefreshTokensDoNotShareTheSameHash() {
        String firstToken = codec.issue("session-123");
        String secondToken = codec.issue("session-123");

        assertThat(firstToken).isNotEqualTo(secondToken);
        assertThat(codec.hash(firstToken)).isNotEqualTo(codec.hash(secondToken));
    }
}
