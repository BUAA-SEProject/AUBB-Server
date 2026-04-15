package com.aubb.server.api.admin.organization;

import com.aubb.server.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.application.organization.OrgUnitTreeNode;
import com.aubb.server.application.organization.OrganizationApplicationService;
import com.aubb.server.domain.organization.OrgUnitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/org-units")
@RequiredArgsConstructor
public class OrganizationAdminController {

    private final OrganizationApplicationService organizationApplicationService;

    @GetMapping("/tree")
    @PreAuthorize("hasAnyAuthority('SCHOOL_ADMIN', 'COLLEGE_ADMIN', 'COURSE_ADMIN', 'CLASS_ADMIN')")
    public List<OrgUnitTreeNode> tree(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return organizationApplicationService.getTree(principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('SCHOOL_ADMIN', 'COLLEGE_ADMIN', 'COURSE_ADMIN')")
    public OrgUnitTreeNode create(
            @Valid @RequestBody CreateOrgUnitRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return organizationApplicationService.createOrgUnit(
                request.name(), request.code(), request.type(), request.parentId(), request.sortOrder(), principal);
    }

    public record CreateOrgUnitRequest(
            @NotBlank String name,
            @NotBlank String code,
            @NotNull OrgUnitType type,
            Long parentId,
            int sortOrder) {}
}
