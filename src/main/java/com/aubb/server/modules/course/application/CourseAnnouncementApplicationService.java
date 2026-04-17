package com.aubb.server.modules.course.application;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.view.CourseAnnouncementView;
import com.aubb.server.modules.course.infrastructure.announcement.CourseAnnouncementEntity;
import com.aubb.server.modules.course.infrastructure.announcement.CourseAnnouncementMapper;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberEntity;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassEntity;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.notification.application.NotificationDispatchService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CourseAnnouncementApplicationService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_BODY_LENGTH = 5_000;

    private final CourseAnnouncementMapper courseAnnouncementMapper;
    private final CourseMemberMapper courseMemberMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final AuditLogApplicationService auditLogApplicationService;
    private final NotificationDispatchService notificationDispatchService;

    @Transactional
    public CourseAnnouncementView createAnnouncement(
            Long offeringId, Long teachingClassId, String title, String body, AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageAnnouncements(principal, offeringId, teachingClassId);
        if (teachingClassId != null) {
            courseAuthorizationService.requireTeachingClassInOffering(offeringId, teachingClassId);
        }

        CourseAnnouncementEntity entity = new CourseAnnouncementEntity();
        entity.setOfferingId(offeringId);
        entity.setTeachingClassId(teachingClassId);
        entity.setTitle(normalizeTitle(title));
        entity.setBody(normalizeBody(body));
        entity.setCreatedByUserId(principal.getUserId());
        entity.setPublishedAt(OffsetDateTime.now());
        courseAnnouncementMapper.insert(entity);

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.COURSE_ANNOUNCEMENT_CREATED,
                "COURSE_ANNOUNCEMENT",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                buildAuditDetails(offeringId, teachingClassId));
        notificationDispatchService.notifyCourseAnnouncementPublished(entity, principal.getUserId());
        return toView(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseAnnouncementView> listTeacherAnnouncements(
            Long offeringId, Long teachingClassId, long page, long pageSize, AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageOffering(principal, offeringId);
        if (teachingClassId != null) {
            courseAuthorizationService.requireTeachingClassInOffering(offeringId, teachingClassId);
        }
        return toPage(
                loadTeacherAnnouncements(offeringId, teachingClassId, page, pageSize),
                countTeacherAnnouncements(offeringId, teachingClassId),
                page,
                pageSize);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseAnnouncementView> listMyAnnouncements(
            Long teachingClassId, long page, long pageSize, AuthenticatedUserPrincipal principal) {
        TeachingClassEntity teachingClass = requireTeachingClass(teachingClassId);
        courseAuthorizationService.assertCanViewAnnouncementsForClass(
                principal, teachingClass.getOfferingId(), teachingClassId);
        return toPage(
                loadMyAnnouncements(teachingClass.getOfferingId(), teachingClassId, page, pageSize),
                countMyAnnouncements(teachingClass.getOfferingId(), teachingClassId),
                page,
                pageSize);
    }

    @Transactional(readOnly = true)
    public CourseAnnouncementView getMyAnnouncement(Long announcementId, AuthenticatedUserPrincipal principal) {
        CourseAnnouncementEntity entity = requireAnnouncement(announcementId);
        if (entity.getTeachingClassId() != null) {
            courseAuthorizationService.assertCanViewAnnouncementsForClass(
                    principal, entity.getOfferingId(), entity.getTeachingClassId());
        } else {
            assertCanViewOfferingWideAnnouncement(principal, entity.getOfferingId());
        }
        return toView(entity);
    }

    private void assertCanViewOfferingWideAnnouncement(AuthenticatedUserPrincipal principal, Long offeringId) {
        if (courseAuthorizationService.canManageOfferingAsAdmin(principal, offeringId)
                || courseAuthorizationService.isInstructor(principal.getUserId(), offeringId)) {
            return;
        }
        if (!courseAuthorizationService.isActiveCourseMember(principal.getUserId(), offeringId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该课程公告");
        }
        Set<Long> activeClassIds = courseMemberMapper
                .selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getUserId, principal.getUserId())
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .isNotNull(CourseMemberEntity::getTeachingClassId)
                        .eq(CourseMemberEntity::getMemberStatus, "ACTIVE")
                        .select(CourseMemberEntity::getTeachingClassId))
                .stream()
                .map(CourseMemberEntity::getTeachingClassId)
                .collect(Collectors.toSet());
        if (activeClassIds.isEmpty()) {
            return;
        }
        boolean enabled = teachingClassMapper.selectBatchIds(activeClassIds).stream()
                .anyMatch(teachingClass -> Boolean.TRUE.equals(teachingClass.getAnnouncementEnabled()));
        if (!enabled) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ANNOUNCEMENT_DISABLED", "当前教学班未启用课程公告功能");
        }
    }

    private TeachingClassEntity requireTeachingClass(Long teachingClassId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(teachingClassId);
        if (teachingClass == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "TEACHING_CLASS_NOT_FOUND", "教学班不存在");
        }
        return teachingClass;
    }

    private CourseAnnouncementEntity requireAnnouncement(Long announcementId) {
        CourseAnnouncementEntity entity = courseAnnouncementMapper.selectById(announcementId);
        if (entity == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COURSE_ANNOUNCEMENT_NOT_FOUND", "课程公告不存在");
        }
        return entity;
    }

    private long countTeacherAnnouncements(Long offeringId, Long teachingClassId) {
        return courseAnnouncementMapper.selectCount(Wrappers.<CourseAnnouncementEntity>lambdaQuery()
                .eq(CourseAnnouncementEntity::getOfferingId, offeringId)
                .eq(teachingClassId != null, CourseAnnouncementEntity::getTeachingClassId, teachingClassId));
    }

    private List<CourseAnnouncementView> loadTeacherAnnouncements(
            Long offeringId, Long teachingClassId, long page, long pageSize) {
        long normalizedPage = Math.max(page, 1);
        long normalizedPageSize = Math.max(pageSize, 1);
        long offset = (normalizedPage - 1) * normalizedPageSize;
        return courseAnnouncementMapper
                .selectList(Wrappers.<CourseAnnouncementEntity>lambdaQuery()
                        .eq(CourseAnnouncementEntity::getOfferingId, offeringId)
                        .eq(teachingClassId != null, CourseAnnouncementEntity::getTeachingClassId, teachingClassId)
                        .orderByDesc(CourseAnnouncementEntity::getPublishedAt)
                        .orderByDesc(CourseAnnouncementEntity::getId)
                        .last("LIMIT " + normalizedPageSize + " OFFSET " + offset))
                .stream()
                .map(this::toView)
                .toList();
    }

    private long countMyAnnouncements(Long offeringId, Long teachingClassId) {
        return courseAnnouncementMapper.selectCount(Wrappers.<CourseAnnouncementEntity>lambdaQuery()
                .eq(CourseAnnouncementEntity::getOfferingId, offeringId)
                .and(wrapper -> wrapper.isNull(CourseAnnouncementEntity::getTeachingClassId)
                        .or()
                        .eq(CourseAnnouncementEntity::getTeachingClassId, teachingClassId)));
    }

    private List<CourseAnnouncementView> loadMyAnnouncements(
            Long offeringId, Long teachingClassId, long page, long pageSize) {
        long normalizedPage = Math.max(page, 1);
        long normalizedPageSize = Math.max(pageSize, 1);
        long offset = (normalizedPage - 1) * normalizedPageSize;
        return courseAnnouncementMapper
                .selectList(Wrappers.<CourseAnnouncementEntity>lambdaQuery()
                        .eq(CourseAnnouncementEntity::getOfferingId, offeringId)
                        .and(wrapper -> wrapper.isNull(CourseAnnouncementEntity::getTeachingClassId)
                                .or()
                                .eq(CourseAnnouncementEntity::getTeachingClassId, teachingClassId))
                        .orderByDesc(CourseAnnouncementEntity::getPublishedAt)
                        .orderByDesc(CourseAnnouncementEntity::getId)
                        .last("LIMIT " + normalizedPageSize + " OFFSET " + offset))
                .stream()
                .map(this::toView)
                .toList();
    }

    private PageResponse<CourseAnnouncementView> toPage(
            List<CourseAnnouncementView> items, long total, long page, long pageSize) {
        long normalizedPage = Math.max(page, 1);
        long normalizedPageSize = Math.max(pageSize, 1);
        return new PageResponse<>(items, total, normalizedPage, normalizedPageSize);
    }

    private CourseAnnouncementView toView(CourseAnnouncementEntity entity) {
        return new CourseAnnouncementView(
                entity.getId(),
                entity.getOfferingId(),
                entity.getTeachingClassId(),
                entity.getTitle(),
                entity.getBody(),
                entity.getPublishedAt(),
                entity.getCreatedAt());
    }

    private String normalizeTitle(String title) {
        String normalized = title == null ? null : title.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_ANNOUNCEMENT_TITLE_REQUIRED", "课程公告标题不能为空");
        }
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_ANNOUNCEMENT_TITLE_TOO_LONG", "课程公告标题过长");
        }
        return normalized;
    }

    private String normalizeBody(String body) {
        String normalized = body == null ? null : body.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_ANNOUNCEMENT_BODY_REQUIRED", "课程公告正文不能为空");
        }
        if (normalized.length() > MAX_BODY_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_ANNOUNCEMENT_BODY_TOO_LONG", "课程公告正文过长");
        }
        return normalized;
    }

    private Map<String, Object> buildAuditDetails(Long offeringId, Long teachingClassId) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("offeringId", offeringId);
        if (teachingClassId != null) {
            details.put("teachingClassId", teachingClassId);
        }
        return details;
    }
}
