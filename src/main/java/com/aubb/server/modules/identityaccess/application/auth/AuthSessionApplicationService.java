package com.aubb.server.modules.identityaccess.application.auth;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.identityaccess.infrastructure.auth.AuthSessionEntity;
import com.aubb.server.modules.identityaccess.infrastructure.auth.AuthSessionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthSessionApplicationService {

    private final AuthSessionMapper authSessionMapper;
    private final OpaqueRefreshTokenCodec opaqueRefreshTokenCodec;
    private final ObjectProvider<JwtTokenService> jwtTokenServiceProvider;
    private final AuthenticatedPrincipalLoader authenticatedPrincipalLoader;
    private final AuditLogApplicationService auditLogApplicationService;

    @Transactional
    public LoginResultView createSession(AuthenticatedUserPrincipal principal) {
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = opaqueRefreshTokenCodec.issue(sessionId);
        OffsetDateTime now = OffsetDateTime.now();

        AuthSessionEntity entity = new AuthSessionEntity();
        entity.setSessionId(sessionId);
        entity.setUserId(principal.getUserId());
        entity.setRefreshTokenHash(opaqueRefreshTokenCodec.hash(refreshToken));
        entity.setRefreshTokenExpiresAt(now.plus(jwtTokenService().refreshTtl()));
        entity.setLastAccessIssuedAt(now);
        authSessionMapper.insert(entity);

        return issueToken(principal, entity, refreshToken);
    }

    @Transactional
    public LoginResultView refresh(String refreshToken) {
        AuthSessionEntity session = requireActiveSession(refreshToken);
        AuthenticatedUserPrincipal principal = authenticatedPrincipalLoader.loadPrincipal(session.getUserId());
        if (principal == null) {
            revokeSession(session, "ACCOUNT_STATUS_INVALID", session.getUserId());
            auditLogApplicationService.record(
                    session.getUserId(),
                    AuditAction.TOKEN_REFRESHED,
                    "AUTH_SESSION",
                    session.getSessionId(),
                    AuditResult.FAILURE,
                    Map.of("reason", "ACCOUNT_STATUS_INVALID", "sessionId", session.getSessionId()));
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "SESSION_INVALIDATED", "当前会话已失效，请重新登录");
        }

        String rotatedRefreshToken = opaqueRefreshTokenCodec.issue(session.getSessionId());
        OffsetDateTime now = OffsetDateTime.now();
        session.setRefreshTokenHash(opaqueRefreshTokenCodec.hash(rotatedRefreshToken));
        session.setRefreshTokenExpiresAt(now.plus(jwtTokenService().refreshTtl()));
        session.setLastRefreshedAt(now);
        session.setLastAccessIssuedAt(now);
        authSessionMapper.updateById(session);

        auditLogApplicationService.record(
                session.getUserId(),
                AuditAction.TOKEN_REFRESHED,
                "AUTH_SESSION",
                session.getSessionId(),
                AuditResult.SUCCESS,
                Map.of("sessionId", session.getSessionId()));
        return issueToken(principal, session, rotatedRefreshToken);
    }

    @Transactional
    public void logoutCurrentSession(AuthenticatedUserPrincipal principal) {
        if (principal == null
                || principal.getSessionId() == null
                || principal.getSessionId().isBlank()) {
            return;
        }
        AuthSessionEntity session = findBySessionId(principal.getSessionId());
        if (session == null) {
            return;
        }
        revokeSession(session, "LOGOUT", principal.getUserId());
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.LOGOUT,
                "AUTH_SESSION",
                session.getSessionId(),
                AuditResult.SUCCESS,
                Map.of("sessionId", session.getSessionId(), "username", principal.getUsername()));
    }

    @Transactional
    public void revokeByRefreshToken(String refreshToken) {
        AuthSessionEntity session = findActiveSession(refreshToken);
        if (session == null) {
            return;
        }
        revokeSession(session, "TOKEN_REVOKED", session.getUserId());
        auditLogApplicationService.record(
                session.getUserId(),
                AuditAction.TOKEN_REVOKED,
                "AUTH_SESSION",
                session.getSessionId(),
                AuditResult.SUCCESS,
                Map.of("sessionId", session.getSessionId()));
    }

    @Transactional
    public int invalidateAllSessionsForUser(Long userId, Long actorUserId, String reason) {
        OffsetDateTime now = OffsetDateTime.now();
        int affected = authSessionMapper.update(
                null,
                Wrappers.<AuthSessionEntity>lambdaUpdate()
                        .eq(AuthSessionEntity::getUserId, userId)
                        .isNull(AuthSessionEntity::getRevokedAt)
                        .set(AuthSessionEntity::getRevokedAt, now)
                        .set(AuthSessionEntity::getRevokedReason, reason)
                        .set(AuthSessionEntity::getRevokedByUserId, actorUserId));
        if (affected > 0 && actorUserId != null) {
            auditLogApplicationService.record(
                    actorUserId,
                    AuditAction.USER_SESSIONS_INVALIDATED,
                    "USER",
                    String.valueOf(userId),
                    AuditResult.SUCCESS,
                    Map.of("reason", reason, "revokedSessionCount", affected));
        }
        return affected;
    }

    @Transactional(readOnly = true)
    public boolean isAccessTokenActive(Long userId, String sessionId) {
        AuthSessionEntity session = findBySessionId(sessionId);
        if (session == null || session.getRevokedAt() != null || !userId.equals(session.getUserId())) {
            return false;
        }
        return authenticatedPrincipalLoader.isUserAllowedToAuthenticate(userId);
    }

    private AuthSessionEntity requireActiveSession(String refreshToken) {
        AuthSessionEntity session = findActiveSession(refreshToken);
        if (session == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "refresh token 无效");
        }
        return session;
    }

    private AuthSessionEntity findActiveSession(String refreshToken) {
        OpaqueRefreshTokenCodec.RefreshTokenPayload payload;
        try {
            payload = opaqueRefreshTokenCodec.parse(refreshToken);
        } catch (BusinessException exception) {
            return null;
        }
        AuthSessionEntity session = findBySessionId(payload.sessionId());
        if (session == null || session.getRevokedAt() != null) {
            return null;
        }
        if (session.getRefreshTokenExpiresAt().isBefore(OffsetDateTime.now())) {
            return null;
        }
        return opaqueRefreshTokenCodec.matches(refreshToken, session.getRefreshTokenHash()) ? session : null;
    }

    private AuthSessionEntity findBySessionId(String sessionId) {
        return authSessionMapper.selectOne(Wrappers.<AuthSessionEntity>lambdaQuery()
                .eq(AuthSessionEntity::getSessionId, sessionId)
                .last("LIMIT 1"));
    }

    private void revokeSession(AuthSessionEntity session, String reason, Long revokedByUserId) {
        if (session.getRevokedAt() != null) {
            return;
        }
        session.setRevokedAt(OffsetDateTime.now());
        session.setRevokedReason(reason);
        session.setRevokedByUserId(revokedByUserId);
        authSessionMapper.updateById(session);
    }

    private JwtTokenService jwtTokenService() {
        return jwtTokenServiceProvider.getObject();
    }

    private LoginResultView issueToken(
            AuthenticatedUserPrincipal principal, AuthSessionEntity session, String refreshToken) {
        Long permissionVersion = session.getLastAccessIssuedAt() == null
                ? null
                : session.getLastAccessIssuedAt().toInstant().toEpochMilli();
        return jwtTokenService().issueToken(principal, session.getSessionId(), refreshToken, permissionVersion);
    }
}
