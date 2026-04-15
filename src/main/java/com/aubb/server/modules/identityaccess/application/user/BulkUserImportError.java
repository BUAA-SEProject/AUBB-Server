package com.aubb.server.modules.identityaccess.application.user;

public record BulkUserImportError(int row, String username, String reason) {}
