package com.aubb.server.modules.course.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.course.application.CourseAdministrationApplicationService;
import com.aubb.server.modules.course.application.view.AcademicTermView;
import com.aubb.server.modules.course.domain.term.AcademicTermSemester;
import com.aubb.server.modules.course.domain.term.AcademicTermStatus;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/admin/academic-terms")
@RequiredArgsConstructor
public class AcademicTermAdminController {

    private final CourseAdministrationApplicationService courseAdministrationApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCHOOL_ADMIN')")
    public AcademicTermView create(
            @Valid @RequestBody CreateAcademicTermRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseAdministrationApplicationService.createTerm(
                request.termCode(),
                request.termName(),
                request.schoolYear(),
                request.semester(),
                request.startDate(),
                request.endDate(),
                principal);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCHOOL_ADMIN')")
    public PageResponse<AcademicTermView> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AcademicTermStatus status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return courseAdministrationApplicationService.listTerms(keyword, status, page, pageSize);
    }

    public record CreateAcademicTermRequest(
            @NotBlank String termCode,
            @NotBlank String termName,
            @NotBlank String schoolYear,
            @NotNull AcademicTermSemester semester,

            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate) {}
}
