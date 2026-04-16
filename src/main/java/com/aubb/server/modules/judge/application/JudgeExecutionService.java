package com.aubb.server.modules.judge.application;

import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.common.programming.ProgrammingSourceSnapshot;
import com.aubb.server.common.storage.ObjectStorageException;
import com.aubb.server.common.storage.ObjectStorageService;
import com.aubb.server.common.storage.StoredObject;
import com.aubb.server.modules.assignment.application.paper.AssignmentPaperApplicationService;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionConfigInput;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionSnapshot;
import com.aubb.server.modules.assignment.application.paper.ProgrammingExecutionEnvironmentInput;
import com.aubb.server.modules.assignment.application.paper.ProgrammingJudgeCaseInput;
import com.aubb.server.modules.assignment.domain.question.ProgrammingJudgeMode;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.assignment.infrastructure.judge.AssignmentJudgeCaseEntity;
import com.aubb.server.modules.assignment.infrastructure.judge.AssignmentJudgeCaseMapper;
import com.aubb.server.modules.assignment.infrastructure.judge.AssignmentJudgeProfileEntity;
import com.aubb.server.modules.assignment.infrastructure.judge.AssignmentJudgeProfileMapper;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.judge.domain.JudgeJobStatus;
import com.aubb.server.modules.judge.domain.JudgeVerdict;
import com.aubb.server.modules.judge.domain.ProgrammingSampleRunInputMode;
import com.aubb.server.modules.judge.infrastructure.JudgeJobEntity;
import com.aubb.server.modules.judge.infrastructure.JudgeJobMapper;
import com.aubb.server.modules.judge.infrastructure.gojudge.GoJudgeClient;
import com.aubb.server.modules.judge.infrastructure.gojudge.GoJudgeClient.CollectorFileDescriptor;
import com.aubb.server.modules.judge.infrastructure.gojudge.GoJudgeClient.Command;
import com.aubb.server.modules.judge.infrastructure.gojudge.GoJudgeClient.CopyInFile;
import com.aubb.server.modules.judge.infrastructure.gojudge.GoJudgeClient.MemoryFileDescriptor;
import com.aubb.server.modules.judge.infrastructure.gojudge.GoJudgeClient.RunRequest;
import com.aubb.server.modules.judge.infrastructure.gojudge.GoJudgeClient.RunResult;
import com.aubb.server.modules.submission.domain.answer.SubmissionAnswerGradingStatus;
import com.aubb.server.modules.submission.infrastructure.SubmissionArtifactEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionArtifactMapper;
import com.aubb.server.modules.submission.infrastructure.SubmissionEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionMapper;
import com.aubb.server.modules.submission.infrastructure.answer.SubmissionAnswerEntity;
import com.aubb.server.modules.submission.infrastructure.answer.SubmissionAnswerMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class JudgeExecutionService {

    private static final String ENGINE_JOB_REF = "GO_JUDGE_SYNC_RUN";
    private static final int MAX_EXCERPT_LENGTH = 2_000;
    private static final int PROC_LIMIT = 32;
    private static final int DEFAULT_TIME_LIMIT_MS = 1_000;
    private static final int DEFAULT_MEMORY_LIMIT_MB = 128;
    private static final int DEFAULT_OUTPUT_LIMIT_KB = 64;
    private static final int DEFAULT_COMPILE_TIME_LIMIT_MS = 10_000;
    private static final int DEFAULT_COMPILE_MEMORY_LIMIT_MB = 512;
    private static final int DEFAULT_COMPILE_OUTPUT_LIMIT_KB = 256;
    private static final List<String> DEFAULT_PROGRAM_ENV =
            List.of("PATH=/usr/local/go/bin:/opt/java/openjdk/bin:/usr/bin:/bin", "HOME=/tmp");
    private static final String COMPILED_BUNDLE_FILE = "_aubb_compiled_bundle.b64";
    private static final long COMPILED_BUNDLE_MAX_BYTES = 16L * 1024L * 1024L;
    private static final String CUSTOM_JUDGE_SCRIPT_FILE = "_aubb_custom_judge.py";
    private static final String CUSTOM_JUDGE_CONTEXT_FILE = "_aubb_judge_context.json";
    private static final String CUSTOM_JUDGE_STDIN_FILE = "_aubb_stdin.txt";
    private static final String CUSTOM_JUDGE_EXPECTED_FILE = "_aubb_expected_stdout.txt";
    private static final String CUSTOM_JUDGE_ACTUAL_FILE = "_aubb_actual_stdout.txt";
    private static final String CUSTOM_JUDGE_STDERR_FILE = "_aubb_actual_stderr.txt";
    private static final Pattern JAVA_PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][\\w.]*)\\s*;");

    private final JudgeJobMapper judgeJobMapper;
    private final SubmissionMapper submissionMapper;
    private final SubmissionAnswerMapper submissionAnswerMapper;
    private final SubmissionArtifactMapper submissionArtifactMapper;
    private final AssignmentPaperApplicationService assignmentPaperApplicationService;
    private final AssignmentJudgeProfileMapper assignmentJudgeProfileMapper;
    private final AssignmentJudgeCaseMapper assignmentJudgeCaseMapper;
    private final GoJudgeClient goJudgeClient;
    private final com.aubb.server.config.GoJudgeConfiguration.GoJudgeProperties goJudgeProperties;
    private final AuditLogApplicationService auditLogApplicationService;
    private final ObjectProvider<ObjectStorageService> objectStorageServiceProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public JudgeExecutionService(
            PlatformTransactionManager transactionManager,
            JudgeJobMapper judgeJobMapper,
            SubmissionMapper submissionMapper,
            SubmissionAnswerMapper submissionAnswerMapper,
            SubmissionArtifactMapper submissionArtifactMapper,
            AssignmentPaperApplicationService assignmentPaperApplicationService,
            AssignmentJudgeProfileMapper assignmentJudgeProfileMapper,
            AssignmentJudgeCaseMapper assignmentJudgeCaseMapper,
            GoJudgeClient goJudgeClient,
            com.aubb.server.config.GoJudgeConfiguration.GoJudgeProperties goJudgeProperties,
            AuditLogApplicationService auditLogApplicationService,
            ObjectProvider<ObjectStorageService> objectStorageServiceProvider,
            ObjectMapper objectMapper) {
        this.judgeJobMapper = judgeJobMapper;
        this.submissionMapper = submissionMapper;
        this.submissionAnswerMapper = submissionAnswerMapper;
        this.submissionArtifactMapper = submissionArtifactMapper;
        this.assignmentPaperApplicationService = assignmentPaperApplicationService;
        this.assignmentJudgeProfileMapper = assignmentJudgeProfileMapper;
        this.assignmentJudgeCaseMapper = assignmentJudgeCaseMapper;
        this.goJudgeClient = goJudgeClient;
        this.goJudgeProperties = goJudgeProperties;
        this.auditLogApplicationService = auditLogApplicationService;
        this.objectStorageServiceProvider = objectStorageServiceProvider;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    void executeJudgeJob(Long judgeJobId) {
        JudgeExecutionContext context = transactionTemplate.execute(status -> startJob(judgeJobId));
        if (context == null) {
            log.debug(
                    "Skip judge job execution because job is missing or no longer pending, judgeJobId={}", judgeJobId);
            return;
        }

        JudgeFinalization outcome;
        try {
            outcome = runJudge(context);
        } catch (RestClientException exception) {
            outcome = JudgeFinalization.failed("go-judge 调用失败: " + safeMessage(exception));
        } catch (RuntimeException exception) {
            outcome = JudgeFinalization.failed("评测执行失败: " + safeMessage(exception));
        }

        JudgeFinalization finalOutcome = outcome;
        transactionTemplate.executeWithoutResult(status -> persistJobOutcome(judgeJobId, finalOutcome));
        try {
            transactionTemplate.executeWithoutResult(status -> finalizeJobSideEffects(judgeJobId, finalOutcome));
        } catch (RuntimeException exception) {
            log.error(
                    "Judge job side effects sync failed, judgeJobId={}, status={}, error={}",
                    judgeJobId,
                    finalOutcome.failed() ? JudgeJobStatus.FAILED : JudgeJobStatus.SUCCEEDED,
                    safeMessage(exception),
                    exception);
        }
    }

    private JudgeExecutionContext startJob(Long judgeJobId) {
        JudgeJobEntity job = judgeJobMapper.selectById(judgeJobId);
        if (job == null || !JudgeJobStatus.PENDING.name().equals(job.getStatus())) {
            return null;
        }

        OffsetDateTime startedAt = OffsetDateTime.now();
        if (job.getQueuedAt() != null && startedAt.isBefore(job.getQueuedAt())) {
            startedAt = job.getQueuedAt();
        }
        job.setStatus(JudgeJobStatus.RUNNING.name());
        job.setStartedAt(startedAt);
        job.setEngineJobRef(ENGINE_JOB_REF);
        judgeJobMapper.updateById(job);

        SubmissionEntity submission = submissionMapper.selectById(job.getSubmissionId());
        SubmissionAnswerEntity answer = job.getSubmissionAnswerId() == null
                ? null
                : submissionAnswerMapper.selectById(job.getSubmissionAnswerId());
        AssignmentQuestionSnapshot question = null;
        if (job.getAssignmentQuestionId() != null) {
            question = assignmentPaperApplicationService.loadQuestionSnapshots(job.getAssignmentId()).stream()
                    .filter(candidate -> Objects.equals(candidate.id(), job.getAssignmentQuestionId()))
                    .findFirst()
                    .orElse(null);
        }
        AssignmentJudgeProfileEntity legacyProfile = assignmentJudgeProfileMapper.selectById(job.getAssignmentId());
        List<AssignmentJudgeCaseEntity> legacyCases =
                assignmentJudgeCaseMapper.selectList(Wrappers.<AssignmentJudgeCaseEntity>lambdaQuery()
                        .eq(AssignmentJudgeCaseEntity::getAssignmentId, job.getAssignmentId())
                        .orderByAsc(AssignmentJudgeCaseEntity::getCaseOrder)
                        .orderByAsc(AssignmentJudgeCaseEntity::getId));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("submissionId", job.getSubmissionId());
        metadata.put("assignmentId", job.getAssignmentId());
        metadata.put("submissionAnswerId", job.getSubmissionAnswerId());
        metadata.put("assignmentQuestionId", job.getAssignmentQuestionId());
        auditLogApplicationService.record(
                job.getRequestedByUserId(),
                AuditAction.JUDGE_JOB_STARTED,
                "JUDGE_JOB",
                String.valueOf(job.getId()),
                AuditResult.SUCCESS,
                metadata);
        return new JudgeExecutionContext(job, submission, answer, question, legacyProfile, legacyCases);
    }

    private JudgeFinalization runJudge(JudgeExecutionContext context) {
        if (!goJudgeProperties.enabled()) {
            return JudgeFinalization.failed("go-judge 未启用");
        }
        if (context.submission() == null) {
            return JudgeFinalization.failed("提交不存在，无法执行自动评测");
        }
        if (context.answer() != null) {
            return runStructuredProgrammingJudge(context);
        }
        return runLegacyJudge(context);
    }

    public ProgrammingSampleRunOutcome runProgrammingSample(
            AssignmentQuestionSnapshot question,
            ProgrammingSourceSnapshot sourceSnapshot,
            List<Long> artifactIds,
            ProgrammingLanguage programmingLanguage,
            String stdinText,
            String expectedStdout,
            ProgrammingSampleRunInputMode inputMode,
            Long workspaceRevisionId) {
        if (!goJudgeProperties.enabled()) {
            return ProgrammingSampleRunOutcome.failed("go-judge 未启用", null, null);
        }
        if (question == null || question.config() == null) {
            return ProgrammingSampleRunOutcome.failed("结构化编程题样例试运行上下文不完整", null, null);
        }
        AssignmentQuestionConfigInput config = question.config();
        if (programmingLanguage == null) {
            return ProgrammingSampleRunOutcome.failed("编程题样例试运行缺少语言信息", null, null);
        }
        if (config.supportedLanguages() != null && !config.supportedLanguages().contains(programmingLanguage)) {
            return ProgrammingSampleRunOutcome.failed("提交语言不在题目支持范围内", null, null);
        }
        if (!StringUtils.hasText(stdinText)) {
            return ProgrammingSampleRunOutcome.failed("编程题试运行缺少标准输入", null, null);
        }
        try {
            SourceBundle sourceBundle = buildSourceBundle(sourceSnapshot, artifactIds, programmingLanguage, config);
            CaseOutcome caseOutcome = evaluateProgrammingCase(
                    sourceBundle, programmingLanguage, config, stdinText, expectedStdout, null, true);
            JudgeJobStoredReport detailReport = new JudgeJobStoredReport(
                    buildSampleRunExecutionMetadata(
                            question,
                            sourceBundle,
                            config,
                            artifactIds,
                            programmingLanguage,
                            inputMode,
                            workspaceRevisionId),
                    List.of(toCaseReport(1, 0, caseOutcome, true)),
                    clip(caseOutcome.stdoutText()),
                    clip(caseOutcome.stderrText()));
            if (caseOutcome.verdict() == JudgeVerdict.SYSTEM_ERROR) {
                return ProgrammingSampleRunOutcome.failed(
                        caseOutcome.errorMessage(), caseOutcome.stdoutText(), caseOutcome.stderrText(), detailReport);
            }
            return ProgrammingSampleRunOutcome.completed(
                    caseOutcome.verdict(),
                    caseOutcome.stdoutText(),
                    caseOutcome.stderrText(),
                    caseOutcome.timeMillis(),
                    caseOutcome.memoryBytes(),
                    sampleRunSummary(caseOutcome, expectedStdout),
                    detailReport);
        } catch (RuntimeException exception) {
            return ProgrammingSampleRunOutcome.failed("样例试运行执行失败：" + safeMessage(exception), null, null);
        }
    }

    private JudgeFinalization runLegacyJudge(JudgeExecutionContext context) {
        if (context.legacyProfile() == null || context.legacyCases().isEmpty()) {
            return JudgeFinalization.failed("任务未配置自动评测规则");
        }
        if (!StringUtils.hasText(context.submission().getContentText())) {
            return JudgeFinalization.failed("当前自动评测仅支持文本代码提交");
        }

        List<JudgeJobCaseResultView> caseResults = new ArrayList<>();
        List<JudgeJobCaseReportView> caseReports = new ArrayList<>();
        int passedCaseCount = 0;
        int totalCaseCount = context.legacyCases().size();
        int score = 0;
        int maxScore = context.legacyCases().stream()
                .map(AssignmentJudgeCaseEntity::getScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        long timeMillis = 0L;
        long memoryBytes = 0L;
        JudgeVerdict overallVerdict = JudgeVerdict.ACCEPTED;
        String stdoutExcerpt = null;
        String stderrExcerpt = null;

        for (AssignmentJudgeCaseEntity testCase : context.legacyCases()) {
            List<String> runCommand =
                    List.of("/usr/bin/python3", context.legacyProfile().getEntryFileName());
            RunResult result = executeCase(
                    runCommand,
                    buildProgramEnvironment(ProgrammingLanguage.PYTHON3, null),
                    null,
                    Map.of(
                            context.legacyProfile().getEntryFileName(),
                            new CopyInFile(context.submission().getContentText())),
                    List.of(),
                    testCase.getStdinText(),
                    context.legacyProfile().getTimeLimitMs(),
                    context.legacyProfile().getMemoryLimitMb(),
                    context.legacyProfile().getOutputLimitKb());
            CaseOutcome caseOutcome =
                    toCaseOutcome(result, testCase.getStdinText(), testCase.getExpectedStdout(), null, runCommand);
            JudgeJobCaseResultView caseResult = toCaseResult(testCase.getCaseOrder(), testCase.getScore(), caseOutcome);
            caseResults.add(caseResult);
            caseReports.add(toCaseReport(testCase.getCaseOrder(), testCase.getScore(), caseOutcome, true));
            timeMillis += caseOutcome.timeMillis();
            memoryBytes = Math.max(memoryBytes, caseOutcome.memoryBytes());
            if (stdoutExcerpt == null && caseOutcome.stdoutText() != null) {
                stdoutExcerpt = clip(caseOutcome.stdoutText());
            }
            if (stderrExcerpt == null && caseOutcome.stderrText() != null) {
                stderrExcerpt = clip(caseOutcome.stderrText());
            }
            if (caseOutcome.verdict() == JudgeVerdict.SYSTEM_ERROR) {
                return JudgeFinalization.failed(
                        caseOutcome.errorMessage(),
                        stdoutExcerpt,
                        stderrExcerpt,
                        caseResults,
                        new JudgeJobStoredReport(
                                buildLegacyExecutionMetadata(context),
                                caseReports,
                                clip(caseOutcome.stdoutText()),
                                clip(caseOutcome.stderrText())));
            }
            if (caseOutcome.verdict() == JudgeVerdict.ACCEPTED) {
                passedCaseCount += 1;
                score += testCase.getScore();
            } else if (overallVerdict == JudgeVerdict.ACCEPTED) {
                overallVerdict = caseOutcome.verdict();
            }
        }

        String summary =
                judgeJobSummary(overallVerdict, stderrExcerpt, passedCaseCount, totalCaseCount, score, maxScore, "用例");
        return JudgeFinalization.completed(
                overallVerdict,
                totalCaseCount,
                passedCaseCount,
                score,
                maxScore,
                stdoutExcerpt,
                stderrExcerpt,
                timeMillis,
                memoryBytes,
                summary,
                caseResults,
                new JudgeJobStoredReport(
                        buildLegacyExecutionMetadata(context), caseReports, stdoutExcerpt, stderrExcerpt));
    }

    private JudgeFinalization runStructuredProgrammingJudge(JudgeExecutionContext context) {
        if (context.answer() == null || context.question() == null) {
            return JudgeFinalization.failed("结构化编程题评测上下文不完整");
        }
        AssignmentQuestionConfigInput config = context.question().config();
        if (config == null || config.judgeCases() == null || config.judgeCases().isEmpty()) {
            return JudgeFinalization.failed("当前编程题未配置隐藏测试用例");
        }

        ProgrammingAnswerPayload payload =
                readProgrammingPayload(context.answer().getAnswerPayloadJson());
        if (payload.programmingLanguage() == null) {
            return JudgeFinalization.failed("编程题答案缺少语言信息");
        }
        if (config.supportedLanguages() != null
                && !config.supportedLanguages().contains(payload.programmingLanguage())) {
            return JudgeFinalization.failed("提交语言不在题目支持范围内");
        }

        SourceBundle sourceBundle = buildSourceBundle(
                ProgrammingSourceSnapshot.fromInput(
                        payload.programmingLanguage(),
                        context.answer().getAnswerText(),
                        payload.entryFilePath(),
                        payload.files()),
                payload.artifactIds(),
                payload.programmingLanguage(),
                config);
        List<JudgeJobCaseResultView> caseResults = new ArrayList<>();
        List<JudgeJobCaseReportView> caseReports = new ArrayList<>();
        int passedCaseCount = 0;
        int totalCaseCount = config.judgeCases().size();
        int score = 0;
        int maxScore = config.judgeCases().stream()
                .mapToInt(ProgrammingJudgeCaseInput::score)
                .sum();
        long timeMillis = 0L;
        long memoryBytes = 0L;
        JudgeVerdict overallVerdict = JudgeVerdict.ACCEPTED;
        String stdoutExcerpt = null;
        String stderrExcerpt = null;

        int caseOrder = 1;
        for (ProgrammingJudgeCaseInput judgeCase : config.judgeCases()) {
            CaseOutcome caseOutcome = evaluateProgrammingCase(
                    sourceBundle,
                    payload.programmingLanguage(),
                    config,
                    judgeCase.stdinText(),
                    judgeCase.expectedStdout(),
                    judgeCase.score(),
                    false);
            JudgeJobCaseResultView caseResult = toCaseResult(caseOrder, judgeCase.score(), caseOutcome);
            caseResults.add(caseResult);
            caseReports.add(toCaseReport(caseOrder, judgeCase.score(), caseOutcome, true));
            timeMillis += caseOutcome.timeMillis();
            memoryBytes = Math.max(memoryBytes, caseOutcome.memoryBytes());
            if (stdoutExcerpt == null && caseOutcome.stdoutText() != null) {
                stdoutExcerpt = clip(caseOutcome.stdoutText());
            }
            if (stderrExcerpt == null && caseOutcome.stderrText() != null) {
                stderrExcerpt = clip(caseOutcome.stderrText());
            }
            if (caseOutcome.verdict() == JudgeVerdict.SYSTEM_ERROR) {
                return JudgeFinalization.failed(
                        caseOutcome.errorMessage(),
                        stdoutExcerpt,
                        stderrExcerpt,
                        caseResults,
                        new JudgeJobStoredReport(
                                buildStructuredExecutionMetadata(context, payload, config, sourceBundle),
                                caseReports,
                                clip(caseOutcome.stdoutText()),
                                clip(caseOutcome.stderrText())));
            }
            score += awardedScore(judgeCase.score(), caseOutcome);
            if (caseOutcome.verdict() == JudgeVerdict.ACCEPTED) {
                passedCaseCount += 1;
            } else if (overallVerdict == JudgeVerdict.ACCEPTED) {
                overallVerdict = caseOutcome.verdict();
            }
            caseOrder++;
        }

        String summary =
                judgeJobSummary(overallVerdict, stderrExcerpt, passedCaseCount, totalCaseCount, score, maxScore, "测试点");
        return JudgeFinalization.completed(
                overallVerdict,
                totalCaseCount,
                passedCaseCount,
                score,
                maxScore,
                stdoutExcerpt,
                stderrExcerpt,
                timeMillis,
                memoryBytes,
                summary,
                caseResults,
                new JudgeJobStoredReport(
                        buildStructuredExecutionMetadata(context, payload, config, sourceBundle),
                        caseReports,
                        stdoutExcerpt,
                        stderrExcerpt));
    }

    private CaseOutcome evaluateProgrammingCase(
            SourceBundle sourceBundle,
            ProgrammingLanguage programmingLanguage,
            AssignmentQuestionConfigInput config,
            String stdinText,
            String expectedStdout,
            Integer maxScore,
            boolean sampleRun) {
        ProgramCommandPlan commandPlan = buildProgramCommand(programmingLanguage, sourceBundle, config);
        RunResult compileResult = null;
        if (!commandPlan.compileShellArgs().isEmpty()) {
            compileResult = executeCase(
                    commandPlan.compileShellArgs(),
                    commandPlan.environment(),
                    commandPlan.cpuRateLimit(),
                    sourceBundle.copyIn(),
                    commandPlan.compileBundlePath() == null ? List.of() : List.of(commandPlan.compileBundlePath()),
                    null,
                    resolveCompileTimeLimitMs(config),
                    resolveCompileMemoryLimitMb(config),
                    resolveCompileOutputLimitKb(config));
            CaseOutcome compileOutcome =
                    toCaseOutcome(compileResult, null, null, commandPlan.compileCommand(), commandPlan.runCommand());
            if (compileOutcome.verdict() != JudgeVerdict.ACCEPTED) {
                return compileOutcome;
            }
        }
        Map<String, CopyInFile> runCopyIn = copyInWithCompiledBundle(sourceBundle.copyIn(), commandPlan, compileResult);
        RunResult programResult = executeCase(
                commandPlan.runShellArgs(),
                commandPlan.environment(),
                commandPlan.cpuRateLimit(),
                runCopyIn,
                List.of(),
                stdinText,
                safeInt(config.timeLimitMs(), DEFAULT_TIME_LIMIT_MS),
                safeInt(config.memoryLimitMb(), DEFAULT_MEMORY_LIMIT_MB),
                safeInt(config.outputLimitKb(), DEFAULT_OUTPUT_LIMIT_KB));
        if (ProgrammingJudgeMode.CUSTOM_SCRIPT.equals(config.judgeMode())) {
            return runCustomJudgeCase(
                    programResult,
                    sourceBundle,
                    programmingLanguage,
                    config,
                    stdinText,
                    expectedStdout,
                    maxScore,
                    commandPlan,
                    compileResult,
                    sampleRun);
        }
        return combineCompileAndRunOutcome(
                compileResult,
                toCaseOutcome(
                        programResult,
                        stdinText,
                        expectedStdout,
                        commandPlan.compileCommand(),
                        commandPlan.runCommand()));
    }

    // checker 只负责给出裁决和分数，学生看到的 stdout / stderr 仍然来自自己的程序运行结果。
    private CaseOutcome runCustomJudgeCase(
            RunResult programResult,
            SourceBundle sourceBundle,
            ProgrammingLanguage programmingLanguage,
            AssignmentQuestionConfigInput config,
            String stdinText,
            String expectedStdout,
            Integer maxScore,
            ProgramCommandPlan commandPlan,
            RunResult compileResult,
            boolean sampleRun) {
        String stdout = normalizeLineEndings(
                programResult.files() == null ? null : programResult.files().get("stdout"));
        String stderr = normalizeLineEndings(
                programResult.files() == null ? null : programResult.files().get("stderr"));
        JudgeVerdict engineVerdict = mapEngineVerdict(programResult.status());
        long timeMillis = nanosToMillis(programResult.time());
        long memoryBytes = safeLong(programResult.memory());

        if (engineVerdict == JudgeVerdict.SYSTEM_ERROR) {
            return new CaseOutcome(
                    JudgeVerdict.SYSTEM_ERROR,
                    stdout,
                    stderr,
                    timeMillis,
                    memoryBytes,
                    null,
                    "go-judge 返回系统错误状态: " + programResult.status(),
                    programResult.status(),
                    programResult.exitStatus(),
                    commandPlan.compileCommand(),
                    commandPlan.runCommand(),
                    stdinText,
                    expectedStdout);
        }
        if (engineVerdict != JudgeVerdict.ACCEPTED) {
            return new CaseOutcome(
                    engineVerdict,
                    stdout,
                    stderr,
                    timeMillis,
                    memoryBytes,
                    0,
                    describeEngineFailure(engineVerdict, clip(stderr)),
                    programResult.status(),
                    programResult.exitStatus(),
                    commandPlan.compileCommand(),
                    commandPlan.runCommand(),
                    stdinText,
                    expectedStdout);
        }
        if (!StringUtils.hasText(config.customJudgeScript())) {
            return new CaseOutcome(
                    JudgeVerdict.SYSTEM_ERROR,
                    stdout,
                    stderr,
                    timeMillis,
                    memoryBytes,
                    null,
                    "自定义评测脚本未配置",
                    programResult.status(),
                    programResult.exitStatus(),
                    commandPlan.compileCommand(),
                    commandPlan.runCommand(),
                    stdinText,
                    expectedStdout);
        }

        RunResult judgeResult = executeCase(
                List.of("/usr/bin/python3", CUSTOM_JUDGE_SCRIPT_FILE),
                buildProgramEnvironment(ProgrammingLanguage.PYTHON3, null),
                null,
                buildCustomJudgeCopyIn(
                        sourceBundle,
                        programmingLanguage,
                        stdinText,
                        expectedStdout,
                        programResult,
                        config,
                        maxScore,
                        sampleRun),
                List.of(),
                null,
                safeInt(config.timeLimitMs(), DEFAULT_TIME_LIMIT_MS),
                safeInt(config.memoryLimitMb(), DEFAULT_MEMORY_LIMIT_MB),
                safeInt(config.outputLimitKb(), DEFAULT_OUTPUT_LIMIT_KB));

        JudgeVerdict judgeEngineVerdict = mapEngineVerdict(judgeResult.status());
        if (judgeEngineVerdict != JudgeVerdict.ACCEPTED) {
            return new CaseOutcome(
                    JudgeVerdict.SYSTEM_ERROR,
                    stdout,
                    stderr,
                    timeMillis,
                    memoryBytes,
                    null,
                    "自定义评测脚本执行失败: " + formatCustomJudgeFailure(judgeResult),
                    programResult.status(),
                    programResult.exitStatus(),
                    commandPlan.compileCommand(),
                    commandPlan.runCommand(),
                    stdinText,
                    expectedStdout);
        }

        String judgeStdout = normalizeLineEndings(
                judgeResult.files() == null ? null : judgeResult.files().get("stdout"));
        if (!StringUtils.hasText(judgeStdout)) {
            return new CaseOutcome(
                    JudgeVerdict.SYSTEM_ERROR,
                    stdout,
                    stderr,
                    timeMillis,
                    memoryBytes,
                    null,
                    "自定义评测脚本未返回裁决 JSON",
                    programResult.status(),
                    programResult.exitStatus(),
                    commandPlan.compileCommand(),
                    commandPlan.runCommand(),
                    stdinText,
                    expectedStdout);
        }

        CustomJudgeDecision decision = readCustomJudgeDecision(judgeStdout);
        JudgeVerdict verdict = readCustomJudgeVerdict(decision.verdict());
        Integer awardedScore = resolveCustomJudgeScore(decision.score(), maxScore, verdict);
        return combineCompileAndRunOutcome(
                compileResult,
                new CaseOutcome(
                        verdict,
                        stdout,
                        stderr,
                        timeMillis,
                        memoryBytes,
                        awardedScore,
                        clip(decision.message()),
                        programResult.status(),
                        programResult.exitStatus(),
                        commandPlan.compileCommand(),
                        commandPlan.runCommand(),
                        stdinText,
                        expectedStdout));
    }

    private Map<String, CopyInFile> buildCustomJudgeCopyIn(
            SourceBundle sourceBundle,
            ProgrammingLanguage programmingLanguage,
            String stdinText,
            String expectedStdout,
            RunResult programResult,
            AssignmentQuestionConfigInput config,
            Integer maxScore,
            boolean sampleRun) {
        Map<String, CopyInFile> copyIn = new LinkedHashMap<>(sourceBundle.copyIn());
        copyIn.put(CUSTOM_JUDGE_SCRIPT_FILE, new CopyInFile(config.customJudgeScript()));
        copyIn.put(CUSTOM_JUDGE_STDIN_FILE, new CopyInFile(safeContent(stdinText)));
        copyIn.put(CUSTOM_JUDGE_EXPECTED_FILE, new CopyInFile(safeContent(expectedStdout)));
        copyIn.put(
                CUSTOM_JUDGE_ACTUAL_FILE,
                new CopyInFile(safeContent(
                        programResult.files() == null
                                ? null
                                : programResult.files().get("stdout"))));
        copyIn.put(
                CUSTOM_JUDGE_STDERR_FILE,
                new CopyInFile(safeContent(
                        programResult.files() == null
                                ? null
                                : programResult.files().get("stderr"))));
        copyIn.put(
                CUSTOM_JUDGE_CONTEXT_FILE,
                new CopyInFile(writeCustomJudgeContext(
                        sourceBundle, programmingLanguage, stdinText, programResult, config, maxScore, sampleRun)));
        return copyIn;
    }

    private String writeCustomJudgeContext(
            SourceBundle sourceBundle,
            ProgrammingLanguage programmingLanguage,
            String stdinText,
            RunResult programResult,
            AssignmentQuestionConfigInput config,
            Integer maxScore,
            boolean sampleRun) {
        try {
            return objectMapper.writeValueAsString(new CustomJudgeContext(
                    sampleRun,
                    programmingLanguage,
                    sourceBundle.entryFileName(),
                    List.copyOf(sourceBundle.copyIn().keySet()),
                    safeContent(stdinText),
                    mapEngineVerdict(programResult.status()),
                    programResult.status(),
                    programResult.exitStatus(),
                    nanosToMillis(programResult.time()),
                    safeLong(programResult.memory()),
                    nanosToMillis(programResult.runTime()),
                    safeInt(config.timeLimitMs(), DEFAULT_TIME_LIMIT_MS),
                    safeInt(config.memoryLimitMb(), DEFAULT_MEMORY_LIMIT_MB),
                    safeInt(config.outputLimitKb(), DEFAULT_OUTPUT_LIMIT_KB),
                    maxScore));
        } catch (JacksonException exception) {
            throw new IllegalStateException("自定义评测上下文无法序列化", exception);
        }
    }

    private CustomJudgeDecision readCustomJudgeDecision(String judgeStdout) {
        try {
            return objectMapper.readValue(judgeStdout, CustomJudgeDecision.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("自定义评测脚本返回的 JSON 无法解析", exception);
        }
    }

    private JudgeVerdict readCustomJudgeVerdict(String verdict) {
        if (!StringUtils.hasText(verdict)) {
            throw new IllegalStateException("自定义评测脚本缺少 verdict");
        }
        try {
            return JudgeVerdict.valueOf(verdict.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("自定义评测脚本返回了未知 verdict: " + verdict, exception);
        }
    }

    private Integer resolveCustomJudgeScore(Integer score, Integer maxScore, JudgeVerdict verdict) {
        if (maxScore == null) {
            return null;
        }
        if (score == null) {
            return JudgeVerdict.ACCEPTED.equals(verdict) ? maxScore : 0;
        }
        if (score < 0 || score > maxScore) {
            throw new IllegalStateException("自定义评测脚本返回的分数超出当前测试点范围");
        }
        return score;
    }

    private int awardedScore(int maxScore, CaseOutcome caseOutcome) {
        if (caseOutcome.awardedScore() != null) {
            return caseOutcome.awardedScore();
        }
        return JudgeVerdict.ACCEPTED.equals(caseOutcome.verdict()) ? maxScore : 0;
    }

    private String formatCustomJudgeFailure(RunResult judgeResult) {
        String stderr = formatProgramOutput(
                judgeResult.files() == null ? null : judgeResult.files().get("stderr"), true);
        if (StringUtils.hasText(stderr)) {
            return stderr;
        }
        return judgeResult.status() == null ? "未知错误" : judgeResult.status();
    }

    private RunResult executeCase(
            List<String> args,
            List<String> env,
            Integer cpuRateLimit,
            Map<String, CopyInFile> copyIn,
            List<String> copyOut,
            String stdinText,
            int timeLimitMs,
            int memoryLimitMb,
            int outputLimitKb) {
        long cpuLimitNanos = timeLimitMs * 1_000_000L;
        long clockLimitNanos = cpuLimitNanos * 2;
        long memoryLimitBytes = memoryLimitMb * 1024L * 1024L;
        long outputLimitBytes = outputLimitKb * 1024L;
        RunRequest request = new RunRequest(List.of(new Command(
                args,
                env,
                List.of(
                        new MemoryFileDescriptor(safeContent(stdinText)),
                        new CollectorFileDescriptor("stdout", outputLimitBytes),
                        new CollectorFileDescriptor("stderr", outputLimitBytes)),
                cpuLimitNanos,
                clockLimitNanos,
                memoryLimitBytes,
                PROC_LIMIT,
                cpuRateLimit,
                copyIn,
                copyOut,
                copyOut == null || copyOut.isEmpty() ? null : COMPILED_BUNDLE_MAX_BYTES)));
        List<RunResult> results = goJudgeClient.run(request);
        if (results.size() != 1) {
            throw new IllegalStateException("go-judge 返回结果数量异常");
        }
        return results.getFirst();
    }

    private CaseOutcome toCaseOutcome(
            RunResult result,
            String stdinText,
            String expectedStdout,
            List<String> compileCommand,
            List<String> runCommand) {
        String stdout = normalizeLineEndings(
                result.files() == null ? null : result.files().get("stdout"));
        String stderr = normalizeLineEndings(
                result.files() == null ? null : result.files().get("stderr"));
        JudgeVerdict engineVerdict = mapEngineVerdict(result.status());
        if (engineVerdict == JudgeVerdict.SYSTEM_ERROR) {
            return new CaseOutcome(
                    JudgeVerdict.SYSTEM_ERROR,
                    stdout,
                    stderr,
                    nanosToMillis(result.time()),
                    safeLong(result.memory()),
                    null,
                    "go-judge 返回系统错误状态: " + result.status(),
                    result.status(),
                    result.exitStatus(),
                    compileCommand,
                    runCommand,
                    stdinText,
                    expectedStdout);
        }
        if (engineVerdict == JudgeVerdict.ACCEPTED
                && expectedStdout != null
                && !Objects.equals(stdout, normalizeLineEndings(expectedStdout))) {
            return new CaseOutcome(
                    JudgeVerdict.WRONG_ANSWER,
                    stdout,
                    stderr,
                    nanosToMillis(result.time()),
                    safeLong(result.memory()),
                    null,
                    null,
                    result.status(),
                    result.exitStatus(),
                    compileCommand,
                    runCommand,
                    stdinText,
                    expectedStdout);
        }
        return new CaseOutcome(
                engineVerdict,
                stdout,
                stderr,
                nanosToMillis(result.time()),
                safeLong(result.memory()),
                null,
                describeEngineFailure(engineVerdict, clip(stderr)),
                result.status(),
                result.exitStatus(),
                compileCommand,
                runCommand,
                stdinText,
                expectedStdout);
    }

    private JudgeJobCaseResultView toCaseResult(int caseOrder, int maxScore, CaseOutcome caseOutcome) {
        return new JudgeJobCaseResultView(
                caseOrder,
                caseOutcome.verdict(),
                awardedScore(maxScore, caseOutcome),
                maxScore,
                clip(caseOutcome.stdoutText()),
                clip(caseOutcome.stderrText()),
                caseOutcome.timeMillis(),
                caseOutcome.memoryBytes(),
                caseOutcome.errorMessage());
    }

    private JudgeJobCaseReportView toCaseReport(
            int caseOrder, int maxScore, CaseOutcome caseOutcome, boolean revealSensitiveFields) {
        return new JudgeJobCaseReportView(
                caseOrder,
                caseOutcome.verdict(),
                awardedScore(maxScore, caseOutcome),
                maxScore,
                revealSensitiveFields ? caseOutcome.stdinText() : null,
                revealSensitiveFields ? caseOutcome.expectedStdout() : null,
                caseOutcome.stdoutText(),
                caseOutcome.stderrText(),
                caseOutcome.timeMillis(),
                caseOutcome.memoryBytes(),
                caseOutcome.errorMessage(),
                caseOutcome.engineStatus(),
                caseOutcome.exitStatus(),
                caseOutcome.compileCommand(),
                caseOutcome.runCommand());
    }

    private Map<String, CopyInFile> copyInWithCompiledBundle(
            Map<String, CopyInFile> sourceCopyIn, ProgramCommandPlan commandPlan, RunResult compileResult) {
        Map<String, CopyInFile> resolved = new LinkedHashMap<>(sourceCopyIn);
        if (commandPlan.compileBundlePath() == null) {
            return resolved;
        }
        String compiledBundle = compileResult == null || compileResult.files() == null
                ? null
                : compileResult.files().get(commandPlan.compileBundlePath());
        if (!StringUtils.hasText(compiledBundle)) {
            throw new IllegalStateException("编译阶段未返回可复用产物");
        }
        resolved.put(commandPlan.compileBundlePath(), new CopyInFile(compiledBundle));
        return resolved;
    }

    private void persistJobOutcome(Long judgeJobId, JudgeFinalization outcome) {
        JudgeJobEntity job = judgeJobMapper.selectById(judgeJobId);
        if (job == null) {
            return;
        }
        OffsetDateTime finishedAt = OffsetDateTime.now();
        if (job.getStartedAt() != null && finishedAt.isBefore(job.getStartedAt())) {
            finishedAt = job.getStartedAt();
        }
        job.setFinishedAt(finishedAt);
        job.setResultSummary(outcome.resultSummary());
        job.setVerdict(outcome.verdict() == null ? null : outcome.verdict().name());
        job.setTotalCaseCount(outcome.totalCaseCount());
        job.setPassedCaseCount(outcome.passedCaseCount());
        job.setScore(outcome.score());
        job.setMaxScore(outcome.maxScore());
        job.setStdoutExcerpt(outcome.stdoutExcerpt());
        job.setStderrExcerpt(outcome.stderrExcerpt());
        job.setTimeMillis(outcome.timeMillis());
        job.setMemoryBytes(outcome.memoryBytes());
        job.setErrorMessage(outcome.errorMessage());
        job.setCaseResultsJson(writeCaseResults(outcome.caseResults()));
        job.setDetailReportJson(writeDetailReport(outcome.detailReport()));
        job.setStatus(outcome.failed() ? JudgeJobStatus.FAILED.name() : JudgeJobStatus.SUCCEEDED.name());
        judgeJobMapper.updateById(job);
        if (outcome.failed()) {
            log.warn(
                    "Judge job finished with failure, judgeJobId={}, submissionId={}, answerId={}, error={}",
                    judgeJobId,
                    job.getSubmissionId(),
                    job.getSubmissionAnswerId(),
                    outcome.errorMessage());
        }
    }

    private void finalizeJobSideEffects(Long judgeJobId, JudgeFinalization outcome) {
        JudgeJobEntity job = judgeJobMapper.selectById(judgeJobId);
        if (job == null) {
            return;
        }
        syncProgrammingAnswer(job, outcome);

        auditLogApplicationService.record(
                job.getRequestedByUserId(),
                outcome.failed() ? AuditAction.JUDGE_JOB_FAILED : AuditAction.JUDGE_JOB_COMPLETED,
                "JUDGE_JOB",
                String.valueOf(job.getId()),
                outcome.failed() ? AuditResult.FAILURE : AuditResult.SUCCESS,
                buildAuditMetadata(job, outcome));
    }

    private void syncProgrammingAnswer(JudgeJobEntity job, JudgeFinalization outcome) {
        if (job.getSubmissionAnswerId() == null) {
            return;
        }
        SubmissionAnswerEntity answer = submissionAnswerMapper.selectById(job.getSubmissionAnswerId());
        if (answer == null) {
            return;
        }
        if (outcome.failed()) {
            answer.setAutoScore(null);
            answer.setFinalScore(null);
            answer.setGradingStatus(SubmissionAnswerGradingStatus.PROGRAMMING_JUDGE_FAILED.name());
            answer.setFeedbackText("评测失败：" + outcome.errorMessage());
        } else {
            answer.setAutoScore(outcome.score());
            answer.setFinalScore(outcome.score());
            answer.setGradingStatus(SubmissionAnswerGradingStatus.PROGRAMMING_JUDGED.name());
            answer.setFeedbackText(outcome.resultSummary());
        }
        submissionAnswerMapper.updateById(answer);
    }

    private Map<String, Object> buildAuditMetadata(JudgeJobEntity job, JudgeFinalization outcome) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("submissionId", job.getSubmissionId());
        metadata.put("submissionAnswerId", job.getSubmissionAnswerId());
        metadata.put("assignmentId", job.getAssignmentId());
        metadata.put("assignmentQuestionId", job.getAssignmentQuestionId());
        metadata.put("status", outcome.failed() ? JudgeJobStatus.FAILED.name() : JudgeJobStatus.SUCCEEDED.name());
        metadata.put(
                "verdict", outcome.verdict() == null ? null : outcome.verdict().name());
        metadata.put("score", outcome.score());
        metadata.put("maxScore", outcome.maxScore());
        return metadata;
    }

    private ProgramCommandPlan buildProgramCommand(
            ProgrammingLanguage language, SourceBundle sourceBundle, AssignmentQuestionConfigInput config) {
        String entryFileName = sourceBundle.entryFileName();
        List<String> compileArgs = normalizeCommandArgs(config.compileArgs());
        List<String> runArgs = normalizeCommandArgs(config.runArgs());
        ProgrammingExecutionEnvironmentInput environment = resolveExecutionEnvironment(config, language);
        String workingDirectory = resolveWorkingDirectory(language, entryFileName, environment);
        String relativeEntryFileName = relativizeToWorkingDirectory(entryFileName, workingDirectory);
        List<String> javaSourceFiles = sourceBundle.sourceFiles().stream()
                .filter(path -> path.endsWith(".java"))
                .map(path -> relativizeToWorkingDirectory(path, workingDirectory))
                .sorted()
                .toList();
        List<String> cppSourceFiles = sourceBundle.sourceFiles().stream()
                .filter(path ->
                        path.endsWith(".cpp") || path.endsWith(".cc") || path.endsWith(".cxx") || path.endsWith(".c"))
                .map(path -> relativizeToWorkingDirectory(path, workingDirectory))
                .sorted()
                .toList();
        List<String> goSourceFiles = sourceBundle.sourceFiles().stream()
                .filter(path -> path.endsWith(".go"))
                .map(path -> relativizeToWorkingDirectory(path, workingDirectory))
                .sorted()
                .toList();
        String launchClassName = resolveJavaLaunchClassName(entryFileName, sourceBundle.copyIn());

        List<String> compileCommand = List.of();
        List<String> runCommand = List.of();
        switch (language) {
            case PYTHON3 -> {
                compileCommand = List.of();
                List<String> resolvedRunCommand = new ArrayList<>();
                resolvedRunCommand.add("/usr/bin/python3");
                resolvedRunCommand.addAll(compileArgs);
                resolvedRunCommand.add(relativeEntryFileName);
                resolvedRunCommand.addAll(runArgs);
                runCommand = List.copyOf(resolvedRunCommand);
            }
            case JAVA21, JAVA17 -> {
                List<String> resolvedCompileCommand =
                        new ArrayList<>(List.of("/opt/java/openjdk/bin/javac", "-encoding", "UTF-8", "-d", "."));
                resolvedCompileCommand.addAll(compileArgs);
                resolvedCompileCommand.addAll(javaSourceFiles);
                compileCommand = List.copyOf(resolvedCompileCommand);
                List<String> resolvedRunCommand =
                        new ArrayList<>(List.of("/opt/java/openjdk/bin/java", "-cp", ".", launchClassName));
                resolvedRunCommand.addAll(runArgs);
                runCommand = List.copyOf(resolvedRunCommand);
            }
            case CPP17 -> {
                List<String> resolvedCompileCommand =
                        new ArrayList<>(List.of("/usr/bin/g++", "-B/usr/bin", "-std=c++17", "-O2"));
                resolvedCompileCommand.addAll(cppSourceFiles);
                resolvedCompileCommand.addAll(compileArgs);
                resolvedCompileCommand.addAll(List.of("-o", "main"));
                compileCommand = List.copyOf(resolvedCompileCommand);
                List<String> resolvedRunCommand = new ArrayList<>(List.of("./main"));
                resolvedRunCommand.addAll(runArgs);
                runCommand = List.copyOf(resolvedRunCommand);
            }
            case GO122 -> {
                List<String> resolvedCompileCommand =
                        new ArrayList<>(List.of("/usr/local/go/bin/go", "build", "-o", "main"));
                resolvedCompileCommand.addAll(compileArgs);
                resolvedCompileCommand.add(".");
                compileCommand = List.copyOf(resolvedCompileCommand);
                List<String> resolvedRunCommand = new ArrayList<>(List.of("./main"));
                resolvedRunCommand.addAll(runArgs);
                runCommand = List.copyOf(resolvedRunCommand);
            }
        }

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("${ENTRY}", shellQuote(relativeEntryFileName));
        placeholders.put("${ENTRY_PATH}", shellQuote(entryFileName));
        placeholders.put("${ENTRY_DIR}", shellQuote(parentDirectory(relativeEntryFileName)));
        placeholders.put("${WORKING_DIRECTORY}", shellQuote(workingDirectory));
        placeholders.put("${JAVA_LAUNCH_CLASS}", shellQuote(launchClassName));
        placeholders.put("${JAVA_SOURCE_FILES}", shellJoin(javaSourceFiles));
        placeholders.put("${CPP_SOURCE_FILES}", shellJoin(cppSourceFiles));
        placeholders.put("${GO_SOURCE_FILES}", shellJoin(goSourceFiles));
        placeholders.put("${COMPILE_ARGS}", shellJoin(compileArgs));
        placeholders.put("${RUN_ARGS}", shellJoin(runArgs));

        List<String> compileCommandDisplay = StringUtils.hasText(environmentCommand(environment, true))
                ? List.of(environmentCommand(environment, true))
                : compileCommand;
        List<String> runCommandDisplay = StringUtils.hasText(environmentCommand(environment, false))
                ? List.of(environmentCommand(environment, false))
                : runCommand;
        String compileShell = StringUtils.hasText(environmentCommand(environment, true))
                ? renderShellTemplate(environmentCommand(environment, true), placeholders)
                : (compileCommand.isEmpty() ? null : shellJoin(compileCommand));
        String runShell = StringUtils.hasText(environmentCommand(environment, false))
                ? renderShellTemplate(environmentCommand(environment, false), placeholders)
                : shellJoin(runCommand);
        String compileBundlePath = compileCommand.isEmpty() ? null : resolveCompileBundlePath(workingDirectory);
        String compileArchiveCommand = compileBundlePath == null
                ? null
                : "tar --exclude='./%s' -cf - . | base64 -w0 > %s"
                        .formatted(COMPILED_BUNDLE_FILE, shellQuote(COMPILED_BUNDLE_FILE));
        String runRestoreCommand = compileBundlePath == null
                ? null
                : "base64 -d %s | tar -xf -".formatted(shellQuote(COMPILED_BUNDLE_FILE));
        return new ProgramCommandPlan(
                buildShellArgs(
                        workingDirectory,
                        environment == null ? null : environment.initScript(),
                        joinCommands(compileShell, compileArchiveCommand)),
                buildShellArgs(
                        workingDirectory,
                        environment == null ? null : environment.initScript(),
                        joinCommands(runRestoreCommand, runShell)),
                buildProgramEnvironment(language, environment),
                environment == null ? null : environment.cpuRateLimit(),
                compileBundlePath,
                compileCommandDisplay,
                runCommandDisplay);
    }

    private String resolveWorkingDirectory(
            ProgrammingLanguage language, String entryFileName, ProgrammingExecutionEnvironmentInput environment) {
        if (environment != null && StringUtils.hasText(environment.workingDirectory())) {
            return environment.workingDirectory().trim();
        }
        return switch (language) {
            case GO122 -> parentDirectory(entryFileName);
            default -> ".";
        };
    }

    private List<String> buildProgramEnvironment(
            ProgrammingLanguage language, ProgrammingExecutionEnvironmentInput environment) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (String envEntry : DEFAULT_PROGRAM_ENV) {
            int separator = envEntry.indexOf('=');
            resolved.put(envEntry.substring(0, separator), envEntry.substring(separator + 1));
        }
        if (ProgrammingLanguage.GO122.equals(language)) {
            resolved.put("GOCACHE", "/tmp/go-build");
            resolved.put("CGO_ENABLED", "0");
            resolved.put("GOFLAGS", "-p=1");
            resolved.put("GOMAXPROCS", "1");
        }
        if (environment != null && environment.environmentVariables() != null) {
            environment.environmentVariables().forEach((key, value) -> resolved.put(key.trim(), value));
        }
        return resolved.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
    }

    private String environmentCommand(ProgrammingExecutionEnvironmentInput environment, boolean compilePhase) {
        if (environment == null) {
            return null;
        }
        return compilePhase ? environment.compileCommand() : environment.runCommand();
    }

    private String renderShellTemplate(String template, Map<String, String> placeholders) {
        String rendered = template == null ? "" : template.trim();
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered.trim();
    }

    private List<String> buildShellArgs(String workingDirectory, String initScript, String command) {
        String shellProgram = buildShellProgram(workingDirectory, initScript, command);
        if (!StringUtils.hasText(shellProgram)) {
            return List.of();
        }
        return List.of("/bin/sh", "-lc", shellProgram);
    }

    private String buildShellProgram(String workingDirectory, String initScript, String command) {
        List<String> steps = new ArrayList<>();
        if (StringUtils.hasText(workingDirectory) && !".".equals(workingDirectory)) {
            steps.add("cd " + shellQuote(workingDirectory));
        }
        if (StringUtils.hasText(initScript)) {
            steps.add(initScript.trim());
        }
        if (StringUtils.hasText(command)) {
            steps.add(command.trim());
        }
        return steps.isEmpty() ? null : String.join(" && ", steps);
    }

    private String joinCommands(String... commands) {
        List<String> resolved = new ArrayList<>();
        for (String command : commands) {
            if (StringUtils.hasText(command)) {
                resolved.add(command.trim());
            }
        }
        return resolved.isEmpty() ? null : String.join(" && ", resolved);
    }

    private String resolveCompileBundlePath(String workingDirectory) {
        if (!StringUtils.hasText(workingDirectory) || ".".equals(workingDirectory)) {
            return COMPILED_BUNDLE_FILE;
        }
        return workingDirectory + "/" + COMPILED_BUNDLE_FILE;
    }

    private int resolveCompileTimeLimitMs(AssignmentQuestionConfigInput config) {
        return Math.max(DEFAULT_COMPILE_TIME_LIMIT_MS, safeInt(config.timeLimitMs(), DEFAULT_TIME_LIMIT_MS));
    }

    private int resolveCompileMemoryLimitMb(AssignmentQuestionConfigInput config) {
        return Math.max(DEFAULT_COMPILE_MEMORY_LIMIT_MB, safeInt(config.memoryLimitMb(), DEFAULT_MEMORY_LIMIT_MB));
    }

    private int resolveCompileOutputLimitKb(AssignmentQuestionConfigInput config) {
        return Math.max(DEFAULT_COMPILE_OUTPUT_LIMIT_KB, safeInt(config.outputLimitKb(), DEFAULT_OUTPUT_LIMIT_KB));
    }

    private CaseOutcome combineCompileAndRunOutcome(RunResult compileResult, CaseOutcome runOutcome) {
        if (compileResult == null || runOutcome == null) {
            return runOutcome;
        }
        return new CaseOutcome(
                runOutcome.verdict(),
                runOutcome.stdoutText(),
                runOutcome.stderrText(),
                nanosToMillis(compileResult.time()) + runOutcome.timeMillis(),
                Math.max(safeLong(compileResult.memory()), runOutcome.memoryBytes()),
                runOutcome.awardedScore(),
                runOutcome.errorMessage(),
                runOutcome.engineStatus(),
                runOutcome.exitStatus(),
                runOutcome.compileCommand(),
                runOutcome.runCommand(),
                runOutcome.stdinText(),
                runOutcome.expectedStdout());
    }

    private String relativizeToWorkingDirectory(String path, String workingDirectory) {
        if (!StringUtils.hasText(path) || !StringUtils.hasText(workingDirectory) || ".".equals(workingDirectory)) {
            return path;
        }
        String prefix = workingDirectory.endsWith("/") ? workingDirectory : workingDirectory + "/";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    private String parentDirectory(String path) {
        if (!StringUtils.hasText(path) || !path.contains("/")) {
            return ".";
        }
        return path.substring(0, path.lastIndexOf('/'));
    }

    private String resolveJavaLaunchClassName(String entryFileName, Map<String, CopyInFile> copyIn) {
        String simpleClassName = entryFileName.endsWith(".java")
                ? entryFileName.substring(entryFileName.lastIndexOf('/') + 1, entryFileName.length() - 5)
                : entryFileName.substring(entryFileName.lastIndexOf('/') + 1);
        CopyInFile entryFile = copyIn.get(entryFileName);
        if (entryFile == null || !StringUtils.hasText(entryFile.content())) {
            return simpleClassName;
        }
        java.util.regex.Matcher matcher = JAVA_PACKAGE_PATTERN.matcher(entryFile.content());
        if (!matcher.find()) {
            return simpleClassName;
        }
        return matcher.group(1) + "." + simpleClassName;
    }

    private SourceBundle buildSourceBundle(
            ProgrammingSourceSnapshot sourceSnapshot,
            List<Long> artifactIds,
            ProgrammingLanguage programmingLanguage,
            AssignmentQuestionConfigInput config) {
        ProgrammingSourceSnapshot normalizedSnapshot = sourceSnapshot == null
                ? ProgrammingSourceSnapshot.fromInput(programmingLanguage, null, null, List.of())
                : sourceSnapshot;
        String entryFileName = normalizedSnapshot.entryFilePath();
        Map<String, CopyInFile> copyIn = readArtifactSourceFiles(artifactIds);
        for (ProgrammingSourceFile file : normalizedSnapshot.files()) {
            copyIn.put(file.path(), new CopyInFile(file.content()));
        }
        List<String> supportFiles = new ArrayList<>();
        ProgrammingExecutionEnvironmentInput environment =
                config == null ? null : resolveExecutionEnvironment(config, programmingLanguage);
        if (environment != null && environment.supportFiles() != null) {
            for (ProgrammingSourceFile supportFile : environment.supportFiles()) {
                if (copyIn.containsKey(supportFile.path())) {
                    throw new IllegalStateException("评测环境支持文件与源码路径冲突");
                }
                copyIn.put(supportFile.path(), new CopyInFile(supportFile.content()));
                supportFiles.add(supportFile.path());
            }
        }
        if (!copyIn.containsKey(entryFileName)) {
            throw new IllegalStateException("当前自动评测需要代码文本或包含入口文件的附件");
        }
        List<String> sourceFiles = copyIn.keySet().stream()
                .filter(path -> !supportFiles.contains(path))
                .sorted()
                .toList();
        return new SourceBundle(entryFileName, copyIn, sourceFiles, List.copyOf(supportFiles));
    }

    private Map<String, CopyInFile> readArtifactSourceFiles(List<Long> artifactIds) {
        if (artifactIds == null || artifactIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        ObjectStorageService storageService = objectStorageServiceProvider.getIfAvailable();
        if (storageService == null) {
            throw new IllegalStateException("对象存储未启用，无法读取代码附件");
        }
        List<SubmissionArtifactEntity> artifacts =
                submissionArtifactMapper.selectList(Wrappers.<SubmissionArtifactEntity>lambdaQuery()
                        .in(SubmissionArtifactEntity::getId, artifactIds)
                        .orderByAsc(SubmissionArtifactEntity::getId));
        Map<String, CopyInFile> copyIn = new LinkedHashMap<>();
        for (SubmissionArtifactEntity artifact : artifacts) {
            try {
                StoredObject storedObject = storageService.getObject(artifact.getObjectKey());
                copyIn.put(
                        artifact.getOriginalFilename(),
                        new CopyInFile(new String(storedObject.content(), StandardCharsets.UTF_8)));
            } catch (ObjectStorageException exception) {
                throw new IllegalStateException("无法读取代码附件: " + artifact.getOriginalFilename(), exception);
            }
        }
        return copyIn;
    }

    private ProgrammingAnswerPayload readProgrammingPayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return new ProgrammingAnswerPayload(List.of(), null, null, List.of());
        }
        try {
            ProgrammingAnswerPayload payload = objectMapper.readValue(payloadJson, ProgrammingAnswerPayload.class);
            return new ProgrammingAnswerPayload(
                    payload.artifactIds() == null ? List.of() : payload.artifactIds(),
                    payload.programmingLanguage(),
                    payload.entryFilePath(),
                    payload.files() == null ? List.of() : payload.files());
        } catch (JacksonException exception) {
            throw new IllegalStateException("编程题答案载荷无法读取", exception);
        }
    }

    private String writeCaseResults(List<JudgeJobCaseResultView> caseResults) {
        if (caseResults == null || caseResults.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(caseResults);
        } catch (JacksonException exception) {
            throw new IllegalStateException("评测结果详情无法序列化", exception);
        }
    }

    private String writeDetailReport(JudgeJobStoredReport detailReport) {
        if (detailReport == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detailReport);
        } catch (JacksonException exception) {
            throw new IllegalStateException("评测详细报告无法序列化", exception);
        }
    }

    private Map<String, Object> buildLegacyExecutionMetadata(JudgeExecutionContext context) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mode", "LEGACY_ASSIGNMENT");
        metadata.put("programmingLanguage", ProgrammingLanguage.PYTHON3.name());
        metadata.put("entryFilePath", context.legacyProfile().getEntryFileName());
        metadata.put("timeLimitMs", context.legacyProfile().getTimeLimitMs());
        metadata.put("memoryLimitMb", context.legacyProfile().getMemoryLimitMb());
        metadata.put("outputLimitKb", context.legacyProfile().getOutputLimitKb());
        metadata.put("submissionId", context.job().getSubmissionId());
        metadata.put("assignmentId", context.job().getAssignmentId());
        metadata.put("assignmentQuestionId", context.job().getAssignmentQuestionId());
        metadata.put("submissionAnswerId", context.job().getSubmissionAnswerId());
        metadata.put("judgeCaseCount", context.legacyCases().size());
        metadata.put("queueMode", "EVENT_OR_QUEUE");
        return metadata;
    }

    private Map<String, Object> buildStructuredExecutionMetadata(
            JudgeExecutionContext context,
            ProgrammingAnswerPayload payload,
            AssignmentQuestionConfigInput config,
            SourceBundle sourceBundle) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mode", "STRUCTURED_PROGRAMMING");
        metadata.put(
                "programmingLanguage",
                payload.programmingLanguage() == null
                        ? null
                        : payload.programmingLanguage().name());
        metadata.put("entryFilePath", sourceBundle.entryFileName());
        metadata.put("sourceFiles", sourceBundle.sourceFiles());
        metadata.put("supportFiles", sourceBundle.supportFiles());
        metadata.put("artifactIds", payload.artifactIds());
        metadata.put("compileArgs", normalizeCommandArgs(config.compileArgs()));
        metadata.put("runArgs", normalizeCommandArgs(config.runArgs()));
        metadata.put("executionEnvironment", buildExecutionEnvironmentMetadata(config, payload.programmingLanguage()));
        metadata.put(
                "judgeMode",
                config.judgeMode() == null ? null : config.judgeMode().name());
        metadata.put("timeLimitMs", safeInt(config.timeLimitMs(), DEFAULT_TIME_LIMIT_MS));
        metadata.put("memoryLimitMb", safeInt(config.memoryLimitMb(), DEFAULT_MEMORY_LIMIT_MB));
        metadata.put("outputLimitKb", safeInt(config.outputLimitKb(), DEFAULT_OUTPUT_LIMIT_KB));
        metadata.put("submissionId", context.job().getSubmissionId());
        metadata.put("assignmentId", context.job().getAssignmentId());
        metadata.put("assignmentQuestionId", context.job().getAssignmentQuestionId());
        metadata.put("submissionAnswerId", context.job().getSubmissionAnswerId());
        metadata.put(
                "judgeCaseCount",
                config.judgeCases() == null ? 0 : config.judgeCases().size());
        metadata.put("queueMode", "EVENT_OR_QUEUE");
        return metadata;
    }

    private Map<String, Object> buildSampleRunExecutionMetadata(
            AssignmentQuestionSnapshot question,
            SourceBundle sourceBundle,
            AssignmentQuestionConfigInput config,
            List<Long> artifactIds,
            ProgrammingLanguage programmingLanguage,
            ProgrammingSampleRunInputMode inputMode,
            Long workspaceRevisionId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mode", "PROGRAMMING_SAMPLE_RUN");
        metadata.put("assignmentQuestionId", question.id());
        metadata.put("programmingLanguage", programmingLanguage == null ? null : programmingLanguage.name());
        metadata.put("entryFilePath", sourceBundle.entryFileName());
        metadata.put("sourceFiles", sourceBundle.sourceFiles());
        metadata.put("supportFiles", sourceBundle.supportFiles());
        metadata.put("artifactIds", artifactIds);
        metadata.put("compileArgs", normalizeCommandArgs(config.compileArgs()));
        metadata.put("runArgs", normalizeCommandArgs(config.runArgs()));
        metadata.put("executionEnvironment", buildExecutionEnvironmentMetadata(config, programmingLanguage));
        metadata.put("inputMode", inputMode == null ? null : inputMode.name());
        metadata.put("workspaceRevisionId", workspaceRevisionId);
        metadata.put(
                "judgeMode",
                config.judgeMode() == null ? null : config.judgeMode().name());
        metadata.put("timeLimitMs", safeInt(config.timeLimitMs(), DEFAULT_TIME_LIMIT_MS));
        metadata.put("memoryLimitMb", safeInt(config.memoryLimitMb(), DEFAULT_MEMORY_LIMIT_MB));
        metadata.put("outputLimitKb", safeInt(config.outputLimitKb(), DEFAULT_OUTPUT_LIMIT_KB));
        return metadata;
    }

    private Map<String, Object> buildExecutionEnvironmentMetadata(
            AssignmentQuestionConfigInput config, ProgrammingLanguage programmingLanguage) {
        ProgrammingExecutionEnvironmentInput environment =
                config == null ? null : resolveExecutionEnvironment(config, programmingLanguage);
        if (environment == null) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("profileId", environment.profileId());
        metadata.put("profileCode", blankToNull(environment.profileCode()));
        metadata.put("profileName", blankToNull(environment.profileName()));
        metadata.put("profileScope", blankToNull(environment.profileScope()));
        metadata.put(
                "languageVersion",
                StringUtils.hasText(environment.languageVersion())
                        ? environment.languageVersion().trim()
                        : defaultLanguageVersion(programmingLanguage));
        metadata.put("workingDirectory", blankToNull(environment.workingDirectory()));
        metadata.put("cpuRateLimit", environment.cpuRateLimit());
        metadata.put("compileCommand", blankToNull(environment.compileCommand()));
        metadata.put("runCommand", blankToNull(environment.runCommand()));
        metadata.put(
                "environmentVariables",
                environment.environmentVariables() == null ? Map.of() : Map.copyOf(environment.environmentVariables()));
        metadata.put(
                "supportFiles",
                environment.supportFiles() == null
                        ? List.of()
                        : environment.supportFiles().stream()
                                .map(ProgrammingSourceFile::path)
                                .toList());
        return metadata;
    }

    private ProgrammingExecutionEnvironmentInput resolveExecutionEnvironment(
            AssignmentQuestionConfigInput config, ProgrammingLanguage programmingLanguage) {
        if (config == null) {
            return null;
        }
        if (config.languageExecutionEnvironments() != null && programmingLanguage != null) {
            for (var languageEnvironment : config.languageExecutionEnvironments()) {
                if (languageEnvironment != null
                        && Objects.equals(languageEnvironment.programmingLanguage(), programmingLanguage)) {
                    return languageEnvironment.executionEnvironment();
                }
            }
        }
        return config.executionEnvironment();
    }

    private String defaultLanguageVersion(ProgrammingLanguage programmingLanguage) {
        if (programmingLanguage == null) {
            return null;
        }
        return switch (programmingLanguage) {
            case PYTHON3 -> "python3";
            case JAVA21 -> "java21";
            case JAVA17 -> "java17";
            case CPP17 -> "c++17";
            case GO122 -> "go1.22";
        };
    }

    private int safeInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private List<String> normalizeCommandArgs(List<String> args) {
        if (args == null || args.isEmpty()) {
            return List.of();
        }
        return args.stream().filter(StringUtils::hasText).map(String::trim).toList();
    }

    private String shellJoin(List<String> args) {
        return args.stream()
                .map(this::shellQuote)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private JudgeVerdict mapEngineVerdict(String status) {
        if (status == null) {
            return JudgeVerdict.SYSTEM_ERROR;
        }
        return switch (status) {
            case "Accepted" -> JudgeVerdict.ACCEPTED;
            case "Time Limit Exceeded" -> JudgeVerdict.TIME_LIMIT_EXCEEDED;
            case "Memory Limit Exceeded" -> JudgeVerdict.MEMORY_LIMIT_EXCEEDED;
            case "Output Limit Exceeded" -> JudgeVerdict.OUTPUT_LIMIT_EXCEEDED;
            case "Nonzero Exit Status", "Non Zero Exit Status", "Signalled", "Dangerous Syscall" ->
                JudgeVerdict.RUNTIME_ERROR;
            case "Internal Error", "File Error" -> JudgeVerdict.SYSTEM_ERROR;
            default -> JudgeVerdict.SYSTEM_ERROR;
        };
    }

    private String normalizeLineEndings(String value) {
        return value == null ? null : value.replace("\r\n", "\n");
    }

    private String safeContent(String value) {
        return value == null ? "" : normalizeLineEndings(value);
    }

    private String clip(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= MAX_EXCERPT_LENGTH ? value : value.substring(0, MAX_EXCERPT_LENGTH);
    }

    private String formatProgramOutput(String value, boolean clipOutput) {
        String normalized = normalizeLineEndings(value);
        return clipOutput ? clip(normalized) : normalized;
    }

    private String judgeJobSummary(
            JudgeVerdict verdict,
            String stderrExcerpt,
            int passedCaseCount,
            int totalCaseCount,
            int score,
            int maxScore,
            String caseLabel) {
        String prefix = verdictSummaryPrefix(verdict, stderrExcerpt);
        return "%s，%s/%s 个%s通过，得分 %s/%s".formatted(prefix, passedCaseCount, totalCaseCount, caseLabel, score, maxScore);
    }

    private String verdictSummaryPrefix(JudgeVerdict verdict, String stderrExcerpt) {
        if (verdict == null) {
            return "UNKNOWN";
        }
        String detail = describeEngineFailure(verdict, stderrExcerpt);
        return StringUtils.hasText(detail) ? verdict.name() + "，" + detail : verdict.name();
    }

    private String describeEngineFailure(JudgeVerdict verdict, String stderrExcerpt) {
        if (verdict == null || JudgeVerdict.ACCEPTED.equals(verdict)) {
            return null;
        }
        return switch (verdict) {
            case WRONG_ANSWER -> "输出与预期不一致";
            case TIME_LIMIT_EXCEEDED -> "超出时间限制";
            case MEMORY_LIMIT_EXCEEDED -> "超出内存限制";
            case OUTPUT_LIMIT_EXCEEDED -> "超出输出限制";
            case RUNTIME_ERROR -> isCompilationFailure(stderrExcerpt) ? "编译失败" : "程序运行失败";
            case SYSTEM_ERROR -> "评测执行失败";
            case ACCEPTED -> null;
        };
    }

    private boolean isCompilationFailure(String stderrExcerpt) {
        if (!StringUtils.hasText(stderrExcerpt)) {
            return false;
        }
        String normalized = stderrExcerpt.toLowerCase(Locale.ROOT);
        return normalized.contains("compilation failed")
                || normalized.contains("compile error")
                || normalized.contains("syntaxerror")
                || normalized.contains("javac")
                || normalized.contains("g++")
                || (normalized.contains(" error:")
                        && (normalized.contains(".java:")
                                || normalized.contains(".cpp:")
                                || normalized.contains(".cc:")
                                || normalized.contains(".cxx:")
                                || normalized.contains(".h:")
                                || normalized.contains(".hpp:")));
    }

    private long nanosToMillis(Long nanos) {
        return nanos == null ? 0L : Math.max(0L, nanos / 1_000_000L);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private String sampleRunSummary(CaseOutcome caseOutcome, String expectedStdout) {
        if (StringUtils.hasText(caseOutcome.errorMessage())) {
            return caseOutcome.verdict().name() + "，" + caseOutcome.errorMessage();
        }
        if (JudgeVerdict.ACCEPTED.equals(caseOutcome.verdict()) && StringUtils.hasText(expectedStdout)) {
            return "ACCEPTED，样例输出与预期一致";
        }
        if (JudgeVerdict.ACCEPTED.equals(caseOutcome.verdict())) {
            return "ACCEPTED，程序运行成功";
        }
        if (JudgeVerdict.WRONG_ANSWER.equals(caseOutcome.verdict())) {
            return "WRONG_ANSWER，样例输出与预期不一致";
        }
        return "样例试运行结果：" + caseOutcome.verdict().name();
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    public record ProgrammingSampleRunOutcome(
            boolean failed,
            JudgeVerdict verdict,
            String stdoutText,
            String stderrText,
            Long timeMillis,
            Long memoryBytes,
            String resultSummary,
            String errorMessage,
            JudgeJobStoredReport detailReport) {

        public static ProgrammingSampleRunOutcome completed(
                JudgeVerdict verdict,
                String stdoutText,
                String stderrText,
                Long timeMillis,
                Long memoryBytes,
                String resultSummary,
                JudgeJobStoredReport detailReport) {
            return new ProgrammingSampleRunOutcome(
                    false, verdict, stdoutText, stderrText, timeMillis, memoryBytes, resultSummary, null, detailReport);
        }

        public static ProgrammingSampleRunOutcome failed(
                String errorMessage, String stdoutText, String stderrText, JudgeJobStoredReport detailReport) {
            return new ProgrammingSampleRunOutcome(
                    true,
                    null,
                    stdoutText,
                    stderrText,
                    0L,
                    0L,
                    errorMessage == null ? "样例试运行失败" : errorMessage,
                    errorMessage,
                    detailReport);
        }

        public static ProgrammingSampleRunOutcome failed(String errorMessage, String stdoutText, String stderrText) {
            return failed(errorMessage, stdoutText, stderrText, null);
        }
    }

    private record JudgeExecutionContext(
            JudgeJobEntity job,
            SubmissionEntity submission,
            SubmissionAnswerEntity answer,
            AssignmentQuestionSnapshot question,
            AssignmentJudgeProfileEntity legacyProfile,
            List<AssignmentJudgeCaseEntity> legacyCases) {}

    private record CaseOutcome(
            JudgeVerdict verdict,
            String stdoutText,
            String stderrText,
            long timeMillis,
            long memoryBytes,
            Integer awardedScore,
            String errorMessage,
            String engineStatus,
            Integer exitStatus,
            List<String> compileCommand,
            List<String> runCommand,
            String stdinText,
            String expectedStdout) {}

    private record JudgeFinalization(
            boolean failed,
            JudgeVerdict verdict,
            Integer totalCaseCount,
            Integer passedCaseCount,
            Integer score,
            Integer maxScore,
            String stdoutExcerpt,
            String stderrExcerpt,
            Long timeMillis,
            Long memoryBytes,
            String resultSummary,
            String errorMessage,
            List<JudgeJobCaseResultView> caseResults,
            JudgeJobStoredReport detailReport) {

        static JudgeFinalization completed(
                JudgeVerdict verdict,
                Integer totalCaseCount,
                Integer passedCaseCount,
                Integer score,
                Integer maxScore,
                String stdoutExcerpt,
                String stderrExcerpt,
                Long timeMillis,
                Long memoryBytes,
                String resultSummary,
                List<JudgeJobCaseResultView> caseResults,
                JudgeJobStoredReport detailReport) {
            return new JudgeFinalization(
                    false,
                    verdict,
                    totalCaseCount,
                    passedCaseCount,
                    score,
                    maxScore,
                    stdoutExcerpt,
                    stderrExcerpt,
                    timeMillis,
                    memoryBytes,
                    resultSummary,
                    null,
                    caseResults,
                    detailReport);
        }

        static JudgeFinalization failed(String errorMessage) {
            return failed(errorMessage, null, null, List.of(), null);
        }

        static JudgeFinalization failed(
                String errorMessage,
                String stdoutExcerpt,
                String stderrExcerpt,
                List<JudgeJobCaseResultView> caseResults,
                JudgeJobStoredReport detailReport) {
            return new JudgeFinalization(
                    true,
                    JudgeVerdict.SYSTEM_ERROR,
                    null,
                    null,
                    null,
                    null,
                    stdoutExcerpt,
                    stderrExcerpt,
                    null,
                    null,
                    "SYSTEM_ERROR，评测执行失败",
                    errorMessage,
                    caseResults == null ? List.of() : caseResults,
                    detailReport);
        }
    }

    private record ProgrammingAnswerPayload(
            List<Long> artifactIds,
            ProgrammingLanguage programmingLanguage,
            String entryFilePath,
            List<ProgrammingSourceFile> files) {}

    private record SourceBundle(
            String entryFileName,
            Map<String, CopyInFile> copyIn,
            List<String> sourceFiles,
            List<String> supportFiles) {}

    private record ProgramCommandPlan(
            List<String> compileShellArgs,
            List<String> runShellArgs,
            List<String> environment,
            Integer cpuRateLimit,
            String compileBundlePath,
            List<String> compileCommand,
            List<String> runCommand) {}

    private record CustomJudgeDecision(String verdict, Integer score, String message) {}

    private record CustomJudgeContext(
            boolean sampleRun,
            ProgrammingLanguage programmingLanguage,
            String entryFileName,
            List<String> sourceFiles,
            String stdinText,
            JudgeVerdict programVerdict,
            String programStatus,
            Integer exitStatus,
            Long timeMillis,
            Long memoryBytes,
            Long runTimeMillis,
            Integer timeLimitMs,
            Integer memoryLimitMb,
            Integer outputLimitKb,
            Integer maxScore) {}
}
