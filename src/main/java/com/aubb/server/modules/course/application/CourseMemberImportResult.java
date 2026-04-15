package com.aubb.server.modules.course.application;

import java.util.List;

public record CourseMemberImportResult(int total, int success, int failed, List<CourseMemberBatchError> errors) {}
