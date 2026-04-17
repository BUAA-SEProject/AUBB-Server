package com.aubb.server.modules.course.application.result;

import java.util.List;

public record CourseMemberBatchResult(int successCount, int failCount, List<CourseMemberBatchError> errors) {}
