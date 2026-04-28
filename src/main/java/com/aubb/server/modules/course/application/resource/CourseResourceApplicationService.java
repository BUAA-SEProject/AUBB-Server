package com.aubb.server.modules.course.application.resource;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.common.storage.ObjectStorageException;
import com.aubb.server.common.storage.ObjectStorageService;
import com.aubb.server.common.storage.StoredObject;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.CourseAuthorizationService;
import com.aubb.server.modules.course.application.view.CourseResourceView;
import com.aubb.server.modules.course.infrastructure.resource.CourseResourceEntity;
import com.aubb.server.modules.course.infrastructure.resource.CourseResourceMapper;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassEntity;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.notification.application.NotificationDispatchService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CourseResourceApplicationService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_STORAGE_FILENAME_LENGTH = 80;
    private static final long MAX_RESOURCE_SIZE_BYTES = 50L * 1024 * 1024;
    private static final String OBJECT_KEY_PREFIX = "course-resources";

    private final CourseResourceMapper courseResourceMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final AuditLogApplicationService auditLogApplicationService;
    private final ObjectProvider<ObjectStorageService> objectStorageServiceProvider;
    private final NotificationDispatchService notificationDispatchService;

    @Transactional
    public CourseResourceView uploadResource(
            Long offeringId,
            Long teachingClassId,
            String title,
            MultipartFile file,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageResources(principal, offeringId, teachingClassId);
        if (teachingClassId != null) {
            courseAuthorizationService.requireTeachingClassInOffering(offeringId, teachingClassId);
        }

        byte[] content = readContent(file);
        String normalizedTitle = normalizeTitle(title);
        String originalFilename = normalizeOriginalFilename(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        String objectKey = generateObjectKey(offeringId, teachingClassId, originalFilename);

        ObjectStorageService storageService = requireStorageService();
        try {
            storageService.putObject(objectKey, content, contentType);
        } catch (ObjectStorageException exception) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE, "COURSE_RESOURCE_STORAGE_WRITE_FAILED", "课程资源暂时不可写入");
        }

        CourseResourceEntity entity = new CourseResourceEntity();
        entity.setOfferingId(offeringId);
        entity.setTeachingClassId(teachingClassId);
        entity.setUploaderUserId(principal.getUserId());
        entity.setTitle(normalizedTitle);
        entity.setObjectKey(objectKey);
        entity.setOriginalFilename(originalFilename);
        entity.setContentType(contentType);
        entity.setSizeBytes((long) content.length);
        try {
            courseResourceMapper.insert(entity);
        } catch (RuntimeException exception) {
            safeDeleteObject(objectKey);
            throw exception;
        }

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.COURSE_RESOURCE_UPLOADED,
                "COURSE_RESOURCE",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                buildAuditDetails(offeringId, teachingClassId));
        notificationDispatchService.notifyCourseResourcePublished(entity, principal.getUserId());
        return toView(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseResourceView> listTeacherResources(
            Long offeringId, Long teachingClassId, long page, long pageSize, AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageResources(principal, offeringId, teachingClassId);
        if (teachingClassId != null) {
            courseAuthorizationService.requireTeachingClassInOffering(offeringId, teachingClassId);
        }
        return toPage(
                loadTeacherResources(offeringId, teachingClassId, page, pageSize),
                countTeacherResources(offeringId, teachingClassId),
                page,
                pageSize);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseResourceView> listMyResources(
            Long teachingClassId, long page, long pageSize, AuthenticatedUserPrincipal principal) {
        TeachingClassEntity teachingClass = requireTeachingClass(teachingClassId);
        courseAuthorizationService.assertCanViewResourcesForClass(
                principal, teachingClass.getOfferingId(), teachingClassId);
        return toPage(
                loadMyResources(teachingClass.getOfferingId(), teachingClassId, page, pageSize),
                countMyResources(teachingClass.getOfferingId(), teachingClassId),
                page,
                pageSize);
    }

    @Transactional(readOnly = true)
    public CourseResourceDownload downloadTeacherResource(Long resourceId, AuthenticatedUserPrincipal principal) {
        CourseResourceEntity entity = requireResource(resourceId);
        courseAuthorizationService.assertCanManageResources(
                principal, entity.getOfferingId(), entity.getTeachingClassId());
        return readResource(entity);
    }

    @Transactional(readOnly = true)
    public CourseResourceDownload downloadMyResource(Long resourceId, AuthenticatedUserPrincipal principal) {
        CourseResourceEntity entity = requireResource(resourceId);
        if (entity.getTeachingClassId() != null) {
            courseAuthorizationService.assertCanViewResourcesForClass(
                    principal, entity.getOfferingId(), entity.getTeachingClassId());
        } else {
            assertCanViewOfferingWideResource(principal, entity.getOfferingId());
        }
        return readResource(entity);
    }

    private void assertCanViewOfferingWideResource(AuthenticatedUserPrincipal principal, Long offeringId) {
        courseAuthorizationService.assertCanViewOfferingWideResources(principal, offeringId);
    }

    private CourseResourceDownload readResource(CourseResourceEntity entity) {
        ObjectStorageService storageService = requireStorageService();
        try {
            StoredObject storedObject = storageService.getObject(entity.getObjectKey());
            return new CourseResourceDownload(
                    entity.getOriginalFilename(), entity.getContentType(), storedObject.content());
        } catch (ObjectStorageException exception) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE, "COURSE_RESOURCE_STORAGE_READ_FAILED", "课程资源暂时不可读取");
        }
    }

    private CourseResourceEntity requireResource(Long resourceId) {
        CourseResourceEntity entity = courseResourceMapper.selectById(resourceId);
        if (entity == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COURSE_RESOURCE_NOT_FOUND", "课程资源不存在");
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

    private ObjectStorageService requireStorageService() {
        ObjectStorageService storageService = objectStorageServiceProvider.getIfAvailable();
        if (storageService == null) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE, "COURSE_RESOURCE_STORAGE_DISABLED", "课程资源存储未启用");
        }
        return storageService;
    }

    private long countTeacherResources(Long offeringId, Long teachingClassId) {
        return courseResourceMapper.selectCount(Wrappers.<CourseResourceEntity>lambdaQuery()
                .eq(CourseResourceEntity::getOfferingId, offeringId)
                .eq(teachingClassId != null, CourseResourceEntity::getTeachingClassId, teachingClassId));
    }

    private List<CourseResourceView> loadTeacherResources(
            Long offeringId, Long teachingClassId, long page, long pageSize) {
        long normalizedPage = Math.max(page, 1);
        long normalizedPageSize = Math.max(pageSize, 1);
        long offset = (normalizedPage - 1) * normalizedPageSize;
        return courseResourceMapper
                .selectList(Wrappers.<CourseResourceEntity>lambdaQuery()
                        .eq(CourseResourceEntity::getOfferingId, offeringId)
                        .eq(teachingClassId != null, CourseResourceEntity::getTeachingClassId, teachingClassId)
                        .orderByDesc(CourseResourceEntity::getCreatedAt)
                        .orderByDesc(CourseResourceEntity::getId)
                        .last("LIMIT " + normalizedPageSize + " OFFSET " + offset))
                .stream()
                .map(this::toView)
                .toList();
    }

    private long countMyResources(Long offeringId, Long teachingClassId) {
        return courseResourceMapper.selectCount(Wrappers.<CourseResourceEntity>lambdaQuery()
                .eq(CourseResourceEntity::getOfferingId, offeringId)
                .and(wrapper -> wrapper.isNull(CourseResourceEntity::getTeachingClassId)
                        .or()
                        .eq(CourseResourceEntity::getTeachingClassId, teachingClassId)));
    }

    private List<CourseResourceView> loadMyResources(Long offeringId, Long teachingClassId, long page, long pageSize) {
        long normalizedPage = Math.max(page, 1);
        long normalizedPageSize = Math.max(pageSize, 1);
        long offset = (normalizedPage - 1) * normalizedPageSize;
        return courseResourceMapper
                .selectList(Wrappers.<CourseResourceEntity>lambdaQuery()
                        .eq(CourseResourceEntity::getOfferingId, offeringId)
                        .and(wrapper -> wrapper.isNull(CourseResourceEntity::getTeachingClassId)
                                .or()
                                .eq(CourseResourceEntity::getTeachingClassId, teachingClassId))
                        .orderByDesc(CourseResourceEntity::getCreatedAt)
                        .orderByDesc(CourseResourceEntity::getId)
                        .last("LIMIT " + normalizedPageSize + " OFFSET " + offset))
                .stream()
                .map(this::toView)
                .toList();
    }

    private PageResponse<CourseResourceView> toPage(
            List<CourseResourceView> items, long total, long page, long pageSize) {
        long normalizedPage = Math.max(page, 1);
        long normalizedPageSize = Math.max(pageSize, 1);
        return new PageResponse<>(items, total, normalizedPage, normalizedPageSize);
    }

    private CourseResourceView toView(CourseResourceEntity entity) {
        return new CourseResourceView(
                entity.getId(),
                entity.getOfferingId(),
                entity.getTeachingClassId(),
                entity.getTitle(),
                entity.getOriginalFilename(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getCreatedAt());
    }

    private byte[] readContent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_RESOURCE_FILE_REQUIRED", "课程资源文件不能为空");
        }
        if (file.getSize() > MAX_RESOURCE_SIZE_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_RESOURCE_FILE_TOO_LARGE", "课程资源文件过大");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_RESOURCE_FILE_READ_FAILED", "课程资源文件读取失败");
        }
    }

    private String normalizeTitle(String title) {
        String normalized = title == null ? null : title.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_RESOURCE_TITLE_REQUIRED", "课程资源标题不能为空");
        }
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_RESOURCE_TITLE_TOO_LONG", "课程资源标题过长");
        }
        return normalized;
    }

    private String normalizeOriginalFilename(String filename) {
        String normalized = filename == null ? null : filename.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_RESOURCE_FILENAME_REQUIRED", "课程资源文件名不能为空");
        }
        if (normalized.length() > 255) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_RESOURCE_FILENAME_TOO_LONG", "课程资源文件名过长");
        }
        return normalized;
    }

    private String normalizeContentType(String contentType) {
        String normalized = contentType == null ? "application/octet-stream" : contentType.trim();
        if (!StringUtils.hasText(normalized)) {
            return "application/octet-stream";
        }
        return normalized.length() > 128 ? "application/octet-stream" : normalized;
    }

    private String generateObjectKey(Long offeringId, Long teachingClassId, String originalFilename) {
        String safeFilename =
                originalFilename.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("_+", "_");
        if (!StringUtils.hasText(safeFilename)) {
            safeFilename = "resource.bin";
        }
        if (safeFilename.length() > MAX_STORAGE_FILENAME_LENGTH) {
            safeFilename = safeFilename.substring(safeFilename.length() - MAX_STORAGE_FILENAME_LENGTH);
        }
        String scopeSegment = teachingClassId == null ? "offering" : "class-" + teachingClassId;
        return OBJECT_KEY_PREFIX + "/" + offeringId + "/" + scopeSegment + "/" + UUID.randomUUID() + "-" + safeFilename;
    }

    private void safeDeleteObject(String objectKey) {
        try {
            requireStorageService().deleteObject(objectKey);
        } catch (RuntimeException ignored) {
        }
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
