package com.aubb.server.modules.identityaccess.infrastructure.permission;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleBindingGrantQueryMapper extends BaseMapper<RoleBindingEntity> {

    @Select({
        "SELECT rb.id AS bindingId,",
        "       rb.user_id AS userId,",
        "       r.id AS roleId,",
        "       r.code AS roleCode,",
        "       r.name AS roleName,",
        "       r.description AS roleDescription,",
        "       r.role_category AS roleCategory,",
        "       r.scope_type AS roleScopeType,",
        "       r.is_builtin AS roleBuiltin,",
        "       r.status AS roleStatus,",
        "       p.id AS permissionId,",
        "       p.code AS permissionCode,",
        "       p.resource_type AS permissionResourceType,",
        "       p.action AS permissionAction,",
        "       p.description AS permissionDescription,",
        "       p.sensitive AS permissionSensitive,",
        "       rb.role_id AS bindingRoleId,",
        "       rb.scope_type AS bindingScopeType,",
        "       rb.scope_id AS bindingScopeId,",
        "       rb.status AS bindingStatus,",
        "       rb.constraints_json::text AS constraintsJson,",
        "       rb.granted_by AS grantedBy,",
        "       rb.source_type AS sourceType,",
        "       rb.source_ref_id AS sourceRefId",
        "FROM role_bindings rb",
        "JOIN roles r ON r.id = rb.role_id",
        "JOIN role_permissions rp ON rp.role_id = r.id",
        "JOIN permissions p ON p.id = rp.permission_id",
        "WHERE rb.user_id = #{userId}",
        "  AND rb.status = 'ACTIVE'",
        "  AND r.status = 'ACTIVE'",
        "  AND (rb.effective_from IS NULL OR rb.effective_from <= (#{asOf} + interval '3 seconds'))",
        "  AND (rb.effective_to IS NULL OR rb.effective_to >= #{asOf})",
        "ORDER BY rb.id ASC, p.id ASC"
    })
    List<RoleBindingGrantRow> selectActiveGrantRowsByUserId(
            @Param("userId") Long userId, @Param("asOf") OffsetDateTime asOf);

    @Select({
        "SELECT rb.id AS bindingId,",
        "       rb.user_id AS userId,",
        "       r.id AS roleId,",
        "       r.code AS roleCode,",
        "       r.name AS roleName,",
        "       r.description AS roleDescription,",
        "       r.role_category AS roleCategory,",
        "       r.scope_type AS roleScopeType,",
        "       r.is_builtin AS roleBuiltin,",
        "       r.status AS roleStatus,",
        "       p.id AS permissionId,",
        "       p.code AS permissionCode,",
        "       p.resource_type AS permissionResourceType,",
        "       p.action AS permissionAction,",
        "       p.description AS permissionDescription,",
        "       p.sensitive AS permissionSensitive,",
        "       rb.role_id AS bindingRoleId,",
        "       rb.scope_type AS bindingScopeType,",
        "       rb.scope_id AS bindingScopeId,",
        "       rb.status AS bindingStatus,",
        "       rb.constraints_json::text AS constraintsJson,",
        "       rb.granted_by AS grantedBy,",
        "       rb.source_type AS sourceType,",
        "       rb.source_ref_id AS sourceRefId",
        "FROM role_bindings rb",
        "JOIN roles r ON r.id = rb.role_id",
        "JOIN role_permissions rp ON rp.role_id = r.id",
        "JOIN permissions p ON p.id = rp.permission_id",
        "WHERE rb.user_id = #{userId}",
        "  AND lower(p.code) = lower(#{permissionCode})",
        "  AND rb.status = 'ACTIVE'",
        "  AND r.status = 'ACTIVE'",
        "  AND (rb.effective_from IS NULL OR rb.effective_from <= (#{asOf} + interval '3 seconds'))",
        "  AND (rb.effective_to IS NULL OR rb.effective_to >= #{asOf})",
        "ORDER BY rb.id ASC, p.id ASC"
    })
    List<RoleBindingGrantRow> selectActiveGrantRowsByUserIdAndPermissionCode(
            @Param("userId") Long userId,
            @Param("permissionCode") String permissionCode,
            @Param("asOf") OffsetDateTime asOf);
}
