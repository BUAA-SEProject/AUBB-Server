package com.aubb.server.modules.notification.application;

import com.aubb.server.common.cache.CacheService;
import com.aubb.server.modules.assignment.infrastructure.AssignmentEntity;
import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.course.domain.member.CourseMemberStatus;
import com.aubb.server.modules.course.infrastructure.announcement.CourseAnnouncementEntity;
import com.aubb.server.modules.course.infrastructure.discussion.CourseDiscussionEntity;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberEntity;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.aubb.server.modules.course.infrastructure.resource.CourseResourceEntity;
import com.aubb.server.modules.grading.domain.appeal.GradeAppealStatus;
import com.aubb.server.modules.grading.infrastructure.appeal.GradeAppealEntity;
import com.aubb.server.modules.judge.domain.JudgeJobStatus;
import com.aubb.server.modules.judge.infrastructure.JudgeJobEntity;
import com.aubb.server.modules.lab.infrastructure.LabEntity;
import com.aubb.server.modules.lab.infrastructure.LabReportEntity;
import com.aubb.server.modules.notification.domain.NotificationType;
import com.aubb.server.modules.notification.infrastructure.NotificationEntity;
import com.aubb.server.modules.notification.infrastructure.NotificationMapper;
import com.aubb.server.modules.notification.infrastructure.NotificationReceiptEntity;
import com.aubb.server.modules.notification.infrastructure.NotificationReceiptMapper;
import com.aubb.server.modules.submission.infrastructure.SubmissionEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_BODY_LENGTH = 500;

    private final NotificationMapper notificationMapper;
    private final NotificationReceiptMapper notificationReceiptMapper;
    private final CourseMemberMapper courseMemberMapper;
    private final SubmissionMapper submissionMapper;
    private final AssignmentMapper assignmentMapper;
    private final CacheService cacheService;
    private final NotificationRealtimeService notificationRealtimeService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public void notifyAssignmentPublished(AssignmentEntity assignment, Long actorUserId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("assignmentId", assignment.getId());
        if (assignment.getDueAt() != null) {
            metadata.put("dueAt", assignment.getDueAt().toString());
        }
        enqueueNotification(
                NotificationType.ASSIGNMENT_PUBLISHED,
                "新作业已发布：" + assignment.getTitle(),
                assignment.getDueAt() == null ? "课程已发布新作业，请及时查看并完成提交。" : "课程已发布新作业，请在截止时间前完成提交。",
                actorUserId,
                "ASSIGNMENT",
                String.valueOf(assignment.getId()),
                assignment.getOfferingId(),
                assignment.getTeachingClassId(),
                metadata,
                loadActiveStudentRecipients(assignment.getOfferingId(), assignment.getTeachingClassId()));
    }

    @Transactional
    public void notifyCourseAnnouncementPublished(CourseAnnouncementEntity announcement, Long actorUserId) {
        if (announcement == null) {
            return;
        }
        enqueueNotification(
                NotificationType.COURSE_ANNOUNCEMENT_PUBLISHED,
                "课程公告已发布：" + announcement.getTitle(),
                "教师发布了新的课程公告，请及时查看。",
                actorUserId,
                "COURSE_ANNOUNCEMENT",
                String.valueOf(announcement.getId()),
                announcement.getOfferingId(),
                announcement.getTeachingClassId(),
                Map.of("announcementId", announcement.getId()),
                loadActiveStudentRecipients(announcement.getOfferingId(), announcement.getTeachingClassId()));
    }

    @Transactional
    public void notifyCourseResourcePublished(CourseResourceEntity resource, Long actorUserId) {
        if (resource == null) {
            return;
        }
        enqueueNotification(
                NotificationType.COURSE_RESOURCE_PUBLISHED,
                "课程资源已上传：" + resource.getTitle(),
                "教师上传了新的课程资源，现在可以下载查看。",
                actorUserId,
                "COURSE_RESOURCE",
                String.valueOf(resource.getId()),
                resource.getOfferingId(),
                resource.getTeachingClassId(),
                Map.of("resourceId", resource.getId()),
                loadActiveStudentRecipients(resource.getOfferingId(), resource.getTeachingClassId()));
    }

    @Transactional
    public void notifyCourseDiscussionUpdated(
            CourseDiscussionEntity discussion, Long actorUserId, boolean notifyStudents) {
        if (discussion == null) {
            return;
        }
        enqueueNotification(
                NotificationType.COURSE_DISCUSSION_UPDATED,
                "课程讨论有新动态：" + discussion.getTitle(),
                notifyStudents ? "教师更新了课程讨论，请及时查看。" : "有学生发起或回复了课程讨论，请及时查看。",
                actorUserId,
                "COURSE_DISCUSSION",
                String.valueOf(discussion.getId()),
                discussion.getOfferingId(),
                discussion.getTeachingClassId(),
                Map.of("discussionId", discussion.getId()),
                notifyStudents
                        ? loadActiveStudentRecipients(discussion.getOfferingId(), discussion.getTeachingClassId())
                        : loadTeachingRecipients(discussion.getOfferingId(), discussion.getTeachingClassId()));
    }

    @Transactional
    public void notifyAssignmentGradesPublished(AssignmentEntity assignment, Long actorUserId) {
        enqueueNotification(
                NotificationType.ASSIGNMENT_GRADES_PUBLISHED,
                "成绩已发布：" + assignment.getTitle(),
                "该作业的成绩与反馈已发布，现在可以查看。",
                actorUserId,
                "ASSIGNMENT",
                String.valueOf(assignment.getId()),
                assignment.getOfferingId(),
                assignment.getTeachingClassId(),
                Map.of("assignmentId", assignment.getId()),
                loadSubmittedStudentRecipients(assignment.getId()));
    }

    @Transactional
    public void notifyGradeAppealResolved(GradeAppealEntity appeal, AssignmentEntity assignment, Long actorUserId) {
        if (appeal == null || assignment == null) {
            return;
        }
        GradeAppealStatus status = GradeAppealStatus.valueOf(appeal.getStatus());
        if (status != GradeAppealStatus.ACCEPTED && status != GradeAppealStatus.REJECTED) {
            return;
        }
        String body = status == GradeAppealStatus.ACCEPTED ? "你的成绩申诉已通过，最新成绩已更新。" : "你的成绩申诉已处理，当前成绩维持不变。";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("appealId", appeal.getId());
        metadata.put("assignmentId", assignment.getId());
        metadata.put("submissionId", appeal.getSubmissionId());
        metadata.put("submissionAnswerId", appeal.getSubmissionAnswerId());
        metadata.put("status", status.name());
        if (appeal.getResolvedScore() != null) {
            metadata.put("resolvedScore", appeal.getResolvedScore());
        }
        enqueueNotification(
                NotificationType.GRADE_APPEAL_RESOLVED,
                "成绩申诉已处理：" + assignment.getTitle(),
                body,
                actorUserId,
                "GRADE_APPEAL",
                String.valueOf(appeal.getId()),
                appeal.getOfferingId(),
                appeal.getTeachingClassId(),
                metadata,
                List.of(appeal.getStudentUserId()));
    }

    @Transactional
    public void notifyLabPublished(LabEntity lab, Long actorUserId) {
        enqueueNotification(
                NotificationType.LAB_PUBLISHED,
                "新实验已发布：" + lab.getTitle(),
                "实验已发布，请按要求完成实验并提交报告。",
                actorUserId,
                "LAB",
                String.valueOf(lab.getId()),
                lab.getOfferingId(),
                lab.getTeachingClassId(),
                Map.of("labId", lab.getId()),
                loadActiveStudentRecipients(lab.getOfferingId(), lab.getTeachingClassId()));
    }

    @Transactional
    public void notifyLabReportSubmitted(LabReportEntity report, LabEntity lab, Long actorUserId) {
        if (report == null || lab == null) {
            return;
        }
        enqueueNotification(
                NotificationType.LAB_REPORT_SUBMITTED,
                "实验报告待评阅：" + lab.getTitle(),
                "有学生提交了新的实验报告，请及时评阅。",
                actorUserId,
                "LAB_REPORT",
                String.valueOf(report.getId()),
                report.getOfferingId(),
                report.getTeachingClassId(),
                Map.of(
                        "labId", report.getLabId(),
                        "labReportId", report.getId(),
                        "studentUserId", report.getStudentUserId()),
                loadTeachingRecipients(report.getOfferingId(), report.getTeachingClassId()));
    }

    @Transactional
    public void notifyLabReportPublished(LabReportEntity report, LabEntity lab, Long actorUserId) {
        if (report == null || lab == null) {
            return;
        }
        enqueueNotification(
                NotificationType.LAB_REPORT_PUBLISHED,
                "实验报告评语已发布：" + lab.getTitle(),
                "教师已发布你的实验报告批注与评语，现在可以查看。",
                actorUserId,
                "LAB_REPORT",
                String.valueOf(report.getId()),
                report.getOfferingId(),
                report.getTeachingClassId(),
                Map.of(
                        "labId", report.getLabId(),
                        "labReportId", report.getId()),
                List.of(report.getStudentUserId()));
    }

    @Transactional
    public void notifyJudgeCompleted(JudgeJobEntity job) {
        if (job == null) {
            return;
        }
        Long recipientUserId = job.getSubmitterUserId() != null ? job.getSubmitterUserId() : job.getRequestedByUserId();
        if (recipientUserId == null) {
            return;
        }
        AssignmentEntity assignment =
                job.getAssignmentId() == null ? null : assignmentMapper.selectById(job.getAssignmentId());
        String assignmentTitle = assignment == null ? null : assignment.getTitle();
        boolean failed = JudgeJobStatus.FAILED.name().equals(job.getStatus());
        String title = StringUtils.hasText(assignmentTitle) ? "评测已完成：" + assignmentTitle : "评测已完成";
        String body;
        if (failed) {
            body = StringUtils.hasText(job.getErrorMessage())
                    ? "你的评测任务已结束，但执行失败：" + job.getErrorMessage()
                    : "你的评测任务已结束，但执行失败，请稍后查看详情。";
        } else if (StringUtils.hasText(job.getVerdict())) {
            body = "你的评测任务已完成，结果：" + job.getVerdict() + "。";
        } else {
            body = "你的评测任务已完成，请查看最新结果。";
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("judgeJobId", job.getId());
        metadata.put("assignmentId", job.getAssignmentId());
        metadata.put("submissionId", job.getSubmissionId());
        metadata.put("submissionAnswerId", job.getSubmissionAnswerId());
        metadata.put("status", job.getStatus());
        metadata.put("verdict", job.getVerdict());
        enqueueNotification(
                NotificationType.JUDGE_COMPLETED,
                title,
                body,
                null,
                "JUDGE_JOB",
                String.valueOf(job.getId()),
                job.getOfferingId(),
                job.getTeachingClassId(),
                metadata,
                List.of(recipientUserId));
    }

    void persistFanout(NotificationFanoutCommand command) {
        if (command == null) {
            return;
        }
        Set<Long> recipients = command.recipientUserIds() == null
                ? Set.of()
                : command.recipientUserIds().stream()
                        .filter(Objects::nonNull)
                        .filter(candidate -> !Objects.equals(candidate, command.actorUserId()))
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (recipients.isEmpty()) {
            return;
        }

        NotificationEntity notification = new NotificationEntity();
        notification.setType(command.type().name());
        notification.setTitle(truncate(command.title(), MAX_TITLE_LENGTH));
        notification.setBody(truncate(command.body(), MAX_BODY_LENGTH));
        notification.setActorUserId(command.actorUserId());
        notification.setTargetType(command.targetType());
        notification.setTargetId(command.targetId());
        notification.setOfferingId(command.offeringId());
        notification.setTeachingClassId(command.teachingClassId());
        notification.setMetadata(new LinkedHashMap<>(sanitizeMetadata(command.metadata())));
        notificationMapper.insert(notification);

        OffsetDateTime createdAt =
                notification.getCreatedAt() == null ? OffsetDateTime.now() : notification.getCreatedAt();
        Map<Long, NotificationView> viewsByRecipient = new LinkedHashMap<>();
        for (Long recipientUserId : recipients) {
            NotificationReceiptEntity receipt = new NotificationReceiptEntity();
            receipt.setNotificationId(notification.getId());
            receipt.setRecipientUserId(recipientUserId);
            receipt.setReadAt(null);
            receipt.setCreatedAt(createdAt);
            receipt.setUpdatedAt(createdAt);
            notificationReceiptMapper.insert(receipt);
            cacheService.evict(
                    NotificationApplicationService.UNREAD_COUNT_CACHE_NAME,
                    NotificationApplicationService.unreadCountCacheKey(recipientUserId));
            viewsByRecipient.put(recipientUserId, toView(notification, receipt));
        }
        notificationRealtimeService.publish(viewsByRecipient);
    }

    private void enqueueNotification(
            NotificationType type,
            String title,
            String body,
            Long actorUserId,
            String targetType,
            String targetId,
            Long offeringId,
            Long teachingClassId,
            Map<String, Object> metadata,
            Collection<Long> recipientUserIds) {
        applicationEventPublisher.publishEvent(new NotificationFanoutRequestedEvent(new NotificationFanoutCommand(
                type,
                title,
                body,
                actorUserId,
                targetType,
                targetId,
                offeringId,
                teachingClassId,
                sanitizeMetadata(metadata),
                sanitizeRecipients(recipientUserIds))));
    }

    private List<Long> loadActiveStudentRecipients(Long offeringId, Long teachingClassId) {
        return courseMemberMapper
                .selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .eq(teachingClassId != null, CourseMemberEntity::getTeachingClassId, teachingClassId)
                        .eq(CourseMemberEntity::getMemberRole, CourseMemberRole.STUDENT.name())
                        .eq(CourseMemberEntity::getMemberStatus, CourseMemberStatus.ACTIVE.name())
                        .select(CourseMemberEntity::getUserId))
                .stream()
                .map(CourseMemberEntity::getUserId)
                .distinct()
                .toList();
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key != null && value != null) {
                sanitized.put(key, value);
            }
        });
        return sanitized.isEmpty() ? Map.of() : Collections.unmodifiableMap(sanitized);
    }

    private List<Long> sanitizeRecipients(Collection<Long> recipientUserIds) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return List.of();
        }
        return recipientUserIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private List<Long> loadSubmittedStudentRecipients(Long assignmentId) {
        return submissionMapper
                .selectList(Wrappers.<SubmissionEntity>lambdaQuery()
                        .eq(SubmissionEntity::getAssignmentId, assignmentId)
                        .select(SubmissionEntity::getSubmitterUserId))
                .stream()
                .map(SubmissionEntity::getSubmitterUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Long> loadTeachingRecipients(Long offeringId, Long teachingClassId) {
        Set<Long> recipients = new LinkedHashSet<>(courseMemberMapper
                .selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .eq(CourseMemberEntity::getMemberRole, CourseMemberRole.INSTRUCTOR.name())
                        .eq(CourseMemberEntity::getMemberStatus, CourseMemberStatus.ACTIVE.name())
                        .select(CourseMemberEntity::getUserId))
                .stream()
                .map(CourseMemberEntity::getUserId)
                .toList());
        if (teachingClassId != null) {
            recipients.addAll(courseMemberMapper
                    .selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                            .eq(CourseMemberEntity::getOfferingId, offeringId)
                            .eq(CourseMemberEntity::getTeachingClassId, teachingClassId)
                            .eq(CourseMemberEntity::getMemberRole, CourseMemberRole.TA.name())
                            .eq(CourseMemberEntity::getMemberStatus, CourseMemberStatus.ACTIVE.name())
                            .select(CourseMemberEntity::getUserId))
                    .stream()
                    .map(CourseMemberEntity::getUserId)
                    .toList());
        }
        return List.copyOf(recipients);
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private NotificationView toView(NotificationEntity notification, NotificationReceiptEntity receipt) {
        return new NotificationView(
                notification.getId(),
                NotificationType.valueOf(notification.getType()),
                notification.getTitle(),
                notification.getBody(),
                notification.getActorUserId(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getOfferingId(),
                notification.getTeachingClassId(),
                notification.getMetadata(),
                receipt.getReadAt() != null,
                receipt.getReadAt(),
                notification.getCreatedAt());
    }
}
