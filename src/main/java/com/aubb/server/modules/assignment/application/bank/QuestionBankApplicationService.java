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
import com.aubb.server.modules.assignment.infrastructure.bank.QuestionBankQuestionTagEntity;
import com.aubb.server.modules.assignment.infrastructure.bank.QuestionBankQuestionTagMapper;
import com.aubb.server.modules.assignment.infrastructure.bank.QuestionBankTagEntity;
import com.aubb.server.modules.assignment.infrastructure.bank.QuestionBankTagMapper;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.CourseAuthorizationService;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class QuestionBankApplicationService {

    private final QuestionBankQuestionMapper questionMapper;
    private final QuestionBankQuestionOptionMapper optionMapper;
    private final QuestionBankTagMapper tagMapper;
    private final QuestionBankQuestionTagMapper questionTagMapper;
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
            List<String> tags,
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
        replaceTags(offeringId, entity.getId(), tags, principal.getUserId());
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
            List<String> tags,
            boolean includeArchived,
            long page,
            long pageSize,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageAssignments(principal, offeringId);
        requireOffering(offeringId);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<String> normalizedTags = normalizeTags(tags);
        String questionTypeCode = questionType == null ? null : questionType.name();
        List<Long> matchedQuestionIdsByTags = resolveQuestionIdsByTags(offeringId, normalizedTags);
        if (!normalizedTags.isEmpty() && matchedQuestionIdsByTags.isEmpty()) {
            return new PageResponse<>(List.of(), 0, Math.max(page, 1), Math.max(pageSize, 1));
        }
        List<QuestionBankQuestionEntity> matched =
                questionMapper.selectList(Wrappers.<QuestionBankQuestionEntity>lambdaQuery()
                        .eq(QuestionBankQuestionEntity::getOfferingId, offeringId)
                        .eq(questionTypeCode != null, QuestionBankQuestionEntity::getQuestionType, questionTypeCode)
                        .and(normalizedKeyword != null, query -> query.like(
                                        QuestionBankQuestionEntity::getTitle, normalizedKeyword)
                                .or()
                                .like(QuestionBankQuestionEntity::getPromptText, normalizedKeyword))
                        .in(!normalizedTags.isEmpty(), QuestionBankQuestionEntity::getId, matchedQuestionIdsByTags)
                        .isNull(!includeArchived, QuestionBankQuestionEntity::getArchivedAt)
                        .orderByDesc(QuestionBankQuestionEntity::getCreatedAt)
                        .orderByDesc(QuestionBankQuestionEntity::getId));
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

    @Transactional
    public QuestionBankQuestionView updateQuestion(
            Long questionId,
            String title,
            String prompt,
            AssignmentQuestionType questionType,
            Integer defaultScore,
            List<AssignmentQuestionOptionInput> options,
            List<String> tags,
            AssignmentQuestionConfigInput config,
            AuthenticatedUserPrincipal principal) {
        QuestionBankQuestionEntity entity = requireQuestion(questionId);
        courseAuthorizationService.assertCanManageAssignments(principal, entity.getOfferingId());
        assertActiveQuestion(entity, "QUESTION_BANK_QUESTION_ARCHIVED", "已归档题目不能再编辑");
        structuredQuestionSupport.validateQuestionDefinition(
                questionType, defaultScore, options, config, "QUESTION_BANK");

        entity.setTitle(structuredQuestionSupport.normalizeTitle(title, "QUESTION_BANK_TITLE_REQUIRED"));
        entity.setPromptText(structuredQuestionSupport.normalizePrompt(prompt, "QUESTION_BANK_PROMPT_REQUIRED"));
        entity.setQuestionType(questionType.name());
        entity.setDefaultScore(structuredQuestionSupport.normalizeScore(defaultScore, "QUESTION_BANK_SCORE_INVALID"));
        entity.setConfigJson(structuredQuestionSupport.writeConfigJson(config));
        questionMapper.updateById(entity);

        replaceOptions(entity.getId(), options);
        replaceTags(entity.getOfferingId(), entity.getId(), tags, principal.getUserId());
        QuestionBankQuestionEntity refreshed = requireQuestion(questionId);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.QUESTION_BANK_QUESTION_UPDATED,
                "QUESTION_BANK_QUESTION",
                String.valueOf(questionId),
                AuditResult.SUCCESS,
                Map.of("offeringId", entity.getOfferingId(), "questionType", questionType.name()));
        return toView(refreshed, true);
    }

    @Transactional
    public QuestionBankQuestionView archiveQuestion(Long questionId, AuthenticatedUserPrincipal principal) {
        QuestionBankQuestionEntity entity = requireQuestion(questionId);
        courseAuthorizationService.assertCanManageAssignments(principal, entity.getOfferingId());
        if (entity.getArchivedAt() == null) {
            entity.setArchivedByUserId(principal.getUserId());
            entity.setArchivedAt(OffsetDateTime.now());
            questionMapper.updateById(entity);
            auditLogApplicationService.record(
                    principal.getUserId(),
                    AuditAction.QUESTION_BANK_QUESTION_ARCHIVED,
                    "QUESTION_BANK_QUESTION",
                    String.valueOf(questionId),
                    AuditResult.SUCCESS,
                    Map.of("offeringId", entity.getOfferingId(), "questionType", entity.getQuestionType()));
        }
        return toView(requireQuestion(questionId), true);
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
        List<String> tags = listTags(entity.getId());
        return new QuestionBankQuestionView(
                entity.getId(),
                entity.getOfferingId(),
                entity.getTitle(),
                entity.getPromptText(),
                AssignmentQuestionType.valueOf(entity.getQuestionType()),
                entity.getDefaultScore(),
                options,
                configView,
                tags,
                entity.getArchivedAt() != null,
                entity.getArchivedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private void replaceOptions(Long questionId, List<AssignmentQuestionOptionInput> options) {
        optionMapper.delete(Wrappers.<QuestionBankQuestionOptionEntity>lambdaQuery()
                .eq(QuestionBankQuestionOptionEntity::getQuestionId, questionId));
        persistOptions(questionId, options);
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

    private void replaceTags(Long offeringId, Long questionId, List<String> tags, Long userId) {
        questionTagMapper.delete(Wrappers.<QuestionBankQuestionTagEntity>lambdaQuery()
                .eq(QuestionBankQuestionTagEntity::getQuestionId, questionId));
        List<String> normalizedTags = normalizeTags(tags);
        if (normalizedTags.isEmpty()) {
            return;
        }
        Map<String, QuestionBankTagEntity> tagMap = loadOrCreateTags(offeringId, normalizedTags, userId);
        for (String tagName : normalizedTags) {
            QuestionBankTagEntity tag = tagMap.get(tagName);
            if (tag == null) {
                continue;
            }
            QuestionBankQuestionTagEntity relation = new QuestionBankQuestionTagEntity();
            relation.setQuestionId(questionId);
            relation.setTagId(tag.getId());
            questionTagMapper.insert(relation);
        }
    }

    private Map<String, QuestionBankTagEntity> loadOrCreateTags(
            Long offeringId, List<String> normalizedTags, Long userId) {
        Map<String, QuestionBankTagEntity> existing = tagMapper
                .selectList(Wrappers.<QuestionBankTagEntity>lambdaQuery()
                        .eq(QuestionBankTagEntity::getOfferingId, offeringId)
                        .in(QuestionBankTagEntity::getTagName, normalizedTags))
                .stream()
                .collect(Collectors.toMap(QuestionBankTagEntity::getTagName, tag -> tag));
        for (String tagName : normalizedTags) {
            if (existing.containsKey(tagName)) {
                continue;
            }
            QuestionBankTagEntity entity = new QuestionBankTagEntity();
            entity.setOfferingId(offeringId);
            entity.setTagName(tagName);
            entity.setCreatedByUserId(userId);
            try {
                tagMapper.insert(entity);
                existing.put(tagName, entity);
            } catch (DuplicateKeyException exception) {
                QuestionBankTagEntity concurrentTag = tagMapper.selectOne(Wrappers.<QuestionBankTagEntity>lambdaQuery()
                        .eq(QuestionBankTagEntity::getOfferingId, offeringId)
                        .eq(QuestionBankTagEntity::getTagName, tagName)
                        .last("LIMIT 1"));
                if (concurrentTag != null) {
                    existing.put(tagName, concurrentTag);
                }
            }
        }
        return existing;
    }

    @Transactional(readOnly = true)
    public List<String> listTags(Long questionId) {
        List<QuestionBankQuestionTagEntity> relations =
                questionTagMapper.selectList(Wrappers.<QuestionBankQuestionTagEntity>lambdaQuery()
                        .eq(QuestionBankQuestionTagEntity::getQuestionId, questionId)
                        .orderByAsc(QuestionBankQuestionTagEntity::getId));
        if (relations.isEmpty()) {
            return List.of();
        }
        Map<Long, String> tagNames = tagMapper
                .selectByIds(relations.stream()
                        .map(QuestionBankQuestionTagEntity::getTagId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(QuestionBankTagEntity::getId, QuestionBankTagEntity::getTagName));
        return relations.stream()
                .map(QuestionBankQuestionTagEntity::getTagId)
                .map(tagNames::get)
                .filter(StringUtils::hasText)
                .sorted()
                .toList();
    }

    private List<Long> resolveQuestionIdsByTags(Long offeringId, List<String> normalizedTags) {
        if (normalizedTags.isEmpty()) {
            return List.of();
        }
        Map<Long, String> tagNamesById = tagMapper
                .selectList(Wrappers.<QuestionBankTagEntity>lambdaQuery()
                        .eq(QuestionBankTagEntity::getOfferingId, offeringId)
                        .in(QuestionBankTagEntity::getTagName, normalizedTags))
                .stream()
                .collect(Collectors.toMap(QuestionBankTagEntity::getId, QuestionBankTagEntity::getTagName));
        if (tagNamesById.size() < normalizedTags.size()) {
            return List.of();
        }
        Set<Long> requestedTagIds = tagNamesById.keySet();
        return questionTagMapper
                .selectList(Wrappers.<QuestionBankQuestionTagEntity>lambdaQuery()
                        .in(QuestionBankQuestionTagEntity::getTagId, requestedTagIds))
                .stream()
                .collect(Collectors.groupingBy(
                        QuestionBankQuestionTagEntity::getQuestionId,
                        Collectors.mapping(QuestionBankQuestionTagEntity::getTagId, Collectors.toSet())))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().containsAll(requestedTagIds))
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<String> normalizeTags(List<String> tags) {
        return tags == null
                ? List.of()
                : tags.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .map(tag -> tag.toLowerCase(Locale.ROOT))
                        .distinct()
                        .sorted()
                        .toList();
    }

    private CourseOfferingEntity requireOffering(Long offeringId) {
        CourseOfferingEntity offering = offeringMapper.selectById(offeringId);
        if (offering == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COURSE_OFFERING_NOT_FOUND", "开课实例不存在");
        }
        return offering;
    }

    private void assertActiveQuestion(QuestionBankQuestionEntity entity, String code, String message) {
        if (entity.getArchivedAt() != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, code, message);
        }
    }
}
