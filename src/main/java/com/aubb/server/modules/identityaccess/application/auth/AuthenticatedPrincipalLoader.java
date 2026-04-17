package com.aubb.server.modules.identityaccess.application.auth;

import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityService;
import com.aubb.server.modules.identityaccess.application.user.view.AcademicProfileView;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.aubb.server.modules.identityaccess.domain.profile.AcademicIdentityType;
import com.aubb.server.modules.identityaccess.domain.profile.AcademicProfileStatus;
import com.aubb.server.modules.identityaccess.infrastructure.profile.AcademicProfileEntity;
import com.aubb.server.modules.identityaccess.infrastructure.profile.AcademicProfileMapper;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserEntity;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticatedPrincipalLoader {

    private final UserMapper userMapper;
    private final AcademicProfileMapper academicProfileMapper;
    private final ScopeIdentityService scopeIdentityService;

    @Transactional(readOnly = true)
    public AuthenticatedUserPrincipal loadPrincipal(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (!isAccountLoginAllowed(user, OffsetDateTime.now())) {
            return null;
        }
        return new AuthenticatedUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPrimaryOrgUnitId(),
                AccountStatus.valueOf(user.getAccountStatus()),
                loadAcademicProfile(user.getId()),
                scopeIdentityService.loadForUser(user.getId()));
    }

    @Transactional(readOnly = true)
    public boolean isUserAllowedToAuthenticate(Long userId) {
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getId, userId)
                .select(
                        UserEntity::getId,
                        UserEntity::getAccountStatus,
                        UserEntity::getLockedUntil,
                        UserEntity::getExpiresAt)
                .last("LIMIT 1"));
        return isAccountLoginAllowed(user, OffsetDateTime.now());
    }

    private boolean isAccountLoginAllowed(UserEntity user, OffsetDateTime now) {
        if (user == null) {
            return false;
        }
        if (user.getExpiresAt() != null && user.getExpiresAt().isBefore(now)) {
            return false;
        }
        if (AccountStatus.DISABLED.name().equals(user.getAccountStatus())) {
            return false;
        }
        return !AccountStatus.LOCKED.name().equals(user.getAccountStatus())
                || (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(now));
    }

    private AcademicProfileView loadAcademicProfile(Long userId) {
        AcademicProfileEntity profile = academicProfileMapper.selectOne(Wrappers.<AcademicProfileEntity>lambdaQuery()
                .eq(AcademicProfileEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (profile == null) {
            return null;
        }
        return new AcademicProfileView(
                profile.getId(),
                profile.getUserId(),
                profile.getAcademicId(),
                profile.getRealName(),
                AcademicIdentityType.valueOf(profile.getIdentityType()),
                AcademicProfileStatus.valueOf(profile.getProfileStatus()),
                profile.getPhone());
    }
}
