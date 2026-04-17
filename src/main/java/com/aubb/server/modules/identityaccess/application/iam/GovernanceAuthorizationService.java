package com.aubb.server.modules.identityaccess.application.iam;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
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
    private final GovernanceRolePolicy governanceRolePolicy = new GovernanceRolePolicy();

    @Transactional(readOnly = true)
    public void assertCanCreateOrg(AuthenticatedUserPrincipal principal, Long parentId, OrgUnitType childType) {
        OrgUnitEntity parent = parentId == null ? null : orgUnitMapper.selectById(parentId);
        if (parentId != null && parent == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ORG_PARENT_NOT_FOUND", "上级组织不存在");
        }
        if (parent == null) {
            if (!principal.hasAuthority(GovernanceRole.SCHOOL_ADMIN.name())) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权创建根节点");
            }
            return;
        }
        boolean allowed = principal.getIdentities().stream().anyMatch(identity -> {
            GovernanceRole role = GovernanceRole.from(identity.roleCode());
            return role.canManageDescendantCreation(childType)
                    && isDescendantOrSelf(parent.getId(), identity.scopeOrgUnitId());
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
            return principal.hasAuthority(GovernanceRole.SCHOOL_ADMIN.name());
        }
        return principal.getIdentities().stream()
                .anyMatch(identity -> isDescendantOrSelf(orgUnitId, identity.scopeOrgUnitId()));
    }

    @Transactional(readOnly = true)
    public void assertCanGrantIdentities(
            AuthenticatedUserPrincipal principal, Collection<IdentityAssignmentCommand> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return;
        }
        for (IdentityAssignmentCommand assignment : assignments) {
            boolean allowed = principal.getIdentities().stream().anyMatch(identity -> {
                GovernanceRole actorRole = GovernanceRole.from(identity.roleCode());
                return governanceRolePolicy.canGrant(actorRole, assignment.roleCode())
                        && isDescendantOrSelf(assignment.scopeOrgUnitId(), identity.scopeOrgUnitId());
            });
            if (!allowed) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权分配该身份");
            }
        }
    }

    @Transactional(readOnly = true)
    public Set<Long> visibleScopeRootIds(AuthenticatedUserPrincipal principal) {
        return principal.getIdentities().stream()
                .map(ScopeIdentityView::scopeOrgUnitId)
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
}
