package com.aubb.server.modules.identityaccess.application.auth;

import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.course.domain.member.CourseMemberStatus;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberEntity;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.aubb.server.modules.identityaccess.application.authz.AuthzScopeResolutionService;
import com.aubb.server.modules.identityaccess.application.authz.GroupBindingView;
import com.aubb.server.modules.identityaccess.application.authz.PermissionGrantResolver;
import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityService;
import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityView;
import com.aubb.server.modules.identityaccess.application.user.view.AcademicProfileView;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.aubb.server.modules.identityaccess.domain.profile.AcademicIdentityType;
import com.aubb.server.modules.identityaccess.domain.profile.AcademicProfileStatus;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthzGroupQueryMapper;
import com.aubb.server.modules.identityaccess.infrastructure.profile.AcademicProfileEntity;
import com.aubb.server.modules.identityaccess.infrastructure.profile.AcademicProfileMapper;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserEntity;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticatedPrincipalLoader {

    private final UserMapper userMapper;
    private final AcademicProfileMapper academicProfileMapper;
    private final CourseMemberMapper courseMemberMapper;
    private final ScopeIdentityService scopeIdentityService;
    private final AuthzGroupQueryMapper authzGroupQueryMapper;
    private final AuthzScopeResolutionService authzScopeResolutionService;
    private final List<PermissionGrantResolver> permissionGrantResolvers;

    @Transactional(readOnly = true)
    public AuthenticatedUserPrincipal loadPrincipal(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        if (user.getExpiresAt() != null && user.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return null;
        }
        if (AccountStatus.DISABLED.name().equals(user.getAccountStatus())) {
            return null;
        }
        if (AccountStatus.LOCKED.name().equals(user.getAccountStatus())
                && (user.getLockedUntil() == null || user.getLockedUntil().isAfter(OffsetDateTime.now()))) {
            return null;
        }
        List<ScopeIdentityView> identities = scopeIdentityService.loadForUser(user.getId());
        List<GroupBindingView> groupBindings = loadGroupBindings(user.getId(), identities);
        AuthenticatedUserPrincipal snapshotPrincipal = new AuthenticatedUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPrimaryOrgUnitId(),
                null,
                AccountStatus.valueOf(user.getAccountStatus()),
                loadAcademicProfile(user.getId()),
                identities,
                groupBindings,
                Set.of(),
                null);
        LinkedHashSet<String> permissionCodes = permissionGrantResolvers.stream()
                .flatMap(resolver -> resolver.resolve(snapshotPrincipal).stream())
                .map(grant -> grant.permission().code())
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new AuthenticatedUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPrimaryOrgUnitId(),
                null,
                AccountStatus.valueOf(user.getAccountStatus()),
                loadAcademicProfile(user.getId()),
                identities,
                groupBindings,
                permissionCodes,
                null);
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

    private List<GroupBindingView> loadGroupBindings(Long userId, List<ScopeIdentityView> identities) {
        List<GroupBindingView> governanceBindings = identities.stream()
                .flatMap(identity -> toGovernanceBinding(identity).stream())
                .toList();
        List<GroupBindingView> courseMemberBindings = courseMemberMapper
                .selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getUserId, userId)
                        .eq(CourseMemberEntity::getMemberStatus, CourseMemberStatus.ACTIVE.name()))
                .stream()
                .map(this::toCourseMemberBinding)
                .toList();
        List<GroupBindingView> persistedBindings = authzGroupQueryMapper.selectActiveBindingsByUserId(userId).stream()
                .map(binding -> new GroupBindingView(
                        "AUTHZ_GROUP", binding.getTemplateCode(), binding.getScopeType(), binding.getScopeRefId()))
                .toList();
        return java.util.stream.Stream.of(governanceBindings, courseMemberBindings, persistedBindings)
                .flatMap(List::stream)
                .distinct()
                .toList();
    }

    private java.util.Optional<GroupBindingView> toGovernanceBinding(ScopeIdentityView identity) {
        String scopeType = identity.scopeOrgType();
        Long scopeRefId = identity.scopeOrgUnitId();
        if ("CLASS".equals(scopeType)) {
            scopeRefId = authzScopeResolutionService.findTeachingClassIdByOrgClassUnitId(identity.scopeOrgUnitId());
            if (scopeRefId == null) {
                return java.util.Optional.empty();
            }
        }
        return java.util.Optional.of(new GroupBindingView(
                "LEGACY_GOVERNANCE", identity.roleCode().toLowerCase().replace('_', '-'), scopeType, scopeRefId));
    }

    private GroupBindingView toCourseMemberBinding(CourseMemberEntity member) {
        CourseMemberRole role = CourseMemberRole.valueOf(member.getMemberRole());
        if (member.getTeachingClassId() != null) {
            return new GroupBindingView(
                    "LEGACY_COURSE_MEMBER", classScopedTemplateCode(role), "CLASS", member.getTeachingClassId());
        }
        return new GroupBindingView(
                "LEGACY_COURSE_MEMBER", offeringScopedTemplateCode(role), "OFFERING", member.getOfferingId());
    }

    private String classScopedTemplateCode(CourseMemberRole role) {
        return switch (role) {
            case INSTRUCTOR -> "class-instructor";
            case TA -> "class-ta";
            case STUDENT -> "student";
            case OBSERVER -> "observer";
        };
    }

    private String offeringScopedTemplateCode(CourseMemberRole role) {
        return switch (role) {
            case INSTRUCTOR -> "offering-instructor";
            case TA -> "offering-ta";
            case STUDENT -> "student";
            case OBSERVER -> "observer";
        };
    }
}
