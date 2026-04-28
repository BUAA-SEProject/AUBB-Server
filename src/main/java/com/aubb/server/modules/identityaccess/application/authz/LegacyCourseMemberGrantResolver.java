package com.aubb.server.modules.identityaccess.application.authz;

import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.course.domain.member.CourseMemberStatus;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberEntity;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @deprecated 教学成员权限主路径已迁移到 {@code role_bindings}，本解析器仅保留为旧成员表兜底。
 */
@Deprecated(forRemoval = false)
@Service
@RequiredArgsConstructor
public class LegacyCourseMemberGrantResolver implements PermissionGrantResolver {

    private final CourseMemberMapper courseMemberMapper;

    @Override
    public List<PermissionGrantView> resolve(AuthenticatedUserPrincipal principal) {
        return courseMemberMapper
                .selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getUserId, principal.getUserId())
                        .eq(CourseMemberEntity::getMemberStatus, CourseMemberStatus.ACTIVE.name()))
                .stream()
                .flatMap(member -> toPermissionGrants(member).stream())
                .distinct()
                .toList();
    }

    private List<PermissionGrantView> toPermissionGrants(CourseMemberEntity member) {
        CourseMemberRole role = CourseMemberRole.valueOf(member.getMemberRole());
        ScopeRef primaryScope = primaryScope(member);
        List<PermissionGrantView> primaryGrants = LegacyPermissionGrantMatrix.forCourseMember(role).stream()
                .map(permission ->
                        PermissionGrantView.allow(permission, primaryScope, "LEGACY_COURSE_MEMBER", role.name()))
                .toList();
        List<PermissionGrantView> offeringVisibilityGrants =
                LegacyPermissionGrantMatrix.offeringVisibilityForClassMember(role).stream()
                        .map(permission -> PermissionGrantView.allow(
                                permission,
                                new ScopeRef(AuthorizationScopeType.OFFERING, member.getOfferingId()),
                                "LEGACY_COURSE_MEMBER",
                                role.name()))
                        .toList();
        return java.util.stream.Stream.concat(primaryGrants.stream(), offeringVisibilityGrants.stream())
                .distinct()
                .toList();
    }

    private ScopeRef primaryScope(CourseMemberEntity member) {
        if (member.getTeachingClassId() != null) {
            return new ScopeRef(AuthorizationScopeType.CLASS, member.getTeachingClassId());
        }
        return new ScopeRef(AuthorizationScopeType.OFFERING, member.getOfferingId());
    }
}
