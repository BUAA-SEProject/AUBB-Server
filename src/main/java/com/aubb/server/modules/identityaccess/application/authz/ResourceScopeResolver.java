package com.aubb.server.modules.identityaccess.application.authz;

public interface ResourceScopeResolver<T> {

    ScopeRef resolve(T resource);
}
