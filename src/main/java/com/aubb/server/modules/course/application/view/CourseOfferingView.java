package com.aubb.server.modules.course.application.view;

import com.aubb.server.modules.course.domain.catalog.CourseDeliveryMode;
import com.aubb.server.modules.course.domain.catalog.CourseLanguage;
import com.aubb.server.modules.course.domain.offering.CourseOfferingStatus;
import com.aubb.server.modules.identityaccess.application.user.view.UserDirectoryEntryView;
import com.aubb.server.modules.organization.application.OrgUnitSummaryView;
import java.time.OffsetDateTime;
import java.util.List;

public record CourseOfferingView(
        Long id,
        Long catalogId,
        String catalogCode,
        String catalogName,
        Long termId,
        String termCode,
        String offeringCode,
        String offeringName,
        Long orgCourseUnitId,
        OrgUnitSummaryView primaryCollege,
        List<OrgUnitSummaryView> managingColleges,
        List<UserDirectoryEntryView> instructors,
        CourseDeliveryMode deliveryMode,
        CourseLanguage language,
        Integer capacity,
        Integer selectedCount,
        CourseOfferingStatus status,
        String intro,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime publishAt,
        OffsetDateTime archivedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
