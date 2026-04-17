package com.aubb.server.modules.identityaccess.application.authz;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.identityaccess.application.auth.AuthSessionApplicationService;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.view.AuthzGroupMemberView;
import com.aubb.server.modules.identityaccess.application.authz.view.AuthzGroupView;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import com.aubb.server.modules.identityaccess.domain.authz.PermissionCode;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthGroupEntity;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthGroupMapper;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthGroupMemberEntity;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthGroupMemberMapper;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthGroupTemplateEntity;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthGroupTemplateMapper;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserEntity;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthzGroupApplicationService {

    private final AuthGroupTemplateMapper authGroupTemplateMapper;
    private final AuthGroupMapper authGroupMapper;
    private final AuthGroupMemberMapper authGroupMemberMapper;
    private final UserMapper userMapper;
    private final AuthorizationService authorizationService;
    private final AuthzScopeResolutionService authzScopeResolutionService;
    private final AuthSessionApplicationService authSessionApplicationService;

    @Transactional
    public AuthzGroupView createGroup(
            String templateCode,
            AuthorizationScopeType scopeType,
            Long scopeRefId,
            String displayName,
            AuthenticatedUserPrincipal principal) {
        AuthGroupTemplateEntity template = requireTemplate(templateCode);
        if (!template.getScopeType().equals(scopeType.name())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "AUTHZ_GROUP_SCOPE_INVALID", "模板作用域与目标作用域不匹配");
        }
        requirePermission(principal, PermissionCode.AUTH_GROUP_MANAGE, scopeType, scopeRefId, "当前用户无权管理该授权组作用域");

        AuthGroupEntity entity = new AuthGroupEntity();
        entity.setTemplateId(template.getId());
        entity.setScopeType(scopeType.name());
        entity.setScopeRefId(scopeRefId);
        entity.setDisplayName(displayName);
        entity.setManagedBySystem(Boolean.FALSE);
        entity.setStatus("ACTIVE");
        try {
            authGroupMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "AUTHZ_GROUP_DUPLICATED", "当前模板与作用域的授权组已存在");
        }
        return toView(entity, template.getCode());
    }

    @Transactional
    public AuthzGroupMemberView addMember(
            Long groupId, Long userId, OffsetDateTime expiresAt, AuthenticatedUserPrincipal principal) {
        AuthGroupEntity group = requireGroup(groupId);
        assertValidExpiresAt(expiresAt);
        requirePermission(
                principal,
                PermissionCode.AUTH_GROUP_MANAGE,
                AuthorizationScopeType.valueOf(group.getScopeType()),
                group.getScopeRefId(),
                "当前用户无权管理该授权组成员");
        requireUser(userId);

        AuthGroupMemberEntity existing = authGroupMemberMapper.selectOne(Wrappers.<AuthGroupMemberEntity>lambdaQuery()
                .eq(AuthGroupMemberEntity::getGroupId, groupId)
                .eq(AuthGroupMemberEntity::getUserId, userId)
                .last("LIMIT 1"));
        OffsetDateTime now = OffsetDateTime.now();
        if (existing != null && isActiveMembership(existing, now)) {
            throw new BusinessException(HttpStatus.CONFLICT, "AUTHZ_GROUP_MEMBER_DUPLICATED", "用户已加入该授权组");
        }

        AuthGroupMemberEntity entity = existing == null ? new AuthGroupMemberEntity() : existing;
        entity.setGroupId(groupId);
        entity.setUserId(userId);
        entity.setSourceType("MANUAL");
        entity.setJoinedAt(now);
        entity.setExpiresAt(expiresAt);
        if (existing == null) {
            authGroupMemberMapper.insert(entity);
        } else {
            authGroupMemberMapper.update(
                    null,
                    Wrappers.<AuthGroupMemberEntity>lambdaUpdate()
                            .eq(AuthGroupMemberEntity::getId, existing.getId())
                            .set(AuthGroupMemberEntity::getSourceType, entity.getSourceType())
                            .set(AuthGroupMemberEntity::getJoinedAt, entity.getJoinedAt())
                            .set(AuthGroupMemberEntity::getExpiresAt, entity.getExpiresAt()));
        }

        authSessionApplicationService.invalidateAllSessionsForUser(
                userId, principal.getUserId(), "AUTHZ_GROUP_CHANGED");
        return new AuthzGroupMemberView(
                entity.getGroupId(),
                entity.getUserId(),
                entity.getSourceType(),
                entity.getJoinedAt(),
                entity.getExpiresAt());
    }

    private AuthGroupTemplateEntity requireTemplate(String templateCode) {
        AuthGroupTemplateEntity template =
                authGroupTemplateMapper.selectOne(Wrappers.<AuthGroupTemplateEntity>lambdaQuery()
                        .eq(AuthGroupTemplateEntity::getCode, templateCode)
                        .eq(AuthGroupTemplateEntity::getStatus, "ACTIVE")
                        .last("LIMIT 1"));
        if (template == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "AUTHZ_TEMPLATE_NOT_FOUND", "授权组模板不存在");
        }
        return template;
    }

    private AuthGroupEntity requireGroup(Long groupId) {
        AuthGroupEntity group = authGroupMapper.selectById(groupId);
        if (group == null || !"ACTIVE".equals(group.getStatus())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "AUTHZ_GROUP_NOT_FOUND", "授权组不存在");
        }
        return group;
    }

    private UserEntity requireUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        return user;
    }

    private void requirePermission(
            AuthenticatedUserPrincipal principal,
            PermissionCode permission,
            AuthorizationScopeType scopeType,
            Long scopeRefId,
            String message) {
        ScopeRef scope = authzScopeResolutionService.resolveScope(scopeType, scopeRefId);
        if (!authorizationService
                .decide(AuthorizationRequest.forPermission(principal, permission, scope))
                .allowed()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
        }
    }

    private void assertValidExpiresAt(OffsetDateTime expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(OffsetDateTime.now())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "AUTHZ_GROUP_MEMBER_EXPIRES_AT_INVALID", "过期时间必须晚于当前时间");
        }
    }

    private boolean isActiveMembership(AuthGroupMemberEntity entity, OffsetDateTime now) {
        return entity.getExpiresAt() == null || entity.getExpiresAt().isAfter(now);
    }

    private AuthzGroupView toView(AuthGroupEntity entity, String templateCode) {
        Long memberCount = authGroupMemberMapper.selectCount(Wrappers.<AuthGroupMemberEntity>lambdaQuery()
                .eq(AuthGroupMemberEntity::getGroupId, entity.getId())
                .and(wrapper -> wrapper.isNull(AuthGroupMemberEntity::getExpiresAt)
                        .or()
                        .gt(AuthGroupMemberEntity::getExpiresAt, OffsetDateTime.now())));
        return new AuthzGroupView(
                entity.getId(),
                templateCode,
                entity.getScopeType(),
                entity.getScopeRefId(),
                entity.getDisplayName(),
                Boolean.TRUE.equals(entity.getManagedBySystem()),
                entity.getStatus(),
                memberCount == null ? 0 : memberCount);
    }
}
