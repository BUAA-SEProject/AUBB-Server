package com.aubb.server.domain.audit;

public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    PLATFORM_CONFIG_UPDATED,
    USER_CREATED,
    USER_IMPORTED,
    USER_STATUS_CHANGED,
    USER_IDENTITIES_CHANGED,
    ORG_UNIT_CREATED
}
