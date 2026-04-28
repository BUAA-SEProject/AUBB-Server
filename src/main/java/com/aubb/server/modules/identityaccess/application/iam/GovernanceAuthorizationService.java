package com.aubb.server.modules.identityaccess.application.iam;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.AuthzScopeResolutionService;
import com.aubb.server.modules.identityaccess.application.authz.GroupBindingView;
import com.aubb.server.modules.identityaccess.domain.governance.GovernanceRole;
import com.aubb.server.modules.identityaccess.domain.governance.GovernanceRolePolicy;
import com.aubb.server.modules.organization.domain.OrgUnitType;
import com.aubb.server.modules.organization.infrastructure.OrgUnitEntity;
import com.aubb.server.modules.organization.infrastructure.OrgUnitMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GovernanceAuthorizationService {

    private final OrgUnitMapper orgUnitMapper;
    private final AuthzScopeResolutionService authzScopeResolutionService;
    private final GovernanceRolePolicy governanceRolePolicy = new GovernanceRolePolicy();

    @Transactional(readOnly = true)
    public void assertCanCreateOrg(AuthenticatedUserPrincipal principal, Long parentId, OrgUnitType childType) {
        OrgUnitEntity parent = parentId == null ? null : orgUnitMapper.selectById(parentId);
        if (parentId != null && parent == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ORG_PARENT_NOT_FOUND", "上级组织不存在");
        }
        if (parent == null) {
            if (loadGovernanceAssignments(principal).stream()
                    .noneMatch(assignment -> assignment.role() == GovernanceRole.SCHOOL_ADMIN)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权创建根节点");
            }
            return;
        }
        boolean allowed = loadGovernanceAssignments(principal).stream().anyMatch(assignment -> {
            GovernanceRole role = assignment.role();
            return role.canManageDescendantCreation(childType)
                    && isDescendantOrSelf(parent.getId(), assignment.scopeOrgUnitId());
        });
        if (!allowed) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权在该组织下创建节点");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanManageUserAt(AuthenticatedUserPrincipal principal, Long orgUnitId) {
        if (!canManageUserAt(principal, orgUnitId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权管理该组织内的用户");
        }
    }

    @Transactional(readOnly = true)
    public boolean canManageUserAt(AuthenticatedUserPrincipal principal, Long orgUnitId) {
        if (orgUnitId == null) {
            return loadGovernanceAssignments(principal).stream()
                    .anyMatch(assignment -> assignment.role() == GovernanceRole.SCHOOL_ADMIN);
        }
        return loadGovernanceAssignments(principal).stream()
                .anyMatch(assignment -> isDescendantOrSelf(orgUnitId, assignment.scopeOrgUnitId()));
    }

    @Transactional(readOnly = true)
    public void assertCanGrantIdentities(
            AuthenticatedUserPrincipal principal, Collection<IdentityAssignmentCommand> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return;
        }
        for (IdentityAssignmentCommand assignment : assignments) {
            boolean allowed = loadGovernanceAssignments(principal).stream().anyMatch(actor -> {
                GovernanceRole actorRole = actor.role();
                return governanceRolePolicy.canGrant(actorRole, assignment.roleCode())
                        && isDescendantOrSelf(assignment.scopeOrgUnitId(), actor.scopeOrgUnitId());
            });
            if (!allowed) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权分配该身份");
            }
        }
    }

    @Transactional(readOnly = true)
    public Set<Long> visibleScopeRootIds(AuthenticatedUserPrincipal principal) {
        return loadGovernanceAssignments(principal).stream()
                .map(GovernanceAssignment::scopeOrgUnitId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Set<Long> loadManageableOrgUnitIds(AuthenticatedUserPrincipal principal) {
        Set<Long> visibleRoots = visibleScopeRootIds(principal);
        if (visibleRoots.isEmpty()) {
            return Set.of();
        }
        Set<Long> manageableOrgUnitIds = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>(visibleRoots);
        while (!queue.isEmpty()) {
            List<Long> frontier = queue.stream().toList();
            queue.clear();
            manageableOrgUnitIds.addAll(frontier);
            orgUnitMapper
                    .selectList(new QueryWrapper<OrgUnitEntity>()
                            .select("id", "parent_id")
                            .in("parent_id", frontier))
                    .stream()
                    .map(OrgUnitEntity::getId)
                    .filter(Objects::nonNull)
                    .filter(candidate -> !manageableOrgUnitIds.contains(candidate))
                    .forEach(queue::addLast);
        }
        return manageableOrgUnitIds;
    }

    public boolean isDescendantOrSelf(Long orgUnitId, Long ancestorOrgUnitId) {
        Long cursor = orgUnitId;
        while (cursor != null) {
            if (cursor.equals(ancestorOrgUnitId)) {
                return true;
            }
            OrgUnitEntity current = orgUnitMapper.selectById(cursor);
            cursor = current == null ? null : current.getParentId();
        }
        return false;
    }

    private List<GovernanceAssignment> loadGovernanceAssignments(AuthenticatedUserPrincipal principal) {
        List<GovernanceAssignment> roleBindingAssignments = principal.getGroupBindings().stream()
                .flatMap(binding -> toGovernanceAssignment(binding).stream())
                .distinct()
                .toList();
        if (!roleBindingAssignments.isEmpty()) {
            return roleBindingAssignments;
        }
        return principal.getIdentities().stream()
                .map(identity ->
                        new GovernanceAssignment(GovernanceRole.from(identity.roleCode()), identity.scopeOrgUnitId()))
                .distinct()
                .toList();
    }

    private java.util.Optional<GovernanceAssignment> toGovernanceAssignment(GroupBindingView binding) {
        if (binding == null || binding.scopeRefId() == null || binding.templateCode() == null) {
            return java.util.Optional.empty();
        }
        GovernanceRole role =
                switch (binding.templateCode()) {
                    case "school-admin" -> GovernanceRole.SCHOOL_ADMIN;
                    case "college-admin" -> GovernanceRole.COLLEGE_ADMIN;
                    case "course-admin" -> GovernanceRole.COURSE_ADMIN;
                    case "class-admin" -> GovernanceRole.CLASS_ADMIN;
                    default -> null;
                };
        if (role == null) {
            return java.util.Optional.empty();
        }
        String normalizedScopeType =
                binding.scopeType() == null ? null : binding.scopeType().trim().toUpperCase(Locale.ROOT);
        Long scopeOrgUnitId =
                switch (normalizedScopeType) {
                    case "SCHOOL", "COLLEGE", "COURSE" -> binding.scopeRefId();
                    case "CLASS" ->
                        authzScopeResolutionService.findOrgClassUnitIdByTeachingClassId(binding.scopeRefId());
                    default -> null;
                };
        return scopeOrgUnitId == null
                ? java.util.Optional.empty()
                : java.util.Optional.of(new GovernanceAssignment(role, scopeOrgUnitId));
    }

    private record GovernanceAssignment(GovernanceRole role, Long scopeOrgUnitId) {}
}
