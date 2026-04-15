package com.aubb.server.modules.submission.application;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.assignment.domain.AssignmentStatus;
import com.aubb.server.modules.assignment.infrastructure.AssignmentEntity;
import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.CourseAuthorizationService;
import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.submission.domain.SubmissionStatus;
import com.aubb.server.modules.submission.infrastructure.SubmissionEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubmissionApplicationService {

    private final SubmissionMapper submissionMapper;
    private final AssignmentMapper assignmentMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final AuditLogApplicationService auditLogApplicationService;

    @Transactional
    public SubmissionView createSubmission(
            Long assignmentId, String contentText, AuthenticatedUserPrincipal principal) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        assertCanSubmit(principal, assignment);
        OffsetDateTime now = OffsetDateTime.now();
        validateSubmissionWindow(assignment, now);
        String normalizedContent = normalizeContentText(contentText);

        int nextAttempt = submissionMapper
                        .selectCount(Wrappers.<SubmissionEntity>lambdaQuery()
                                .eq(SubmissionEntity::getAssignmentId, assignmentId)
                                .eq(SubmissionEntity::getSubmitterUserId, principal.getUserId()))
                        .intValue()
                + 1;
        if (nextAttempt > assignment.getMaxSubmissions()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_LIMIT_REACHED", "已达到当前作业最大提交次数");
        }

        SubmissionEntity entity = new SubmissionEntity();
        entity.setSubmissionNo(generateSubmissionNo());
        entity.setAssignmentId(assignmentId);
        entity.setOfferingId(assignment.getOfferingId());
        entity.setTeachingClassId(assignment.getTeachingClassId());
        entity.setSubmitterUserId(principal.getUserId());
        entity.setAttemptNo(nextAttempt);
        entity.setStatus(SubmissionStatus.SUBMITTED.name());
        entity.setContentText(normalizedContent);
        entity.setSubmittedAt(now);
        submissionMapper.insert(entity);

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.SUBMISSION_CREATED,
                "SUBMISSION",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of(
                        "assignmentId", assignmentId,
                        "offeringId", assignment.getOfferingId(),
                        "attemptNo", nextAttempt));
        return toView(entity, assignment);
    }

    @Transactional(readOnly = true)
    public PageResponse<SubmissionView> listMySubmissions(
            Long assignmentId, long page, long pageSize, AuthenticatedUserPrincipal principal) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        if (!courseAuthorizationService.canViewAssignment(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该作业提交");
        }
        List<SubmissionEntity> matched = submissionMapper.selectList(Wrappers.<SubmissionEntity>lambdaQuery()
                .eq(SubmissionEntity::getAssignmentId, assignmentId)
                .eq(SubmissionEntity::getSubmitterUserId, principal.getUserId())
                .orderByDesc(SubmissionEntity::getSubmittedAt)
                .orderByDesc(SubmissionEntity::getId));
        return toPage(matched, assignment, page, pageSize);
    }

    @Transactional(readOnly = true)
    public SubmissionView getMySubmission(Long submissionId, AuthenticatedUserPrincipal principal) {
        SubmissionEntity submission = requireSubmission(submissionId);
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        if (!Objects.equals(submission.getSubmitterUserId(), principal.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该提交");
        }
        if (!courseAuthorizationService.canViewAssignment(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该提交");
        }
        return toView(submission, assignment);
    }

    @Transactional(readOnly = true)
    public PageResponse<SubmissionView> listTeacherSubmissions(
            Long assignmentId,
            Long submitterUserId,
            boolean latestOnly,
            long page,
            long pageSize,
            AuthenticatedUserPrincipal principal) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanManageAssignments(principal, assignment.getOfferingId());
        List<SubmissionEntity> matched = submissionMapper.selectList(Wrappers.<SubmissionEntity>lambdaQuery()
                .eq(SubmissionEntity::getAssignmentId, assignmentId)
                .eq(submitterUserId != null, SubmissionEntity::getSubmitterUserId, submitterUserId)
                .orderByDesc(SubmissionEntity::getSubmittedAt)
                .orderByDesc(SubmissionEntity::getId));
        if (latestOnly) {
            matched = loadLatestSubmissions(matched);
        }
        return toPage(matched, assignment, page, pageSize);
    }

    @Transactional(readOnly = true)
    public SubmissionView getTeacherSubmission(Long submissionId, AuthenticatedUserPrincipal principal) {
        SubmissionEntity submission = requireSubmission(submissionId);
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        courseAuthorizationService.assertCanManageAssignments(principal, assignment.getOfferingId());
        return toView(submission, assignment);
    }

    private PageResponse<SubmissionView> toPage(
            List<SubmissionEntity> entities, AssignmentEntity assignment, long page, long pageSize) {
        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePage - 1) * safePageSize;
        List<SubmissionView> items = entities.stream()
                .skip(offset)
                .limit(safePageSize)
                .map(entity -> toView(entity, assignment))
                .toList();
        return new PageResponse<>(items, entities.size(), safePage, safePageSize);
    }

    private SubmissionView toView(SubmissionEntity entity, AssignmentEntity assignment) {
        return new SubmissionView(
                entity.getId(),
                entity.getSubmissionNo(),
                assignment.getId(),
                assignment.getTitle(),
                entity.getOfferingId(),
                entity.getTeachingClassId(),
                entity.getSubmitterUserId(),
                entity.getAttemptNo(),
                SubmissionStatus.valueOf(entity.getStatus()),
                entity.getContentText(),
                entity.getSubmittedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private List<SubmissionEntity> loadLatestSubmissions(List<SubmissionEntity> entities) {
        return new LinkedHashMap<Long, SubmissionEntity>() {
            {
                entities.forEach(entity -> putIfAbsent(entity.getSubmitterUserId(), entity));
            }
        }.values().stream().toList();
    }

    private AssignmentEntity requireAssignment(Long assignmentId) {
        AssignmentEntity assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "任务不存在");
        }
        return assignment;
    }

    private SubmissionEntity requireSubmission(Long submissionId) {
        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "提交不存在");
        }
        return submission;
    }

    private void assertCanSubmit(AuthenticatedUserPrincipal principal, AssignmentEntity assignment) {
        if (!AssignmentStatus.PUBLISHED.name().equals(assignment.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ASSIGNMENT_UNAVAILABLE", "当前作业暂不允许提交");
        }
        if (!courseAuthorizationService.hasActiveMemberRole(
                principal.getUserId(),
                assignment.getOfferingId(),
                assignment.getTeachingClassId(),
                CourseMemberRole.STUDENT)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权提交该作业");
        }
    }

    private void validateSubmissionWindow(AssignmentEntity assignment, OffsetDateTime now) {
        if (now.isBefore(assignment.getOpenAt()) || now.isAfter(assignment.getDueAt())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_WINDOW_INVALID", "当前不在作业开放提交时间内");
        }
    }

    private String normalizeContentText(String contentText) {
        if (contentText == null || contentText.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_CONTENT_REQUIRED", "提交内容不能为空");
        }
        String normalized = contentText.trim();
        if (normalized.length() > 20000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_CONTENT_TOO_LONG", "提交内容长度不能超过 20000");
        }
        return normalized;
    }

    private String generateSubmissionNo() {
        return "SUB-" + UUID.randomUUID().toString().replace("-", "");
    }
}
