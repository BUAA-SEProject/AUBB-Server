package com.aubb.server.modules.identityaccess.application.authz;

public record GroupBindingView(String source, String templateCode, String scopeType, Long scopeRefId) {}
