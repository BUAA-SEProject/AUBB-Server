package com.aubb.server.modules.assignment.application;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.assignment.application.judge.AssignmentJudgeCaseInput;
import com.aubb.server.modules.assignment.application.judge.AssignmentJudgeConfigInput;
import com.aubb.server.modules.assignment.application.judge.AssignmentJudgeConfigView;
import com.aubb.server.modules.assignment.application.paper.AssignmentPaperApplicationService;
import com.aubb.server.modules.assignment.application.paper.AssignmentPaperInput;
import com.aubb.server.modules.assignment.application.paper.AssignmentPaperView;
import com.aubb.server.modules.assignment.domain.AssignmentStatus;
import com.aubb.server.modules.assignment.domain.judge.AssignmentJudgeLanguage;
import com.aubb.server.modules.assignment.infrastructure.AssignmentEntity;
import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.assignment.infrastructure.judge.AssignmentJudgeCaseEntity;
import com.aubb.server.modules.assignment.infrastructure.judge.AssignmentJudgeCaseMapper;
import com.aubb.server.modules.assignment.infrastructure.judge.AssignmentJudgeProfileEntity;
import com.aubb.server.modules.assignment.infrastructure.judge.AssignmentJudgeProfileMapper;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.CourseAuthorizationService;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingMapper;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassEntity;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.notification.application.NotificationDispatchService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssignmentApplicationService {

    private static final String JUDGE_SOURCE_TYPE = "TEXT_BODY";
    private static final String JUDGE_ENTRY_FILE_NAME = "main.py";
    private static final int MAX_JUDGE_CASES = 20;
    private static final int DEFAULT_GRADE_WEIGHT = 100;
    private static final int MAX_GRADE_WEIGHT = 1000;

    private final AssignmentMapper assignmentMapper;
    private final AssignmentJudgeProfileMapper assignmentJudgeProfileMapper;
    private final AssignmentJudgeCaseMapper assignmentJudgeCaseMapper;
    private final CourseOfferingMapper courseOfferingMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final AssignmentPaperApplicationService assignmentPaperApplicationService;
    private final AuditLogApplicationService auditLogApplicationService;
    private final NotificationDispatchService notificationDispatchService;

    @Transactional
    public AssignmentView createAssignment(
            Long offeringId,
            String title,
            String description,
            Long teachingClassId,
            OffsetDateTime openAt,
            OffsetDateTime dueAt,
            Integer maxSubmissions,
            Integer gradeWeight,
            AssignmentPaperInput paper,
            AssignmentJudgeConfigInput judgeConfig,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanCreateAssignment(principal, offeringId, teachingClassId);
        CourseOfferingEntity offering = requireOffering(offeringId);
        TeachingClassEntity teachingClass = validateTeachingClassBelongsToOffering(offeringId, teachingClassId);
        validateSchedule(openAt, dueAt);
        validateMaxSubmissions(maxSubmissions);
        validateAssignmentMode(paper, judgeConfig);

        AssignmentEntity entity = new AssignmentEntity();
        entity.setOfferingId(offeringId);
        entity.setTeachingClassId(teachingClassId);
        entity.setTitle(normalizeTitle(title));
        entity.setDescription(normalizeDescription(description));
        entity.setStatus(AssignmentStatus.DRAFT.name());
        entity.setOpenAt(openAt);
        entity.setDueAt(dueAt);
        entity.setMaxSubmissions(maxSubmissions);
        entity.setGradeWeight(normalizeGradeWeight(gradeWeight));
        entity.setCreatedByUserId(principal.getUserId());
        assignmentMapper.insert(entity);
        assignmentPaperApplicationService.persistPaper(entity.getId(), offeringId, paper);
        persistJudgeConfig(entity.getId(), judgeConfig);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("offeringId", offeringId);
        metadata.put("teachingClassId", teachingClassId);
        metadata.put("title", entity.getTitle());
        metadata.put("judgeEnabled", judgeConfig != null);
        metadata.put("structuredPaperEnabled", paper != null);
        metadata.put("gradeWeight", entity.getGradeWeight());

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.ASSIGNMENT_CREATED,
                "ASSIGNMENT",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                metadata);
        return toView(
                entity,
                offering,
                teachingClass,
                assignmentPaperApplicationService.loadPaper(entity.getId(), true),
                loadJudgeConfigView(entity.getId()));
    }

    @Transactional
    public AssignmentView updateAssignment(
            Long assignmentId,
            String title,
            String description,
            Long teachingClassId,
            OffsetDateTime openAt,
            OffsetDateTime dueAt,
            Integer maxSubmissions,
            Integer gradeWeight,
            AssignmentPaperInput paper,
            AssignmentJudgeConfigInput judgeConfig,
            AuthenticatedUserPrincipal principal) {
        AssignmentEntity entity = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanUpdateAssignment(
                principal, entity.getOfferingId(), entity.getTeachingClassId());
        if (!AssignmentStatus.DRAFT.name().equals(entity.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_STATUS_INVALID", "只有草稿任务可以编辑");
        }
        TeachingClassEntity teachingClass =
                validateTeachingClassBelongsToOffering(entity.getOfferingId(), teachingClassId);
        validateSchedule(openAt, dueAt);
        validateMaxSubmissions(maxSubmissions);
        validateAssignmentMode(paper, judgeConfig);

        entity.setTeachingClassId(teachingClassId);
        entity.setTitle(normalizeTitle(title));
        entity.setDescription(normalizeDescription(description));
        entity.setOpenAt(openAt);
        entity.setDueAt(dueAt);
        entity.setMaxSubmissions(maxSubmissions);
        entity.setGradeWeight(normalizeGradeWeight(gradeWeight));
        assignmentMapper.updateById(entity);
        assignmentPaperApplicationService.replacePaper(entity.getId(), entity.getOfferingId(), paper);
        replaceJudgeConfig(entity.getId(), judgeConfig);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("offeringId", entity.getOfferingId());
        metadata.put("teachingClassId", teachingClassId);
        metadata.put("title", entity.getTitle());
        metadata.put("judgeEnabled", judgeConfig != null);
        metadata.put("structuredPaperEnabled", paper != null);
        metadata.put("gradeWeight", entity.getGradeWeight());

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.ASSIGNMENT_UPDATED,
                "ASSIGNMENT",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                metadata);
        return toView(
                entity,
                requireOffering(entity.getOfferingId()),
                teachingClass,
                assignmentPaperApplicationService.loadPaper(entity.getId(), true),
                loadJudgeConfigView(entity.getId()));
    }

    @Transactional
    public AssignmentView replaceAssignmentPaper(
            Long assignmentId, AssignmentPaperInput paper, AuthenticatedUserPrincipal principal) {
        AssignmentEntity entity = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanUpdateAssignment(
                principal, entity.getOfferingId(), entity.getTeachingClassId());
        if (!AssignmentStatus.DRAFT.name().equals(entity.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_STATUS_INVALID", "只有草稿任务可以编辑");
        }
        if (hasAssignmentJudgeConfig(entity.getId())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "ASSIGNMENT_MODE_CONFLICT", "结构化作业暂不支持 assignment 级自动评测配置，请二选一");
        }
        assignmentPaperApplicationService.replacePaper(entity.getId(), entity.getOfferingId(), paper);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.ASSIGNMENT_UPDATED,
                "ASSIGNMENT",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of("offeringId", entity.getOfferingId(), "updateScope", "paper"));
        return toView(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<AssignmentView> listTeacherAssignments(
            Long offeringId,
            AssignmentStatus status,
            Long teachingClassId,
            long page,
            long pageSize,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanReadAssignment(principal, offeringId, teachingClassId);
        CourseOfferingEntity offering = requireOffering(offeringId);
        if (teachingClassId != null) {
            validateTeachingClassBelongsToOffering(offeringId, teachingClassId);
        }
        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePage - 1) * safePageSize;
        String statusCode = status == null ? null : status.name();
        long total = assignmentMapper.selectCount(Wrappers.<AssignmentEntity>lambdaQuery()
                .eq(AssignmentEntity::getOfferingId, offeringId)
                .eq(statusCode != null, AssignmentEntity::getStatus, statusCode)
                .eq(teachingClassId != null, AssignmentEntity::getTeachingClassId, teachingClassId));
        if (total == 0) {
            return new PageResponse<>(List.of(), 0, safePage, safePageSize);
        }
        List<AssignmentEntity> pageItems = assignmentMapper.selectList(Wrappers.<AssignmentEntity>lambdaQuery()
                .eq(AssignmentEntity::getOfferingId, offeringId)
                .eq(statusCode != null, AssignmentEntity::getStatus, statusCode)
                .eq(teachingClassId != null, AssignmentEntity::getTeachingClassId, teachingClassId)
                .orderByDesc(AssignmentEntity::getCreatedAt)
                .orderByDesc(AssignmentEntity::getId)
                .last("LIMIT " + safePageSize + " OFFSET " + offset));
        return toCurrentSlicePage(pageItems, Map.of(offering.getId(), offering), total, safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public AssignmentView getTeacherAssignment(Long assignmentId, AuthenticatedUserPrincipal principal) {
        AssignmentEntity entity = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanReadAssignment(
                principal, entity.getOfferingId(), entity.getTeachingClassId());
        return toView(entity);
    }

    @Transactional
    public AssignmentView publishAssignment(Long assignmentId, AuthenticatedUserPrincipal principal) {
        AssignmentEntity entity = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanPublishAssignment(
                principal, entity.getOfferingId(), entity.getTeachingClassId());
        if (!AssignmentStatus.DRAFT.name().equals(entity.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_STATUS_INVALID", "只有草稿任务可以发布");
        }
        entity.setStatus(AssignmentStatus.PUBLISHED.name());
        entity.setPublishedAt(OffsetDateTime.now());
        assignmentMapper.updateById(entity);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.ASSIGNMENT_PUBLISHED,
                "ASSIGNMENT",
                String.valueOf(assignmentId),
                AuditResult.SUCCESS,
                Map.of("offeringId", entity.getOfferingId()));
        notificationDispatchService.notifyAssignmentPublished(entity, principal.getUserId());
        return toView(entity);
    }

    @Transactional
    public AssignmentView closeAssignment(Long assignmentId, AuthenticatedUserPrincipal principal) {
        AssignmentEntity entity = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanCloseAssignment(
                principal, entity.getOfferingId(), entity.getTeachingClassId());
        if (AssignmentStatus.CLOSED.name().equals(entity.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_STATUS_INVALID", "任务已关闭");
        }
        if (AssignmentStatus.DRAFT.name().equals(entity.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_STATUS_INVALID", "草稿任务不能直接关闭");
        }
        entity.setStatus(AssignmentStatus.CLOSED.name());
        entity.setClosedAt(OffsetDateTime.now());
        assignmentMapper.updateById(entity);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.ASSIGNMENT_CLOSED,
                "ASSIGNMENT",
                String.valueOf(assignmentId),
                AuditResult.SUCCESS,
                Map.of("offeringId", entity.getOfferingId()));
        return toView(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<AssignmentView> listMyAssignments(
            Long offeringId, long page, long pageSize, AuthenticatedUserPrincipal principal) {
        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePage - 1) * safePageSize;
        List<Long> fullOfferingIds =
                courseAuthorizationService.loadFullAssignmentAccessOfferingIds(principal, offeringId).stream()
                        .toList();
        long total =
                assignmentMapper.countVisibleAssignmentsForUser(principal.getUserId(), offeringId, fullOfferingIds);
        if (total == 0) {
            return new PageResponse<>(List.of(), 0, safePage, safePageSize);
        }
        List<AssignmentEntity> pagedAssignments = assignmentMapper.selectVisibleAssignmentsForUserPage(
                principal.getUserId(), offeringId, fullOfferingIds, offset, safePageSize);
        return toCurrentSlicePage(pagedAssignments, loadOfferings(pagedAssignments), total, safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public AssignmentView getMyAssignment(Long assignmentId, AuthenticatedUserPrincipal principal) {
        AssignmentEntity entity = requireAssignment(assignmentId);
        if (AssignmentStatus.DRAFT.name().equals(entity.getStatus())
                || !courseAuthorizationService.canViewAssignment(
                        principal, entity.getOfferingId(), entity.getTeachingClassId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该任务");
        }
        CourseOfferingEntity offering = requireOffering(entity.getOfferingId());
        TeachingClassEntity teachingClass =
                entity.getTeachingClassId() == null ? null : requireTeachingClass(entity.getTeachingClassId());
        return toView(
                entity,
                offering,
                teachingClass,
                assignmentPaperApplicationService.loadPaper(entity.getId(), false),
                loadJudgeConfigView(entity.getId()));
    }

    private PageResponse<AssignmentView> toPage(
            List<AssignmentEntity> entities, CourseOfferingEntity offering, long page, long pageSize) {
        Map<Long, CourseOfferingEntity> offeringIndex = entities.stream()
                .collect(Collectors.toMap(
                        AssignmentEntity::getOfferingId,
                        ignored -> offering,
                        (left, right) -> left,
                        LinkedHashMap::new));
        return toPage(entities, offeringIndex, page, pageSize);
    }

    private PageResponse<AssignmentView> toPage(
            List<AssignmentEntity> entities, Map<Long, CourseOfferingEntity> offerings, long page, long pageSize) {
        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePage - 1) * safePageSize;
        Map<Long, TeachingClassEntity> classIndex = loadTeachingClasses(entities);
        Map<Long, AssignmentJudgeConfigView> judgeConfigIndex = loadJudgeConfigViews(entities);
        List<AssignmentView> items = entities.stream()
                .skip(offset)
                .limit(safePageSize)
                .map(assignment -> {
                    TeachingClassEntity teachingClass = assignment.getTeachingClassId() == null
                            ? null
                            : classIndex.get(assignment.getTeachingClassId());
                    return toView(
                            assignment,
                            offerings.get(assignment.getOfferingId()),
                            teachingClass,
                            null,
                            judgeConfigIndex.get(assignment.getId()));
                })
                .toList();
        return new PageResponse<>(items, entities.size(), safePage, safePageSize);
    }

    private PageResponse<AssignmentView> toCurrentSlicePage(
            List<AssignmentEntity> entities,
            Map<Long, CourseOfferingEntity> offerings,
            long total,
            long page,
            long pageSize) {
        Map<Long, TeachingClassEntity> classIndex = loadTeachingClasses(entities);
        Map<Long, AssignmentJudgeConfigView> judgeConfigIndex = loadJudgeConfigViews(entities);
        List<AssignmentView> items = entities.stream()
                .map(assignment -> {
                    TeachingClassEntity teachingClass = assignment.getTeachingClassId() == null
                            ? null
                            : classIndex.get(assignment.getTeachingClassId());
                    return toView(
                            assignment,
                            offerings.get(assignment.getOfferingId()),
                            teachingClass,
                            null,
                            judgeConfigIndex.get(assignment.getId()));
                })
                .toList();
        return new PageResponse<>(items, total, page, pageSize);
    }

    private AssignmentView toView(AssignmentEntity entity) {
        CourseOfferingEntity offering = requireOffering(entity.getOfferingId());
        TeachingClassEntity teachingClass =
                entity.getTeachingClassId() == null ? null : requireTeachingClass(entity.getTeachingClassId());
        return toView(
                entity,
                offering,
                teachingClass,
                assignmentPaperApplicationService.loadPaper(entity.getId(), true),
                loadJudgeConfigView(entity.getId()));
    }

    private AssignmentView toView(
            AssignmentEntity entity,
            CourseOfferingEntity offering,
            TeachingClassEntity teachingClass,
            AssignmentPaperView paper,
            AssignmentJudgeConfigView judgeConfigView) {
        AssignmentClassView classView = teachingClass == null
                ? null
                : new AssignmentClassView(
                        teachingClass.getId(), teachingClass.getClassCode(), teachingClass.getClassName());
        return new AssignmentView(
                entity.getId(),
                offering.getId(),
                offering.getOfferingCode(),
                offering.getOfferingName(),
                classView,
                entity.getTitle(),
                entity.getDescription(),
                AssignmentStatus.valueOf(entity.getStatus()),
                entity.getOpenAt(),
                entity.getDueAt(),
                entity.getMaxSubmissions(),
                entity.getGradeWeight(),
                paper,
                judgeConfigView == null
                        ? new AssignmentJudgeConfigView(false, null, null, null, null, 0)
                        : judgeConfigView,
                entity.getPublishedAt(),
                entity.getClosedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private AssignmentEntity requireAssignment(Long assignmentId) {
        AssignmentEntity entity = assignmentMapper.selectById(assignmentId);
        if (entity == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "任务不存在");
        }
        return entity;
    }

    private CourseOfferingEntity requireOffering(Long offeringId) {
        CourseOfferingEntity offering = courseOfferingMapper.selectById(offeringId);
        if (offering == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COURSE_OFFERING_NOT_FOUND", "开课实例不存在");
        }
        return offering;
    }

    private TeachingClassEntity requireTeachingClass(Long teachingClassId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(teachingClassId);
        if (teachingClass == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "TEACHING_CLASS_NOT_FOUND", "教学班不存在");
        }
        return teachingClass;
    }

    private TeachingClassEntity validateTeachingClassBelongsToOffering(Long offeringId, Long teachingClassId) {
        if (teachingClassId == null) {
            return null;
        }
        TeachingClassEntity teachingClass = requireTeachingClass(teachingClassId);
        if (!Objects.equals(offeringId, teachingClass.getOfferingId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_CLASS_SCOPE_INVALID", "教学班不属于当前开课实例");
        }
        return teachingClass;
    }

    private void validateSchedule(OffsetDateTime openAt, OffsetDateTime dueAt) {
        if (openAt == null || dueAt == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_SCHEDULE_REQUIRED", "开放和截止时间不能为空");
        }
        if (dueAt.isBefore(openAt)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_SCHEDULE_INVALID", "截止时间不能早于开放时间");
        }
    }

    private void validateMaxSubmissions(Integer maxSubmissions) {
        if (maxSubmissions == null || maxSubmissions <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_MAX_SUBMISSIONS_INVALID", "最大提交次数必须大于 0");
        }
    }

    private Integer normalizeGradeWeight(Integer gradeWeight) {
        int normalized = gradeWeight == null ? DEFAULT_GRADE_WEIGHT : gradeWeight;
        if (normalized <= 0 || normalized > MAX_GRADE_WEIGHT) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "ASSIGNMENT_GRADE_WEIGHT_INVALID",
                    "成绩权重必须在 1 到 %s 之间".formatted(MAX_GRADE_WEIGHT));
        }
        return normalized;
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_TITLE_REQUIRED", "任务标题不能为空");
        }
        String normalized = title.trim();
        if (normalized.length() > 128) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_TITLE_TOO_LONG", "任务标题长度不能超过 128");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private void validateAssignmentMode(AssignmentPaperInput paper, AssignmentJudgeConfigInput judgeConfig) {
        if (paper != null && judgeConfig != null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "ASSIGNMENT_MODE_CONFLICT", "结构化作业暂不支持 assignment 级自动评测配置，请二选一");
        }
    }

    private boolean hasAssignmentJudgeConfig(Long assignmentId) {
        return assignmentJudgeProfileMapper.selectById(assignmentId) != null;
    }

    private void persistJudgeConfig(Long assignmentId, AssignmentJudgeConfigInput judgeConfig) {
        if (judgeConfig == null) {
            return;
        }
        validateJudgeConfig(judgeConfig);
        AssignmentJudgeProfileEntity profile = new AssignmentJudgeProfileEntity();
        profile.setAssignmentId(assignmentId);
        profile.setSourceType(JUDGE_SOURCE_TYPE);
        profile.setLanguage(judgeConfig.language().name());
        profile.setEntryFileName(JUDGE_ENTRY_FILE_NAME);
        profile.setTimeLimitMs(judgeConfig.timeLimitMs());
        profile.setMemoryLimitMb(judgeConfig.memoryLimitMb());
        profile.setOutputLimitKb(judgeConfig.outputLimitKb());
        assignmentJudgeProfileMapper.insert(profile);

        int caseOrder = 1;
        for (AssignmentJudgeCaseInput testCase : judgeConfig.testCases()) {
            AssignmentJudgeCaseEntity entity = new AssignmentJudgeCaseEntity();
            entity.setAssignmentId(assignmentId);
            entity.setCaseOrder(caseOrder++);
            entity.setStdinText(normalizeJudgeText(testCase.stdinText()));
            entity.setExpectedStdout(normalizeJudgeText(testCase.expectedStdout()));
            entity.setScore(testCase.score());
            assignmentJudgeCaseMapper.insert(entity);
        }
    }

    private void replaceJudgeConfig(Long assignmentId, AssignmentJudgeConfigInput judgeConfig) {
        assignmentJudgeProfileMapper.deleteById(assignmentId);
        persistJudgeConfig(assignmentId, judgeConfig);
    }

    private void validateJudgeConfig(AssignmentJudgeConfigInput judgeConfig) {
        if (judgeConfig.language() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_JUDGE_LANGUAGE_REQUIRED", "自动评测语言不能为空");
        }
        if (!AssignmentJudgeLanguage.PYTHON3.equals(judgeConfig.language())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "ASSIGNMENT_JUDGE_LANGUAGE_UNSUPPORTED", "当前仅支持 PYTHON3");
        }
        if (judgeConfig.timeLimitMs() == null || judgeConfig.timeLimitMs() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_JUDGE_TIME_LIMIT_INVALID", "时间限制必须大于 0");
        }
        if (judgeConfig.memoryLimitMb() == null || judgeConfig.memoryLimitMb() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_JUDGE_MEMORY_LIMIT_INVALID", "内存限制必须大于 0");
        }
        if (judgeConfig.outputLimitKb() == null || judgeConfig.outputLimitKb() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_JUDGE_OUTPUT_LIMIT_INVALID", "输出限制必须大于 0");
        }
        if (judgeConfig.testCases() == null || judgeConfig.testCases().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_JUDGE_CASES_REQUIRED", "自动评测至少需要一个测试用例");
        }
        if (judgeConfig.testCases().size() > MAX_JUDGE_CASES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_JUDGE_CASES_TOO_MANY", "自动评测用例数量过多");
        }
        int totalScore = 0;
        for (AssignmentJudgeCaseInput testCase : judgeConfig.testCases()) {
            if (testCase == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_JUDGE_CASE_INVALID", "自动评测用例不能为空");
            }
            if (testCase.stdinText() == null || testCase.expectedStdout() == null) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "ASSIGNMENT_JUDGE_CASE_TEXT_REQUIRED", "测试用例输入和输出不能为空");
            }
            if (testCase.score() == null || testCase.score() < 0) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "ASSIGNMENT_JUDGE_CASE_SCORE_INVALID", "测试用例分值不能小于 0");
            }
            totalScore += testCase.score();
        }
        if (totalScore <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_JUDGE_SCORE_INVALID", "自动评测总分必须大于 0");
        }
    }

    private String normalizeJudgeText(String value) {
        return value.replace("\r\n", "\n");
    }

    private Map<Long, TeachingClassEntity> loadTeachingClasses(List<AssignmentEntity> entities) {
        List<Long> classIds = entities.stream()
                .map(AssignmentEntity::getTeachingClassId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (classIds.isEmpty()) {
            return Map.of();
        }
        return teachingClassMapper.selectByIds(classIds).stream()
                .collect(Collectors.toMap(
                        TeachingClassEntity::getId,
                        teachingClass -> teachingClass,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private Map<Long, CourseOfferingEntity> loadOfferings(List<AssignmentEntity> entities) {
        List<Long> offeringIds = entities.stream()
                .map(AssignmentEntity::getOfferingId)
                .distinct()
                .toList();
        if (offeringIds.isEmpty()) {
            return Map.of();
        }
        return courseOfferingMapper.selectByIds(offeringIds).stream()
                .collect(Collectors.toMap(
                        CourseOfferingEntity::getId, offering -> offering, (left, right) -> left, LinkedHashMap::new));
    }

    private AssignmentJudgeConfigView loadJudgeConfigView(Long assignmentId) {
        return loadJudgeConfigViews(List.of(requireAssignment(assignmentId))).get(assignmentId);
    }

    private Map<Long, AssignmentJudgeConfigView> loadJudgeConfigViews(Collection<AssignmentEntity> entities) {
        List<Long> assignmentIds =
                entities.stream().map(AssignmentEntity::getId).distinct().toList();
        if (assignmentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AssignmentJudgeProfileEntity> profiles =
                assignmentJudgeProfileMapper.selectByIds(assignmentIds).stream()
                        .collect(Collectors.toMap(
                                AssignmentJudgeProfileEntity::getAssignmentId,
                                profile -> profile,
                                (left, right) -> left,
                                LinkedHashMap::new));
        if (profiles.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> caseCounts = assignmentJudgeCaseMapper
                .selectList(Wrappers.<AssignmentJudgeCaseEntity>lambdaQuery()
                        .in(AssignmentJudgeCaseEntity::getAssignmentId, profiles.keySet()))
                .stream()
                .collect(Collectors.groupingBy(
                        AssignmentJudgeCaseEntity::getAssignmentId, LinkedHashMap::new, Collectors.counting()));
        Map<Long, AssignmentJudgeConfigView> result = new LinkedHashMap<>();
        for (AssignmentJudgeProfileEntity profile : profiles.values()) {
            result.put(
                    profile.getAssignmentId(),
                    new AssignmentJudgeConfigView(
                            true,
                            AssignmentJudgeLanguage.valueOf(profile.getLanguage()),
                            profile.getTimeLimitMs(),
                            profile.getMemoryLimitMb(),
                            profile.getOutputLimitKb(),
                            Math.toIntExact(caseCounts.getOrDefault(profile.getAssignmentId(), 0L))));
        }
        return result;
    }
}
