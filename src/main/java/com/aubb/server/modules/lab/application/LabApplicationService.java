package com.aubb.server.modules.lab.application;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.common.storage.ObjectStorageException;
import com.aubb.server.common.storage.ObjectStorageService;
import com.aubb.server.common.storage.StoredObject;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.CourseAuthorizationService;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassEntity;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserEntity;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserMapper;
import com.aubb.server.modules.lab.domain.LabReportLifecyclePolicy;
import com.aubb.server.modules.lab.domain.LabReportStatus;
import com.aubb.server.modules.lab.domain.LabStatus;
import com.aubb.server.modules.lab.infrastructure.LabEntity;
import com.aubb.server.modules.lab.infrastructure.LabMapper;
import com.aubb.server.modules.lab.infrastructure.LabReportAttachmentEntity;
import com.aubb.server.modules.lab.infrastructure.LabReportAttachmentMapper;
import com.aubb.server.modules.lab.infrastructure.LabReportEntity;
import com.aubb.server.modules.lab.infrastructure.LabReportMapper;
import com.aubb.server.modules.notification.application.NotificationDispatchService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LabApplicationService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_REPORT_LENGTH = 20_000;
    private static final int MAX_REVIEW_TEXT_LENGTH = 5_000;
    private static final long MAX_ATTACHMENT_SIZE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_ATTACHMENTS_PER_REPORT = 10;
    private static final int MAX_STORAGE_FILENAME_LENGTH = 80;
    private static final String OBJECT_KEY_PREFIX = "lab-report-attachments";

    private final LabMapper labMapper;
    private final LabReportMapper labReportMapper;
    private final LabReportAttachmentMapper labReportAttachmentMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final UserMapper userMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final AuditLogApplicationService auditLogApplicationService;
    private final ObjectProvider<ObjectStorageService> objectStorageServiceProvider;
    private final NotificationDispatchService notificationDispatchService;
    private final LabReportLifecyclePolicy labReportLifecyclePolicy = new LabReportLifecyclePolicy();

    @Transactional
    public LabView createLab(
            Long offeringId,
            Long teachingClassId,
            String title,
            String description,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageLabs(principal, offeringId, teachingClassId);
        TeachingClassEntity teachingClass =
                courseAuthorizationService.requireTeachingClassInOffering(offeringId, teachingClassId);

        LabEntity entity = new LabEntity();
        entity.setOfferingId(offeringId);
        entity.setTeachingClassId(teachingClassId);
        entity.setTitle(normalizeTitle(title));
        entity.setDescription(normalizeDescription(description));
        entity.setStatus(LabStatus.DRAFT.name());
        entity.setCreatedByUserId(principal.getUserId());
        labMapper.insert(entity);

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.LAB_CREATED,
                "LAB",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of("offeringId", offeringId, "teachingClassId", teachingClassId));
        return toLabView(entity, teachingClass);
    }

    @Transactional
    public LabView updateLab(Long labId, String title, String description, AuthenticatedUserPrincipal principal) {
        LabEntity entity = requireLab(labId);
        courseAuthorizationService.assertCanManageLabs(principal, entity.getOfferingId(), entity.getTeachingClassId());
        if (!LabStatus.DRAFT.name().equals(entity.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_STATUS_INVALID", "只有草稿实验可以编辑");
        }
        entity.setTitle(normalizeTitle(title));
        entity.setDescription(normalizeDescription(description));
        labMapper.updateById(entity);

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.LAB_UPDATED,
                "LAB",
                String.valueOf(labId),
                AuditResult.SUCCESS,
                Map.of("offeringId", entity.getOfferingId(), "teachingClassId", entity.getTeachingClassId()));
        return toLabView(entity, requireTeachingClass(entity.getTeachingClassId()));
    }

    @Transactional
    public LabView publishLab(Long labId, AuthenticatedUserPrincipal principal) {
        LabEntity entity = requireLab(labId);
        courseAuthorizationService.assertCanManageLabs(principal, entity.getOfferingId(), entity.getTeachingClassId());
        if (!LabStatus.DRAFT.name().equals(entity.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_STATUS_INVALID", "只有草稿实验可以发布");
        }
        entity.setStatus(LabStatus.PUBLISHED.name());
        entity.setPublishedAt(OffsetDateTime.now());
        labMapper.updateById(entity);

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.LAB_PUBLISHED,
                "LAB",
                String.valueOf(labId),
                AuditResult.SUCCESS,
                Map.of("offeringId", entity.getOfferingId(), "teachingClassId", entity.getTeachingClassId()));
        notificationDispatchService.notifyLabPublished(entity, principal.getUserId());
        return toLabView(entity, requireTeachingClass(entity.getTeachingClassId()));
    }

    @Transactional
    public LabView closeLab(Long labId, AuthenticatedUserPrincipal principal) {
        LabEntity entity = requireLab(labId);
        courseAuthorizationService.assertCanManageLabs(principal, entity.getOfferingId(), entity.getTeachingClassId());
        if (LabStatus.CLOSED.name().equals(entity.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_STATUS_INVALID", "实验已关闭");
        }
        if (LabStatus.DRAFT.name().equals(entity.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_STATUS_INVALID", "草稿实验不能直接关闭");
        }
        entity.setStatus(LabStatus.CLOSED.name());
        entity.setClosedAt(OffsetDateTime.now());
        labMapper.updateById(entity);

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.LAB_CLOSED,
                "LAB",
                String.valueOf(labId),
                AuditResult.SUCCESS,
                Map.of("offeringId", entity.getOfferingId(), "teachingClassId", entity.getTeachingClassId()));
        return toLabView(entity, requireTeachingClass(entity.getTeachingClassId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<LabView> listTeacherLabs(
            Long offeringId,
            Long teachingClassId,
            LabStatus status,
            long page,
            long pageSize,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageOffering(principal, offeringId);
        if (teachingClassId != null) {
            courseAuthorizationService.assertCanManageLabs(principal, offeringId, teachingClassId);
        }
        List<LabEntity> matched = labMapper
                .selectList(Wrappers.<LabEntity>lambdaQuery()
                        .eq(LabEntity::getOfferingId, offeringId)
                        .eq(teachingClassId != null, LabEntity::getTeachingClassId, teachingClassId)
                        .orderByDesc(LabEntity::getCreatedAt)
                        .orderByDesc(LabEntity::getId))
                .stream()
                .filter(lab -> status == null || status.name().equals(lab.getStatus()))
                .filter(this::isLabFeatureEnabled)
                .toList();
        return toLabPage(matched, page, pageSize);
    }

    @Transactional(readOnly = true)
    public LabView getTeacherLab(Long labId, AuthenticatedUserPrincipal principal) {
        LabEntity entity = requireLab(labId);
        courseAuthorizationService.assertCanManageLabs(principal, entity.getOfferingId(), entity.getTeachingClassId());
        return toLabView(entity, requireTeachingClass(entity.getTeachingClassId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<LabView> listMyLabs(
            Long teachingClassId, long page, long pageSize, AuthenticatedUserPrincipal principal) {
        TeachingClassEntity teachingClass = requireTeachingClass(teachingClassId);
        courseAuthorizationService.assertCanViewLab(principal, teachingClass.getOfferingId(), teachingClassId);
        List<LabEntity> matched = labMapper
                .selectList(Wrappers.<LabEntity>lambdaQuery()
                        .eq(LabEntity::getTeachingClassId, teachingClassId)
                        .orderByDesc(LabEntity::getPublishedAt)
                        .orderByDesc(LabEntity::getCreatedAt)
                        .orderByDesc(LabEntity::getId))
                .stream()
                .filter(lab -> !LabStatus.DRAFT.name().equals(lab.getStatus()))
                .toList();
        return toLabPage(matched, page, pageSize);
    }

    @Transactional(readOnly = true)
    public LabView getMyLab(Long labId, AuthenticatedUserPrincipal principal) {
        LabEntity entity = requireLab(labId);
        if (LabStatus.DRAFT.name().equals(entity.getStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该实验");
        }
        courseAuthorizationService.assertCanViewLab(principal, entity.getOfferingId(), entity.getTeachingClassId());
        return toLabView(entity, requireTeachingClass(entity.getTeachingClassId()));
    }

    @Transactional
    public LabReportAttachmentView uploadAttachment(
            Long labId, MultipartFile file, AuthenticatedUserPrincipal principal) {
        LabEntity lab = requirePublishedLabForStudentMutation(labId, principal);
        assertPendingAttachmentCapacityAvailable(labId, principal.getUserId());
        byte[] content = readAttachmentContent(file);
        String originalFilename = normalizeOriginalFilename(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        String objectKey = generateAttachmentObjectKey(labId, principal.getUserId(), originalFilename);

        ObjectStorageService storageService = requireStorageService();
        try {
            storageService.putObject(objectKey, content, contentType);
        } catch (ObjectStorageException exception) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE, "LAB_REPORT_STORAGE_WRITE_FAILED", "实验报告附件暂时不可写入");
        }

        LabReportAttachmentEntity attachment = new LabReportAttachmentEntity();
        attachment.setLabId(labId);
        attachment.setOfferingId(lab.getOfferingId());
        attachment.setTeachingClassId(lab.getTeachingClassId());
        attachment.setUploaderUserId(principal.getUserId());
        attachment.setObjectKey(objectKey);
        attachment.setOriginalFilename(originalFilename);
        attachment.setContentType(contentType);
        attachment.setSizeBytes((long) content.length);
        attachment.setUploadedAt(OffsetDateTime.now());
        try {
            labReportAttachmentMapper.insert(attachment);
        } catch (RuntimeException exception) {
            safeDeleteObject(objectKey);
            throw exception;
        }

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.LAB_REPORT_ATTACHMENT_UPLOADED,
                "LAB_REPORT_ATTACHMENT",
                String.valueOf(attachment.getId()),
                AuditResult.SUCCESS,
                Map.of("labId", labId, "offeringId", lab.getOfferingId(), "teachingClassId", lab.getTeachingClassId()));
        return toAttachmentView(attachment);
    }

    @Transactional
    public LabReportView saveMyReport(
            Long labId,
            String reportContentText,
            List<Long> attachmentIds,
            boolean submit,
            AuthenticatedUserPrincipal principal) {
        LabEntity lab = requirePublishedLabForStudentMutation(labId, principal);
        List<Long> normalizedAttachmentIds = normalizeAttachmentIds(attachmentIds);
        String normalizedReportContent = normalizeReportContent(reportContentText, !normalizedAttachmentIds.isEmpty());
        LabReportEntity existing = findReportByLabAndStudent(labId, principal.getUserId());
        if (existing != null
                && !labReportLifecyclePolicy.canStudentMutate(parseLabReportStatus(existing.getStatus()))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_REPORT_STATUS_INVALID", "当前报告已进入教师评阅流程，不能再修改");
        }
        List<LabReportAttachmentEntity> attachments =
                loadAttachableAttachments(labId, principal.getUserId(), normalizedAttachmentIds);

        LabReportEntity report = existing == null ? new LabReportEntity() : existing;
        if (existing == null) {
            report.setLabId(labId);
            report.setOfferingId(lab.getOfferingId());
            report.setTeachingClassId(lab.getTeachingClassId());
            report.setStudentUserId(principal.getUserId());
        }
        LabReportStatus nextStatus = labReportLifecyclePolicy.nextStudentStatus(submit);
        report.setStatus(nextStatus.name());
        report.setReportContentText(normalizedReportContent);
        report.setTeacherAnnotationText(null);
        report.setTeacherCommentText(null);
        report.setReviewedAt(null);
        report.setPublishedAt(null);
        report.setReviewerUserId(null);
        report.setSubmittedAt(submit ? OffsetDateTime.now() : null);
        if (existing == null) {
            labReportMapper.insert(report);
        } else {
            labReportMapper.updateById(report);
        }

        reconcileReportAttachments(report.getId(), labId, principal.getUserId(), normalizedAttachmentIds);

        AuditAction action = submit ? AuditAction.LAB_REPORT_SUBMITTED : AuditAction.LAB_REPORT_SAVED;
        auditLogApplicationService.record(
                principal.getUserId(),
                action,
                "LAB_REPORT",
                String.valueOf(report.getId()),
                AuditResult.SUCCESS,
                Map.of(
                        "labId",
                        labId,
                        "offeringId",
                        lab.getOfferingId(),
                        "teachingClassId",
                        lab.getTeachingClassId(),
                        "attachmentCount",
                        attachments.size()));
        if (submit) {
            notificationDispatchService.notifyLabReportSubmitted(report, lab, principal.getUserId());
        }
        return toLabReportView(
                report,
                toUserSummary(principal.getUserId(), loadUsersByIds(List.of(principal.getUserId()))),
                null,
                loadAttachmentViews(report.getId()),
                false);
    }

    @Transactional(readOnly = true)
    public LabReportView getMyReport(Long labId, AuthenticatedUserPrincipal principal) {
        LabEntity lab = requireLab(labId);
        if (LabStatus.DRAFT.name().equals(lab.getStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该实验报告");
        }
        courseAuthorizationService.assertCanViewLab(principal, lab.getOfferingId(), lab.getTeachingClassId());
        LabReportEntity report = requireReportByLabAndStudent(labId, principal.getUserId());
        Map<Long, UserEntity> users = loadUsersByIds(userIdsOf(report.getStudentUserId(), report.getReviewerUserId()));
        return toLabReportView(
                report,
                toUserSummary(report.getStudentUserId(), users),
                toUserSummary(report.getReviewerUserId(), users),
                loadAttachmentViews(report.getId()),
                false);
    }

    @Transactional(readOnly = true)
    public PageResponse<LabReportSummaryView> listTeacherReports(
            Long labId, LabReportStatus status, long page, long pageSize, AuthenticatedUserPrincipal principal) {
        LabEntity lab = requireLab(labId);
        courseAuthorizationService.assertCanReviewLabReports(principal, lab.getOfferingId(), lab.getTeachingClassId());
        List<LabReportEntity> matched = labReportMapper
                .selectList(Wrappers.<LabReportEntity>lambdaQuery()
                        .eq(LabReportEntity::getLabId, labId)
                        .orderByDesc(LabReportEntity::getUpdatedAt)
                        .orderByDesc(LabReportEntity::getId))
                .stream()
                .filter(report -> status == null || status.name().equals(report.getStatus()))
                .toList();
        Map<Long, UserEntity> users = loadUsersByIds(
                matched.stream().map(LabReportEntity::getStudentUserId).collect(Collectors.toSet()));
        Map<Long, Integer> attachmentCounts = countAttachmentsByReportIds(
                matched.stream().map(LabReportEntity::getId).toList());
        return toReportSummaryPage(matched, users, attachmentCounts, page, pageSize);
    }

    @Transactional(readOnly = true)
    public LabReportView getTeacherReport(Long reportId, AuthenticatedUserPrincipal principal) {
        LabReportEntity report = requireLabReport(reportId);
        LabEntity lab = requireLab(report.getLabId());
        courseAuthorizationService.assertCanReviewLabReports(principal, lab.getOfferingId(), lab.getTeachingClassId());
        Map<Long, UserEntity> users = loadUsersByIds(userIdsOf(report.getStudentUserId(), report.getReviewerUserId()));
        return toLabReportView(
                report,
                toUserSummary(report.getStudentUserId(), users),
                toUserSummary(report.getReviewerUserId(), users),
                loadAttachmentViews(report.getId()),
                true);
    }

    @Transactional
    public LabReportView reviewReport(
            Long reportId,
            String teacherAnnotationText,
            String teacherCommentText,
            AuthenticatedUserPrincipal principal) {
        LabReportEntity report = requireLabReport(reportId);
        LabEntity lab = requireLab(report.getLabId());
        courseAuthorizationService.assertCanReviewLabReports(principal, lab.getOfferingId(), lab.getTeachingClassId());
        if (!labReportLifecyclePolicy.canTeacherReview(parseLabReportStatus(report.getStatus()))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_REPORT_STATUS_INVALID", "只有已提交的报告可以评阅");
        }
        String annotation = normalizeReviewText(teacherAnnotationText, "批注");
        String comment = normalizeReviewText(teacherCommentText, "评语");
        if (!StringUtils.hasText(annotation) && !StringUtils.hasText(comment)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_REPORT_REVIEW_EMPTY", "批注和评语不能同时为空");
        }

        report.setStatus(LabReportStatus.REVIEWED.name());
        report.setTeacherAnnotationText(annotation);
        report.setTeacherCommentText(comment);
        report.setReviewedAt(OffsetDateTime.now());
        report.setReviewerUserId(principal.getUserId());
        report.setPublishedAt(null);
        labReportMapper.updateById(report);

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.LAB_REPORT_REVIEWED,
                "LAB_REPORT",
                String.valueOf(reportId),
                AuditResult.SUCCESS,
                Map.of("labId", report.getLabId(), "studentUserId", report.getStudentUserId()));
        Map<Long, UserEntity> users = loadUsersByIds(userIdsOf(report.getStudentUserId(), report.getReviewerUserId()));
        return toLabReportView(
                report,
                toUserSummary(report.getStudentUserId(), users),
                toUserSummary(report.getReviewerUserId(), users),
                loadAttachmentViews(report.getId()),
                true);
    }

    @Transactional
    public LabReportView publishReport(Long reportId, AuthenticatedUserPrincipal principal) {
        LabReportEntity report = requireLabReport(reportId);
        LabEntity lab = requireLab(report.getLabId());
        courseAuthorizationService.assertCanReviewLabReports(principal, lab.getOfferingId(), lab.getTeachingClassId());
        if (!labReportLifecyclePolicy.canTeacherPublish(parseLabReportStatus(report.getStatus()))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_REPORT_STATUS_INVALID", "只有已评阅的报告可以发布评语");
        }
        if (!StringUtils.hasText(report.getTeacherAnnotationText())
                && !StringUtils.hasText(report.getTeacherCommentText())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_REPORT_REVIEW_EMPTY", "请先填写批注或评语后再发布");
        }
        if (report.getReviewedAt() == null) {
            report.setReviewedAt(OffsetDateTime.now());
            report.setReviewerUserId(principal.getUserId());
        }
        report.setStatus(LabReportStatus.PUBLISHED.name());
        report.setPublishedAt(OffsetDateTime.now());
        labReportMapper.updateById(report);

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.LAB_REPORT_PUBLISHED,
                "LAB_REPORT",
                String.valueOf(reportId),
                AuditResult.SUCCESS,
                Map.of("labId", report.getLabId(), "studentUserId", report.getStudentUserId()));
        notificationDispatchService.notifyLabReportPublished(report, lab, principal.getUserId());
        Map<Long, UserEntity> users = loadUsersByIds(userIdsOf(report.getStudentUserId(), report.getReviewerUserId()));
        return toLabReportView(
                report,
                toUserSummary(report.getStudentUserId(), users),
                toUserSummary(report.getReviewerUserId(), users),
                loadAttachmentViews(report.getId()),
                true);
    }

    @Transactional(readOnly = true)
    public LabReportAttachmentDownload downloadMyAttachment(Long attachmentId, AuthenticatedUserPrincipal principal) {
        LabReportAttachmentEntity attachment = requireAttachment(attachmentId);
        LabEntity lab = requireLab(attachment.getLabId());
        courseAuthorizationService.assertCanViewLab(principal, lab.getOfferingId(), lab.getTeachingClassId());
        if (!Objects.equals(attachment.getUploaderUserId(), principal.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权下载该附件");
        }
        return loadStoredAttachment(attachment);
    }

    @Transactional(readOnly = true)
    public LabReportAttachmentDownload downloadTeacherAttachment(
            Long attachmentId, AuthenticatedUserPrincipal principal) {
        LabReportAttachmentEntity attachment = requireAttachment(attachmentId);
        LabEntity lab = requireLab(attachment.getLabId());
        courseAuthorizationService.assertCanReviewLabReports(principal, lab.getOfferingId(), lab.getTeachingClassId());
        return loadStoredAttachment(attachment);
    }

    private LabEntity requireLab(Long labId) {
        LabEntity entity = labMapper.selectById(labId);
        if (entity == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "LAB_NOT_FOUND", "实验不存在");
        }
        return entity;
    }

    private TeachingClassEntity requireTeachingClass(Long teachingClassId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(teachingClassId);
        if (teachingClass == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "TEACHING_CLASS_NOT_FOUND", "教学班不存在");
        }
        return teachingClass;
    }

    private LabReportEntity requireLabReport(Long reportId) {
        LabReportEntity report = labReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "LAB_REPORT_NOT_FOUND", "实验报告不存在");
        }
        return report;
    }

    private LabReportEntity requireReportByLabAndStudent(Long labId, Long studentUserId) {
        LabReportEntity report = findReportByLabAndStudent(labId, studentUserId);
        if (report == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "LAB_REPORT_NOT_FOUND", "实验报告不存在");
        }
        return report;
    }

    private LabReportEntity findReportByLabAndStudent(Long labId, Long studentUserId) {
        return labReportMapper.selectOne(Wrappers.<LabReportEntity>lambdaQuery()
                .eq(LabReportEntity::getLabId, labId)
                .eq(LabReportEntity::getStudentUserId, studentUserId)
                .last("LIMIT 1"));
    }

    private LabReportAttachmentEntity requireAttachment(Long attachmentId) {
        LabReportAttachmentEntity attachment = labReportAttachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "LAB_REPORT_ATTACHMENT_NOT_FOUND", "实验报告附件不存在");
        }
        return attachment;
    }

    private LabEntity requirePublishedLabForStudentMutation(Long labId, AuthenticatedUserPrincipal principal) {
        LabEntity lab = requireLab(labId);
        courseAuthorizationService.assertCanViewLab(principal, lab.getOfferingId(), lab.getTeachingClassId());
        if (!LabStatus.PUBLISHED.name().equals(lab.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_STATUS_INVALID", "当前实验未开放报告提交");
        }
        return lab;
    }

    private void assertPendingAttachmentCapacityAvailable(Long labId, Long uploaderUserId) {
        long pendingAttachmentCount =
                labReportAttachmentMapper.selectCount(Wrappers.<LabReportAttachmentEntity>lambdaQuery()
                        .eq(LabReportAttachmentEntity::getLabId, labId)
                        .eq(LabReportAttachmentEntity::getUploaderUserId, uploaderUserId)
                        .isNull(LabReportAttachmentEntity::getLabReportId));
        if (pendingAttachmentCount >= MAX_ATTACHMENTS_PER_REPORT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_REPORT_ATTACHMENT_LIMIT", "当前最多只能保留 10 个待关联实验附件");
        }
    }

    private List<LabReportAttachmentEntity> loadAttachableAttachments(
            Long labId, Long uploaderUserId, List<Long> attachmentIds) {
        if (attachmentIds.isEmpty()) {
            return List.of();
        }
        if (attachmentIds.size() > MAX_ATTACHMENTS_PER_REPORT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_REPORT_ATTACHMENT_LIMIT", "实验报告附件数量超过上限");
        }
        List<LabReportAttachmentEntity> attachments =
                labReportAttachmentMapper.selectList(Wrappers.<LabReportAttachmentEntity>lambdaQuery()
                        .eq(LabReportAttachmentEntity::getLabId, labId)
                        .eq(LabReportAttachmentEntity::getUploaderUserId, uploaderUserId)
                        .in(LabReportAttachmentEntity::getId, attachmentIds)
                        .orderByAsc(LabReportAttachmentEntity::getId));
        if (attachments.size() != attachmentIds.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_REPORT_ATTACHMENT_INVALID", "实验报告附件不存在或无权引用");
        }
        Map<Long, LabReportAttachmentEntity> index =
                attachments.stream().collect(Collectors.toMap(LabReportAttachmentEntity::getId, Function.identity()));
        return attachmentIds.stream().map(index::get).toList();
    }

    private void reconcileReportAttachments(Long reportId, Long labId, Long uploaderUserId, List<Long> attachmentIds) {
        List<LabReportAttachmentEntity> existing =
                labReportAttachmentMapper.selectList(Wrappers.<LabReportAttachmentEntity>lambdaQuery()
                        .eq(LabReportAttachmentEntity::getLabId, labId)
                        .eq(LabReportAttachmentEntity::getUploaderUserId, uploaderUserId)
                        .eq(LabReportAttachmentEntity::getLabReportId, reportId)
                        .orderByAsc(LabReportAttachmentEntity::getId));
        Set<Long> selectedIds = new LinkedHashSet<>(attachmentIds);
        for (LabReportAttachmentEntity attachment : existing) {
            if (!selectedIds.contains(attachment.getId())) {
                attachment.setLabReportId(null);
                labReportAttachmentMapper.updateById(attachment);
            }
        }
        if (selectedIds.isEmpty()) {
            return;
        }
        List<LabReportAttachmentEntity> selected = loadAttachableAttachments(labId, uploaderUserId, attachmentIds);
        for (LabReportAttachmentEntity attachment : selected) {
            if (!Objects.equals(reportId, attachment.getLabReportId())) {
                attachment.setLabReportId(reportId);
                labReportAttachmentMapper.updateById(attachment);
            }
        }
    }

    private String normalizeTitle(String title) {
        String normalized = title == null ? null : title.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_TITLE_REQUIRED", "实验标题不能为空");
        }
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_TITLE_TOO_LONG", "实验标题长度不能超过 200");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }
        return description.trim();
    }

    private String normalizeReportContent(String reportContentText, boolean hasAttachments) {
        String normalized = StringUtils.hasText(reportContentText) ? reportContentText.trim() : null;
        if (normalized != null && normalized.length() > MAX_REPORT_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_REPORT_CONTENT_TOO_LONG", "实验报告正文不能超过 20000 字符");
        }
        if (!StringUtils.hasText(normalized) && !hasAttachments) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_REPORT_EMPTY", "实验报告正文和附件不能同时为空");
        }
        return normalized;
    }

    private String normalizeReviewText(String value, String fieldLabel) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_REVIEW_TEXT_LENGTH) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "LAB_REPORT_REVIEW_TEXT_TOO_LONG", fieldLabel + "长度不能超过 5000 字符");
        }
        return normalized;
    }

    private List<Long> normalizeAttachmentIds(List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> deduplicated = new LinkedHashSet<>();
        for (Long attachmentId : attachmentIds) {
            if (attachmentId == null || attachmentId <= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_REPORT_ATTACHMENT_INVALID", "实验报告附件编号非法");
            }
            deduplicated.add(attachmentId);
        }
        return List.copyOf(deduplicated);
    }

    private byte[] readAttachmentContent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_REPORT_ATTACHMENT_EMPTY", "实验报告附件不能为空");
        }
        if (file.getSize() <= 0 || file.getSize() > MAX_ATTACHMENT_SIZE_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_REPORT_ATTACHMENT_TOO_LARGE", "实验报告附件大小不能超过 20MB");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LAB_REPORT_ATTACHMENT_READ_FAILED", "实验报告附件读取失败");
        }
    }

    private String normalizeOriginalFilename(String originalFilename) {
        String candidate = StringUtils.hasText(originalFilename) ? originalFilename.trim() : "attachment.bin";
        if (candidate.length() > 255) {
            candidate = candidate.substring(candidate.length() - 255);
        }
        return candidate;
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType.trim() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private String generateAttachmentObjectKey(Long labId, Long userId, String originalFilename) {
        String sanitizedFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitizedFilename.length() > MAX_STORAGE_FILENAME_LENGTH) {
            sanitizedFilename = sanitizedFilename.substring(sanitizedFilename.length() - MAX_STORAGE_FILENAME_LENGTH);
        }
        return "%s/%s/%s/%s-%s".formatted(OBJECT_KEY_PREFIX, labId, userId, UUID.randomUUID(), sanitizedFilename);
    }

    private ObjectStorageService requireStorageService() {
        ObjectStorageService storageService = objectStorageServiceProvider.getIfAvailable();
        if (storageService == null) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE, "LAB_REPORT_STORAGE_DISABLED", "对象存储未启用，当前无法上传实验报告附件");
        }
        return storageService;
    }

    private void safeDeleteObject(String objectKey) {
        try {
            ObjectStorageService storageService = objectStorageServiceProvider.getIfAvailable();
            if (storageService != null) {
                storageService.deleteObject(objectKey);
            }
        } catch (ObjectStorageException ignored) {
            // 写入数据库失败后的清理属于尽力而为，避免吞掉主异常。
        }
    }

    private LabReportAttachmentDownload loadStoredAttachment(LabReportAttachmentEntity attachment) {
        ObjectStorageService storageService = requireStorageService();
        try {
            StoredObject storedObject = storageService.getObject(attachment.getObjectKey());
            return new LabReportAttachmentDownload(
                    attachment.getOriginalFilename(), attachment.getContentType(), storedObject.content());
        } catch (ObjectStorageException exception) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE, "LAB_REPORT_STORAGE_READ_FAILED", "实验报告附件暂时无法读取");
        }
    }

    private boolean isLabFeatureEnabled(LabEntity lab) {
        return Boolean.TRUE.equals(
                requireTeachingClass(lab.getTeachingClassId()).getLabEnabled());
    }

    private PageResponse<LabView> toLabPage(List<LabEntity> matched, long page, long pageSize) {
        List<LabEntity> pageItems = slice(matched, page, pageSize);
        Map<Long, TeachingClassEntity> classIndex = loadTeachingClassIndex(
                pageItems.stream().map(LabEntity::getTeachingClassId).collect(Collectors.toSet()));
        List<LabView> items = pageItems.stream()
                .map(lab -> toLabView(lab, classIndex.get(lab.getTeachingClassId())))
                .toList();
        return new PageResponse<>(items, matched.size(), page, pageSize);
    }

    private PageResponse<LabReportSummaryView> toReportSummaryPage(
            List<LabReportEntity> matched,
            Map<Long, UserEntity> users,
            Map<Long, Integer> attachmentCounts,
            long page,
            long pageSize) {
        List<LabReportEntity> pageItems = slice(matched, page, pageSize);
        List<LabReportSummaryView> items = pageItems.stream()
                .map(report -> new LabReportSummaryView(
                        report.getId(),
                        report.getLabId(),
                        toUserSummary(report.getStudentUserId(), users),
                        parseLabReportStatus(report.getStatus()),
                        attachmentCounts.getOrDefault(report.getId(), 0),
                        report.getSubmittedAt(),
                        report.getReviewedAt(),
                        report.getPublishedAt(),
                        report.getUpdatedAt()))
                .toList();
        return new PageResponse<>(items, matched.size(), page, pageSize);
    }

    private Map<Long, TeachingClassEntity> loadTeachingClassIndex(Collection<Long> teachingClassIds) {
        if (teachingClassIds == null || teachingClassIds.isEmpty()) {
            return Map.of();
        }
        return teachingClassMapper.selectBatchIds(teachingClassIds).stream()
                .collect(Collectors.toMap(TeachingClassEntity::getId, Function.identity()));
    }

    private Map<Long, UserEntity> loadUsersByIds(Collection<Long> userIds) {
        Set<Long> ids = userIds == null
                ? Set.of()
                : userIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }

    private Map<Long, Integer> countAttachmentsByReportIds(Collection<Long> reportIds) {
        if (reportIds == null || reportIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> counts = new LinkedHashMap<>();
        List<LabReportAttachmentEntity> attachments =
                labReportAttachmentMapper.selectList(Wrappers.<LabReportAttachmentEntity>lambdaQuery()
                        .in(LabReportAttachmentEntity::getLabReportId, reportIds)
                        .orderByAsc(LabReportAttachmentEntity::getId));
        for (LabReportAttachmentEntity attachment : attachments) {
            if (attachment.getLabReportId() != null) {
                counts.merge(attachment.getLabReportId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private List<LabReportAttachmentView> loadAttachmentViews(Long reportId) {
        if (reportId == null) {
            return List.of();
        }
        return labReportAttachmentMapper
                .selectList(Wrappers.<LabReportAttachmentEntity>lambdaQuery()
                        .eq(LabReportAttachmentEntity::getLabReportId, reportId)
                        .orderByAsc(LabReportAttachmentEntity::getId))
                .stream()
                .map(this::toAttachmentView)
                .toList();
    }

    private LabView toLabView(LabEntity entity, TeachingClassEntity teachingClass) {
        TeachingClassEntity resolvedTeachingClass =
                teachingClass == null ? requireTeachingClass(entity.getTeachingClassId()) : teachingClass;
        return new LabView(
                entity.getId(),
                entity.getOfferingId(),
                new LabClassView(
                        resolvedTeachingClass.getId(),
                        resolvedTeachingClass.getClassCode(),
                        resolvedTeachingClass.getClassName()),
                entity.getTitle(),
                entity.getDescription(),
                LabStatus.valueOf(entity.getStatus()),
                entity.getPublishedAt(),
                entity.getClosedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private LabReportView toLabReportView(
            LabReportEntity report,
            LabUserSummaryView student,
            LabUserSummaryView reviewer,
            List<LabReportAttachmentView> attachments,
            boolean includeTeacherFeedback) {
        boolean feedbackVisible =
                includeTeacherFeedback || LabReportStatus.PUBLISHED.name().equals(report.getStatus());
        return new LabReportView(
                report.getId(),
                report.getLabId(),
                parseLabReportStatus(report.getStatus()),
                report.getReportContentText(),
                feedbackVisible ? report.getTeacherAnnotationText() : null,
                feedbackVisible ? report.getTeacherCommentText() : null,
                student,
                reviewer,
                attachments,
                report.getSubmittedAt(),
                report.getReviewedAt(),
                report.getPublishedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt());
    }

    private LabReportAttachmentView toAttachmentView(LabReportAttachmentEntity attachment) {
        return new LabReportAttachmentView(
                attachment.getId(),
                attachment.getLabId(),
                attachment.getLabReportId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getUploadedAt());
    }

    private LabUserSummaryView toUserSummary(Long userId, Map<Long, UserEntity> users) {
        if (userId == null) {
            return null;
        }
        UserEntity user = users.get(userId);
        if (user == null) {
            return new LabUserSummaryView(userId, null, null);
        }
        return new LabUserSummaryView(user.getId(), user.getUsername(), user.getDisplayName());
    }

    private LabReportStatus parseLabReportStatus(String status) {
        return LabReportStatus.valueOf(status);
    }

    private Set<Long> userIdsOf(Long... userIds) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (userIds != null) {
            for (Long userId : userIds) {
                if (userId != null) {
                    ids.add(userId);
                }
            }
        }
        return ids;
    }

    private <T> List<T> slice(List<T> source, long page, long pageSize) {
        long normalizedPage = page <= 0 ? 1 : page;
        long normalizedPageSize = pageSize <= 0 ? 20 : pageSize;
        int fromIndex = (int) Math.min(source.size(), (normalizedPage - 1) * normalizedPageSize);
        int toIndex = (int) Math.min(source.size(), fromIndex + normalizedPageSize);
        return source.subList(fromIndex, toIndex);
    }
}
