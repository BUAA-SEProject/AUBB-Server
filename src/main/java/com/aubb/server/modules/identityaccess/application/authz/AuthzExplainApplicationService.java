package com.aubb.server.modules.identityaccess.application.authz;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedPrincipalLoader;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationContext;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResourceRef;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResourceType;
import com.aubb.server.modules.identityaccess.application.authz.core.PermissionAuthorizationService;
import com.aubb.server.modules.identityaccess.application.authz.view.AuthzExplainView;
import com.aubb.server.modules.identityaccess.application.authz.view.AuthzExplainView.AuthzGrantView;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import com.aubb.server.modules.identityaccess.domain.authz.PermissionCode;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthGroupEntity;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthGroupMapper;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthGroupMemberEntity;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthGroupMemberMapper;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthGroupTemplateEntity;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthGroupTemplateMapper;
import com.aubb.server.modules.identityaccess.infrastructure.permission.RoleBindingGrantQueryMapper;
import com.aubb.server.modules.identityaccess.infrastructure.permission.RoleBindingGrantRow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthzExplainApplicationService {

    private final PermissionAuthorizationService permissionAuthorizationService;
    private final AuthenticatedPrincipalLoader authenticatedPrincipalLoader;
    private final AuthzScopeResolutionService authzScopeResolutionService;
    private final RoleBindingGrantQueryMapper roleBindingGrantQueryMapper;
    private final AuthGroupMemberMapper authGroupMemberMapper;
    private final AuthGroupMapper authGroupMapper;
    private final AuthGroupTemplateMapper authGroupTemplateMapper;

    @Transactional(readOnly = true)
    public AuthzExplainView explain(
            Long userId,
            PermissionCode permission,
            AuthorizationScopeType scopeType,
            Long scopeRefId,
            AuthenticatedUserPrincipal principal) {
        ScopeRef scope = authzScopeResolutionService.resolveScope(scopeType, scopeRefId);
        requireExplainPermission(principal, scopeType, scopeRefId);
        AuthenticatedUserPrincipal subject = authenticatedPrincipalLoader.loadPrincipal(userId);
        if (subject == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "目标用户不存在或不可用");
        }
        String permissionCode = modernPermissionCode(permission, scopeType);
        List<RoleBindingGrantRow> grantRows =
                roleBindingGrantQueryMapper.selectActiveGrantRowsByUserIdAndPermissionCode(
                        subject.getUserId(), permissionCode, OffsetDateTime.now());
        List<AuthzGrantView> grants = grantRows.stream()
                .filter(row -> isScopeMatched(scope, row))
                .map(this::toRoleBindingGrantView)
                .distinct()
                .toList();
        String reasonCode = grants.isEmpty()
                ? grantRows.isEmpty() ? "DENY_NO_ROLE_BINDING" : "DENY_SCOPE_MISMATCH"
                : "ALLOW_BY_SCOPE_ROLE";
        return new AuthzExplainView(
                userId, permissionCode, scopeType.name(), scopeRefId, !grants.isEmpty(), reasonCode, grants);
    }

    private void requireExplainPermission(
            AuthenticatedUserPrincipal principal, AuthorizationScopeType scopeType, Long scopeRefId) {
        ScopeRef scope = authzScopeResolutionService.resolveScope(scopeType, scopeRefId);
        AuthorizationResourceRef resourceRef =
                new AuthorizationResourceRef(AuthorizationResourceType.valueOf(scopeType.name()), scopeRefId);
        var result = permissionAuthorizationService.authorize(
                principal, "auth.explain.read", resourceRef, AuthorizationContext.of(OffsetDateTime.now()));
        if (result.allowed()) {
            return;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该授权解释");
    }

    private boolean isScopeMatched(ScopeRef targetScope, RoleBindingGrantRow row) {
        ScopeRef bindingScope = authzScopeResolutionService.resolveScope(
                AuthorizationScopeType.fromDatabaseValue(row.getBindingScopeType()), row.getBindingScopeId());
        return targetScope.isCoveredBy(bindingScope);
    }

    private String resolveSourceReference(RoleBindingGrantRow row) {
        String sourceType = row.getSourceType();
        if (sourceType != null && sourceType.toUpperCase().contains("AUTHZ_GROUP") && row.getSourceRefId() != null) {
            Long groupId = resolveAuthGroupId(row.getSourceRefId());
            String templateCode = loadAuthGroupTemplateCode(groupId);
            if (templateCode != null) {
                return templateCode;
            }
        }
        if (row.getRoleCode() != null) {
            return row.getRoleCode();
        }
        if (row.getSourceType() == null) {
            return null;
        }
        return row.getSourceRefId() == null ? row.getSourceType() : row.getSourceType() + ":" + row.getSourceRefId();
    }

    private String normalizeGrantSource(String sourceType) {
        if (sourceType != null && sourceType.toUpperCase().contains("AUTHZ_GROUP")) {
            return "AUTHZ_GROUP";
        }
        return sourceType;
    }

    private AuthzGrantView toRoleBindingGrantView(RoleBindingGrantRow row) {
        return new AuthzGrantView(
                normalizeGrantSource(row.getSourceType()),
                resolveSourceReference(row),
                AuthorizationScopeType.fromDatabaseValue(row.getBindingScopeType())
                        .name(),
                row.getBindingScopeId());
    }

    private Long resolveAuthGroupId(Long sourceRefId) {
        AuthGroupMemberEntity member = authGroupMemberMapper.selectById(sourceRefId);
        if (member != null && member.getGroupId() != null) {
            return member.getGroupId();
        }
        return sourceRefId;
    }

    private String loadAuthGroupTemplateCode(Long groupId) {
        if (groupId == null) {
            return null;
        }
        AuthGroupEntity group = authGroupMapper.selectById(groupId);
        if (group == null || group.getTemplateId() == null) {
            return null;
        }
        AuthGroupTemplateEntity template = authGroupTemplateMapper.selectById(group.getTemplateId());
        return template == null ? null : template.getCode();
    }

    private String modernPermissionCode(PermissionCode permission, AuthorizationScopeType scopeType) {
        if (permission == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "AUTHZ_PERMISSION_INVALID", "权限代码不能为空");
        }
        String mappedCode =
                switch (permission) {
                    case ORG_UNIT_READ ->
                        switch (scopeType) {
                            case SCHOOL -> "school.read";
                            case COLLEGE -> "college.read";
                            case COURSE -> "course.read";
                            default -> null;
                        };
                    case ORG_UNIT_MANAGE ->
                        switch (scopeType) {
                            case SCHOOL -> "school.manage";
                            case COLLEGE -> "college.manage";
                            case COURSE -> "course.manage";
                            default -> null;
                        };
                    case COURSE_READ -> "course.read";
                    case COURSE_MANAGE -> "course.manage";
                    case OFFERING_READ -> "offering.read";
                    case OFFERING_MANAGE -> "offering.manage";
                    case CLASS_READ -> "class.read";
                    case CLASS_MANAGE -> "class.manage";
                    case MEMBER_READ -> "member.read";
                    case MEMBER_MANAGE -> "member.manage";
                    case ASSIGNMENT_READ -> "task.read";
                    case ASSIGNMENT_CREATE -> "task.create";
                    case ASSIGNMENT_UPDATE -> "task.edit";
                    case ASSIGNMENT_PUBLISH -> "task.publish";
                    case ASSIGNMENT_CLOSE -> "task.close";
                    case QUESTION_BANK_MANAGE, QUESTION_MANAGE -> "question_bank.manage";
                    case JUDGE_PROFILE_MANAGE -> "judge.config";
                    case JUDGE_HIDDEN_READ, JUDGE_HIDDEN_MANAGE -> "judge.view_hidden";
                    case SUBMISSION_READ_OWN, SUBMISSION_READ_CLASS, SUBMISSION_READ_OFFERING -> "submission.read";
                    case SUBMISSION_CODE_READ_SENSITIVE -> "submission.read_source";
                    case SUBMISSION_GRADE -> "submission.grade";
                    case SUBMISSION_REJUDGE -> "judge.rejudge";
                    case GRADE_READ_OWN, GRADE_READ_UNPUBLISHED -> "grade.read";
                    case GRADE_EXPORT_CLASS, GRADE_EXPORT_OFFERING -> "grade.export";
                    case GRADE_OVERRIDE -> "grade.override";
                    case GRADE_PUBLISH -> "grade.publish";
                    case APPEAL_READ_OWN, APPEAL_READ_CLASS -> "appeal.read";
                    case APPEAL_REVIEW -> "appeal.review";
                    case LAB_READ -> "lab.read";
                    case LAB_MANAGE -> "lab.manage";
                    case LAB_REPORT_REVIEW -> "lab.report.review";
                    case AUTH_GROUP_MANAGE -> "auth.group.manage";
                    case AUTH_EXPLAIN_READ -> "auth.explain.read";
                    default -> permission.code();
                };
        if (!Objects.equals(mappedCode, null)) {
            return mappedCode;
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "AUTHZ_PERMISSION_SCOPE_INVALID", "当前权限与目标作用域不匹配");
    }
}
