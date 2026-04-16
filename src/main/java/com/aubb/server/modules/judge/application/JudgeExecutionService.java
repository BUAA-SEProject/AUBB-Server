package com.aubb.server.modules.judge.application;

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
import com.aubb.server.modules.submission.infrastructure.SubmissionEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

@Service
public class JudgeExecutionService {

    private static final String ENGINE_JOB_REF = "GO_JUDGE_SYNC_RUN";
    private static final int MAX_EXCERPT_LENGTH = 2_000;
    private static final int PROC_LIMIT = 32;

    private final JudgeJobMapper judgeJobMapper;
    private final SubmissionMapper submissionMapper;
    private final AssignmentJudgeProfileMapper assignmentJudgeProfileMapper;
    private final AssignmentJudgeCaseMapper assignmentJudgeCaseMapper;
    private final GoJudgeClient goJudgeClient;
    private final com.aubb.server.config.GoJudgeConfiguration.GoJudgeProperties goJudgeProperties;
    private final AuditLogApplicationService auditLogApplicationService;
    private final TransactionTemplate transactionTemplate;

    public JudgeExecutionService(
            PlatformTransactionManager transactionManager,
            JudgeJobMapper judgeJobMapper,
            SubmissionMapper submissionMapper,
            AssignmentJudgeProfileMapper assignmentJudgeProfileMapper,
            AssignmentJudgeCaseMapper assignmentJudgeCaseMapper,
            GoJudgeClient goJudgeClient,
            com.aubb.server.config.GoJudgeConfiguration.GoJudgeProperties goJudgeProperties,
            AuditLogApplicationService auditLogApplicationService) {
        this.judgeJobMapper = judgeJobMapper;
        this.submissionMapper = submissionMapper;
        this.assignmentJudgeProfileMapper = assignmentJudgeProfileMapper;
        this.assignmentJudgeCaseMapper = assignmentJudgeCaseMapper;
        this.goJudgeClient = goJudgeClient;
        this.goJudgeProperties = goJudgeProperties;
        this.auditLogApplicationService = auditLogApplicationService;
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
        AssignmentJudgeProfileEntity profile = assignmentJudgeProfileMapper.selectById(job.getAssignmentId());
        List<AssignmentJudgeCaseEntity> cases =
                assignmentJudgeCaseMapper.selectList(Wrappers.<AssignmentJudgeCaseEntity>lambdaQuery()
                        .eq(AssignmentJudgeCaseEntity::getAssignmentId, job.getAssignmentId())
                        .orderByAsc(AssignmentJudgeCaseEntity::getCaseOrder)
                        .orderByAsc(AssignmentJudgeCaseEntity::getId));

        auditLogApplicationService.record(
                job.getRequestedByUserId(),
                AuditAction.JUDGE_JOB_STARTED,
                "JUDGE_JOB",
                String.valueOf(job.getId()),
                AuditResult.SUCCESS,
                Map.of("submissionId", job.getSubmissionId(), "assignmentId", job.getAssignmentId()));
        return new JudgeExecutionContext(job, submission, profile, cases);
    }

    private JudgeFinalization runJudge(JudgeExecutionContext context) {
        if (!goJudgeProperties.enabled()) {
            return JudgeFinalization.failed("go-judge 未启用");
        }
        if (context.submission() == null) {
            return JudgeFinalization.failed("提交不存在，无法执行自动评测");
        }
        if (context.profile() == null || context.cases().isEmpty()) {
            return JudgeFinalization.failed("任务未配置自动评测规则");
        }
        if (context.submission().getContentText() == null
                || context.submission().getContentText().isBlank()) {
            return JudgeFinalization.failed("当前自动评测仅支持文本代码提交");
        }

        List<AssignmentJudgeCaseEntity> cases = context.cases();
        int passedCaseCount = 0;
        int totalCaseCount = cases.size();
        int score = 0;
        int maxScore = cases.stream()
                .map(AssignmentJudgeCaseEntity::getScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        long timeMillis = 0L;
        long memoryBytes = 0L;
        JudgeVerdict overallVerdict = JudgeVerdict.ACCEPTED;
        String stdoutExcerpt = null;
        String stderrExcerpt = null;

        for (AssignmentJudgeCaseEntity testCase : cases) {
            RunResult result =
                    executeCase(context.profile(), context.submission().getContentText(), testCase);
            CaseOutcome caseOutcome = toCaseOutcome(result, testCase);
            timeMillis += caseOutcome.timeMillis();
            memoryBytes = Math.max(memoryBytes, caseOutcome.memoryBytes());
            if (stdoutExcerpt == null && caseOutcome.stdoutExcerpt() != null) {
                stdoutExcerpt = caseOutcome.stdoutExcerpt();
            }
            if (stderrExcerpt == null && caseOutcome.stderrExcerpt() != null) {
                stderrExcerpt = caseOutcome.stderrExcerpt();
            }
            if (caseOutcome.verdict() == JudgeVerdict.SYSTEM_ERROR) {
                return JudgeFinalization.failed(caseOutcome.errorMessage(), stdoutExcerpt, stderrExcerpt);
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
                summary);
    }

    private RunResult executeCase(
            AssignmentJudgeProfileEntity profile, String sourceCode, AssignmentJudgeCaseEntity testCase) {
        long cpuLimitNanos = profile.getTimeLimitMs() * 1_000_000L;
        long clockLimitNanos = cpuLimitNanos * 2;
        long memoryLimitBytes = profile.getMemoryLimitMb() * 1024L * 1024L;
        long outputLimitBytes = profile.getOutputLimitKb() * 1024L;
        RunRequest request = new RunRequest(List.of(new Command(
                List.of("/usr/bin/python3", profile.getEntryFileName()),
                List.of(
                        new FileDescriptor(testCase.getStdinText(), null, null),
                        new FileDescriptor(null, "stdout", outputLimitBytes),
                        new FileDescriptor(null, "stderr", outputLimitBytes)),
                cpuLimitNanos,
                clockLimitNanos,
                memoryLimitBytes,
                PROC_LIMIT,
                Map.of(profile.getEntryFileName(), new CopyInFile(sourceCode)))));
        List<RunResult> results = goJudgeClient.run(request);
        if (results.size() != 1) {
            throw new IllegalStateException("go-judge 返回结果数量异常");
        }
        return results.getFirst();
    }

    private CaseOutcome toCaseOutcome(RunResult result, AssignmentJudgeCaseEntity testCase) {
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
                        normalizeLineEndings(testCase.getExpectedStdout()))) {
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
        job.setStatus(outcome.failed() ? JudgeJobStatus.FAILED.name() : JudgeJobStatus.SUCCEEDED.name());
        judgeJobMapper.updateById(job);

        auditLogApplicationService.record(
                job.getRequestedByUserId(),
                outcome.failed() ? AuditAction.JUDGE_JOB_FAILED : AuditAction.JUDGE_JOB_COMPLETED,
                "JUDGE_JOB",
                String.valueOf(job.getId()),
                outcome.failed() ? AuditResult.FAILURE : AuditResult.SUCCESS,
                buildAuditMetadata(job, outcome));
    }

    private Map<String, Object> buildAuditMetadata(JudgeJobEntity job, JudgeFinalization outcome) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("submissionId", job.getSubmissionId());
        metadata.put("assignmentId", job.getAssignmentId());
        metadata.put("status", outcome.failed() ? JudgeJobStatus.FAILED.name() : JudgeJobStatus.SUCCEEDED.name());
        metadata.put(
                "verdict", outcome.verdict() == null ? null : outcome.verdict().name());
        metadata.put("score", outcome.score());
        metadata.put("maxScore", outcome.maxScore());
        return metadata;
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
            AssignmentJudgeProfileEntity profile,
            List<AssignmentJudgeCaseEntity> cases) {}

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
            String errorMessage) {

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
                String resultSummary) {
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
                    null);
        }

        static JudgeFinalization failed(String errorMessage) {
            return failed(errorMessage, null, null);
        }

        static JudgeFinalization failed(String errorMessage, String stdoutExcerpt, String stderrExcerpt) {
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
                    errorMessage);
        }
    }
}
