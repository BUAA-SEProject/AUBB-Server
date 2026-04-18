package com.aubb.server.modules.identityaccess.infrastructure.authz;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthzGroupQueryMapper extends BaseMapper<AuthGroupEntity> {

    @Select({
        "SELECT g.id AS groupId,",
        "       t.code AS templateCode,",
        "       g.scope_type AS scopeType,",
        "       g.scope_ref_id AS scopeRefId,",
        "       tp.permission_code AS permissionCode",
        "FROM auth_group_members gm",
        "JOIN auth_groups g ON g.id = gm.group_id",
        "JOIN auth_group_templates t ON t.id = g.template_id",
        "JOIN auth_group_template_permissions tp ON tp.template_id = t.id",
        "WHERE gm.user_id = #{userId}",
        "  AND g.status = 'ACTIVE'",
        "  AND t.status = 'ACTIVE'",
        "  AND (gm.expires_at IS NULL OR gm.expires_at > now())",
        "ORDER BY g.id ASC, tp.permission_code ASC"
    })
    List<AuthzGroupGrantRow> selectActiveGrantRowsByUserId(@Param("userId") Long userId);

    @Select({
        "SELECT g.id AS groupId,",
        "       t.code AS templateCode,",
        "       g.scope_type AS scopeType,",
        "       g.scope_ref_id AS scopeRefId",
        "FROM auth_group_members gm",
        "JOIN auth_groups g ON g.id = gm.group_id",
        "JOIN auth_group_templates t ON t.id = g.template_id",
        "WHERE gm.user_id = #{userId}",
        "  AND g.status = 'ACTIVE'",
        "  AND t.status = 'ACTIVE'",
        "  AND (gm.expires_at IS NULL OR gm.expires_at > now())",
        "ORDER BY g.id ASC"
    })
    List<AuthzGroupBindingRow> selectActiveBindingsByUserId(@Param("userId") Long userId);
}
