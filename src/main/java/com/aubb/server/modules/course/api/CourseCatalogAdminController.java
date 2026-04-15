package com.aubb.server.modules.course.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.course.application.CourseAdministrationApplicationService;
import com.aubb.server.modules.course.application.CourseCatalogView;
import com.aubb.server.modules.course.domain.CourseCatalogStatus;
import com.aubb.server.modules.course.domain.CourseType;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/course-catalogs")
@RequiredArgsConstructor
public class CourseCatalogAdminController {

    private final CourseAdministrationApplicationService courseAdministrationApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('SCHOOL_ADMIN', 'COLLEGE_ADMIN')")
    public CourseCatalogView create(
            @Valid @RequestBody CreateCourseCatalogRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseAdministrationApplicationService.createCatalog(
                request.courseCode(),
                request.courseName(),
                request.courseType(),
                request.credit(),
                request.totalHours(),
                request.departmentUnitId(),
                request.description(),
                principal);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCHOOL_ADMIN', 'COLLEGE_ADMIN')")
    public PageResponse<CourseCatalogView> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CourseType courseType,
            @RequestParam(required = false) Long departmentUnitId,
            @RequestParam(required = false) CourseCatalogStatus status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseAdministrationApplicationService.listCatalogs(
                principal, keyword, courseType, departmentUnitId, status, page, pageSize);
    }

    public record CreateCourseCatalogRequest(
            @NotBlank String courseCode,
            @NotBlank String courseName,
            @NotNull CourseType courseType,
            @NotNull @DecimalMin("0.0") BigDecimal credit,
            @NotNull @PositiveOrZero Integer totalHours,
            @NotNull Long departmentUnitId,
            String description) {}
}
