package com.aubb.server.modules.identityaccess.infrastructure.authz;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthzGroupGrantRow {

    private Long groupId;
    private String templateCode;
    private String scopeType;
    private Long scopeRefId;
    private String permissionCode;
}
