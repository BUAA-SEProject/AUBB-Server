package com.aubb.server.application.iam;

import com.aubb.server.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.domain.iam.GovernanceRole;
import com.aubb.server.domain.iam.GovernanceRolePolicy;
import com.aubb.server.domain.organization.OrgUnitType;
import com.aubb.server.infrastructure.organization.OrgUnitEntity;
import com.aubb.server.infrastructure.organization.OrgUnitMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
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
        Map<Long, OrgUnitEntity> index = loadOrgUnitIndex();
        boolean allowed = principal.getIdentities().stream().anyMatch(identity -> {
            GovernanceRole role = GovernanceRole.from(identity.roleCode());
            return role.canManageDescendantCreation(childType)
                    && isDescendantOrSelf(parent.getId(), identity.scopeOrgUnitId(), index);
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
        Map<Long, OrgUnitEntity> index = loadOrgUnitIndex();
        return principal.getIdentities().stream()
                .anyMatch(identity -> isDescendantOrSelf(orgUnitId, identity.scopeOrgUnitId(), index));
    }

    @Transactional(readOnly = true)
    public void assertCanGrantIdentities(
            AuthenticatedUserPrincipal principal, Collection<IdentityAssignmentCommand> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return;
        }
        Map<Long, OrgUnitEntity> index = loadOrgUnitIndex();
        for (IdentityAssignmentCommand assignment : assignments) {
            boolean allowed = principal.getIdentities().stream().anyMatch(identity -> {
                GovernanceRole actorRole = GovernanceRole.from(identity.roleCode());
                return governanceRolePolicy.canGrant(actorRole, assignment.roleCode())
                        && isDescendantOrSelf(assignment.scopeOrgUnitId(), identity.scopeOrgUnitId(), index);
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
    public Map<Long, OrgUnitEntity> loadOrgUnitIndex() {
        return orgUnitMapper.selectList(Wrappers.<OrgUnitEntity>lambdaQuery()).stream()
                .collect(Collectors.toMap(
                        OrgUnitEntity::getId, entity -> entity, (left, right) -> left, LinkedHashMap::new));
    }

    public boolean isDescendantOrSelf(Long orgUnitId, Long ancestorOrgUnitId, Map<Long, OrgUnitEntity> index) {
        Long cursor = orgUnitId;
        while (cursor != null) {
            if (cursor.equals(ancestorOrgUnitId)) {
                return true;
            }
            OrgUnitEntity current = index.get(cursor);
            cursor = current == null ? null : current.getParentId();
        }
        return false;
    }
}
