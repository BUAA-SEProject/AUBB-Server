package com.aubb.server.modules.course.application;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.audit.application.SensitiveOperationAuditService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.course.domain.member.CourseMemberStatus;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberEntity;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingCollegeMapEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingCollegeMapMapper;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingMapper;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassEntity;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.AuthorizationRequest;
import com.aubb.server.modules.identityaccess.application.authz.AuthorizationService;
import com.aubb.server.modules.identityaccess.application.authz.ScopeRef;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationContext;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResourceRef;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResourceType;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResult;
import com.aubb.server.modules.identityaccess.application.authz.core.PermissionAuthorizationService;
import com.aubb.server.modules.identityaccess.application.iam.GovernanceAuthorizationService;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import com.aubb.server.modules.identityaccess.domain.authz.PermissionCode;
import com.aubb.server.modules.organization.domain.OrgUnitType;
import com.aubb.server.modules.organization.infrastructure.OrgUnitEntity;
import com.aubb.server.modules.organization.infrastructure.OrgUnitMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseAuthorizationService {

    private static final Set<String> HISTORY_READABLE_STUDENT_STATUSES = Set.of(
            CourseMemberStatus.ACTIVE.name(),
            CourseMemberStatus.DROPPED.name(),
            CourseMemberStatus.TRANSFERRED.name(),
            CourseMemberStatus.COMPLETED.name());

    private final AuthorizationService authorizationService;
    private final PermissionAuthorizationService permissionAuthorizationService;
    private final SensitiveOperationAuditService sensitiveOperationAuditService;
    private final GovernanceAuthorizationService governanceAuthorizationService;
    private final CourseOfferingMapper courseOfferingMapper;
    private final CourseOfferingCollegeMapMapper courseOfferingCollegeMapMapper;
    private final CourseMemberMapper courseMemberMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final OrgUnitMapper orgUnitMapper;

    @Transactional(readOnly = true)
    public void assertCanCreateCatalog(AuthenticatedUserPrincipal principal, Long departmentUnitId) {
        assertCollegeUnit(departmentUnitId);
        governanceAuthorizationService.assertCanManageUserAt(principal, departmentUnitId);
    }

    @Transactional(readOnly = true)
    public void assertCanCreateOffering(AuthenticatedUserPrincipal principal, Long primaryCollegeUnitId) {
        assertCollegeUnit(primaryCollegeUnitId);
        governanceAuthorizationService.assertCanManageUserAt(principal, primaryCollegeUnitId);
    }

    @Transactional(readOnly = true)
    public void assertCanViewOfferingAsAdmin(AuthenticatedUserPrincipal principal, Long offeringId) {
        if (!canManageOfferingAsAdmin(principal, offeringId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该课程");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanManageOffering(AuthenticatedUserPrincipal principal, Long offeringId) {
        assertPermissionWithFallback(
                principal,
                "offering.manage",
                offeringResource(offeringId),
                PermissionCode.OFFERING_MANAGE,
                resolveOfferingScope(offeringId),
                "当前用户无权管理该课程",
                null);
    }

    @Transactional(readOnly = true)
    public void assertCanManageMembers(AuthenticatedUserPrincipal principal, Long offeringId) {
        assertPermissionWithFallback(
                principal,
                "member.manage",
                offeringResource(offeringId),
                PermissionCode.MEMBER_MANAGE,
                resolveOfferingScope(offeringId),
                "当前用户无权管理课程成员",
                null);
    }

    @Transactional(readOnly = true)
    public boolean canManageMembers(AuthenticatedUserPrincipal principal, Long offeringId) {
        AuthorizationResult result = permissionAuthorizationService.authorize(
                principal, "member.manage", offeringResource(offeringId), currentContext());
        if (result.allowed()) {
            return true;
        }
        return "DENY_NO_ROLE_BINDING".equals(result.reasonCode())
                && hasPermission(principal, PermissionCode.MEMBER_MANAGE, resolveOfferingScope(offeringId));
    }

    @Transactional(readOnly = true)
    public void assertCanImportMembers(AuthenticatedUserPrincipal principal, Long offeringId) {
        assertPermissionWithFallback(
                principal,
                "member.import",
                offeringResource(offeringId),
                PermissionCode.MEMBER_MANAGE,
                resolveOfferingScope(offeringId),
                "当前用户无权导入课程成员",
                AuditAction.COURSE_MEMBERS_IMPORTED);
    }

    @Transactional(readOnly = true)
    public void assertCanManageAssignments(AuthenticatedUserPrincipal principal, Long offeringId) {
        assertPermissionWithFallback(
                principal,
                "task.edit",
                offeringResource(offeringId),
                PermissionCode.ASSIGNMENT_UPDATE,
                resolveOfferingScope(offeringId),
                "当前用户无权管理该课程作业",
                null);
    }

    @Transactional(readOnly = true)
    public void assertCanReadAssignment(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermission(
                principal,
                PermissionCode.ASSIGNMENT_READ,
                resolveAssignmentScope(offeringId, teachingClassId),
                "当前用户无权查看该课程作业");
    }

    @Transactional(readOnly = true)
    public void assertCanCreateAssignment(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermissionWithFallback(
                principal,
                "task.create",
                teachingResource(offeringId, teachingClassId),
                PermissionCode.ASSIGNMENT_CREATE,
                resolveAssignmentScope(offeringId, teachingClassId),
                "当前用户无权创建该课程作业",
                null);
    }

    @Transactional(readOnly = true)
    public void assertCanUpdateAssignment(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermissionWithFallback(
                principal,
                "task.edit",
                teachingResource(offeringId, teachingClassId),
                PermissionCode.ASSIGNMENT_UPDATE,
                resolveAssignmentScope(offeringId, teachingClassId),
                "当前用户无权编辑该课程作业",
                null);
    }

    @Transactional(readOnly = true)
    public void assertCanPublishAssignment(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermissionWithFallback(
                principal,
                "task.publish",
                teachingResource(offeringId, teachingClassId),
                PermissionCode.ASSIGNMENT_PUBLISH,
                resolveAssignmentScope(offeringId, teachingClassId),
                "当前用户无权发布该课程作业",
                null);
    }

    @Transactional(readOnly = true)
    public void assertCanCloseAssignment(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermissionWithFallback(
                principal,
                "task.close",
                teachingResource(offeringId, teachingClassId),
                PermissionCode.ASSIGNMENT_CLOSE,
                resolveAssignmentScope(offeringId, teachingClassId),
                "当前用户无权关闭该课程作业",
                null);
    }

    @Transactional(readOnly = true)
    public void assertCanManageQuestionBank(AuthenticatedUserPrincipal principal, Long offeringId) {
        assertPermission(
                principal, PermissionCode.QUESTION_BANK_MANAGE, resolveOfferingScope(offeringId), "当前用户无权管理题库");
    }

    @Transactional(readOnly = true)
    public void assertCanManageJudgeProfiles(AuthenticatedUserPrincipal principal, Long offeringId) {
        assertPermissionWithFallback(
                principal,
                "judge.config",
                offeringResource(offeringId),
                PermissionCode.JUDGE_PROFILE_MANAGE,
                resolveOfferingScope(offeringId),
                "当前用户无权管理评测环境",
                AuditAction.JUDGE_CONFIG_CHANGE);
    }

    @Transactional(readOnly = true)
    public void assertCanManageAnnouncements(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertTeachingFeatureManagePermission(
                principal,
                "announcement.publish",
                offeringId,
                teachingClassId,
                "当前用户无权管理该课程公告",
                teachingClassId == null ? null : this::assertAnnouncementFeatureEnabled);
    }

    @Transactional(readOnly = true)
    public void assertCanViewAnnouncementsForClass(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertAnnouncementFeatureEnabled(offeringId, teachingClassId);
        assertTeachingClassReadPermission(
                principal, "announcement.read", offeringId, teachingClassId, "当前用户无权查看该课程公告");
    }

    @Transactional(readOnly = true)
    public void assertCanViewOfferingWideAnnouncements(AuthenticatedUserPrincipal principal, Long offeringId) {
        assertOfferingWideTeachingReadPermission(
                principal,
                offeringId,
                "announcement.read",
                teachingClass -> Boolean.TRUE.equals(teachingClass.getAnnouncementEnabled()),
                "ANNOUNCEMENT_DISABLED",
                "当前教学班未启用课程公告功能",
                "当前用户无权查看该课程公告");
    }

    @Transactional(readOnly = true)
    public void assertCanManageResources(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertTeachingFeatureManagePermission(
                principal,
                "resource.manage",
                offeringId,
                teachingClassId,
                "当前用户无权管理该课程资源",
                teachingClassId == null ? null : this::assertResourceFeatureEnabled);
    }

    @Transactional(readOnly = true)
    public void assertCanViewResourcesForClass(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertResourceFeatureEnabled(offeringId, teachingClassId);
        assertTeachingClassReadPermission(
                principal, "resource.read", offeringId, teachingClassId, "当前用户无权查看该课程资源");
    }

    @Transactional(readOnly = true)
    public void assertCanViewOfferingWideResources(AuthenticatedUserPrincipal principal, Long offeringId) {
        assertOfferingWideTeachingReadPermission(
                principal,
                offeringId,
                "resource.read",
                teachingClass -> Boolean.TRUE.equals(teachingClass.getResourceEnabled()),
                "RESOURCE_DISABLED",
                "当前教学班未启用课程资源功能",
                "当前用户无权查看该课程资源");
    }

    @Transactional(readOnly = true)
    public void assertCanManageDiscussions(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertTeachingFeatureManagePermission(
                principal,
                "discussion.manage",
                offeringId,
                teachingClassId,
                "当前用户无权管理该课程讨论",
                teachingClassId == null ? null : this::assertDiscussionFeatureEnabled);
    }

    @Transactional(readOnly = true)
    public void assertCanParticipateDiscussionsForClass(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertDiscussionFeatureEnabled(offeringId, teachingClassId);
        assertTeachingClassReadPermission(
                principal, "discussion.participate", offeringId, teachingClassId, "当前用户无权参与该课程讨论");
    }

    @Transactional(readOnly = true)
    public void assertCanAccessOfferingWideDiscussions(AuthenticatedUserPrincipal principal, Long offeringId) {
        assertOfferingWideTeachingReadPermission(
                principal,
                offeringId,
                "discussion.participate",
                teachingClass -> Boolean.TRUE.equals(teachingClass.getDiscussionEnabled()),
                "DISCUSSION_DISABLED",
                "当前教学班未启用课程讨论功能",
                "当前用户无权查看该课程讨论");
    }

    @Transactional(readOnly = true)
    public void assertCanManageLabs(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertLabFeatureEnabled(offeringId, teachingClassId);
        assertPermission(
                principal,
                PermissionCode.LAB_MANAGE,
                resolveTeachingClassScope(offeringId, teachingClassId),
                "当前用户无权管理该实验");
    }

    @Transactional(readOnly = true)
    public void assertCanReviewLabReports(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertLabFeatureEnabled(offeringId, teachingClassId);
        assertPermission(
                principal,
                PermissionCode.LAB_REPORT_REVIEW,
                resolveTeachingClassScope(offeringId, teachingClassId),
                "当前用户无权评阅该实验报告");
    }

    @Transactional(readOnly = true)
    public void assertCanGradeSubmission(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermissionWithFallback(
                principal,
                "submission.grade",
                teachingResource(offeringId, teachingClassId),
                PermissionCode.SUBMISSION_GRADE,
                resolveSubmissionScope(offeringId, teachingClassId),
                "当前用户无权批改该提交",
                null);
    }

    @Transactional(readOnly = true)
    public void assertCanReadSubmission(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        if (canReadSubmission(principal, offeringId, teachingClassId)) {
            return;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该提交");
    }

    @Transactional(readOnly = true)
    public boolean canReadSubmission(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        ScopeRef scope = resolveSubmissionScope(offeringId, teachingClassId);
        if (teachingClassId == null) {
            return hasPermission(principal, PermissionCode.SUBMISSION_READ_OFFERING, scope);
        }
        return hasAnyPermission(
                principal,
                List.of(PermissionCode.SUBMISSION_READ_CLASS, PermissionCode.SUBMISSION_READ_OFFERING),
                scope);
    }

    @Transactional(readOnly = true)
    public void assertCanReadSensitiveSubmission(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        if (canReadSensitiveSubmission(principal, offeringId, teachingClassId)) {
            return;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看敏感提交详情");
    }

    @Transactional(readOnly = true)
    public boolean canReadSensitiveSubmission(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        return hasPermission(
                principal,
                PermissionCode.SUBMISSION_CODE_READ_SENSITIVE,
                resolveSubmissionScope(offeringId, teachingClassId));
    }

    @Transactional(readOnly = true)
    public void assertCanRejudgeSubmission(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermissionWithFallback(
                principal,
                "judge.rejudge",
                teachingResource(offeringId, teachingClassId),
                PermissionCode.SUBMISSION_REJUDGE,
                resolveSubmissionScope(offeringId, teachingClassId),
                "当前用户无权重判该提交",
                AuditAction.JUDGE_REJUDGE);
    }

    @Transactional(readOnly = true)
    public void assertCanManageClassFeatures(AuthenticatedUserPrincipal principal, Long teachingClassId) {
        TeachingClassEntity teachingClass = requireTeachingClass(teachingClassId);
        assertPermissionWithFallback(
                principal,
                "class.manage",
                classResource(teachingClassId),
                PermissionCode.CLASS_MANAGE,
                resolveTeachingClassScope(teachingClass.getOfferingId(), teachingClassId),
                "当前用户无权管理该教学班",
                null);
    }

    @Transactional(readOnly = true)
    public void assertCanViewMembers(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermission(
                principal,
                PermissionCode.MEMBER_READ,
                resolveMemberScope(offeringId, teachingClassId),
                "当前用户无权查看该课程成员");
    }

    @Transactional(readOnly = true)
    public void assertCanViewLab(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertLabFeatureEnabled(offeringId, teachingClassId);
        assertPermission(
                principal,
                PermissionCode.LAB_READ,
                resolveTeachingClassScope(offeringId, teachingClassId),
                "当前用户无权查看该实验");
    }

    @Transactional(readOnly = true)
    public void assertCanViewOfferingGradebook(AuthenticatedUserPrincipal principal, Long offeringId) {
        if (canViewOfferingGradebook(principal, offeringId)) {
            return;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看课程成绩册");
    }

    @Transactional(readOnly = true)
    public boolean canViewOfferingGradebook(AuthenticatedUserPrincipal principal, Long offeringId) {
        return hasPermission(principal, PermissionCode.GRADE_EXPORT_OFFERING, resolveOfferingScope(offeringId));
    }

    @Transactional(readOnly = true)
    public void assertCanViewTeachingClassGradebook(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        if (canViewTeachingClassGradebook(principal, offeringId, teachingClassId)) {
            return;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看教学班成绩册");
    }

    @Transactional(readOnly = true)
    public boolean canViewTeachingClassGradebook(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        return hasAnyPermission(
                principal,
                List.of(PermissionCode.GRADE_EXPORT_CLASS, PermissionCode.GRADE_EXPORT_OFFERING),
                resolveTeachingClassScope(offeringId, teachingClassId));
    }

    @Transactional(readOnly = true)
    public void assertCanPublishGrades(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermissionWithFallback(
                principal,
                "grade.publish",
                teachingResource(offeringId, teachingClassId),
                PermissionCode.GRADE_PUBLISH,
                resolveAssignmentScope(offeringId, teachingClassId),
                "当前用户无权发布成绩",
                AuditAction.GRADE_PUBLISH);
    }

    @Transactional(readOnly = true)
    public void assertCanImportGrades(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermissionWithFallback(
                principal,
                "grade.import",
                teachingResource(offeringId, teachingClassId),
                PermissionCode.SUBMISSION_GRADE,
                resolveAssignmentScope(offeringId, teachingClassId),
                "当前用户无权导入成绩",
                AuditAction.ASSIGNMENT_GRADES_IMPORTED);
    }

    @Transactional(readOnly = true)
    public void assertCanOverrideGrade(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermissionWithFallback(
                principal,
                "grade.override",
                teachingResource(offeringId, teachingClassId),
                PermissionCode.GRADE_OVERRIDE,
                resolveAssignmentScope(offeringId, teachingClassId),
                "当前用户无权覆盖成绩",
                AuditAction.GRADE_OVERRIDE);
    }

    @Transactional(readOnly = true)
    public void assertCanReadAppeals(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermission(
                principal,
                PermissionCode.APPEAL_READ_CLASS,
                resolveAssignmentScope(offeringId, teachingClassId),
                "当前用户无权查看成绩申诉");
    }

    @Transactional(readOnly = true)
    public void assertCanReviewAppeal(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermission(
                principal,
                PermissionCode.APPEAL_REVIEW,
                resolveAssignmentScope(offeringId, teachingClassId),
                "当前用户无权处理成绩申诉");
    }

    @Transactional(readOnly = true)
    public boolean canManageOfferingAsAdmin(AuthenticatedUserPrincipal principal, Long offeringId) {
        CourseOfferingEntity offering = courseOfferingMapper.selectById(offeringId);
        if (offering == null) {
            return false;
        }
        if (governanceAuthorizationService.canManageUserAt(principal, offering.getOrgCourseUnitId())
                || governanceAuthorizationService.canManageUserAt(principal, offering.getPrimaryCollegeUnitId())) {
            return true;
        }
        Set<Long> managingCollegeIds = courseOfferingCollegeMapMapper
                .selectList(Wrappers.<CourseOfferingCollegeMapEntity>lambdaQuery()
                        .eq(CourseOfferingCollegeMapEntity::getOfferingId, offeringId))
                .stream()
                .map(CourseOfferingCollegeMapEntity::getCollegeUnitId)
                .collect(Collectors.toSet());
        return managingCollegeIds.stream()
                .anyMatch(collegeUnitId -> governanceAuthorizationService.canManageUserAt(principal, collegeUnitId));
    }

    @Transactional(readOnly = true)
    public boolean isInstructor(Long userId, Long offeringId) {
        return hasActiveMemberRole(userId, offeringId, null, CourseMemberRole.INSTRUCTOR);
    }

    @Transactional(readOnly = true)
    public boolean isTeachingAssistantForClass(Long userId, Long offeringId, Long teachingClassId) {
        return hasActiveMemberRole(userId, offeringId, teachingClassId, CourseMemberRole.TA);
    }

    @Transactional(readOnly = true)
    public boolean isActiveCourseMember(Long userId, Long offeringId) {
        return courseMemberMapper.selectOne(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getUserId, userId)
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .eq(CourseMemberEntity::getMemberStatus, CourseMemberStatus.ACTIVE.name())
                        .last("LIMIT 1"))
                != null;
    }

    @Transactional(readOnly = true)
    public boolean isActiveClassMember(Long userId, Long offeringId, Long teachingClassId) {
        return courseMemberMapper.selectOne(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getUserId, userId)
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .eq(CourseMemberEntity::getTeachingClassId, teachingClassId)
                        .eq(CourseMemberEntity::getMemberStatus, CourseMemberStatus.ACTIVE.name())
                        .last("LIMIT 1"))
                != null;
    }

    @Transactional(readOnly = true)
    public boolean canViewAssignment(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        if (teachingClassId == null) {
            return hasPermission(principal, PermissionCode.ASSIGNMENT_READ, resolveOfferingScope(offeringId));
        }
        if (loadFullAssignmentAccessOfferingIds(principal, offeringId).contains(offeringId)) {
            return true;
        }
        return isActiveClassMember(principal.getUserId(), offeringId, teachingClassId);
    }

    @Transactional(readOnly = true)
    public Set<Long> loadFullAssignmentAccessOfferingIds(AuthenticatedUserPrincipal principal, Long offeringId) {
        Set<Long> offeringIds = new LinkedHashSet<>(loadAdminAccessibleOfferingIds(principal, offeringId));
        offeringIds.addAll(courseMemberMapper
                .selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getUserId, principal.getUserId())
                        .in(
                                CourseMemberEntity::getMemberRole,
                                List.of(CourseMemberRole.INSTRUCTOR.name(), CourseMemberRole.OFFERING_TA.name()))
                        .eq(CourseMemberEntity::getMemberStatus, CourseMemberStatus.ACTIVE.name())
                        .eq(offeringId != null, CourseMemberEntity::getOfferingId, offeringId))
                .stream()
                .map(CourseMemberEntity::getOfferingId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return Set.copyOf(offeringIds);
    }

    @Transactional(readOnly = true)
    public void assertSecondaryCollegesCompatible(Long primaryCollegeUnitId, List<Long> secondaryCollegeUnitIds) {
        if (secondaryCollegeUnitIds == null || secondaryCollegeUnitIds.isEmpty()) {
            return;
        }
        OrgUnitEntity primaryCollege = orgUnitMapper.selectById(primaryCollegeUnitId);
        if (primaryCollege == null || !OrgUnitType.COLLEGE.name().equals(primaryCollege.getType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COLLEGE_NOT_FOUND", "主学院不存在");
        }
        Long primarySchoolId = rootSchoolId(primaryCollege.getId());
        for (Long collegeUnitId : secondaryCollegeUnitIds) {
            OrgUnitEntity secondaryCollege = orgUnitMapper.selectById(collegeUnitId);
            if (secondaryCollege == null || !OrgUnitType.COLLEGE.name().equals(secondaryCollege.getType())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "COLLEGE_NOT_FOUND", "共享学院不存在");
            }
            if (collegeUnitId.equals(primaryCollegeUnitId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "COLLEGE_LINK_DUPLICATED", "共享学院不能与主学院重复");
            }
            Long secondarySchoolId = rootSchoolId(secondaryCollege.getId());
            if (!Objects.equals(primarySchoolId, secondarySchoolId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "COLLEGE_LINK_CROSS_SCHOOL", "当前只支持同一学校内的跨学院共同管理");
            }
        }
    }

    private void assertCollegeUnit(Long orgUnitId) {
        OrgUnitEntity orgUnit = orgUnitMapper.selectById(orgUnitId);
        if (orgUnit == null || !OrgUnitType.COLLEGE.name().equals(orgUnit.getType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COLLEGE_NOT_FOUND", "指定学院不存在");
        }
    }

    private TeachingClassEntity requireTeachingClass(Long teachingClassId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(teachingClassId);
        if (teachingClass == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "TEACHING_CLASS_NOT_FOUND", "教学班不存在");
        }
        return teachingClass;
    }

    @Transactional(readOnly = true)
    public TeachingClassEntity requireTeachingClassInOffering(Long offeringId, Long teachingClassId) {
        TeachingClassEntity teachingClass = requireTeachingClass(teachingClassId);
        if (!Objects.equals(offeringId, teachingClass.getOfferingId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "TEACHING_CLASS_NOT_FOUND", "教学班不属于当前开课");
        }
        return teachingClass;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveMemberRole(Long userId, Long offeringId, Long teachingClassId, CourseMemberRole role) {
        return courseMemberMapper.selectOne(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getUserId, userId)
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .eq(CourseMemberEntity::getMemberRole, role.name())
                        .eq(teachingClassId != null, CourseMemberEntity::getTeachingClassId, teachingClassId)
                        .eq(CourseMemberEntity::getMemberStatus, CourseMemberStatus.ACTIVE.name())
                        .last("LIMIT 1"))
                != null;
    }

    @Transactional(readOnly = true)
    public boolean hasReadableStudentMembership(Long userId, Long offeringId, Long teachingClassId) {
        return courseMemberMapper.selectOne(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getUserId, userId)
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .eq(CourseMemberEntity::getMemberRole, CourseMemberRole.STUDENT.name())
                        .eq(teachingClassId != null, CourseMemberEntity::getTeachingClassId, teachingClassId)
                        .in(CourseMemberEntity::getMemberStatus, HISTORY_READABLE_STUDENT_STATUSES)
                        .last("LIMIT 1"))
                != null;
    }

    private Set<Long> loadAdminAccessibleOfferingIds(AuthenticatedUserPrincipal principal, Long offeringId) {
        Set<Long> manageableOrgUnitIds = governanceAuthorizationService.loadManageableOrgUnitIds(principal);
        if (manageableOrgUnitIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> offeringIds = new LinkedHashSet<>(courseOfferingMapper
                .selectList(Wrappers.<CourseOfferingEntity>lambdaQuery()
                        .eq(offeringId != null, CourseOfferingEntity::getId, offeringId)
                        .and(wrapper -> wrapper.in(CourseOfferingEntity::getOrgCourseUnitId, manageableOrgUnitIds)
                                .or()
                                .in(CourseOfferingEntity::getPrimaryCollegeUnitId, manageableOrgUnitIds)))
                .stream()
                .map(CourseOfferingEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        offeringIds.addAll(courseOfferingCollegeMapMapper
                .selectList(Wrappers.<CourseOfferingCollegeMapEntity>lambdaQuery()
                        .eq(offeringId != null, CourseOfferingCollegeMapEntity::getOfferingId, offeringId)
                        .in(CourseOfferingCollegeMapEntity::getCollegeUnitId, manageableOrgUnitIds))
                .stream()
                .map(CourseOfferingCollegeMapEntity::getOfferingId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return Set.copyOf(offeringIds);
    }

    private void assertLabFeatureEnabled(Long offeringId, Long teachingClassId) {
        TeachingClassEntity teachingClass = requireTeachingClassInOffering(offeringId, teachingClassId);
        if (!Boolean.TRUE.equals(teachingClass.getLabEnabled())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "LAB_DISABLED", "当前教学班未启用实验功能");
        }
    }

    private void assertAnnouncementFeatureEnabled(Long offeringId, Long teachingClassId) {
        TeachingClassEntity teachingClass = requireTeachingClassInOffering(offeringId, teachingClassId);
        if (!Boolean.TRUE.equals(teachingClass.getAnnouncementEnabled())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ANNOUNCEMENT_DISABLED", "当前教学班未启用课程公告功能");
        }
    }

    private void assertResourceFeatureEnabled(Long offeringId, Long teachingClassId) {
        TeachingClassEntity teachingClass = requireTeachingClassInOffering(offeringId, teachingClassId);
        if (!Boolean.TRUE.equals(teachingClass.getResourceEnabled())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "RESOURCE_DISABLED", "当前教学班未启用课程资源功能");
        }
    }

    private void assertDiscussionFeatureEnabled(Long offeringId, Long teachingClassId) {
        TeachingClassEntity teachingClass = requireTeachingClassInOffering(offeringId, teachingClassId);
        if (!Boolean.TRUE.equals(teachingClass.getDiscussionEnabled())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "DISCUSSION_DISABLED", "当前教学班未启用课程讨论功能");
        }
    }

    private void assertTeachingFeatureManagePermission(
            AuthenticatedUserPrincipal principal,
            String permissionCode,
            Long offeringId,
            Long teachingClassId,
            String message,
            TeachingClassFeatureAssertion featureAssertion) {
        if (featureAssertion != null) {
            featureAssertion.assertEnabled(offeringId, teachingClassId);
        }
        AuthorizationResult result = permissionAuthorizationService.authorize(
                principal, permissionCode, teachingResource(offeringId, teachingClassId), currentContext());
        if (result.allowed()) {
            return;
        }
        if ("DENY_NO_ROLE_BINDING".equals(result.reasonCode())
                && canFallbackToLegacyAuthorization(principal)
                && canManageTeachingFeatureLegacy(principal, offeringId, teachingClassId)) {
            return;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    private void assertTeachingClassReadPermission(
            AuthenticatedUserPrincipal principal,
            String permissionCode,
            Long offeringId,
            Long teachingClassId,
            String message) {
        AuthorizationResult result =
                permissionAuthorizationService.authorize(principal, permissionCode, classResource(teachingClassId), currentContext());
        if (result.allowed()) {
            return;
        }
        if ("DENY_NO_ROLE_BINDING".equals(result.reasonCode())
                && canFallbackToLegacyAuthorization(principal)
                && canAccessTeachingClassLegacy(principal, offeringId, teachingClassId)) {
            return;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    private void assertOfferingWideTeachingReadPermission(
            AuthenticatedUserPrincipal principal,
            Long offeringId,
            String permissionCode,
            Predicate<TeachingClassEntity> featureEnabled,
            String disabledCode,
            String disabledMessage,
            String forbiddenMessage) {
        if (hasScopedPermission(principal, permissionCode, offeringResource(offeringId))) {
            return;
        }

        List<TeachingClassEntity> activeClasses =
                loadActiveTeachingClasses(principal == null ? null : principal.getUserId(), offeringId);
        boolean anyEnabledClass = activeClasses.stream().anyMatch(featureEnabled);
        boolean anyAuthorizedEnabledClass = activeClasses.stream()
                .filter(featureEnabled)
                .map(TeachingClassEntity::getId)
                .anyMatch(classId -> hasScopedPermission(principal, permissionCode, classResource(classId)));
        if (anyAuthorizedEnabledClass) {
            return;
        }

        if (canFallbackToLegacyAuthorization(principal)) {
            if (canManageOfferingAsAdmin(principal, offeringId) || isInstructor(principal.getUserId(), offeringId)) {
                return;
            }
            if (!isActiveCourseMember(principal.getUserId(), offeringId)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", forbiddenMessage);
            }
            if (activeClasses.isEmpty() || anyEnabledClass) {
                return;
            }
            throw new BusinessException(HttpStatus.FORBIDDEN, disabledCode, disabledMessage);
        }

        if (!activeClasses.isEmpty() && !anyEnabledClass) {
            throw new BusinessException(HttpStatus.FORBIDDEN, disabledCode, disabledMessage);
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", forbiddenMessage);
    }

    private boolean hasScopedPermission(
            AuthenticatedUserPrincipal principal, String permissionCode, AuthorizationResourceRef resourceRef) {
        return permissionAuthorizationService.authorize(principal, permissionCode, resourceRef, currentContext())
                .allowed();
    }

    private List<TeachingClassEntity> loadActiveTeachingClasses(Long userId, Long offeringId) {
        if (userId == null || offeringId == null) {
            return List.of();
        }
        Set<Long> activeClassIds = courseMemberMapper
                .selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getUserId, userId)
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .isNotNull(CourseMemberEntity::getTeachingClassId)
                        .eq(CourseMemberEntity::getMemberStatus, CourseMemberStatus.ACTIVE.name())
                        .select(CourseMemberEntity::getTeachingClassId))
                .stream()
                .map(CourseMemberEntity::getTeachingClassId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (activeClassIds.isEmpty()) {
            return List.of();
        }
        return teachingClassMapper.selectBatchIds(activeClassIds);
    }

    private boolean canManageTeachingFeatureLegacy(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        if (teachingClassId == null) {
            return hasPermission(principal, PermissionCode.OFFERING_MANAGE, resolveOfferingScope(offeringId));
        }
        return hasPermission(principal, PermissionCode.CLASS_MANAGE, resolveTeachingClassScope(offeringId, teachingClassId))
                || hasPermission(principal, PermissionCode.OFFERING_MANAGE, resolveOfferingScope(offeringId));
    }

    private boolean canAccessTeachingClassLegacy(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        return canManageOfferingAsAdmin(principal, offeringId)
                || isInstructor(principal.getUserId(), offeringId)
                || isTeachingAssistantForClass(principal.getUserId(), offeringId, teachingClassId)
                || isActiveClassMember(principal.getUserId(), offeringId, teachingClassId);
    }

    private void assertPermission(
            AuthenticatedUserPrincipal principal, PermissionCode permission, ScopeRef scope, String message) {
        if (!hasPermission(principal, permission, scope)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
        }
    }

    private void assertPermissionWithFallback(
            AuthenticatedUserPrincipal principal,
            String permissionCode,
            AuthorizationResourceRef resourceRef,
            PermissionCode legacyPermission,
            ScopeRef legacyScope,
            String message,
            AuditAction deniedAuditAction) {
        AuthorizationResult result =
                permissionAuthorizationService.authorize(principal, permissionCode, resourceRef, currentContext());
        if (result.allowed()) {
            return;
        }
        if (!"DENY_NO_ROLE_BINDING".equals(result.reasonCode())) {
            recordDeniedAudit(principal, deniedAuditAction, permissionCode, resourceRef, result);
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
        }
        if (canFallbackToLegacyAuthorization(principal)
                && legacyPermission != null
                && hasPermission(principal, legacyPermission, legacyScope)) {
            return;
        }
        recordDeniedAudit(principal, deniedAuditAction, permissionCode, resourceRef, result);
        throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    private void assertAnyPermission(
            AuthenticatedUserPrincipal principal, List<PermissionCode> permissions, ScopeRef scope, String message) {
        if (!hasAnyPermission(principal, permissions, scope)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
        }
    }

    private boolean hasPermission(AuthenticatedUserPrincipal principal, PermissionCode permission, ScopeRef scope) {
        AuthorizationResult modernResult = authorizeMappedPermission(principal, permission, scope);
        if (modernResult != null) {
            if (modernResult.allowed()) {
                return true;
            }
            if (!"DENY_NO_ROLE_BINDING".equals(modernResult.reasonCode())
                    || !canFallbackToLegacyAuthorization(principal)) {
                return false;
            }
        }
        return authorizationService
                .decide(AuthorizationRequest.forPermission(principal, permission, scope))
                .allowed();
    }

    private boolean hasAnyPermission(
            AuthenticatedUserPrincipal principal, List<PermissionCode> permissions, ScopeRef scope) {
        return permissions.stream().anyMatch(permission -> hasPermission(principal, permission, scope));
    }

    /**
     * 仅当当前会话尚未基于 role_bindings 构建权限快照时才允许旧矩阵兜底。
     * 否则会把已经迁移到新权限核心的用户重新混回旧治理/成员解析链，造成新旧权限混用。
     */
    private boolean canFallbackToLegacyAuthorization(AuthenticatedUserPrincipal principal) {
        return principal != null && !principal.isRoleBindingSnapshot();
    }

    private AuthorizationResult authorizeMappedPermission(
            AuthenticatedUserPrincipal principal, PermissionCode permission, ScopeRef scope) {
        if (principal == null || permission == null || scope == null) {
            return null;
        }
        String modernPermissionCode = modernPermissionCode(permission);
        AuthorizationResourceRef resourceRef = resourceForScope(scope);
        if (modernPermissionCode == null || resourceRef == null) {
            return null;
        }
        return permissionAuthorizationService.authorize(principal, modernPermissionCode, resourceRef, currentContext());
    }

    private AuthorizationContext currentContext() {
        return AuthorizationContext.of(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private String modernPermissionCode(PermissionCode permission) {
        return switch (permission) {
            case OFFERING_READ -> "offering.read";
            case OFFERING_MANAGE -> "offering.manage";
            case CLASS_READ -> "class.read";
            case CLASS_MANAGE -> "class.manage";
            case MEMBER_READ -> "member.read";
            case MEMBER_MANAGE -> "member.manage";
            case ASSIGNMENT_READ -> "task.read";
            case ASSIGNMENT_CREATE -> "task.create";
            case ASSIGNMENT_UPDATE -> "task.edit";
            case ASSIGNMENT_PUBLISH -> "task.publish";
            case ASSIGNMENT_CLOSE -> "task.close";
            case QUESTION_BANK_MANAGE, QUESTION_MANAGE -> "question_bank.manage";
            case SUBMISSION_READ_CLASS, SUBMISSION_READ_OFFERING -> "submission.read";
            case SUBMISSION_CODE_READ_SENSITIVE -> "submission.read_source";
            case SUBMISSION_GRADE -> "submission.grade";
            case GRADE_EXPORT_CLASS, GRADE_EXPORT_OFFERING -> "grade.export";
            case GRADE_OVERRIDE -> "grade.override";
            case GRADE_PUBLISH -> "grade.publish";
            case APPEAL_READ_OWN, APPEAL_READ_CLASS -> "appeal.read";
            case APPEAL_REVIEW -> "appeal.review";
            case JUDGE_PROFILE_MANAGE -> "judge.config";
            case LAB_READ -> "lab.read";
            case LAB_MANAGE -> "lab.manage";
            case LAB_REPORT_REVIEW -> "lab.report.review";
            default -> null;
        };
    }

    private AuthorizationResourceRef offeringResource(Long offeringId) {
        return new AuthorizationResourceRef(AuthorizationResourceType.OFFERING, offeringId);
    }

    private AuthorizationResourceRef classResource(Long teachingClassId) {
        return new AuthorizationResourceRef(AuthorizationResourceType.CLASS, teachingClassId);
    }

    private AuthorizationResourceRef teachingResource(Long offeringId, Long teachingClassId) {
        return teachingClassId == null ? offeringResource(offeringId) : classResource(teachingClassId);
    }

    private AuthorizationResourceRef resourceForScope(ScopeRef scope) {
        return switch (scope.type()) {
            case PLATFORM -> new AuthorizationResourceRef(AuthorizationResourceType.PLATFORM, scope.refId());
            case SCHOOL -> new AuthorizationResourceRef(AuthorizationResourceType.SCHOOL, scope.refId());
            case COLLEGE -> new AuthorizationResourceRef(AuthorizationResourceType.COLLEGE, scope.refId());
            case COURSE -> new AuthorizationResourceRef(AuthorizationResourceType.COURSE, scope.refId());
            case OFFERING -> new AuthorizationResourceRef(AuthorizationResourceType.OFFERING, scope.refId());
            case CLASS -> new AuthorizationResourceRef(AuthorizationResourceType.CLASS, scope.refId());
        };
    }

    private void recordDeniedAudit(
            AuthenticatedUserPrincipal principal,
            AuditAction deniedAuditAction,
            String permissionCode,
            AuthorizationResourceRef resourceRef,
            AuthorizationResult result) {
        if (deniedAuditAction == null) {
            return;
        }
        sensitiveOperationAuditService.recordDenied(
                principal, deniedAuditAction, permissionCode, resourceRef, result, Map.of());
    }

    @FunctionalInterface
    private interface TeachingClassFeatureAssertion {
        void assertEnabled(Long offeringId, Long teachingClassId);
    }

    private ScopeRef resolveAssignmentScope(Long offeringId, Long teachingClassId) {
        return teachingClassId == null
                ? resolveOfferingScope(offeringId)
                : resolveTeachingClassScope(offeringId, teachingClassId);
    }

    private ScopeRef resolveSubmissionScope(Long offeringId, Long teachingClassId) {
        return teachingClassId == null
                ? resolveOfferingScope(offeringId)
                : resolveTeachingClassScope(offeringId, teachingClassId);
    }

    private ScopeRef resolveMemberScope(Long offeringId, Long teachingClassId) {
        return teachingClassId == null
                ? resolveOfferingScope(offeringId)
                : resolveTeachingClassScope(offeringId, teachingClassId);
    }

    private ScopeRef resolveTeachingClassScope(Long offeringId, Long teachingClassId) {
        TeachingClassEntity teachingClass = requireTeachingClassInOffering(offeringId, teachingClassId);
        ScopeRef offeringScope = resolveOfferingScope(offeringId);
        List<ScopeRef> ancestors = new ArrayList<>();
        ancestors.add(new ScopeRef(AuthorizationScopeType.OFFERING, offeringId));
        ancestors.addAll(offeringScope.ancestors());
        return new ScopeRef(AuthorizationScopeType.CLASS, teachingClass.getId(), deduplicateScopes(ancestors));
    }

    private ScopeRef resolveOfferingScope(Long offeringId) {
        CourseOfferingEntity offering = courseOfferingMapper.selectById(offeringId);
        if (offering == null) {
            return new ScopeRef(AuthorizationScopeType.OFFERING, offeringId);
        }
        List<ScopeRef> ancestors = new ArrayList<>();
        collectOrgScopes(ancestors, offering.getOrgCourseUnitId());
        collectOrgScopes(ancestors, offering.getPrimaryCollegeUnitId());
        courseOfferingCollegeMapMapper
                .selectList(Wrappers.<CourseOfferingCollegeMapEntity>lambdaQuery()
                        .eq(CourseOfferingCollegeMapEntity::getOfferingId, offeringId))
                .stream()
                .map(CourseOfferingCollegeMapEntity::getCollegeUnitId)
                .forEach(collegeUnitId -> collectOrgScopes(ancestors, collegeUnitId));
        return new ScopeRef(AuthorizationScopeType.OFFERING, offeringId, deduplicateScopes(ancestors));
    }

    private void collectOrgScopes(List<ScopeRef> scopes, Long orgUnitId) {
        Long cursor = orgUnitId;
        while (cursor != null) {
            OrgUnitEntity orgUnit = orgUnitMapper.selectById(cursor);
            if (orgUnit == null) {
                return;
            }
            AuthorizationScopeType scopeType = mapScopeType(orgUnit.getType());
            if (scopeType != null) {
                scopes.add(new ScopeRef(scopeType, orgUnit.getId()));
            }
            cursor = orgUnit.getParentId();
        }
    }

    private List<ScopeRef> deduplicateScopes(List<ScopeRef> scopes) {
        Map<String, ScopeRef> ordered = new LinkedHashMap<>();
        for (ScopeRef scope : scopes) {
            ordered.putIfAbsent(scope.type().name() + ":" + scope.refId(), scope);
        }
        return List.copyOf(ordered.values());
    }

    private AuthorizationScopeType mapScopeType(String orgType) {
        if (orgType == null) {
            return null;
        }
        OrgUnitType orgUnitType = OrgUnitType.valueOf(orgType);
        return switch (orgUnitType) {
            case SCHOOL -> AuthorizationScopeType.SCHOOL;
            case COLLEGE -> AuthorizationScopeType.COLLEGE;
            case COURSE -> AuthorizationScopeType.COURSE;
            case CLASS -> AuthorizationScopeType.CLASS;
        };
    }

    private Long rootSchoolId(Long orgUnitId) {
        Long cursor = orgUnitId;
        Long latest = cursor;
        while (cursor != null) {
            latest = cursor;
            OrgUnitEntity current = orgUnitMapper.selectById(cursor);
            cursor = current == null ? null : current.getParentId();
        }
        return latest;
    }
}
