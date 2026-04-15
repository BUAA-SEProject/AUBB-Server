package com.aubb.server.modules.assignment.application;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.assignment.domain.AssignmentStatus;
import com.aubb.server.modules.assignment.infrastructure.AssignmentEntity;
import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.CourseAuthorizationService;
import com.aubb.server.modules.course.infrastructure.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.CourseOfferingMapper;
import com.aubb.server.modules.course.infrastructure.TeachingClassEntity;
import com.aubb.server.modules.course.infrastructure.TeachingClassMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
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

    private final AssignmentMapper assignmentMapper;
    private final CourseOfferingMapper courseOfferingMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final AuditLogApplicationService auditLogApplicationService;

    @Transactional
    public AssignmentView createAssignment(
            Long offeringId,
            String title,
            String description,
            Long teachingClassId,
            OffsetDateTime openAt,
            OffsetDateTime dueAt,
            Integer maxSubmissions,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageAssignments(principal, offeringId);
        CourseOfferingEntity offering = requireOffering(offeringId);
        TeachingClassEntity teachingClass = validateTeachingClassBelongsToOffering(offeringId, teachingClassId);
        validateSchedule(openAt, dueAt);
        validateMaxSubmissions(maxSubmissions);

        AssignmentEntity entity = new AssignmentEntity();
        entity.setOfferingId(offeringId);
        entity.setTeachingClassId(teachingClassId);
        entity.setTitle(normalizeTitle(title));
        entity.setDescription(normalizeDescription(description));
        entity.setStatus(AssignmentStatus.DRAFT.name());
        entity.setOpenAt(openAt);
        entity.setDueAt(dueAt);
        entity.setMaxSubmissions(maxSubmissions);
        entity.setCreatedByUserId(principal.getUserId());
        assignmentMapper.insert(entity);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("offeringId", offeringId);
        metadata.put("teachingClassId", teachingClassId);
        metadata.put("title", entity.getTitle());

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.ASSIGNMENT_CREATED,
                "ASSIGNMENT",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                metadata);
        return toView(entity, offering, teachingClass);
    }

    @Transactional(readOnly = true)
    public PageResponse<AssignmentView> listTeacherAssignments(
            Long offeringId,
            AssignmentStatus status,
            Long teachingClassId,
            long page,
            long pageSize,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageAssignments(principal, offeringId);
        CourseOfferingEntity offering = requireOffering(offeringId);
        if (teachingClassId != null) {
            validateTeachingClassBelongsToOffering(offeringId, teachingClassId);
        }
        List<AssignmentEntity> matched = assignmentMapper
                .selectList(Wrappers.<AssignmentEntity>lambdaQuery()
                        .eq(AssignmentEntity::getOfferingId, offeringId)
                        .orderByDesc(AssignmentEntity::getCreatedAt)
                        .orderByDesc(AssignmentEntity::getId))
                .stream()
                .filter(assignment -> status == null || status.name().equals(assignment.getStatus()))
                .filter(assignment ->
                        teachingClassId == null || Objects.equals(teachingClassId, assignment.getTeachingClassId()))
                .toList();
        return toPage(matched, offering, page, pageSize);
    }

    @Transactional(readOnly = true)
    public AssignmentView getTeacherAssignment(Long assignmentId, AuthenticatedUserPrincipal principal) {
        AssignmentEntity entity = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanManageAssignments(principal, entity.getOfferingId());
        return toView(entity);
    }

    @Transactional
    public AssignmentView publishAssignment(Long assignmentId, AuthenticatedUserPrincipal principal) {
        AssignmentEntity entity = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanManageAssignments(principal, entity.getOfferingId());
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
        return toView(entity);
    }

    @Transactional
    public AssignmentView closeAssignment(Long assignmentId, AuthenticatedUserPrincipal principal) {
        AssignmentEntity entity = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanManageAssignments(principal, entity.getOfferingId());
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
        List<AssignmentEntity> matched = assignmentMapper
                .selectList(Wrappers.<AssignmentEntity>lambdaQuery()
                        .orderByAsc(AssignmentEntity::getOpenAt)
                        .orderByAsc(AssignmentEntity::getId))
                .stream()
                .filter(assignment -> offeringId == null || Objects.equals(offeringId, assignment.getOfferingId()))
                .filter(assignment -> !AssignmentStatus.DRAFT.name().equals(assignment.getStatus()))
                .filter(assignment -> courseAuthorizationService.canViewAssignment(
                        principal, assignment.getOfferingId(), assignment.getTeachingClassId()))
                .toList();
        return toPage(matched, loadOfferings(matched), page, pageSize);
    }

    @Transactional(readOnly = true)
    public AssignmentView getMyAssignment(Long assignmentId, AuthenticatedUserPrincipal principal) {
        AssignmentEntity entity = requireAssignment(assignmentId);
        if (AssignmentStatus.DRAFT.name().equals(entity.getStatus())
                || !courseAuthorizationService.canViewAssignment(
                        principal, entity.getOfferingId(), entity.getTeachingClassId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该任务");
        }
        return toView(entity);
    }

    private PageResponse<AssignmentView> toPage(
            List<AssignmentEntity> entities, CourseOfferingEntity offering, long page, long pageSize) {
        return toPage(
                entities,
                entities.stream().collect(Collectors.toMap(AssignmentEntity::getOfferingId, ignored -> offering)),
                page,
                pageSize);
    }

    private PageResponse<AssignmentView> toPage(
            List<AssignmentEntity> entities, Map<Long, CourseOfferingEntity> offerings, long page, long pageSize) {
        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePage - 1) * safePageSize;
        Map<Long, TeachingClassEntity> classIndex = loadTeachingClasses(entities);
        List<AssignmentView> items = entities.stream()
                .skip(offset)
                .limit(safePageSize)
                .map(assignment -> {
                    TeachingClassEntity teachingClass = assignment.getTeachingClassId() == null
                            ? null
                            : classIndex.get(assignment.getTeachingClassId());
                    return toView(assignment, offerings.get(assignment.getOfferingId()), teachingClass);
                })
                .toList();
        return new PageResponse<>(items, entities.size(), safePage, safePageSize);
    }

    private AssignmentView toView(AssignmentEntity entity) {
        CourseOfferingEntity offering = requireOffering(entity.getOfferingId());
        TeachingClassEntity teachingClass =
                entity.getTeachingClassId() == null ? null : requireTeachingClass(entity.getTeachingClassId());
        return toView(entity, offering, teachingClass);
    }

    private AssignmentView toView(
            AssignmentEntity entity, CourseOfferingEntity offering, TeachingClassEntity teachingClass) {
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
}
