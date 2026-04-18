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
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthzGroupGrantRow;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthzGroupQueryMapper;
import com.aubb.server.modules.identityaccess.infrastructure.permission.RoleBindingGrantQueryMapper;
import com.aubb.server.modules.identityaccess.infrastructure.permission.RoleBindingGrantRow;
import com.aubb.server.modules.identityaccess.infrastructure.profile.AcademicProfileEntity;
import com.aubb.server.modules.identityaccess.infrastructure.profile.AcademicProfileMapper;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserEntity;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
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
    private final RoleBindingGrantQueryMapper roleBindingGrantQueryMapper;
    private final List<PermissionGrantResolver> permissionGrantResolvers;

    @Transactional(readOnly = true)
    public AuthenticatedUserPrincipal loadPrincipal(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        OffsetDateTime now = OffsetDateTime.now();
        if (!isAccountLoginAllowed(user, now)) {
            return null;
        }
        List<ScopeIdentityView> identities = scopeIdentityService.loadForUser(user.getId());
        List<RoleBindingGrantRow> roleBindingGrantRows =
                roleBindingGrantQueryMapper.selectActiveGrantRowsByUserId(user.getId(), now);
        List<AuthzGroupGrantRow> authzGroupGrantRows =
                authzGroupQueryMapper.selectActiveGrantRowsByUserId(user.getId());
        List<GroupBindingView> groupBindings = roleBindingGrantRows.isEmpty()
                ? loadLegacyGroupBindings(user.getId(), identities)
                : mergeBindings(
                        loadRoleBindingViews(roleBindingGrantRows),
                        loadPersistedAuthzGroupBindings(authzGroupGrantRows));
        AcademicProfileView academicProfile = loadAcademicProfile(user.getId());
        LinkedHashSet<String> permissionCodes = roleBindingGrantRows.isEmpty()
                ? loadLegacyPermissionCodes(user, academicProfile, identities, groupBindings)
                : mergePermissionCodes(
                        loadRoleBindingPermissionCodes(roleBindingGrantRows),
                        loadPersistedAuthzGroupPermissionCodes(authzGroupGrantRows));
        AuthenticatedUserPrincipal snapshotPrincipal = new AuthenticatedUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPrimaryOrgUnitId(),
                null,
                AccountStatus.valueOf(user.getAccountStatus()),
                academicProfile,
                identities,
                groupBindings,
                permissionCodes,
                null);
        return new AuthenticatedUserPrincipal(
                snapshotPrincipal.getUserId(),
                snapshotPrincipal.getUsername(),
                snapshotPrincipal.getDisplayName(),
                snapshotPrincipal.getPrimaryOrgUnitId(),
                snapshotPrincipal.getSessionId(),
                snapshotPrincipal.getAccountStatus(),
                snapshotPrincipal.getAcademicProfile(),
                snapshotPrincipal.getIdentities(),
                snapshotPrincipal.getGroupBindings(),
                snapshotPrincipal.getPermissionCodes(),
                snapshotPrincipal.getPermissionVersion());
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

    private LinkedHashSet<String> loadLegacyPermissionCodes(
            UserEntity user,
            AcademicProfileView academicProfile,
            List<ScopeIdentityView> identities,
            List<GroupBindingView> groupBindings) {
        AuthenticatedUserPrincipal snapshotPrincipal = new AuthenticatedUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPrimaryOrgUnitId(),
                null,
                AccountStatus.valueOf(user.getAccountStatus()),
                academicProfile,
                identities,
                groupBindings,
                Set.of(),
                null);
        return permissionGrantResolvers.stream()
                .flatMap(resolver -> resolver.resolve(snapshotPrincipal).stream())
                .map(grant -> grant.permission().code())
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private LinkedHashSet<String> loadRoleBindingPermissionCodes(List<RoleBindingGrantRow> roleBindingGrantRows) {
        return roleBindingGrantRows.stream()
                .map(RoleBindingGrantRow::getPermissionCode)
                .filter(code -> code != null && !code.isBlank())
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private LinkedHashSet<String> loadPersistedAuthzGroupPermissionCodes(List<AuthzGroupGrantRow> authzGroupGrantRows) {
        return authzGroupGrantRows.stream()
                .map(AuthzGroupGrantRow::getPermissionCode)
                .filter(code -> code != null && !code.isBlank())
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private List<GroupBindingView> loadRoleBindingViews(List<RoleBindingGrantRow> roleBindingGrantRows) {
        LinkedHashMap<Long, GroupBindingView> bindingsById = new LinkedHashMap<>();
        roleBindingGrantRows.forEach(row -> bindingsById.putIfAbsent(row.getBindingId(), toRoleBindingView(row)));
        return List.copyOf(bindingsById.values());
    }

    private List<GroupBindingView> loadPersistedAuthzGroupBindings(List<AuthzGroupGrantRow> authzGroupGrantRows) {
        LinkedHashMap<Long, GroupBindingView> bindingsById = new LinkedHashMap<>();
        authzGroupGrantRows.forEach(row -> bindingsById.putIfAbsent(
                row.getGroupId(),
                new GroupBindingView("AUTHZ_GROUP", row.getTemplateCode(), row.getScopeType(), row.getScopeRefId())));
        return List.copyOf(bindingsById.values());
    }

    private GroupBindingView toRoleBindingView(RoleBindingGrantRow row) {
        String templateCode = legacyTemplateCode(row.getRoleCode());
        return new GroupBindingView(
                row.getSourceType(),
                templateCode,
                normalizeScopeType(row.getBindingScopeType()),
                normalizeScopeRefId(templateCode, row));
    }

    private List<GroupBindingView> loadLegacyGroupBindings(Long userId, List<ScopeIdentityView> identities) {
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

    private List<GroupBindingView> mergeBindings(List<GroupBindingView> primary, List<GroupBindingView> secondary) {
        LinkedHashSet<GroupBindingView> merged = new LinkedHashSet<>(primary);
        merged.addAll(secondary);
        return List.copyOf(merged);
    }

    private LinkedHashSet<String> mergePermissionCodes(LinkedHashSet<String> primary, LinkedHashSet<String> secondary) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(primary);
        merged.addAll(secondary);
        return merged;
    }

    private String legacyTemplateCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return "unknown";
        }
        return switch (roleCode) {
            case "school_admin" -> "school-admin";
            case "college_admin" -> "college-admin";
            case "course_manager" -> "course-admin";
            case "class_admin" -> "class-admin";
            case "offering_teacher" -> "offering-instructor";
            case "class_teacher" -> "class-instructor";
            case "offering_ta" -> "offering-ta";
            case "class_ta" -> "class-ta";
            case "student" -> "student";
            case "observer" -> "observer";
            case "auditor" -> "audit-readonly";
            case "grader" -> "grade-corrector";
            default -> roleCode.replace('_', '-');
        };
    }

    private String normalizeScopeType(String scopeType) {
        return scopeType == null ? null : scopeType.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private Long normalizeScopeRefId(String templateCode, RoleBindingGrantRow row) {
        if (!"class-admin".equals(templateCode)) {
            return row.getBindingScopeId();
        }
        Long teachingClassId = authzScopeResolutionService.findTeachingClassIdByOrgClassUnitId(row.getBindingScopeId());
        return teachingClassId == null ? row.getBindingScopeId() : teachingClassId;
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
            case INSTRUCTOR, CLASS_INSTRUCTOR -> "class-instructor";
            case OFFERING_TA, TA -> "class-ta";
            case STUDENT -> "student";
            case OBSERVER -> "observer";
        };
    }

    private String offeringScopedTemplateCode(CourseMemberRole role) {
        return switch (role) {
            case INSTRUCTOR -> "offering-instructor";
            case CLASS_INSTRUCTOR -> "class-instructor";
            case OFFERING_TA, TA -> "offering-ta";
            case STUDENT -> "student";
            case OBSERVER -> "observer";
        };
    }
}
