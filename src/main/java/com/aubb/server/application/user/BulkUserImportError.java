package com.aubb.server.application.user;

public record BulkUserImportError(int row, String username, String reason) {}
