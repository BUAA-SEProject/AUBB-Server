package com.aubb.server.modules.identityaccess.application.auth;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserEntity;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationApplicationService {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final UserMapper userMapper;
    private final AuthenticatedPrincipalLoader authenticatedPrincipalLoader;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogApplicationService auditLogApplicationService;

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthenticatedUserPrincipal login(String username, String rawPassword) {
        String normalizedUsername = normalizeUsername(username);
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, normalizedUsername)
                .last("LIMIT 1"));

        if (user == null) {
            auditLogApplicationService.record(
                    null,
                    AuditAction.LOGIN_FAILED,
                    "USER",
                    normalizedUsername,
                    AuditResult.FAILURE,
                    Map.of("reason", "INVALID_CREDENTIALS"));
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
        }

        refreshExpiredAutoLock(user);
        ensureAccountLoginAllowed(user);

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            return handleFailedPassword(user);
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setAccountStatus(AccountStatus.ACTIVE.name());
        user.setLastLoginAt(OffsetDateTime.now());
        userMapper.updateById(user);

        AuthenticatedUserPrincipal principal = authenticatedPrincipalLoader.loadPrincipal(user.getId());
        auditLogApplicationService.record(
                user.getId(),
                AuditAction.LOGIN_SUCCESS,
                "USER",
                String.valueOf(user.getId()),
                AuditResult.SUCCESS,
                Map.of("username", user.getUsername()));
        return principal;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserPrincipal loadPrincipal(Long userId) {
        return authenticatedPrincipalLoader.loadPrincipal(userId);
    }

    private void refreshExpiredAutoLock(UserEntity user) {
        if (AccountStatus.LOCKED.name().equals(user.getAccountStatus())
                && user.getLockedUntil() != null
                && user.getLockedUntil().isBefore(OffsetDateTime.now())) {
            user.setAccountStatus(AccountStatus.ACTIVE.name());
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userMapper.updateById(user);
        }
    }

    private void ensureAccountLoginAllowed(UserEntity user) {
        OffsetDateTime now = OffsetDateTime.now();
        if (user.getExpiresAt() != null && user.getExpiresAt().isBefore(now)) {
            user.setAccountStatus(AccountStatus.EXPIRED.name());
            userMapper.updateById(user);
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_EXPIRED", "账号已失效");
        }
        if (AccountStatus.DISABLED.name().equals(user.getAccountStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "账号已停用");
        }
        if (AccountStatus.EXPIRED.name().equals(user.getAccountStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_EXPIRED", "账号已失效");
        }
        if (AccountStatus.LOCKED.name().equals(user.getAccountStatus())
                && (user.getLockedUntil() == null || user.getLockedUntil().isAfter(now))) {
            throw new BusinessException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "账号已被锁定");
        }
    }

    private AuthenticatedUserPrincipal handleFailedPassword(UserEntity user) {
        int failedAttempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(failedAttempts);
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            user.setAccountStatus(AccountStatus.LOCKED.name());
            user.setLockedUntil(OffsetDateTime.now().plusMinutes(30));
            userMapper.updateById(user);
            auditLogApplicationService.record(
                    user.getId(),
                    AuditAction.LOGIN_FAILED,
                    "USER",
                    String.valueOf(user.getId()),
                    AuditResult.FAILURE,
                    Map.of("reason", "ACCOUNT_LOCKED", "failedAttempts", failedAttempts));
            throw new BusinessException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "账号已被锁定");
        }

        userMapper.updateById(user);
        auditLogApplicationService.record(
                user.getId(),
                AuditAction.LOGIN_FAILED,
                "USER",
                String.valueOf(user.getId()),
                AuditResult.FAILURE,
                Map.of("reason", "INVALID_CREDENTIALS", "failedAttempts", failedAttempts));
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "USERNAME_REQUIRED", "用户名不能为空");
        }
        return username.trim().toLowerCase();
    }
}
