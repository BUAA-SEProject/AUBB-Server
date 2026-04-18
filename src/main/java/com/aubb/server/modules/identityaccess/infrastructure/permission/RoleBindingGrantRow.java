package com.aubb.server.modules.identityaccess.infrastructure.permission;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleBindingGrantRow {

    private Long bindingId;

    private Long userId;

    private Long roleId;

    private String roleCode;

    private String roleName;

    private String roleDescription;

    private String roleCategory;

    private String roleScopeType;

    private Boolean roleBuiltin;

    private String roleStatus;

    private Long permissionId;

    private String permissionCode;

    private String permissionResourceType;

    private String permissionAction;

    private String permissionDescription;

    private Boolean permissionSensitive;

    private Long bindingRoleId;

    private String bindingScopeType;

    private Long bindingScopeId;

    private String bindingStatus;

    private String constraintsJson;

    private Long grantedBy;

    private String sourceType;

    private Long sourceRefId;
}
