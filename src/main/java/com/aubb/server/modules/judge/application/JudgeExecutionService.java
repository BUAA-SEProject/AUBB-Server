package com.aubb.server.modules.judge.application;

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
import com.aubb.server.modules.judge.infrastructure.gojudge.GoJudgeClient.Command;
import com.aubb.server.modules.judge.infrastructure.gojudge.GoJudgeClient.CopyInFile;
import com.aubb.server.modules.judge.infrastructure.gojudge.GoJudgeClient.FileDescriptor;
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
import java.util.Map;
import java.util.Objects;
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

        String summary = "%s，%s/%s 个用例通过，得分 %s/%s"
                .formatted(overallVerdict.name(), passedCaseCount, totalCaseCount, score, maxScore);
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
        if (ProgrammingJudgeMode.CUSTOM_SCRIPT.equals(config.judgeMode())) {
            return JudgeFinalization.failed("当前结构化编程题题目级评测暂仅支持 STANDARD_IO");
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

        SourceBundle sourceBundle = buildSourceBundle(context.answer(), payload, config);
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
            RunResult result = executeCase(
                    buildProgramCommand(payload.programmingLanguage(), sourceBundle.entryFileName()),
                    sourceBundle.copyIn(),
                    judgeCase.stdinText(),
                    safeInt(config.timeLimitMs(), DEFAULT_TIME_LIMIT_MS),
                    safeInt(config.memoryLimitMb(), DEFAULT_MEMORY_LIMIT_MB),
                    safeInt(config.outputLimitKb(), DEFAULT_OUTPUT_LIMIT_KB));
            CaseOutcome caseOutcome = toCaseOutcome(result, judgeCase.expectedStdout());
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
            if (caseOutcome.verdict() == JudgeVerdict.ACCEPTED) {
                passedCaseCount += 1;
                score += judgeCase.score();
            } else if (overallVerdict == JudgeVerdict.ACCEPTED) {
                overallVerdict = caseOutcome.verdict();
            }
            caseOrder++;
        }

        String summary = "%s，%s/%s 个测试点通过，得分 %s/%s"
                .formatted(overallVerdict.name(), passedCaseCount, totalCaseCount, score, maxScore);
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
                        new FileDescriptor(stdinText, null, null),
                        new FileDescriptor(null, "stdout", outputLimitBytes),
                        new FileDescriptor(null, "stderr", outputLimitBytes)),
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
        String stdout = clip(normalizeLineEndings(
                result.files() == null ? null : result.files().get("stdout")));
        String stderr = clip(normalizeLineEndings(
                result.files() == null ? null : result.files().get("stderr")));
        JudgeVerdict engineVerdict = mapEngineVerdict(result.status());
        if (engineVerdict == JudgeVerdict.SYSTEM_ERROR) {
            return new CaseOutcome(
                    JudgeVerdict.SYSTEM_ERROR,
                    stdout,
                    stderr,
                    nanosToMillis(result.time()),
                    safeLong(result.memory()),
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
                    null);
        }
        return new CaseOutcome(
                engineVerdict, stdout, stderr, nanosToMillis(result.time()), safeLong(result.memory()), null);
    }

    private JudgeJobCaseResultView toCaseResult(int caseOrder, int maxScore, CaseOutcome caseOutcome) {
        return new JudgeJobCaseResultView(
                caseOrder,
                caseOutcome.verdict(),
                JudgeVerdict.ACCEPTED.equals(caseOutcome.verdict()) ? maxScore : 0,
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

    private List<String> buildProgramCommand(ProgrammingLanguage language, String entryFileName) {
        return switch (language) {
            case PYTHON3 -> List.of("/usr/bin/python3", entryFileName);
            case JAVA17 -> {
                String className = entryFileName.endsWith(".java")
                        ? entryFileName.substring(0, entryFileName.length() - 5)
                        : entryFileName;
                yield List.of("/bin/sh", "-lc", "/usr/bin/javac " + entryFileName + " && /usr/bin/java " + className);
            }
            case CPP17 ->
                List.of("/bin/sh", "-lc", "/usr/bin/g++ -std=c++17 -O2 " + entryFileName + " -o main && ./main");
        };
    }

    private SourceBundle buildSourceBundle(
            SubmissionAnswerEntity answer, ProgrammingAnswerPayload payload, AssignmentQuestionConfigInput config) {
        String entryFileName = defaultEntryFileName(payload.programmingLanguage());
        Map<String, CopyInFile> copyIn = readArtifactSourceFiles(payload.artifactIds());
        if (StringUtils.hasText(answer.getAnswerText())) {
            copyIn.put(entryFileName, new CopyInFile(answer.getAnswerText()));
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
            return new ProgrammingAnswerPayload(List.of(), null);
        }
        try {
            return objectMapper.readValue(payloadJson, ProgrammingAnswerPayload.class);
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

    private String defaultEntryFileName(ProgrammingLanguage language) {
        return switch (language) {
            case PYTHON3 -> "main.py";
            case JAVA17 -> "Main.java";
            case CPP17 -> "main.cpp";
        };
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
            case "Non Zero Exit Status", "Signalled", "Dangerous Syscall" -> JudgeVerdict.RUNTIME_ERROR;
            case "Internal Error", "File Error" -> JudgeVerdict.SYSTEM_ERROR;
            default -> JudgeVerdict.SYSTEM_ERROR;
        };
    }

    private String normalizeLineEndings(String value) {
        return value == null ? null : value.replace("\r\n", "\n");
    }

    private String clip(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= MAX_EXCERPT_LENGTH ? value : value.substring(0, MAX_EXCERPT_LENGTH);
    }

    private long nanosToMillis(Long nanos) {
        return nanos == null ? 0L : Math.max(0L, nanos / 1_000_000L);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
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

    private record ProgrammingAnswerPayload(List<Long> artifactIds, ProgrammingLanguage programmingLanguage) {}

    private record SourceBundle(String entryFileName, Map<String, CopyInFile> copyIn) {}
}
