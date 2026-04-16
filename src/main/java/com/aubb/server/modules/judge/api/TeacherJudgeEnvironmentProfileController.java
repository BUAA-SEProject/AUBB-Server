package com.aubb.server.modules.judge.api;

import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.modules.assignment.application.paper.ProgrammingExecutionEnvironmentInput;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.judge.application.environment.JudgeEnvironmentProfileApplicationService;
import com.aubb.server.modules.judge.application.environment.JudgeEnvironmentProfileView;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
public class TeacherJudgeEnvironmentProfileController {

    private final JudgeEnvironmentProfileApplicationService judgeEnvironmentProfileApplicationService;

    @PostMapping("/course-offerings/{offeringId}/judge-environment-profiles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public JudgeEnvironmentProfileView create(
            @PathVariable Long offeringId,
            @Valid @RequestBody SaveJudgeEnvironmentProfileRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return judgeEnvironmentProfileApplicationService.createProfile(
                offeringId,
                request.profileCode(),
                request.profileName(),
                request.description(),
                request.programmingLanguage(),
                request.environment().toInput(),
                principal);
    }

    @GetMapping("/course-offerings/{offeringId}/judge-environment-profiles")
    @PreAuthorize("isAuthenticated()")
    public List<JudgeEnvironmentProfileView> list(
            @PathVariable Long offeringId,
            @RequestParam(required = false) ProgrammingLanguage programmingLanguage,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return judgeEnvironmentProfileApplicationService.listProfiles(
                offeringId, programmingLanguage, includeArchived, principal);
    }

    @GetMapping("/judge-environment-profiles/{profileId}")
    @PreAuthorize("isAuthenticated()")
    public JudgeEnvironmentProfileView detail(
            @PathVariable Long profileId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return judgeEnvironmentProfileApplicationService.getProfile(profileId, principal);
    }

    @PutMapping("/judge-environment-profiles/{profileId}")
    @PreAuthorize("isAuthenticated()")
    public JudgeEnvironmentProfileView update(
            @PathVariable Long profileId,
            @Valid @RequestBody SaveJudgeEnvironmentProfileRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return judgeEnvironmentProfileApplicationService.updateProfile(
                profileId,
                request.profileCode(),
                request.profileName(),
                request.description(),
                request.environment().toInput(),
                principal);
    }

    @PostMapping("/judge-environment-profiles/{profileId}/archive")
    @PreAuthorize("isAuthenticated()")
    public JudgeEnvironmentProfileView archive(
            @PathVariable Long profileId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return judgeEnvironmentProfileApplicationService.archiveProfile(profileId, principal);
    }

    public record SaveJudgeEnvironmentProfileRequest(
            @NotBlank String profileCode,
            @NotBlank String profileName,
            String description,
            @NotNull ProgrammingLanguage programmingLanguage,
            @NotNull @Valid ProgrammingExecutionEnvironmentRequest environment) {}

    public record ProgrammingExecutionEnvironmentRequest(
            String languageVersion,
            String workingDirectory,
            String initScript,
            String compileCommand,
            String runCommand,
            java.util.Map<String, String> environmentVariables,
            Integer cpuRateLimit,
            List<ProgrammingSourceFile> supportFiles) {

        ProgrammingExecutionEnvironmentInput toInput() {
            return new ProgrammingExecutionEnvironmentInput(
                    null,
                    null,
                    null,
                    null,
                    languageVersion,
                    workingDirectory,
                    initScript,
                    compileCommand,
                    runCommand,
                    environmentVariables,
                    cpuRateLimit,
                    supportFiles == null ? List.of() : supportFiles);
        }
    }
}
