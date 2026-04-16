package com.aubb.server.modules.judge.application;

import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.common.programming.ProgrammingSourceSnapshot;
import com.aubb.server.common.storage.ObjectStorageException;
import com.aubb.server.common.storage.ObjectStorageService;
import com.aubb.server.common.storage.StoredObject;
import com.aubb.server.modules.assignment.application.paper.AssignmentPaperApplicationService;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionConfigInput;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionSnapshot;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class JudgeExecutionService {

    private static final String ENGINE_JOB_REF = "GO_JUDGE_SYNC_RUN";
    private static final int MAX_EXCERPT_LENGTH = 2_000;
    private static final int PROC_LIMIT = 32;
    private static final int DEFAULT_TIME_LIMIT_MS = 1_000;
    private static final int DEFAULT_MEMORY_LIMIT_MB = 128;
    private static final int DEFAULT_OUTPUT_LIMIT_KB = 64;
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

    @Async("judgeExecutionTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleJudgeExecutionRequested(JudgeExecutionRequestedEvent event) {
        executeJudgeJob(event.judgeJobId());
    }

    void executeJudgeJob(Long judgeJobId) {
        JudgeExecutionContext context = transactionTemplate.execute(status -> startJob(judgeJobId));
        if (context == null) {
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
        transactionTemplate.executeWithoutResult(status -> finishJob(judgeJobId, finalOutcome));
    }

    private JudgeExecutionContext startJob(Long judgeJobId) {
        JudgeJobEntity job = judgeJobMapper.selectById(judgeJobId);
        if (job == null || !JudgeJobStatus.PENDING.name().equals(job.getStatus())) {
            return null;
        }

        job.setStatus(JudgeJobStatus.RUNNING.name());
        job.setStartedAt(OffsetDateTime.now());
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
            ProgrammingLanguage programmingLanguage) {
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
        if (!StringUtils.hasText(config.sampleStdinText())) {
            return ProgrammingSampleRunOutcome.failed("当前编程题未配置样例输入", null, null);
        }
        try {
            SourceBundle sourceBundle = buildSourceBundle(sourceSnapshot, artifactIds, programmingLanguage);
            CaseOutcome caseOutcome = evaluateProgrammingCase(
                    sourceBundle,
                    programmingLanguage,
                    config,
                    config.sampleStdinText(),
                    config.sampleExpectedStdout(),
                    null,
                    false,
                    true);
            if (caseOutcome.verdict() == JudgeVerdict.SYSTEM_ERROR) {
                return ProgrammingSampleRunOutcome.failed(
                        caseOutcome.errorMessage(), caseOutcome.stdoutExcerpt(), caseOutcome.stderrExcerpt());
            }
            return ProgrammingSampleRunOutcome.completed(
                    caseOutcome.verdict(),
                    caseOutcome.stdoutExcerpt(),
                    caseOutcome.stderrExcerpt(),
                    caseOutcome.timeMillis(),
                    caseOutcome.memoryBytes(),
                    sampleRunSummary(caseOutcome, config.sampleExpectedStdout()));
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
            RunResult result = executeCase(
                    List.of("/usr/bin/python3", context.legacyProfile().getEntryFileName()),
                    Map.of(
                            context.legacyProfile().getEntryFileName(),
                            new CopyInFile(context.submission().getContentText())),
                    testCase.getStdinText(),
                    context.legacyProfile().getTimeLimitMs(),
                    context.legacyProfile().getMemoryLimitMb(),
                    context.legacyProfile().getOutputLimitKb());
            CaseOutcome caseOutcome = toCaseOutcome(result, testCase.getExpectedStdout());
            JudgeJobCaseResultView caseResult = toCaseResult(testCase.getCaseOrder(), testCase.getScore(), caseOutcome);
            caseResults.add(caseResult);
            timeMillis += caseOutcome.timeMillis();
            memoryBytes = Math.max(memoryBytes, caseOutcome.memoryBytes());
            if (stdoutExcerpt == null && caseOutcome.stdoutExcerpt() != null) {
                stdoutExcerpt = caseOutcome.stdoutExcerpt();
            }
            if (stderrExcerpt == null && caseOutcome.stderrExcerpt() != null) {
                stderrExcerpt = caseOutcome.stderrExcerpt();
            }
            if (caseOutcome.verdict() == JudgeVerdict.SYSTEM_ERROR) {
                return JudgeFinalization.failed(caseOutcome.errorMessage(), stdoutExcerpt, stderrExcerpt, caseResults);
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
                caseResults);
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
                payload.programmingLanguage());
        List<JudgeJobCaseResultView> caseResults = new ArrayList<>();
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
                    true,
                    false);
            JudgeJobCaseResultView caseResult = toCaseResult(caseOrder, judgeCase.score(), caseOutcome);
            caseResults.add(caseResult);
            timeMillis += caseOutcome.timeMillis();
            memoryBytes = Math.max(memoryBytes, caseOutcome.memoryBytes());
            if (stdoutExcerpt == null && caseOutcome.stdoutExcerpt() != null) {
                stdoutExcerpt = caseOutcome.stdoutExcerpt();
            }
            if (stderrExcerpt == null && caseOutcome.stderrExcerpt() != null) {
                stderrExcerpt = caseOutcome.stderrExcerpt();
            }
            if (caseOutcome.verdict() == JudgeVerdict.SYSTEM_ERROR) {
                return JudgeFinalization.failed(caseOutcome.errorMessage(), stdoutExcerpt, stderrExcerpt, caseResults);
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
                caseResults);
    }

    private CaseOutcome evaluateProgrammingCase(
            SourceBundle sourceBundle,
            ProgrammingLanguage programmingLanguage,
            AssignmentQuestionConfigInput config,
            String stdinText,
            String expectedStdout,
            Integer maxScore,
            boolean clipOutput,
            boolean sampleRun) {
        RunResult programResult = executeCase(
                buildProgramCommand(programmingLanguage, sourceBundle),
                sourceBundle.copyIn(),
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
                    clipOutput,
                    sampleRun);
        }
        return toCaseOutcome(programResult, expectedStdout, clipOutput);
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
            boolean clipOutput,
            boolean sampleRun) {
        String stdout = formatProgramOutput(
                programResult.files() == null ? null : programResult.files().get("stdout"), clipOutput);
        String stderr = formatProgramOutput(
                programResult.files() == null ? null : programResult.files().get("stderr"), clipOutput);
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
                    "go-judge 返回系统错误状态: " + programResult.status());
        }
        if (engineVerdict != JudgeVerdict.ACCEPTED) {
            return new CaseOutcome(
                    engineVerdict,
                    stdout,
                    stderr,
                    timeMillis,
                    memoryBytes,
                    0,
                    describeEngineFailure(engineVerdict, stderr));
        }
        if (!StringUtils.hasText(config.customJudgeScript())) {
            return new CaseOutcome(
                    JudgeVerdict.SYSTEM_ERROR, stdout, stderr, timeMillis, memoryBytes, null, "自定义评测脚本未配置");
        }

        RunResult judgeResult = executeCase(
                List.of("/usr/bin/python3", CUSTOM_JUDGE_SCRIPT_FILE),
                buildCustomJudgeCopyIn(
                        sourceBundle,
                        programmingLanguage,
                        stdinText,
                        expectedStdout,
                        programResult,
                        config,
                        maxScore,
                        sampleRun),
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
                    "自定义评测脚本执行失败: " + formatCustomJudgeFailure(judgeResult));
        }

        String judgeStdout = normalizeLineEndings(
                judgeResult.files() == null ? null : judgeResult.files().get("stdout"));
        if (!StringUtils.hasText(judgeStdout)) {
            return new CaseOutcome(
                    JudgeVerdict.SYSTEM_ERROR, stdout, stderr, timeMillis, memoryBytes, null, "自定义评测脚本未返回裁决 JSON");
        }

        CustomJudgeDecision decision = readCustomJudgeDecision(judgeStdout);
        JudgeVerdict verdict = readCustomJudgeVerdict(decision.verdict());
        Integer awardedScore = resolveCustomJudgeScore(decision.score(), maxScore, verdict);
        return new CaseOutcome(
                verdict, stdout, stderr, timeMillis, memoryBytes, awardedScore, clip(decision.message()));
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
            Map<String, CopyInFile> copyIn,
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
                List.of(
                        new MemoryFileDescriptor(safeContent(stdinText)),
                        new CollectorFileDescriptor("stdout", outputLimitBytes),
                        new CollectorFileDescriptor("stderr", outputLimitBytes)),
                cpuLimitNanos,
                clockLimitNanos,
                memoryLimitBytes,
                PROC_LIMIT,
                copyIn)));
        List<RunResult> results = goJudgeClient.run(request);
        if (results.size() != 1) {
            throw new IllegalStateException("go-judge 返回结果数量异常");
        }
        return results.getFirst();
    }

    private CaseOutcome toCaseOutcome(RunResult result, String expectedStdout) {
        return toCaseOutcome(result, expectedStdout, true);
    }

    private CaseOutcome toCaseOutcome(RunResult result, String expectedStdout, boolean clipOutput) {
        String stdout = formatProgramOutput(
                result.files() == null ? null : result.files().get("stdout"), clipOutput);
        String stderr = formatProgramOutput(
                result.files() == null ? null : result.files().get("stderr"), clipOutput);
        JudgeVerdict engineVerdict = mapEngineVerdict(result.status());
        if (engineVerdict == JudgeVerdict.SYSTEM_ERROR) {
            return new CaseOutcome(
                    JudgeVerdict.SYSTEM_ERROR,
                    stdout,
                    stderr,
                    nanosToMillis(result.time()),
                    safeLong(result.memory()),
                    null,
                    "go-judge 返回系统错误状态: " + result.status());
        }
        if (engineVerdict == JudgeVerdict.ACCEPTED
                && !Objects.equals(
                        normalizeLineEndings(
                                result.files() == null ? null : result.files().get("stdout")),
                        normalizeLineEndings(expectedStdout))) {
            return new CaseOutcome(
                    JudgeVerdict.WRONG_ANSWER,
                    stdout,
                    stderr,
                    nanosToMillis(result.time()),
                    safeLong(result.memory()),
                    null,
                    null);
        }
        return new CaseOutcome(
                engineVerdict,
                stdout,
                stderr,
                nanosToMillis(result.time()),
                safeLong(result.memory()),
                null,
                describeEngineFailure(engineVerdict, stderr));
    }

    private JudgeJobCaseResultView toCaseResult(int caseOrder, int maxScore, CaseOutcome caseOutcome) {
        return new JudgeJobCaseResultView(
                caseOrder,
                caseOutcome.verdict(),
                awardedScore(maxScore, caseOutcome),
                maxScore,
                caseOutcome.stdoutExcerpt(),
                caseOutcome.stderrExcerpt(),
                caseOutcome.timeMillis(),
                caseOutcome.memoryBytes(),
                caseOutcome.errorMessage());
    }

    private void finishJob(Long judgeJobId, JudgeFinalization outcome) {
        JudgeJobEntity job = judgeJobMapper.selectById(judgeJobId);
        if (job == null) {
            return;
        }
        job.setFinishedAt(OffsetDateTime.now());
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
        job.setStatus(outcome.failed() ? JudgeJobStatus.FAILED.name() : JudgeJobStatus.SUCCEEDED.name());
        judgeJobMapper.updateById(job);
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

    private List<String> buildProgramCommand(ProgrammingLanguage language, SourceBundle sourceBundle) {
        String entryFileName = sourceBundle.entryFileName();
        return switch (language) {
            case PYTHON3 -> List.of("/usr/bin/python3", entryFileName);
            case JAVA21, JAVA17 -> {
                List<String> javaSourceFiles = sourceBundle.copyIn().keySet().stream()
                        .filter(path -> path.endsWith(".java"))
                        .sorted()
                        .toList();
                String launchClassName = resolveJavaLaunchClassName(entryFileName, sourceBundle.copyIn());
                yield List.of(
                        "/bin/sh",
                        "-lc",
                        "/opt/java/openjdk/bin/javac -encoding UTF-8 -d . "
                                + String.join(" ", javaSourceFiles)
                                + " && /opt/java/openjdk/bin/java -cp . "
                                + launchClassName);
            }
            case CPP17 ->
                List.of(
                        "/bin/sh",
                        "-lc",
                        "/usr/bin/g++ -B/usr/bin -std=c++17 -O2 " + entryFileName + " -o main && ./main");
        };
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
            ProgrammingSourceSnapshot sourceSnapshot, List<Long> artifactIds, ProgrammingLanguage programmingLanguage) {
        ProgrammingSourceSnapshot normalizedSnapshot = sourceSnapshot == null
                ? ProgrammingSourceSnapshot.fromInput(programmingLanguage, null, null, List.of())
                : sourceSnapshot;
        String entryFileName = normalizedSnapshot.entryFilePath();
        Map<String, CopyInFile> copyIn = readArtifactSourceFiles(artifactIds);
        for (ProgrammingSourceFile file : normalizedSnapshot.files()) {
            copyIn.put(file.path(), new CopyInFile(file.content()));
        }
        if (!copyIn.containsKey(entryFileName)) {
            throw new IllegalStateException("当前自动评测需要代码文本或包含入口文件的附件");
        }
        return new SourceBundle(entryFileName, copyIn);
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

    private int safeInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
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
            String errorMessage) {

        public static ProgrammingSampleRunOutcome completed(
                JudgeVerdict verdict,
                String stdoutText,
                String stderrText,
                Long timeMillis,
                Long memoryBytes,
                String resultSummary) {
            return new ProgrammingSampleRunOutcome(
                    false, verdict, stdoutText, stderrText, timeMillis, memoryBytes, resultSummary, null);
        }

        public static ProgrammingSampleRunOutcome failed(String errorMessage, String stdoutText, String stderrText) {
            return new ProgrammingSampleRunOutcome(
                    true,
                    null,
                    stdoutText,
                    stderrText,
                    0L,
                    0L,
                    errorMessage == null ? "样例试运行失败" : errorMessage,
                    errorMessage);
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
            String stdoutExcerpt,
            String stderrExcerpt,
            long timeMillis,
            long memoryBytes,
            Integer awardedScore,
            String errorMessage) {}

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
            List<JudgeJobCaseResultView> caseResults) {

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
                List<JudgeJobCaseResultView> caseResults) {
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
                    caseResults);
        }

        static JudgeFinalization failed(String errorMessage) {
            return failed(errorMessage, null, null, List.of());
        }

        static JudgeFinalization failed(
                String errorMessage,
                String stdoutExcerpt,
                String stderrExcerpt,
                List<JudgeJobCaseResultView> caseResults) {
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
                    caseResults == null ? List.of() : caseResults);
        }
    }

    private record ProgrammingAnswerPayload(
            List<Long> artifactIds,
            ProgrammingLanguage programmingLanguage,
            String entryFilePath,
            List<ProgrammingSourceFile> files) {}

    private record SourceBundle(String entryFileName, Map<String, CopyInFile> copyIn) {}

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
