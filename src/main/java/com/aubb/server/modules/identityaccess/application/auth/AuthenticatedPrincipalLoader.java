package com.aubb.server.modules.identityaccess.application.auth;

import com.aubb.server.modules.identityaccess.application.authz.AuthzScopeResolutionService;
import com.aubb.server.modules.identityaccess.application.authz.GroupBindingView;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticatedPrincipalLoader {

    private final UserMapper userMapper;
    private final AcademicProfileMapper academicProfileMapper;
    private final ScopeIdentityService scopeIdentityService;
    private final AuthzGroupQueryMapper authzGroupQueryMapper;
    private final AuthzScopeResolutionService authzScopeResolutionService;
    private final RoleBindingGrantQueryMapper roleBindingGrantQueryMapper;

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
        boolean roleBindingSnapshot = !roleBindingGrantRows.isEmpty();
        List<AuthzGroupGrantRow> authzGroupGrantRows =
                authzGroupQueryMapper.selectActiveGrantRowsByUserId(user.getId());
        List<GroupBindingView> groupBindings = mergeBindings(
                loadRoleBindingViews(roleBindingGrantRows), loadPersistedAuthzGroupBindings(authzGroupGrantRows));
        AcademicProfileView academicProfile = loadAcademicProfile(user.getId());
        LinkedHashSet<String> permissionCodes = mergePermissionCodes(
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
                null,
                roleBindingSnapshot);
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
                snapshotPrincipal.getPermissionVersion(),
                roleBindingSnapshot);
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
}
