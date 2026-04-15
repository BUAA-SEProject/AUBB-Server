package com.aubb.server.modules.identityaccess.application.user;

import java.util.List;

public record BulkUserImportResult(int total, int success, int failed, List<BulkUserImportError> errors) {}
