package com.aubb.server.modules.identityaccess.application.authz.core;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.assignment.infrastructure.AssignmentEntity;
import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingMapper;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassEntity;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.grading.infrastructure.appeal.GradeAppealEntity;
import com.aubb.server.modules.grading.infrastructure.appeal.GradeAppealMapper;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import com.aubb.server.modules.organization.infrastructure.OrgUnitEntity;
import com.aubb.server.modules.organization.infrastructure.OrgUnitMapper;
import com.aubb.server.modules.submission.infrastructure.SubmissionEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionMapper;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResourceOwnershipResolutionService {

    private final OrgUnitMapper orgUnitMapper;
    private final CourseOfferingMapper courseOfferingMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final AssignmentMapper assignmentMapper;
    private final SubmissionMapper submissionMapper;
    private final GradeAppealMapper gradeAppealMapper;

    @Transactional(readOnly = true)
    public ResolvedAuthorizationResource resolve(AuthorizationResourceRef resourceRef) {
        return switch (resourceRef.type()) {
            case PLATFORM ->
                new ResolvedAuthorizationResource(
                        resourceRef, AuthorizationScopePath.platform(), null, true, false, null, null, false);
            case SCHOOL, COLLEGE, COURSE -> resolveOrgUnitResource(resourceRef);
            case OFFERING -> resolveOfferingResource(resourceRef);
            case CLASS -> resolveClassResource(resourceRef);
            case ASSIGNMENT -> resolveAssignmentResource(resourceRef);
            case SUBMISSION -> resolveSubmissionResource(resourceRef);
            case GRADE_APPEAL -> resolveGradeAppealResource(resourceRef);
        };
    }

    private ResolvedAuthorizationResource resolveOrgUnitResource(AuthorizationResourceRef resourceRef) {
        OrgUnitEntity orgUnit =
                requireEntity(orgUnitMapper.selectById(resourceRef.id()), "ORG_UNIT_NOT_FOUND", "组织不存在");
        OrgChain chain = resolveOrgChain(orgUnit.getId());
        AuthorizationScopePath scopePath =
                switch (resourceRef.type()) {
                    case SCHOOL -> AuthorizationScopePath.forSchool(chain.schoolId());
                    case COLLEGE -> AuthorizationScopePath.forCollege(chain.schoolId(), chain.collegeId());
                    case COURSE ->
                        AuthorizationScopePath.forCourse(chain.schoolId(), chain.collegeId(), chain.courseId());
                    default ->
                        throw new IllegalArgumentException("Unsupported org resource type: " + resourceRef.type());
                };
        return new ResolvedAuthorizationResource(resourceRef, scopePath, null, true, false, null, null, false);
    }

    private ResolvedAuthorizationResource resolveOfferingResource(AuthorizationResourceRef resourceRef) {
        CourseOfferingEntity offering =
                requireEntity(courseOfferingMapper.selectById(resourceRef.id()), "COURSE_OFFERING_NOT_FOUND", "开课不存在");
        AuthorizationScopePath scopePath = resolveOfferingScopePath(offering);
        return new ResolvedAuthorizationResource(
                resourceRef,
                scopePath,
                null,
                isPublished(offering.getStatus(), offering.getPublishAt()),
                isArchived(offering.getStatus(), offering.getArchivedAt()),
                offering.getStartAt(),
                offering.getEndAt(),
                false);
    }

    private ResolvedAuthorizationResource resolveClassResource(AuthorizationResourceRef resourceRef) {
        TeachingClassEntity teachingClass =
                requireEntity(teachingClassMapper.selectById(resourceRef.id()), "TEACHING_CLASS_NOT_FOUND", "教学班不存在");
        CourseOfferingEntity offering = requireEntity(
                courseOfferingMapper.selectById(teachingClass.getOfferingId()), "COURSE_OFFERING_NOT_FOUND", "开课不存在");
        AuthorizationScopePath offeringScopePath = resolveOfferingScopePath(offering);
        AuthorizationScopePath classScopePath = AuthorizationScopePath.forClass(
                offeringScopePath.schoolId(),
                offeringScopePath.collegeId(),
                offeringScopePath.courseId(),
                teachingClass.getOfferingId(),
                teachingClass.getId());
        return new ResolvedAuthorizationResource(
                resourceRef,
                classScopePath,
                null,
                isPublished(teachingClass.getStatus(), null),
                isArchived(teachingClass.getStatus(), offering.getArchivedAt()),
                offering.getStartAt(),
                offering.getEndAt(),
                false);
    }

    private ResolvedAuthorizationResource resolveAssignmentResource(AuthorizationResourceRef resourceRef) {
        AssignmentEntity assignment =
                requireEntity(assignmentMapper.selectById(resourceRef.id()), "ASSIGNMENT_NOT_FOUND", "作业不存在");
        CourseOfferingEntity offering = requireEntity(
                courseOfferingMapper.selectById(assignment.getOfferingId()), "COURSE_OFFERING_NOT_FOUND", "开课不存在");
        AuthorizationScopePath scopePath =
                resolveTeachingScopePath(assignment.getOfferingId(), assignment.getTeachingClassId(), offering);
        return new ResolvedAuthorizationResource(
                resourceRef,
                scopePath,
                null,
                isPublished(assignment.getStatus(), assignment.getPublishedAt()),
                isArchived(assignment.getStatus(), offering.getArchivedAt()),
                assignment.getOpenAt(),
                assignment.getDueAt(),
                false);
    }

    private ResolvedAuthorizationResource resolveSubmissionResource(AuthorizationResourceRef resourceRef) {
        SubmissionEntity submission =
                requireEntity(submissionMapper.selectById(resourceRef.id()), "SUBMISSION_NOT_FOUND", "提交不存在");
        CourseOfferingEntity offering = requireEntity(
                courseOfferingMapper.selectById(submission.getOfferingId()), "COURSE_OFFERING_NOT_FOUND", "开课不存在");
        AssignmentEntity assignment =
                submission.getAssignmentId() == null ? null : assignmentMapper.selectById(submission.getAssignmentId());
        AuthorizationScopePath scopePath =
                resolveTeachingScopePath(submission.getOfferingId(), submission.getTeachingClassId(), offering);
        return new ResolvedAuthorizationResource(
                resourceRef,
                scopePath,
                submission.getSubmitterUserId(),
                assignment == null || isPublished(assignment.getStatus(), assignment.getPublishedAt()),
                assignment != null
                        ? isArchived(assignment.getStatus(), offering.getArchivedAt())
                        : isArchived(offering.getStatus(), offering.getArchivedAt()),
                assignment == null ? null : assignment.getOpenAt(),
                assignment == null ? null : assignment.getDueAt(),
                false);
    }

    private ResolvedAuthorizationResource resolveGradeAppealResource(AuthorizationResourceRef resourceRef) {
        GradeAppealEntity appeal =
                requireEntity(gradeAppealMapper.selectById(resourceRef.id()), "GRADE_APPEAL_NOT_FOUND", "成绩申诉不存在");
        CourseOfferingEntity offering = requireEntity(
                courseOfferingMapper.selectById(appeal.getOfferingId()), "COURSE_OFFERING_NOT_FOUND", "开课不存在");
        AuthorizationScopePath scopePath =
                resolveTeachingScopePath(appeal.getOfferingId(), appeal.getTeachingClassId(), offering);
        AssignmentEntity assignment =
                appeal.getAssignmentId() == null ? null : assignmentMapper.selectById(appeal.getAssignmentId());
        return new ResolvedAuthorizationResource(
                resourceRef,
                scopePath,
                appeal.getStudentUserId(),
                assignment == null || assignment.getGradePublishedAt() != null,
                isArchived(offering.getStatus(), offering.getArchivedAt()),
                null,
                null,
                false);
    }

    private AuthorizationScopePath resolveTeachingScopePath(
            Long offeringId, Long teachingClassId, CourseOfferingEntity offering) {
        AuthorizationScopePath offeringScopePath = resolveOfferingScopePath(offering);
        if (teachingClassId == null) {
            return offeringScopePath;
        }
        TeachingClassEntity teachingClass =
                requireEntity(teachingClassMapper.selectById(teachingClassId), "TEACHING_CLASS_NOT_FOUND", "教学班不存在");
        if (!offeringId.equals(teachingClass.getOfferingId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "SCOPE_CHAIN_MISMATCH", "资源归属链不一致");
        }
        return AuthorizationScopePath.forClass(
                offeringScopePath.schoolId(),
                offeringScopePath.collegeId(),
                offeringScopePath.courseId(),
                offeringId,
                teachingClassId);
    }

    private AuthorizationScopePath resolveOfferingScopePath(CourseOfferingEntity offering) {
        OrgChain chain = resolveOrgChain(offering.getOrgCourseUnitId());
        if (chain.schoolId() == null || chain.collegeId() == null || chain.courseId() == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "SCOPE_CHAIN_INCOMPLETE", "资源归属链不完整");
        }
        return AuthorizationScopePath.forOffering(
                chain.schoolId(), chain.collegeId(), chain.courseId(), offering.getId());
    }

    private OrgChain resolveOrgChain(Long orgUnitId) {
        Long schoolId = null;
        Long collegeId = null;
        Long courseId = null;
        Long cursor = orgUnitId;
        while (cursor != null) {
            OrgUnitEntity orgUnit = orgUnitMapper.selectById(cursor);
            if (orgUnit == null) {
                break;
            }
            AuthorizationScopeType scopeType = AuthorizationScopeType.fromDatabaseValue(orgUnit.getType());
            switch (scopeType) {
                case SCHOOL -> schoolId = orgUnit.getId();
                case COLLEGE -> collegeId = orgUnit.getId();
                case COURSE -> courseId = orgUnit.getId();
                case PLATFORM, OFFERING, CLASS -> {
                    // org_units 当前不承载 platform/offering 作用域，class 由业务表补链。
                }
            }
            cursor = orgUnit.getParentId();
        }
        return new OrgChain(schoolId, collegeId, courseId);
    }

    private static boolean isPublished(String status, OffsetDateTime publishedAt) {
        return publishedAt != null || !equalsIgnoreCase(status, "DRAFT");
    }

    private static boolean isArchived(String status, OffsetDateTime archivedAt) {
        return archivedAt != null || equalsIgnoreCase(status, "ARCHIVED");
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && left.equalsIgnoreCase(right);
    }

    private <T> T requireEntity(T entity, String code, String message) {
        if (entity == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, code, message);
        }
        return entity;
    }

    private record OrgChain(Long schoolId, Long collegeId, Long courseId) {}
}
