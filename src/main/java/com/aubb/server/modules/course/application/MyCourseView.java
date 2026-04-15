package com.aubb.server.modules.course.application;

import com.aubb.server.modules.course.domain.CourseOfferingStatus;
import com.aubb.server.modules.organization.application.OrgUnitSummaryView;
import java.util.List;

public record MyCourseView(
        Long offeringId,
        String offeringCode,
        String offeringName,
        CourseOfferingStatus status,
        OrgUnitSummaryView primaryCollege,
        List<String> roles,
        List<MyCourseClassView> classes) {}
