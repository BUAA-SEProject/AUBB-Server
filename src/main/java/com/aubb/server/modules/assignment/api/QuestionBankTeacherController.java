package com.aubb.server.modules.assignment.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.modules.assignment.application.bank.QuestionBankApplicationService;
import com.aubb.server.modules.assignment.application.bank.QuestionBankCategoryView;
import com.aubb.server.modules.assignment.application.bank.QuestionBankQuestionView;
import com.aubb.server.modules.assignment.application.bank.QuestionBankTagView;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionConfigInput;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionOptionInput;
import com.aubb.server.modules.assignment.application.paper.ProgrammingExecutionEnvironmentInput;
import com.aubb.server.modules.assignment.application.paper.ProgrammingJudgeCaseInput;
import com.aubb.server.modules.assignment.application.paper.ProgrammingLanguageExecutionEnvironmentInput;
import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import com.aubb.server.modules.assignment.domain.question.ProgrammingJudgeMode;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
public class QuestionBankTeacherController {

    private final QuestionBankApplicationService questionBankApplicationService;

    @PostMapping("/course-offerings/{offeringId}/question-bank/questions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public QuestionBankQuestionView create(
            @PathVariable Long offeringId,
            @Valid @RequestBody CreateQuestionBankQuestionRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return questionBankApplicationService.createQuestion(
                offeringId,
                request.title(),
                request.prompt(),
                request.questionType(),
                request.defaultScore(),
                request.toOptionInputs(),
                request.categoryName(),
                request.tags(),
                request.toConfigInput(),
                principal);
    }

    @GetMapping("/course-offerings/{offeringId}/question-bank/questions")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<QuestionBankQuestionView> list(
            @PathVariable Long offeringId,
            @RequestParam(required = false) AssignmentQuestionType questionType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(name = "tag", required = false) List<String> tags,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return questionBankApplicationService.listQuestions(
                offeringId, questionType, keyword, category, tags, includeArchived, page, pageSize, principal);
    }

    @GetMapping("/course-offerings/{offeringId}/question-bank/categories")
    @PreAuthorize("isAuthenticated()")
    public List<QuestionBankCategoryView> listCategories(
            @PathVariable Long offeringId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return questionBankApplicationService.listCategories(offeringId, principal);
    }

    @GetMapping("/course-offerings/{offeringId}/question-bank/tags")
    @PreAuthorize("isAuthenticated()")
    public List<QuestionBankTagView> listTags(
            @PathVariable Long offeringId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return questionBankApplicationService.listTagDictionary(offeringId, principal);
    }

    @GetMapping("/question-bank/questions/{questionId}")
    @PreAuthorize("isAuthenticated()")
    public QuestionBankQuestionView detail(
            @PathVariable Long questionId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return questionBankApplicationService.getQuestion(questionId, principal);
    }

    @PutMapping("/question-bank/questions/{questionId}")
    @PreAuthorize("isAuthenticated()")
    public QuestionBankQuestionView update(
            @PathVariable Long questionId,
            @Valid @RequestBody CreateQuestionBankQuestionRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return questionBankApplicationService.updateQuestion(
                questionId,
                request.title(),
                request.prompt(),
                request.questionType(),
                request.defaultScore(),
                request.toOptionInputs(),
                request.categoryName(),
                request.tags(),
                request.toConfigInput(),
                principal);
    }

    @PostMapping("/question-bank/questions/{questionId}/archive")
    @PreAuthorize("isAuthenticated()")
    public QuestionBankQuestionView archive(
            @PathVariable Long questionId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return questionBankApplicationService.archiveQuestion(questionId, principal);
    }

    public record CreateQuestionBankQuestionRequest(
            @NotBlank String title,
            @NotBlank String prompt,
            @NotNull AssignmentQuestionType questionType,
            @NotNull @Positive Integer defaultScore,
            List<@Valid QuestionOptionRequest> options,
            @Size(max = 64) String categoryName,
            List<@NotBlank @Size(max = 32) String> tags,
            @Valid QuestionConfigRequest config) {

        List<AssignmentQuestionOptionInput> toOptionInputs() {
            return options == null
                    ? List.of()
                    : options.stream()
                            .map(option -> new AssignmentQuestionOptionInput(
                                    option.optionKey(), option.content(), option.correct()))
                            .toList();
        }

        AssignmentQuestionConfigInput toConfigInput() {
            return config == null ? null : config.toInput();
        }
    }

    public record QuestionOptionRequest(
            @NotBlank String optionKey, @NotBlank String content, Boolean correct) {}

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
            List<@Valid ProgrammingJudgeCaseRequest> judgeCases,
            List<@Valid ProgrammingLanguageExecutionEnvironmentRequest> languageExecutionEnvironments,
            @Valid ProgrammingExecutionEnvironmentRequest executionEnvironment) {

        AssignmentQuestionConfigInput toInput() {
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
                                    .toList(),
                    languageExecutionEnvironments == null
                            ? List.of()
                            : languageExecutionEnvironments.stream()
                                    .map(ProgrammingLanguageExecutionEnvironmentRequest::toInput)
                                    .toList(),
                    executionEnvironment == null ? null : executionEnvironment.toInput());
        }
    }

    public record ProgrammingExecutionEnvironmentRequest(
            Long profileId,
            String profileCode,
            String profileName,
            String profileScope,
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
                    profileId,
                    profileCode,
                    profileName,
                    profileScope,
                    languageVersion,
                    workingDirectory,
                    initScript,
                    compileCommand,
                    runCommand,
                    environmentVariables,
                    cpuRateLimit,
                    supportFiles);
        }
    }

    public record ProgrammingLanguageExecutionEnvironmentRequest(
            @NotNull ProgrammingLanguage programmingLanguage,
            @NotNull @Valid ProgrammingExecutionEnvironmentRequest executionEnvironment) {

        ProgrammingLanguageExecutionEnvironmentInput toInput() {
            return new ProgrammingLanguageExecutionEnvironmentInput(
                    programmingLanguage, executionEnvironment.toInput());
        }
    }

    public record ProgrammingJudgeCaseRequest(
            @NotBlank String stdinText,
            @NotBlank String expectedStdout,
            @NotNull @Positive Integer score) {

        ProgrammingJudgeCaseInput toInput() {
            return new ProgrammingJudgeCaseInput(stdinText, expectedStdout, score);
        }
    }
}
