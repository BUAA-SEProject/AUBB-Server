package com.aubb.server.modules.submission.application.answer;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.assignment.application.paper.AssignmentPaperApplicationService;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionConfigInput;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionOptionView;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionSnapshot;
import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.submission.domain.answer.SubmissionAnswerGradingStatus;
import com.aubb.server.modules.submission.infrastructure.SubmissionArtifactEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionEntity;
import com.aubb.server.modules.submission.infrastructure.answer.SubmissionAnswerEntity;
import com.aubb.server.modules.submission.infrastructure.answer.SubmissionAnswerMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class SubmissionAnswerApplicationService {

    private final SubmissionAnswerMapper submissionAnswerMapper;
    private final AssignmentPaperApplicationService assignmentPaperApplicationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public PersistedStructuredAnswers persistStructuredAnswers(
            SubmissionEntity submission,
            List<SubmissionAnswerInput> answerInputs,
            Map<Long, SubmissionArtifactEntity> artifactsById) {
        List<AssignmentQuestionSnapshot> questions =
                assignmentPaperApplicationService.loadQuestionSnapshots(submission.getAssignmentId());
        if (questions.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_PAPER_NOT_FOUND", "当前作业未配置结构化试卷");
        }
        if (answerInputs == null || answerInputs.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ANSWERS_REQUIRED", "结构化作业必须按题提交答案");
        }
        Map<Long, AssignmentQuestionSnapshot> questionIndex = questions.stream()
                .collect(Collectors.toMap(
                        AssignmentQuestionSnapshot::id,
                        question -> question,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<Long, SubmissionAnswerInput> inputIndex = new LinkedHashMap<>();
        for (SubmissionAnswerInput input : answerInputs) {
            if (input == null || input.assignmentQuestionId() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ANSWER_INVALID", "答案必须绑定题目");
            }
            if (inputIndex.putIfAbsent(input.assignmentQuestionId(), input) != null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ANSWER_DUPLICATED", "同一题目不能重复提交答案");
            }
        }
        if (!questionIndex.keySet().equals(inputIndex.keySet())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ANSWER_SET_INVALID", "当前提交必须覆盖结构化作业中的全部题目");
        }

        List<SubmissionAnswerEntity> persistedAnswers = new java.util.ArrayList<>();
        for (AssignmentQuestionSnapshot question : questions) {
            SubmissionAnswerInput input = inputIndex.get(question.id());
            EvaluatedAnswer evaluatedAnswer = evaluateAnswer(question, input, artifactsById);
            SubmissionAnswerEntity entity = new SubmissionAnswerEntity();
            entity.setSubmissionId(submission.getId());
            entity.setAssignmentQuestionId(question.id());
            entity.setAnswerText(evaluatedAnswer.answerText());
            entity.setAnswerPayloadJson(writePayload(evaluatedAnswer.payload()));
            entity.setAutoScore(evaluatedAnswer.autoScore());
            entity.setManualScore(evaluatedAnswer.manualScore());
            entity.setFinalScore(evaluatedAnswer.finalScore());
            entity.setGradingStatus(evaluatedAnswer.gradingStatus().name());
            entity.setFeedbackText(evaluatedAnswer.feedbackText());
            submissionAnswerMapper.insert(entity);
            persistedAnswers.add(entity);
        }
        return new PersistedStructuredAnswers(List.copyOf(persistedAnswers), questionIndex);
    }

    @Transactional(readOnly = true)
    public List<SubmissionAnswerView> loadAnswerViews(Long submissionId, Long assignmentId) {
        return loadAnswerViews(submissionId, assignmentId, true);
    }

    @Transactional(readOnly = true)
    public List<SubmissionAnswerView> loadAnswerViews(
            Long submissionId, Long assignmentId, boolean revealNonObjectiveScores) {
        List<SubmissionAnswerEntity> entities = loadAnswerEntities(submissionId);
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<Long, AssignmentQuestionSnapshot> questionIndex = loadQuestionIndex(assignmentId);
        return entities.stream()
                .map(entity ->
                        toView(entity, questionIndex.get(entity.getAssignmentQuestionId()), revealNonObjectiveScores))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubmissionScoreSummaryView loadScoreSummary(Long submissionId, Long assignmentId) {
        return loadScoreSummary(submissionId, assignmentId, true, true);
    }

    @Transactional(readOnly = true)
    public SubmissionScoreSummaryView loadScoreSummary(
            Long submissionId, Long assignmentId, boolean revealNonObjectiveScores, boolean gradePublished) {
        List<SubmissionAnswerEntity> entities = loadAnswerEntities(submissionId);
        if (entities.isEmpty()) {
            return null;
        }
        Map<Long, AssignmentQuestionSnapshot> questionIndex = loadQuestionIndex(assignmentId);
        int autoScore = entities.stream()
                .filter(entity ->
                        revealNonObjectiveScores || isObjective(questionIndex.get(entity.getAssignmentQuestionId())))
                .map(SubmissionAnswerEntity::getAutoScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        Integer manualScore = revealNonObjectiveScores
                ? entities.stream()
                        .map(SubmissionAnswerEntity::getManualScore)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .sum()
                : null;
        int finalScore = entities.stream()
                .filter(entity ->
                        revealNonObjectiveScores || isObjective(questionIndex.get(entity.getAssignmentQuestionId())))
                .map(SubmissionAnswerEntity::getFinalScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int maxScore = entities.stream()
                .map(SubmissionAnswerEntity::getAssignmentQuestionId)
                .map(questionIndex::get)
                .filter(Objects::nonNull)
                .mapToInt(AssignmentQuestionSnapshot::score)
                .sum();
        int pendingManualCount = 0;
        int pendingProgrammingCount = 0;
        for (SubmissionAnswerEntity entity : entities) {
            SubmissionAnswerGradingStatus gradingStatus =
                    SubmissionAnswerGradingStatus.valueOf(entity.getGradingStatus());
            if (SubmissionAnswerGradingStatus.PENDING_MANUAL.equals(gradingStatus)) {
                pendingManualCount++;
            }
            if (SubmissionAnswerGradingStatus.PENDING_PROGRAMMING_JUDGE.equals(gradingStatus)) {
                pendingProgrammingCount++;
            }
        }
        return new SubmissionScoreSummaryView(
                autoScore,
                manualScore,
                finalScore,
                maxScore,
                pendingManualCount,
                pendingProgrammingCount,
                pendingManualCount == 0 && pendingProgrammingCount == 0,
                gradePublished);
    }

    private EvaluatedAnswer evaluateAnswer(
            AssignmentQuestionSnapshot question,
            SubmissionAnswerInput input,
            Map<Long, SubmissionArtifactEntity> artifactsById) {
        return switch (question.questionType()) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> evaluateObjective(question, input);
            case SHORT_ANSWER -> evaluateShortAnswer(input);
            case FILE_UPLOAD -> evaluateFileUpload(input, artifactsById);
            case PROGRAMMING -> evaluateProgramming(question.config(), input, artifactsById);
        };
    }

    private EvaluatedAnswer evaluateObjective(AssignmentQuestionSnapshot question, SubmissionAnswerInput input) {
        Set<String> selectedKeys = normalizeOptionKeys(input.selectedOptionKeys());
        if (selectedKeys.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_OPTION_REQUIRED", "客观题必须选择至少一个选项");
        }
        Set<String> validKeys = question.options().stream()
                .map(AssignmentQuestionOptionView::optionKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!validKeys.containsAll(selectedKeys)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_OPTION_INVALID", "客观题选项非法");
        }
        if (AssignmentQuestionType.SINGLE_CHOICE.equals(question.questionType()) && selectedKeys.size() != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_SINGLE_CHOICE_INVALID", "单选题必须且只能选择一个选项");
        }
        int score = selectedKeys.equals(question.correctOptionKeys()) ? question.score() : 0;
        return new EvaluatedAnswer(
                null,
                new AnswerPayload(List.copyOf(selectedKeys), List.of(), null),
                score,
                null,
                score,
                SubmissionAnswerGradingStatus.AUTO_GRADED,
                "客观题自动判分完成");
    }

    private EvaluatedAnswer evaluateShortAnswer(SubmissionAnswerInput input) {
        if (!StringUtils.hasText(input.answerText())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_SHORT_ANSWER_REQUIRED", "简答题答案不能为空");
        }
        return new EvaluatedAnswer(
                input.answerText().trim(),
                new AnswerPayload(List.of(), List.of(), null),
                null,
                null,
                null,
                SubmissionAnswerGradingStatus.PENDING_MANUAL,
                "等待人工批改");
    }

    private EvaluatedAnswer evaluateFileUpload(
            SubmissionAnswerInput input, Map<Long, SubmissionArtifactEntity> artifactsById) {
        List<Long> artifactIds = normalizeArtifactIds(input.artifactIds(), artifactsById.keySet());
        if (artifactIds.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_FILE_REQUIRED", "文件题必须上传附件");
        }
        return new EvaluatedAnswer(
                null,
                new AnswerPayload(List.of(), artifactIds, null),
                null,
                null,
                null,
                SubmissionAnswerGradingStatus.PENDING_MANUAL,
                "等待人工批改");
    }

    private EvaluatedAnswer evaluateProgramming(
            AssignmentQuestionConfigInput config,
            SubmissionAnswerInput input,
            Map<Long, SubmissionArtifactEntity> artifactsById) {
        List<Long> artifactIds = normalizeArtifactIds(input.artifactIds(), artifactsById.keySet());
        if (!StringUtils.hasText(input.answerText()) && artifactIds.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_PROGRAM_REQUIRED", "编程题必须提供代码文本或附件");
        }
        if (input.programmingLanguage() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_PROGRAM_LANGUAGE_REQUIRED", "编程题必须指定语言");
        }
        if (config != null
                && config.supportedLanguages() != null
                && !config.supportedLanguages().isEmpty()
                && !config.supportedLanguages().contains(input.programmingLanguage())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "SUBMISSION_PROGRAM_LANGUAGE_UNSUPPORTED", "当前语言不在题目支持范围内");
        }
        if (config != null && config.maxFileCount() != null && artifactIds.size() > config.maxFileCount()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "SUBMISSION_PROGRAM_FILE_COUNT_EXCEEDED", "当前编程题附件数量超过限制");
        }
        if (config != null && !Boolean.TRUE.equals(config.allowMultipleFiles()) && artifactIds.size() > 1) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "SUBMISSION_PROGRAM_MULTIPLE_FILES_FORBIDDEN", "当前编程题不允许提交多个代码附件");
        }
        if (config != null
                && config.acceptedExtensions() != null
                && !config.acceptedExtensions().isEmpty()) {
            Set<String> acceptedExtensions = config.acceptedExtensions().stream()
                    .filter(StringUtils::hasText)
                    .map(extension -> extension.startsWith(".") ? extension.substring(1) : extension)
                    .map(String::toLowerCase)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (Long artifactId : artifactIds) {
                String filename = artifactsById.get(artifactId).getOriginalFilename();
                String extension = filename != null && filename.contains(".")
                        ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
                        : "";
                if (!acceptedExtensions.contains(extension)) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST, "SUBMISSION_PROGRAM_EXTENSION_UNSUPPORTED", "当前编程题附件类型不在允许范围内");
                }
            }
        }
        return new EvaluatedAnswer(
                StringUtils.hasText(input.answerText()) ? input.answerText().trim() : null,
                new AnswerPayload(List.of(), artifactIds, input.programmingLanguage()),
                null,
                null,
                null,
                SubmissionAnswerGradingStatus.PENDING_PROGRAMMING_JUDGE,
                "等待编程题评测");
    }

    private List<Long> normalizeArtifactIds(List<Long> artifactIds, Collection<Long> allowedArtifactIds) {
        if (artifactIds == null) {
            return List.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long artifactId : artifactIds) {
            if (artifactId == null || !allowedArtifactIds.contains(artifactId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_INVALID", "答案中引用了无效附件");
            }
            normalized.add(artifactId);
        }
        return List.copyOf(normalized);
    }

    private Set<String> normalizeOptionKeys(List<String> optionKeys) {
        if (optionKeys == null) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String optionKey : optionKeys) {
            if (!StringUtils.hasText(optionKey)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_OPTION_INVALID", "客观题选项不能为空");
            }
            normalized.add(optionKey.trim());
        }
        return normalized;
    }

    private List<SubmissionAnswerEntity> loadAnswerEntities(Long submissionId) {
        return submissionAnswerMapper.selectList(Wrappers.<SubmissionAnswerEntity>lambdaQuery()
                .eq(SubmissionAnswerEntity::getSubmissionId, submissionId)
                .orderByAsc(SubmissionAnswerEntity::getAssignmentQuestionId)
                .orderByAsc(SubmissionAnswerEntity::getId));
    }

    private Map<Long, AssignmentQuestionSnapshot> loadQuestionIndex(Long assignmentId) {
        return assignmentPaperApplicationService.loadQuestionSnapshots(assignmentId).stream()
                .collect(Collectors.toMap(
                        AssignmentQuestionSnapshot::id,
                        question -> question,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private SubmissionAnswerView toView(
            SubmissionAnswerEntity entity, AssignmentQuestionSnapshot question, boolean revealNonObjectiveScores) {
        AnswerPayload payload = readPayload(entity.getAnswerPayloadJson());
        boolean revealAnswerResult = revealNonObjectiveScores || isObjective(question);
        return new SubmissionAnswerView(
                entity.getId(),
                entity.getAssignmentQuestionId(),
                question == null ? null : question.title(),
                question == null ? null : question.questionType(),
                entity.getAnswerText(),
                payload.selectedOptionKeys(),
                payload.artifactIds(),
                payload.programmingLanguage(),
                revealAnswerResult ? entity.getAutoScore() : null,
                revealAnswerResult ? entity.getManualScore() : null,
                revealAnswerResult ? entity.getFinalScore() : null,
                SubmissionAnswerGradingStatus.valueOf(entity.getGradingStatus()),
                revealAnswerResult ? entity.getFeedbackText() : null,
                revealAnswerResult ? entity.getGradedByUserId() : null,
                revealAnswerResult ? entity.getGradedAt() : null);
    }

    private boolean isObjective(AssignmentQuestionSnapshot question) {
        return question != null && isObjective(question.questionType());
    }

    private boolean isObjective(AssignmentQuestionType questionType) {
        return AssignmentQuestionType.SINGLE_CHOICE.equals(questionType)
                || AssignmentQuestionType.MULTIPLE_CHOICE.equals(questionType);
    }

    private String writePayload(AnswerPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_PAYLOAD_INVALID", "答案载荷无法序列化");
        }
    }

    private AnswerPayload readPayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return new AnswerPayload(List.of(), List.of(), null);
        }
        try {
            return objectMapper.readValue(payloadJson, AnswerPayload.class);
        } catch (JacksonException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "SUBMISSION_PAYLOAD_BROKEN", "答案载荷无法读取");
        }
    }

    private record EvaluatedAnswer(
            String answerText,
            AnswerPayload payload,
            Integer autoScore,
            Integer manualScore,
            Integer finalScore,
            SubmissionAnswerGradingStatus gradingStatus,
            String feedbackText) {}

    private record AnswerPayload(
            List<String> selectedOptionKeys, List<Long> artifactIds, ProgrammingLanguage programmingLanguage) {}

    public record PersistedStructuredAnswers(
            List<SubmissionAnswerEntity> answers, Map<Long, AssignmentQuestionSnapshot> questionIndex) {}
}
