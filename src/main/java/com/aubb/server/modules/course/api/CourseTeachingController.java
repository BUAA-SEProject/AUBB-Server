package com.aubb.server.modules.course.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.course.application.CourseTeachingApplicationService;
import com.aubb.server.modules.course.application.command.CourseMemberCommand;
import com.aubb.server.modules.course.application.result.CourseMemberBatchResult;
import com.aubb.server.modules.course.application.result.CourseMemberImportResult;
import com.aubb.server.modules.course.application.view.CourseMemberView;
import com.aubb.server.modules.course.application.view.TeachingClassView;
import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.course.domain.member.CourseMemberStatus;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
public class CourseTeachingController {

    private final CourseTeachingApplicationService courseTeachingApplicationService;

    @PostMapping("/course-offerings/{offeringId}/classes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public TeachingClassView createTeachingClass(
            @PathVariable Long offeringId,
            @Valid @RequestBody CreateTeachingClassRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseTeachingApplicationService.createTeachingClass(
                offeringId,
                request.classCode(),
                request.className(),
                request.entryYear(),
                request.capacity(),
                request.scheduleSummary(),
                principal);
    }

    @GetMapping("/course-offerings/{offeringId}/classes")
    @PreAuthorize("isAuthenticated()")
    public List<TeachingClassView> listTeachingClasses(
            @PathVariable Long offeringId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseTeachingApplicationService.listTeachingClasses(offeringId, principal);
    }

    @PutMapping("/course-classes/{teachingClassId}/features")
    @PreAuthorize("isAuthenticated()")
    public TeachingClassView updateTeachingClassFeatures(
            @PathVariable Long teachingClassId,
            @Valid @RequestBody UpdateTeachingClassFeaturesRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseTeachingApplicationService.updateTeachingClassFeatures(
                teachingClassId,
                request.announcementEnabled(),
                request.discussionEnabled(),
                request.resourceEnabled(),
                request.labEnabled(),
                request.assignmentEnabled(),
                principal);
    }

    @PostMapping("/course-offerings/{offeringId}/members/batch")
    @PreAuthorize("isAuthenticated()")
    public CourseMemberBatchResult batchAddMembers(
            @PathVariable Long offeringId,
            @Valid @RequestBody BatchAddMembersRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseTeachingApplicationService.addMembersBatch(offeringId, request.members(), principal);
    }

    @PostMapping("/course-offerings/{offeringId}/members/import")
    @PreAuthorize("isAuthenticated()")
    public CourseMemberImportResult importMembers(
            @PathVariable Long offeringId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "csv") String importType,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseTeachingApplicationService.importMembers(offeringId, file, importType, principal);
    }

    @PatchMapping("/course-offerings/{offeringId}/members/{memberId}/status")
    @PreAuthorize("isAuthenticated()")
    public CourseMemberView updateMemberStatus(
            @PathVariable Long offeringId,
            @PathVariable Long memberId,
            @Valid @RequestBody UpdateMemberStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseTeachingApplicationService.updateMemberStatus(
                offeringId, memberId, request.memberStatus(), request.remark(), principal);
    }

    @PostMapping("/course-offerings/{offeringId}/members/{memberId}/transfer")
    @PreAuthorize("isAuthenticated()")
    public CourseMemberView transferMember(
            @PathVariable Long offeringId,
            @PathVariable Long memberId,
            @Valid @RequestBody TransferMemberRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseTeachingApplicationService.transferStudent(
                offeringId, memberId, request.targetTeachingClassId(), request.remark(), principal);
    }

    @GetMapping("/course-offerings/{offeringId}/members")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<CourseMemberView> listMembers(
            @PathVariable Long offeringId,
            @RequestParam(required = false) Long teachingClassId,
            @RequestParam(required = false) CourseMemberRole memberRole,
            @RequestParam(required = false) CourseMemberStatus memberStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseTeachingApplicationService.listMembers(
                offeringId, teachingClassId, memberRole, memberStatus, keyword, page, pageSize, principal);
    }

    public record CreateTeachingClassRequest(
            @NotBlank String classCode,
            @NotBlank String className,
            @NotNull Integer entryYear,
            @NotNull @Positive Integer capacity,
            String scheduleSummary) {}

    public record UpdateTeachingClassFeaturesRequest(
            boolean announcementEnabled,
            boolean discussionEnabled,
            boolean resourceEnabled,
            boolean labEnabled,
            boolean assignmentEnabled) {}

    public record UpdateMemberStatusRequest(@NotNull CourseMemberStatus memberStatus, String remark) {}

    public record TransferMemberRequest(@NotNull Long targetTeachingClassId, String remark) {}

    public record BatchAddMembersRequest(List<@Valid CourseMemberCommand> members) {}
}
