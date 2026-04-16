package com.aubb.server.modules.assignment.application.paper;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import com.aubb.server.modules.assignment.infrastructure.bank.QuestionBankQuestionEntity;
import com.aubb.server.modules.assignment.infrastructure.bank.QuestionBankQuestionMapper;
import com.aubb.server.modules.assignment.infrastructure.bank.QuestionBankQuestionOptionEntity;
import com.aubb.server.modules.assignment.infrastructure.bank.QuestionBankQuestionOptionMapper;
import com.aubb.server.modules.assignment.infrastructure.paper.AssignmentQuestionEntity;
import com.aubb.server.modules.assignment.infrastructure.paper.AssignmentQuestionMapper;
import com.aubb.server.modules.assignment.infrastructure.paper.AssignmentQuestionOptionEntity;
import com.aubb.server.modules.assignment.infrastructure.paper.AssignmentQuestionOptionMapper;
import com.aubb.server.modules.assignment.infrastructure.paper.AssignmentSectionEntity;
import com.aubb.server.modules.assignment.infrastructure.paper.AssignmentSectionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AssignmentPaperApplicationService {

    private final AssignmentSectionMapper assignmentSectionMapper;
    private final AssignmentQuestionMapper assignmentQuestionMapper;
    private final AssignmentQuestionOptionMapper assignmentQuestionOptionMapper;
    private final QuestionBankQuestionMapper questionBankQuestionMapper;
    private final QuestionBankQuestionOptionMapper questionBankQuestionOptionMapper;
    private final StructuredQuestionSupport structuredQuestionSupport;

    @Transactional
    public void persistPaper(Long assignmentId, Long offeringId, AssignmentPaperInput paper) {
        if (paper == null) {
            return;
        }
        if (paper.sections() == null || paper.sections().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_PAPER_REQUIRED", "结构化作业必须至少包含一个大题");
        }
        int sectionOrder = 1;
        for (AssignmentSectionInput sectionInput : paper.sections()) {
            if (sectionInput == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_SECTION_INVALID", "大题不能为空");
            }
            if (!StringUtils.hasText(sectionInput.title())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_SECTION_TITLE_REQUIRED", "大题标题不能为空");
            }
            if (sectionInput.questions() == null || sectionInput.questions().isEmpty()) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "ASSIGNMENT_SECTION_QUESTIONS_REQUIRED", "每个大题至少需要一道题");
            }
            List<ResolvedQuestion> resolvedQuestions = sectionInput.questions().stream()
                    .map(question -> resolveQuestion(offeringId, question))
                    .toList();
            AssignmentSectionEntity sectionEntity = new AssignmentSectionEntity();
            sectionEntity.setAssignmentId(assignmentId);
            sectionEntity.setSectionOrder(sectionOrder++);
            sectionEntity.setTitle(sectionInput.title().trim());
            sectionEntity.setDescription(
                    StringUtils.hasText(sectionInput.description())
                            ? sectionInput.description().trim()
                            : null);
            sectionEntity.setTotalScore(
                    resolvedQuestions.stream().mapToInt(ResolvedQuestion::score).sum());
            assignmentSectionMapper.insert(sectionEntity);
            persistSectionQuestions(assignmentId, sectionEntity.getId(), resolvedQuestions);
        }
    }

    @Transactional(readOnly = true)
    public boolean hasStructuredPaper(Long assignmentId) {
        return assignmentSectionMapper.selectCount(Wrappers.<AssignmentSectionEntity>lambdaQuery()
                        .eq(AssignmentSectionEntity::getAssignmentId, assignmentId))
                > 0;
    }

    @Transactional(readOnly = true)
    public AssignmentPaperView loadPaper(Long assignmentId, boolean revealSensitiveFields) {
        List<AssignmentSectionEntity> sections =
                assignmentSectionMapper.selectList(Wrappers.<AssignmentSectionEntity>lambdaQuery()
                        .eq(AssignmentSectionEntity::getAssignmentId, assignmentId)
                        .orderByAsc(AssignmentSectionEntity::getSectionOrder)
                        .orderByAsc(AssignmentSectionEntity::getId));
        if (sections.isEmpty()) {
            return null;
        }
        List<Long> sectionIds =
                sections.stream().map(AssignmentSectionEntity::getId).toList();
        List<AssignmentQuestionEntity> questions =
                assignmentQuestionMapper.selectList(Wrappers.<AssignmentQuestionEntity>lambdaQuery()
                        .eq(AssignmentQuestionEntity::getAssignmentId, assignmentId)
                        .in(AssignmentQuestionEntity::getAssignmentSectionId, sectionIds)
                        .orderByAsc(AssignmentQuestionEntity::getAssignmentSectionId)
                        .orderByAsc(AssignmentQuestionEntity::getQuestionOrder)
                        .orderByAsc(AssignmentQuestionEntity::getId));
        Map<Long, List<AssignmentQuestionOptionEntity>> optionsByQuestionId = loadPaperOptions(questions);
        Map<Long, List<AssignmentQuestionEntity>> questionsBySectionId = questions.stream()
                .collect(Collectors.groupingBy(
                        AssignmentQuestionEntity::getAssignmentSectionId, LinkedHashMap::new, Collectors.toList()));
        List<AssignmentSectionView> sectionViews = sections.stream()
                .map(section -> toSectionView(
                        section,
                        questionsBySectionId.getOrDefault(section.getId(), List.of()),
                        optionsByQuestionId,
                        revealSensitiveFields))
                .toList();
        int questionCount = sectionViews.stream()
                .mapToInt(section -> section.questions().size())
                .sum();
        int totalScore = sectionViews.stream()
                .mapToInt(AssignmentSectionView::totalScore)
                .sum();
        return new AssignmentPaperView(sectionViews.size(), questionCount, totalScore, sectionViews);
    }

    @Transactional(readOnly = true)
    public List<AssignmentQuestionSnapshot> loadQuestionSnapshots(Long assignmentId) {
        List<AssignmentQuestionEntity> questions =
                assignmentQuestionMapper.selectList(Wrappers.<AssignmentQuestionEntity>lambdaQuery()
                        .eq(AssignmentQuestionEntity::getAssignmentId, assignmentId)
                        .orderByAsc(AssignmentQuestionEntity::getAssignmentSectionId)
                        .orderByAsc(AssignmentQuestionEntity::getQuestionOrder)
                        .orderByAsc(AssignmentQuestionEntity::getId));
        Map<Long, List<AssignmentQuestionOptionEntity>> optionsByQuestionId = loadPaperOptions(questions);
        return questions.stream()
                .map(question -> {
                    List<AssignmentQuestionOptionEntity> optionEntities =
                            optionsByQuestionId.getOrDefault(question.getId(), List.of());
                    AssignmentQuestionConfigInput config =
                            structuredQuestionSupport.readConfigInput(question.getConfigJson());
                    return new AssignmentQuestionSnapshot(
                            question.getId(),
                            question.getAssignmentId(),
                            question.getSourceQuestionId(),
                            question.getQuestionOrder(),
                            question.getTitle(),
                            question.getPromptText(),
                            AssignmentQuestionType.valueOf(question.getQuestionType()),
                            question.getScore(),
                            structuredQuestionSupport.toOptionViewsFromPaper(optionEntities, true),
                            structuredQuestionSupport.correctOptionKeysFromPaper(optionEntities),
                            config);
                })
                .toList();
    }

    private AssignmentSectionView toSectionView(
            AssignmentSectionEntity section,
            List<AssignmentQuestionEntity> questions,
            Map<Long, List<AssignmentQuestionOptionEntity>> optionsByQuestionId,
            boolean revealSensitiveFields) {
        List<AssignmentQuestionView> questionViews = questions.stream()
                .map(question -> {
                    List<AssignmentQuestionOptionEntity> optionEntities =
                            optionsByQuestionId.getOrDefault(question.getId(), List.of());
                    return new AssignmentQuestionView(
                            question.getId(),
                            question.getSourceQuestionId(),
                            question.getQuestionOrder(),
                            question.getTitle(),
                            question.getPromptText(),
                            AssignmentQuestionType.valueOf(question.getQuestionType()),
                            question.getScore(),
                            structuredQuestionSupport.toOptionViewsFromPaper(optionEntities, revealSensitiveFields),
                            structuredQuestionSupport.toConfigView(
                                    structuredQuestionSupport.readConfigInput(question.getConfigJson()),
                                    revealSensitiveFields));
                })
                .toList();
        return new AssignmentSectionView(
                section.getId(),
                section.getSectionOrder(),
                section.getTitle(),
                section.getDescription(),
                section.getTotalScore(),
                questionViews);
    }

    private Map<Long, List<AssignmentQuestionOptionEntity>> loadPaperOptions(List<AssignmentQuestionEntity> questions) {
        List<Long> questionIds =
                questions.stream().map(AssignmentQuestionEntity::getId).toList();
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return assignmentQuestionOptionMapper
                .selectList(Wrappers.<AssignmentQuestionOptionEntity>lambdaQuery()
                        .in(AssignmentQuestionOptionEntity::getAssignmentQuestionId, questionIds)
                        .orderByAsc(AssignmentQuestionOptionEntity::getAssignmentQuestionId)
                        .orderByAsc(AssignmentQuestionOptionEntity::getOptionOrder)
                        .orderByAsc(AssignmentQuestionOptionEntity::getId))
                .stream()
                .collect(Collectors.groupingBy(
                        AssignmentQuestionOptionEntity::getAssignmentQuestionId,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private void persistSectionQuestions(Long assignmentId, Long sectionId, List<ResolvedQuestion> resolvedQuestions) {
        int questionOrder = 1;
        for (ResolvedQuestion resolvedQuestion : resolvedQuestions) {
            AssignmentQuestionEntity entity = new AssignmentQuestionEntity();
            entity.setAssignmentId(assignmentId);
            entity.setAssignmentSectionId(sectionId);
            entity.setSourceQuestionId(resolvedQuestion.sourceQuestionId());
            entity.setQuestionOrder(questionOrder++);
            entity.setTitle(resolvedQuestion.title());
            entity.setPromptText(resolvedQuestion.prompt());
            entity.setQuestionType(resolvedQuestion.questionType().name());
            entity.setScore(resolvedQuestion.score());
            entity.setConfigJson(resolvedQuestion.configJson());
            assignmentQuestionMapper.insert(entity);
            int optionOrder = 1;
            for (ResolvedOption option : resolvedQuestion.options()) {
                AssignmentQuestionOptionEntity optionEntity = new AssignmentQuestionOptionEntity();
                optionEntity.setAssignmentQuestionId(entity.getId());
                optionEntity.setOptionOrder(optionOrder++);
                optionEntity.setOptionKey(option.optionKey());
                optionEntity.setContent(option.content());
                optionEntity.setIsCorrect(option.correct());
                assignmentQuestionOptionMapper.insert(optionEntity);
            }
        }
    }

    private ResolvedQuestion resolveQuestion(Long offeringId, AssignmentQuestionInput input) {
        if (input == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_QUESTION_INVALID", "题目不能为空");
        }
        if (input.bankQuestionId() != null) {
            return resolveBankQuestion(offeringId, input);
        }
        structuredQuestionSupport.validateQuestionDefinition(
                input.questionType(), input.score(), input.options(), input.config(), "ASSIGNMENT_QUESTION");
        return new ResolvedQuestion(
                null,
                structuredQuestionSupport.normalizeTitle(input.title(), "ASSIGNMENT_QUESTION_TITLE_REQUIRED"),
                structuredQuestionSupport.normalizePrompt(input.prompt(), "ASSIGNMENT_QUESTION_PROMPT_REQUIRED"),
                input.questionType(),
                structuredQuestionSupport.normalizeScore(input.score(), "ASSIGNMENT_QUESTION_SCORE_INVALID"),
                structuredQuestionSupport.writeConfigJson(input.config()),
                normalizeOptions(input.options()));
    }

    private ResolvedQuestion resolveBankQuestion(Long offeringId, AssignmentQuestionInput input) {
        if (StringUtils.hasText(input.title())
                || StringUtils.hasText(input.prompt())
                || input.questionType() != null
                || input.options() != null
                || input.config() != null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "ASSIGNMENT_QUESTION_BANK_REFERENCE_INVALID", "引用题库题目时仅允许覆盖分值");
        }
        QuestionBankQuestionEntity bankQuestion = questionBankQuestionMapper.selectById(input.bankQuestionId());
        if (bankQuestion == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "QUESTION_BANK_QUESTION_NOT_FOUND", "题库题目不存在");
        }
        if (!Objects.equals(offeringId, bankQuestion.getOfferingId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "QUESTION_BANK_SCOPE_INVALID", "题库题目不属于当前开课实例");
        }
        List<QuestionBankQuestionOptionEntity> bankOptions =
                questionBankQuestionOptionMapper.selectList(Wrappers.<QuestionBankQuestionOptionEntity>lambdaQuery()
                        .eq(QuestionBankQuestionOptionEntity::getQuestionId, bankQuestion.getId())
                        .orderByAsc(QuestionBankQuestionOptionEntity::getOptionOrder)
                        .orderByAsc(QuestionBankQuestionOptionEntity::getId));
        int score = input.score() == null
                ? bankQuestion.getDefaultScore()
                : structuredQuestionSupport.normalizeScore(input.score(), "ASSIGNMENT_QUESTION_SCORE_INVALID");
        return new ResolvedQuestion(
                bankQuestion.getId(),
                bankQuestion.getTitle(),
                bankQuestion.getPromptText(),
                AssignmentQuestionType.valueOf(bankQuestion.getQuestionType()),
                score,
                bankQuestion.getConfigJson(),
                bankOptions.stream()
                        .map(option ->
                                new ResolvedOption(option.getOptionKey(), option.getContent(), option.getIsCorrect()))
                        .toList());
    }

    private List<ResolvedOption> normalizeOptions(List<AssignmentQuestionOptionInput> options) {
        List<AssignmentQuestionOptionInput> safeOptions = options == null ? List.of() : options;
        return safeOptions.stream()
                .map(option -> new ResolvedOption(
                        option.optionKey().trim(), option.content().trim(), Boolean.TRUE.equals(option.correct())))
                .toList();
    }

    private record ResolvedQuestion(
            Long sourceQuestionId,
            String title,
            String prompt,
            AssignmentQuestionType questionType,
            Integer score,
            String configJson,
            List<ResolvedOption> options) {}

    private record ResolvedOption(String optionKey, String content, Boolean correct) {}
}
