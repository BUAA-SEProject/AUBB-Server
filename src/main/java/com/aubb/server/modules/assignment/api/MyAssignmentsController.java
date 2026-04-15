package com.aubb.server.modules.assignment.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.assignment.application.AssignmentApplicationService;
import com.aubb.server.modules.assignment.application.AssignmentView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/assignments")
@RequiredArgsConstructor
public class MyAssignmentsController {

    private final AssignmentApplicationService assignmentApplicationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<AssignmentView> list(
            @RequestParam(required = false) Long offeringId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return assignmentApplicationService.listMyAssignments(offeringId, page, pageSize, principal);
    }

    @GetMapping("/{assignmentId}")
    @PreAuthorize("isAuthenticated()")
    public AssignmentView detail(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return assignmentApplicationService.getMyAssignment(assignmentId, principal);
    }
}
