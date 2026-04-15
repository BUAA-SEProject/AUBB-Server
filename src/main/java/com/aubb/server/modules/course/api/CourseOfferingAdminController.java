package com.aubb.server.modules.course.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.course.application.CourseAdministrationApplicationService;
import com.aubb.server.modules.course.application.CourseOfferingView;
import com.aubb.server.modules.course.domain.CourseDeliveryMode;
import com.aubb.server.modules.course.domain.CourseLanguage;
import com.aubb.server.modules.course.domain.CourseOfferingStatus;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/course-offerings")
@RequiredArgsConstructor
public class CourseOfferingAdminController {

    private final CourseAdministrationApplicationService courseAdministrationApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('SCHOOL_ADMIN', 'COLLEGE_ADMIN')")
    public CourseOfferingView create(
            @Valid @RequestBody CreateCourseOfferingRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseAdministrationApplicationService.createOffering(
                request.catalogId(),
                request.termId(),
                request.offeringCode(),
                request.offeringName(),
                request.primaryCollegeUnitId(),
                request.secondaryCollegeUnitIds(),
                request.deliveryMode(),
                request.language(),
                request.capacity(),
                request.instructorUserIds(),
                request.startAt(),
                request.endAt(),
                principal);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCHOOL_ADMIN', 'COLLEGE_ADMIN', 'COURSE_ADMIN')")
    public PageResponse<CourseOfferingView> list(
            @RequestParam(required = false) Long collegeUnitId,
            @RequestParam(required = false) CourseOfferingStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseAdministrationApplicationService.listOfferings(
                principal, collegeUnitId, status, keyword, page, pageSize);
    }

    @GetMapping("/{offeringId}")
    @PreAuthorize("hasAnyAuthority('SCHOOL_ADMIN', 'COLLEGE_ADMIN', 'COURSE_ADMIN')")
    public CourseOfferingView detail(
            @PathVariable Long offeringId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseAdministrationApplicationService.getOffering(offeringId, principal);
    }

    public record CreateCourseOfferingRequest(
            @NotNull Long catalogId,
            @NotNull Long termId,
            @NotBlank String offeringCode,
            @NotBlank String offeringName,
            @NotNull Long primaryCollegeUnitId,
            List<Long> secondaryCollegeUnitIds,
            @NotNull CourseDeliveryMode deliveryMode,
            @NotNull CourseLanguage language,
            @NotNull @Positive Integer capacity,
            @NotEmpty List<Long> instructorUserIds,

            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime startAt,

            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime endAt) {}
}
