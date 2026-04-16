package com.aubb.server.modules.assignment.application.bank;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionConfigInput;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionConfigView;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionOptionInput;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionOptionView;
import com.aubb.server.modules.assignment.application.paper.StructuredQuestionSupport;
import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import com.aubb.server.modules.assignment.infrastructure.bank.QuestionBankQuestionEntity;
import com.aubb.server.modules.assignment.infrastructure.bank.QuestionBankQuestionMapper;
import com.aubb.server.modules.assignment.infrastructure.bank.QuestionBankQuestionOptionEntity;
import com.aubb.server.modules.assignment.infrastructure.bank.QuestionBankQuestionOptionMapper;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.CourseAuthorizationService;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class QuestionBankApplicationService {

    private final QuestionBankQuestionMapper questionMapper;
    private final QuestionBankQuestionOptionMapper optionMapper;
    private final CourseOfferingMapper offeringMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final StructuredQuestionSupport structuredQuestionSupport;
    private final AuditLogApplicationService auditLogApplicationService;

    @Transactional
    public QuestionBankQuestionView createQuestion(
            Long offeringId,
            String title,
            String prompt,
            AssignmentQuestionType questionType,
            Integer defaultScore,
            List<AssignmentQuestionOptionInput> options,
            AssignmentQuestionConfigInput config,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageAssignments(principal, offeringId);
        requireOffering(offeringId);
        structuredQuestionSupport.validateQuestionDefinition(
                questionType, defaultScore, options, config, "QUESTION_BANK");

        QuestionBankQuestionEntity entity = new QuestionBankQuestionEntity();
        entity.setOfferingId(offeringId);
        entity.setCreatedByUserId(principal.getUserId());
        entity.setTitle(structuredQuestionSupport.normalizeTitle(title, "QUESTION_BANK_TITLE_REQUIRED"));
        entity.setPromptText(structuredQuestionSupport.normalizePrompt(prompt, "QUESTION_BANK_PROMPT_REQUIRED"));
        entity.setQuestionType(questionType.name());
        entity.setDefaultScore(structuredQuestionSupport.normalizeScore(defaultScore, "QUESTION_BANK_SCORE_INVALID"));
        entity.setConfigJson(structuredQuestionSupport.writeConfigJson(config));
        questionMapper.insert(entity);

        persistOptions(entity.getId(), options);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.QUESTION_BANK_QUESTION_CREATED,
                "QUESTION_BANK_QUESTION",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of("offeringId", offeringId, "questionType", questionType.name()));
        return toView(entity, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionBankQuestionView> listQuestions(
            Long offeringId,
            AssignmentQuestionType questionType,
            String keyword,
            long page,
            long pageSize,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageAssignments(principal, offeringId);
        requireOffering(offeringId);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<QuestionBankQuestionEntity> matched = questionMapper
                .selectList(Wrappers.<QuestionBankQuestionEntity>lambdaQuery()
                        .eq(QuestionBankQuestionEntity::getOfferingId, offeringId)
                        .eq(questionType != null, QuestionBankQuestionEntity::getQuestionType, questionType.name())
                        .orderByDesc(QuestionBankQuestionEntity::getCreatedAt)
                        .orderByDesc(QuestionBankQuestionEntity::getId))
                .stream()
                .filter(question -> normalizedKeyword == null
                        || question.getTitle().contains(normalizedKeyword)
                        || question.getPromptText().contains(normalizedKeyword))
                .toList();
        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePage - 1) * safePageSize;
        List<QuestionBankQuestionView> items = matched.stream()
                .skip(offset)
                .limit(safePageSize)
                .map(question -> toView(question, true))
                .toList();
        return new PageResponse<>(items, matched.size(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public QuestionBankQuestionView getQuestion(Long questionId, AuthenticatedUserPrincipal principal) {
        QuestionBankQuestionEntity entity = requireQuestion(questionId);
        courseAuthorizationService.assertCanManageAssignments(principal, entity.getOfferingId());
        return toView(entity, true);
    }

    @Transactional(readOnly = true)
    public QuestionBankQuestionEntity requireQuestion(Long questionId) {
        QuestionBankQuestionEntity entity = questionMapper.selectById(questionId);
        if (entity == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "QUESTION_BANK_QUESTION_NOT_FOUND", "题库题目不存在");
        }
        return entity;
    }

    @Transactional(readOnly = true)
    public List<QuestionBankQuestionOptionEntity> listOptions(Long questionId) {
        return optionMapper.selectList(Wrappers.<QuestionBankQuestionOptionEntity>lambdaQuery()
                .eq(QuestionBankQuestionOptionEntity::getQuestionId, questionId)
                .orderByAsc(QuestionBankQuestionOptionEntity::getOptionOrder)
                .orderByAsc(QuestionBankQuestionOptionEntity::getId));
    }

    private QuestionBankQuestionView toView(QuestionBankQuestionEntity entity, boolean revealCorrect) {
        List<QuestionBankQuestionOptionEntity> optionEntities = listOptions(entity.getId());
        List<AssignmentQuestionOptionView> options =
                structuredQuestionSupport.toOptionViewsFromBank(optionEntities, revealCorrect);
        AssignmentQuestionConfigView configView = structuredQuestionSupport.toConfigView(
                structuredQuestionSupport.readConfigInput(entity.getConfigJson()), revealCorrect);
        return new QuestionBankQuestionView(
                entity.getId(),
                entity.getOfferingId(),
                entity.getTitle(),
                entity.getPromptText(),
                AssignmentQuestionType.valueOf(entity.getQuestionType()),
                entity.getDefaultScore(),
                options,
                configView,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private void persistOptions(Long questionId, List<AssignmentQuestionOptionInput> options) {
        List<AssignmentQuestionOptionInput> safeOptions = options == null ? List.of() : options;
        int optionOrder = 1;
        for (AssignmentQuestionOptionInput option : safeOptions) {
            QuestionBankQuestionOptionEntity entity = new QuestionBankQuestionOptionEntity();
            entity.setQuestionId(questionId);
            entity.setOptionOrder(optionOrder++);
            entity.setOptionKey(option.optionKey().trim());
            entity.setContent(option.content().trim());
            entity.setIsCorrect(Boolean.TRUE.equals(option.correct()));
            optionMapper.insert(entity);
        }
    }

    private CourseOfferingEntity requireOffering(Long offeringId) {
        CourseOfferingEntity offering = offeringMapper.selectById(offeringId);
        if (offering == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COURSE_OFFERING_NOT_FOUND", "开课实例不存在");
        }
        return offering;
    }
}
