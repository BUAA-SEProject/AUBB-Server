package com.aubb.server.modules.identityaccess.application.user.result;

public record BulkUserImportError(int row, String username, String reason) {}
