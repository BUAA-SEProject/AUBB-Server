package com.aubb.server.modules.assignment.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.modules.assignment.application.AssignmentApplicationService;
import com.aubb.server.modules.assignment.application.AssignmentView;
import com.aubb.server.modules.assignment.application.judge.AssignmentJudgeCaseInput;
import com.aubb.server.modules.assignment.application.judge.AssignmentJudgeConfigInput;
import com.aubb.server.modules.assignment.application.paper.AssignmentPaperInput;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionConfigInput;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionInput;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionOptionInput;
import com.aubb.server.modules.assignment.application.paper.AssignmentSectionInput;
import com.aubb.server.modules.assignment.application.paper.ProgrammingJudgeCaseInput;
import com.aubb.server.modules.assignment.domain.AssignmentStatus;
import com.aubb.server.modules.assignment.domain.judge.AssignmentJudgeLanguage;
import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import com.aubb.server.modules.assignment.domain.question.ProgrammingJudgeMode;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
public class AssignmentTeacherController {

    private final AssignmentApplicationService assignmentApplicationService;

    @PostMapping("/course-offerings/{offeringId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public AssignmentView create(
            @PathVariable Long offeringId,
            @Valid @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return assignmentApplicationService.createAssignment(
                offeringId,
                request.title(),
                request.description(),
                request.teachingClassId(),
                request.openAt(),
                request.dueAt(),
                request.maxSubmissions(),
                request.toPaperInput(),
                request.toJudgeConfigInput(),
                principal);
    }

    @GetMapping("/course-offerings/{offeringId}/assignments")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<AssignmentView> list(
            @PathVariable Long offeringId,
            @RequestParam(required = false) AssignmentStatus status,
            @RequestParam(required = false) Long teachingClassId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return assignmentApplicationService.listTeacherAssignments(
                offeringId, status, teachingClassId, page, pageSize, principal);
    }

    @GetMapping("/assignments/{assignmentId}")
    @PreAuthorize("isAuthenticated()")
    public AssignmentView detail(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return assignmentApplicationService.getTeacherAssignment(assignmentId, principal);
    }

    @PostMapping("/assignments/{assignmentId}/publish")
    @PreAuthorize("isAuthenticated()")
    public AssignmentView publish(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return assignmentApplicationService.publishAssignment(assignmentId, principal);
    }

    @PostMapping("/assignments/{assignmentId}/close")
    @PreAuthorize("isAuthenticated()")
    public AssignmentView close(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return assignmentApplicationService.closeAssignment(assignmentId, principal);
    }

    public record CreateAssignmentRequest(
            @NotBlank String title,
            String description,
            Long teachingClassId,
            @NotNull OffsetDateTime openAt,
            @NotNull OffsetDateTime dueAt,
            @NotNull @Positive Integer maxSubmissions,
            @Valid PaperRequest paper,
            @Valid JudgeConfigRequest judgeConfig) {

        AssignmentPaperInput toPaperInput() {
            return paper == null ? null : paper.toInput();
        }

        AssignmentJudgeConfigInput toJudgeConfigInput() {
            return judgeConfig == null ? null : judgeConfig.toInput();
        }
    }

    public record PaperRequest(@NotEmpty List<@Valid SectionRequest> sections) {

        AssignmentPaperInput toInput() {
            return new AssignmentPaperInput(
                    sections.stream().map(SectionRequest::toInput).toList());
        }
    }

    public record SectionRequest(
            @NotBlank String title,
            String description,
            @NotEmpty List<@Valid QuestionRequest> questions) {

        AssignmentSectionInput toInput() {
            return new AssignmentSectionInput(
                    title,
                    description,
                    questions.stream().map(QuestionRequest::toInput).toList());
        }
    }

    public record QuestionRequest(
            Long bankQuestionId,
            String title,
            String prompt,
            AssignmentQuestionType questionType,
            Integer score,
            List<@Valid QuestionOptionRequest> options,
            @Valid QuestionConfigRequest config) {

        AssignmentQuestionInput toInput() {
            return new AssignmentQuestionInput(
                    bankQuestionId,
                    title,
                    prompt,
                    questionType,
                    score,
                    options == null
                            ? null
                            : options.stream()
                                    .map(QuestionOptionRequest::toInput)
                                    .toList(),
                    config == null ? null : config.toQuestionInput());
        }
    }

    public record QuestionOptionRequest(
            @NotBlank String optionKey, @NotBlank String content, Boolean correct) {

        AssignmentQuestionOptionInput toInput() {
            return new AssignmentQuestionOptionInput(optionKey, content, correct);
        }
    }

    public record QuestionConfigRequest(
            List<ProgrammingLanguage> supportedLanguages,
            Integer maxFileCount,
            Integer maxFileSizeMb,
            List<String> acceptedExtensions,
            Boolean allowMultipleFiles,
            Boolean allowSampleRun,
            String sampleStdinText,
            String sampleExpectedStdout,
            String templateEntryFilePath,
            List<String> templateDirectories,
            List<ProgrammingSourceFile> templateFiles,
            Integer timeLimitMs,
            Integer memoryLimitMb,
            Integer outputLimitKb,
            List<String> compileArgs,
            List<String> runArgs,
            ProgrammingJudgeMode judgeMode,
            String customJudgeScript,
            String referenceAnswer,
            List<@Valid ProgrammingJudgeCaseRequest> judgeCases) {

        AssignmentQuestionConfigInput toQuestionInput() {
            return new AssignmentQuestionConfigInput(
                    supportedLanguages,
                    maxFileCount,
                    maxFileSizeMb,
                    acceptedExtensions,
                    allowMultipleFiles,
                    allowSampleRun,
                    sampleStdinText,
                    sampleExpectedStdout,
                    templateEntryFilePath,
                    templateDirectories,
                    templateFiles,
                    timeLimitMs,
                    memoryLimitMb,
                    outputLimitKb,
                    compileArgs,
                    runArgs,
                    judgeMode,
                    customJudgeScript,
                    referenceAnswer,
                    judgeCases == null
                            ? List.of()
                            : judgeCases.stream()
                                    .map(ProgrammingJudgeCaseRequest::toInput)
                                    .toList());
        }
    }

    public record ProgrammingJudgeCaseRequest(
            @NotBlank String stdinText,
            @NotBlank String expectedStdout,
            @NotNull @PositiveOrZero Integer score) {

        ProgrammingJudgeCaseInput toInput() {
            return new ProgrammingJudgeCaseInput(stdinText, expectedStdout, score);
        }
    }

    public record JudgeConfigRequest(
            @NotNull AssignmentJudgeLanguage language,
            @NotNull @Positive Integer timeLimitMs,
            @NotNull @Positive Integer memoryLimitMb,
            @NotNull @Positive Integer outputLimitKb,
            @NotEmpty List<@Valid JudgeCaseRequest> testCases) {

        AssignmentJudgeConfigInput toInput() {
            return new AssignmentJudgeConfigInput(
                    language,
                    timeLimitMs,
                    memoryLimitMb,
                    outputLimitKb,
                    testCases.stream().map(JudgeCaseRequest::toInput).toList());
        }
    }

    public record JudgeCaseRequest(
            @NotNull String stdinText,
            @NotNull String expectedStdout,
            @NotNull @PositiveOrZero Integer score) {

        AssignmentJudgeCaseInput toInput() {
            return new AssignmentJudgeCaseInput(stdinText, expectedStdout, score);
        }
    }
}
