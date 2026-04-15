package com.aubb.server.modules.identityaccess.application.iam;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.identityaccess.domain.GovernanceRolePolicy;
import com.aubb.server.modules.identityaccess.infrastructure.UserScopeRoleEntity;
import com.aubb.server.modules.identityaccess.infrastructure.UserScopeRoleMapper;
import com.aubb.server.modules.organization.domain.OrgUnitType;
import com.aubb.server.modules.organization.infrastructure.OrgUnitEntity;
import com.aubb.server.modules.organization.infrastructure.OrgUnitMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScopeIdentityService {

    private final UserScopeRoleMapper userScopeRoleMapper;
    private final OrgUnitMapper orgUnitMapper;
    private final GovernanceRolePolicy governanceRolePolicy = new GovernanceRolePolicy();

    @Transactional(readOnly = true)
    public List<ScopeIdentityView> loadForUser(Long userId) {
        return loadForUsers(List.of(userId)).getOrDefault(userId, List.of());
    }

    @Transactional(readOnly = true)
    public Map<Long, List<ScopeIdentityView>> loadForUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserScopeRoleEntity> entities = userScopeRoleMapper.selectList(
                Wrappers.<UserScopeRoleEntity>lambdaQuery().in(UserScopeRoleEntity::getUserId, userIds));
        if (entities.isEmpty()) {
            return Map.of();
        }
        Map<Long, OrgUnitEntity> orgUnitIndex = loadOrgUnitIndex(entities.stream()
                .map(UserScopeRoleEntity::getScopeOrgUnitId)
                .distinct()
                .toList());
        return entities.stream()
                .collect(Collectors.groupingBy(
                        UserScopeRoleEntity::getUserId,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                entity -> toView(entity, orgUnitIndex.get(entity.getScopeOrgUnitId())),
                                Collectors.collectingAndThen(Collectors.toList(), identities -> {
                                    identities.sort(
                                            (left, right) -> left.roleCode().compareTo(right.roleCode()));
                                    return Collections.unmodifiableList(identities);
                                }))));
    }

    @Transactional
    public void replace(Long userId, Collection<IdentityAssignmentCommand> assignments) {
        userScopeRoleMapper.delete(
                Wrappers.<UserScopeRoleEntity>lambdaQuery().eq(UserScopeRoleEntity::getUserId, userId));
        if (assignments == null || assignments.isEmpty()) {
            return;
        }
        Map<Long, OrgUnitEntity> orgUnitIndex = loadOrgUnitIndex(assignments.stream()
                .map(IdentityAssignmentCommand::scopeOrgUnitId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        for (IdentityAssignmentCommand assignment : assignments) {
            OrgUnitEntity scopeOrgUnit = orgUnitIndex.get(assignment.scopeOrgUnitId());
            if (scopeOrgUnit == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ORG_NOT_FOUND", "指定组织不存在");
            }
            var validation = governanceRolePolicy.validateScope(
                    assignment.roleCode(), OrgUnitType.valueOf(scopeOrgUnit.getType()));
            if (!validation.valid()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "IDENTITY_SCOPE_INVALID", validation.reason());
            }
            UserScopeRoleEntity entity = new UserScopeRoleEntity();
            entity.setUserId(userId);
            entity.setScopeOrgUnitId(assignment.scopeOrgUnitId());
            entity.setRoleCode(assignment.roleCode().name());
            userScopeRoleMapper.insert(entity);
        }
    }

    @Transactional(readOnly = true)
    public List<UserScopeRoleEntity> loadAssignments(Long userId) {
        return userScopeRoleMapper.selectList(
                Wrappers.<UserScopeRoleEntity>lambdaQuery().eq(UserScopeRoleEntity::getUserId, userId));
    }

    @Transactional(readOnly = true)
    public Map<Long, OrgUnitEntity> loadOrgUnitIndex(Collection<Long> orgUnitIds) {
        if (orgUnitIds == null || orgUnitIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, OrgUnitEntity> index = new LinkedHashMap<>();
        orgUnitMapper.selectByIds(orgUnitIds).forEach(entity -> index.put(entity.getId(), entity));
        return index;
    }

    private ScopeIdentityView toView(UserScopeRoleEntity entity, OrgUnitEntity orgUnitEntity) {
        return new ScopeIdentityView(
                entity.getRoleCode(),
                entity.getScopeOrgUnitId(),
                orgUnitEntity == null ? null : orgUnitEntity.getType(),
                orgUnitEntity == null ? null : orgUnitEntity.getName());
    }
}
