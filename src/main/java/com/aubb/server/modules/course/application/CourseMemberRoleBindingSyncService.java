package com.aubb.server.modules.course.application;

import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.course.domain.member.CourseMemberStatus;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberEntity;
import com.aubb.server.modules.identityaccess.infrastructure.permission.RoleBindingEntity;
import com.aubb.server.modules.identityaccess.infrastructure.permission.RoleBindingMapper;
import com.aubb.server.modules.identityaccess.infrastructure.permission.RoleEntity;
import com.aubb.server.modules.identityaccess.infrastructure.permission.RoleMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseMemberRoleBindingSyncService {

    private static final String SOURCE_TYPE = "LEGACY_COURSE_MEMBER";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String INACTIVE_STATUS = "INACTIVE";

    private final RoleMapper roleMapper;
    private final RoleBindingMapper roleBindingMapper;

    public void sync(CourseMemberEntity member) {
        if (member == null || member.getId() == null) {
            return;
        }
        RoleBindingEntity binding = findExistingBinding(member.getId());
        String roleCode = toRoleCode(member);
        Long scopeId = resolveScopeId(member);
        if (roleCode == null || scopeId == null) {
            if (binding != null) {
                roleBindingMapper.deleteById(binding.getId());
            }
            return;
        }
        RoleEntity role = requireRole(roleCode);
        RoleBindingEntity target = binding == null ? new RoleBindingEntity() : binding;
        target.setUserId(member.getUserId());
        target.setRoleId(role.getId());
        target.setScopeType(resolveScopeType(member));
        target.setScopeId(scopeId);
        target.setConstraintsJson(Map.of());
        target.setStatus(CourseMemberStatus.ACTIVE.name().equals(member.getMemberStatus()) ? ACTIVE_STATUS : INACTIVE_STATUS);
        target.setEffectiveFrom(member.getJoinedAt());
        target.setEffectiveTo(member.getLeftAt());
        target.setGrantedBy(null);
        target.setSourceType(SOURCE_TYPE);
        target.setSourceRefId(member.getId());
        if (binding == null) {
            roleBindingMapper.insert(target);
            return;
        }
        roleBindingMapper.updateById(target);
    }

    private RoleBindingEntity findExistingBinding(Long sourceRefId) {
        return roleBindingMapper.selectOne(Wrappers.<RoleBindingEntity>lambdaQuery()
                .eq(RoleBindingEntity::getSourceType, SOURCE_TYPE)
                .eq(RoleBindingEntity::getSourceRefId, sourceRefId)
                .last("LIMIT 1"));
    }

    private RoleEntity requireRole(String roleCode) {
        RoleEntity role = roleMapper.selectOne(Wrappers.<RoleEntity>lambdaQuery()
                .eq(RoleEntity::getCode, roleCode)
                .eq(RoleEntity::getStatus, ACTIVE_STATUS)
                .last("LIMIT 1"));
        if (role == null) {
            throw new IllegalStateException("缺少内建角色: " + roleCode);
        }
        return role;
    }

    private String toRoleCode(CourseMemberEntity member) {
        return switch (CourseMemberRole.valueOf(member.getMemberRole())) {
            case INSTRUCTOR -> "offering_teacher";
            case CLASS_INSTRUCTOR -> "class_teacher";
            case OFFERING_TA -> "offering_ta";
            case TA -> "class_ta";
            case STUDENT -> "student";
            case OBSERVER -> "observer";
        };
    }

    private String resolveScopeType(CourseMemberEntity member) {
        CourseMemberRole role = CourseMemberRole.valueOf(member.getMemberRole());
        return switch (role) {
            case INSTRUCTOR, OFFERING_TA, OBSERVER -> "offering";
            case CLASS_INSTRUCTOR, TA, STUDENT -> "class";
        };
    }

    private Long resolveScopeId(CourseMemberEntity member) {
        CourseMemberRole role = CourseMemberRole.valueOf(member.getMemberRole());
        return switch (role) {
            case INSTRUCTOR, OFFERING_TA, OBSERVER -> member.getOfferingId();
            case CLASS_INSTRUCTOR, TA, STUDENT -> member.getTeachingClassId();
        };
    }
}
