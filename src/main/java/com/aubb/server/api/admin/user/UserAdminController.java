package com.aubb.server.api.admin.user;

import com.aubb.server.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.application.iam.IdentityAssignmentCommand;
import com.aubb.server.application.user.BulkUserImportResult;
import com.aubb.server.application.user.UserAdministrationApplicationService;
import com.aubb.server.application.user.UserView;
import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.domain.iam.AccountStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdministrationApplicationService userAdministrationApplicationService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCHOOL_ADMIN', 'COLLEGE_ADMIN', 'COURSE_ADMIN', 'CLASS_ADMIN')")
    public PageResponse<UserView> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AccountStatus accountStatus,
            @RequestParam(required = false) Long orgUnitId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return userAdministrationApplicationService.listUsers(
                principal, keyword, accountStatus, orgUnitId, page, pageSize);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyAuthority('SCHOOL_ADMIN', 'COLLEGE_ADMIN', 'COURSE_ADMIN', 'CLASS_ADMIN')")
    public UserView detail(@PathVariable Long userId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return userAdministrationApplicationService.getUser(userId, principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('SCHOOL_ADMIN', 'COLLEGE_ADMIN', 'COURSE_ADMIN', 'CLASS_ADMIN')")
    public UserView create(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return userAdministrationApplicationService.createUser(
                request.username(),
                request.displayName(),
                request.email(),
                request.password(),
                request.primaryOrgUnitId(),
                request.identityAssignments(),
                request.accountStatus(),
                principal);
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('SCHOOL_ADMIN', 'COLLEGE_ADMIN', 'COURSE_ADMIN', 'CLASS_ADMIN')")
    public BulkUserImportResult importUsers(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "csv") String importType,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        if (!"csv".equalsIgnoreCase(importType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IMPORT_TYPE_UNSUPPORTED", "当前仅支持 csv 导入");
        }
        return userAdministrationApplicationService.importUsers(file, principal);
    }

    @PutMapping("/{userId}/identities")
    @PreAuthorize("hasAnyAuthority('SCHOOL_ADMIN', 'COLLEGE_ADMIN', 'COURSE_ADMIN', 'CLASS_ADMIN')")
    public UserView updateIdentities(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateIdentitiesRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return userAdministrationApplicationService.updateIdentities(userId, request.identityAssignments(), principal);
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAnyAuthority('SCHOOL_ADMIN', 'COLLEGE_ADMIN', 'COURSE_ADMIN', 'CLASS_ADMIN')")
    public UserView updateStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return userAdministrationApplicationService.updateStatus(userId, request.accountStatus(), principal);
    }

    public record CreateUserRequest(
            @NotBlank String username,
            @NotBlank String displayName,
            @Email @NotBlank String email,
            @NotBlank String password,
            Long primaryOrgUnitId,
            List<IdentityAssignmentCommand> identityAssignments,
            AccountStatus accountStatus) {}

    public record UpdateIdentitiesRequest(List<IdentityAssignmentCommand> identityAssignments) {}

    public record UpdateStatusRequest(@NotNull AccountStatus accountStatus) {}
}
