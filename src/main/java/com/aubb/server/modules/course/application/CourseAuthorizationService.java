package com.aubb.server.modules.course.application;

import com.aubb.server.common.exception.BusinessException;
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
import com.aubb.server.modules.identityaccess.application.iam.GovernanceAuthorizationService;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import com.aubb.server.modules.identityaccess.domain.authz.PermissionCode;
import com.aubb.server.modules.organization.domain.OrgUnitType;
import com.aubb.server.modules.organization.infrastructure.OrgUnitEntity;
import com.aubb.server.modules.organization.infrastructure.OrgUnitMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseAuthorizationService {

    private final AuthorizationService authorizationService;
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
        assertPermission(principal, PermissionCode.OFFERING_MANAGE, resolveOfferingScope(offeringId), "当前用户无权管理该课程");
    }

    @Transactional(readOnly = true)
    public void assertCanManageMembers(AuthenticatedUserPrincipal principal, Long offeringId) {
        assertPermission(principal, PermissionCode.MEMBER_MANAGE, resolveOfferingScope(offeringId), "当前用户无权管理课程成员");
    }

    @Transactional(readOnly = true)
    public void assertCanManageAssignments(AuthenticatedUserPrincipal principal, Long offeringId) {
        assertPermission(
                principal, PermissionCode.ASSIGNMENT_UPDATE, resolveOfferingScope(offeringId), "当前用户无权管理该课程作业");
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
        assertPermission(
                principal,
                PermissionCode.ASSIGNMENT_CREATE,
                resolveAssignmentScope(offeringId, teachingClassId),
                "当前用户无权创建该课程作业");
    }

    @Transactional(readOnly = true)
    public void assertCanUpdateAssignment(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermission(
                principal,
                PermissionCode.ASSIGNMENT_UPDATE,
                resolveAssignmentScope(offeringId, teachingClassId),
                "当前用户无权编辑该课程作业");
    }

    @Transactional(readOnly = true)
    public void assertCanPublishAssignment(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermission(
                principal,
                PermissionCode.ASSIGNMENT_PUBLISH,
                resolveAssignmentScope(offeringId, teachingClassId),
                "当前用户无权发布该课程作业");
    }

    @Transactional(readOnly = true)
    public void assertCanCloseAssignment(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermission(
                principal,
                PermissionCode.ASSIGNMENT_CLOSE,
                resolveAssignmentScope(offeringId, teachingClassId),
                "当前用户无权关闭该课程作业");
    }

    @Transactional(readOnly = true)
    public void assertCanManageQuestionBank(AuthenticatedUserPrincipal principal, Long offeringId) {
        assertPermission(principal, PermissionCode.QUESTION_MANAGE, resolveOfferingScope(offeringId), "当前用户无权管理题库");
    }

    @Transactional(readOnly = true)
    public void assertCanManageJudgeProfiles(AuthenticatedUserPrincipal principal, Long offeringId) {
        assertPermission(
                principal, PermissionCode.JUDGE_PROFILE_MANAGE, resolveOfferingScope(offeringId), "当前用户无权管理评测环境");
    }

    @Transactional(readOnly = true)
    public void assertCanManageAnnouncements(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertCanManageOffering(principal, offeringId);
        if (teachingClassId != null) {
            assertAnnouncementFeatureEnabled(offeringId, teachingClassId);
        }
    }

    @Transactional(readOnly = true)
    public void assertCanViewAnnouncementsForClass(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertAnnouncementFeatureEnabled(offeringId, teachingClassId);
        if (canManageOfferingAsAdmin(principal, offeringId)
                || isInstructor(principal.getUserId(), offeringId)
                || isTeachingAssistantForClass(principal.getUserId(), offeringId, teachingClassId)
                || isActiveClassMember(principal.getUserId(), offeringId, teachingClassId)) {
            return;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该课程公告");
    }

    @Transactional(readOnly = true)
    public void assertCanManageResources(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertCanManageOffering(principal, offeringId);
        if (teachingClassId != null) {
            assertResourceFeatureEnabled(offeringId, teachingClassId);
        }
    }

    @Transactional(readOnly = true)
    public void assertCanViewResourcesForClass(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertResourceFeatureEnabled(offeringId, teachingClassId);
        if (canManageOfferingAsAdmin(principal, offeringId)
                || isInstructor(principal.getUserId(), offeringId)
                || isTeachingAssistantForClass(principal.getUserId(), offeringId, teachingClassId)
                || isActiveClassMember(principal.getUserId(), offeringId, teachingClassId)) {
            return;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该课程资源");
    }

    @Transactional(readOnly = true)
    public void assertCanManageDiscussions(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertCanManageOffering(principal, offeringId);
        if (teachingClassId != null) {
            assertDiscussionFeatureEnabled(offeringId, teachingClassId);
        }
    }

    @Transactional(readOnly = true)
    public void assertCanParticipateDiscussionsForClass(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertDiscussionFeatureEnabled(offeringId, teachingClassId);
        if (canManageOfferingAsAdmin(principal, offeringId)
                || isInstructor(principal.getUserId(), offeringId)
                || isTeachingAssistantForClass(principal.getUserId(), offeringId, teachingClassId)
                || isActiveClassMember(principal.getUserId(), offeringId, teachingClassId)) {
            return;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权参与该课程讨论");
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
        assertPermission(
                principal,
                PermissionCode.SUBMISSION_GRADE,
                resolveSubmissionScope(offeringId, teachingClassId),
                "当前用户无权批改该提交");
    }

    @Transactional(readOnly = true)
    public void assertCanReadSubmission(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        ScopeRef scope = resolveSubmissionScope(offeringId, teachingClassId);
        if (teachingClassId == null) {
            assertPermission(principal, PermissionCode.SUBMISSION_READ_OFFERING, scope, "当前用户无权查看该提交");
            return;
        }
        assertAnyPermission(
                principal,
                List.of(PermissionCode.SUBMISSION_READ_CLASS, PermissionCode.SUBMISSION_READ_OFFERING),
                scope,
                "当前用户无权查看该提交");
    }

    @Transactional(readOnly = true)
    public void assertCanReadSensitiveSubmission(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermission(
                principal,
                PermissionCode.SUBMISSION_CODE_READ_SENSITIVE,
                resolveSubmissionScope(offeringId, teachingClassId),
                "当前用户无权查看敏感提交详情");
    }

    @Transactional(readOnly = true)
    public void assertCanRejudgeSubmission(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermission(
                principal,
                PermissionCode.SUBMISSION_REJUDGE,
                resolveSubmissionScope(offeringId, teachingClassId),
                "当前用户无权重判该提交");
    }

    @Transactional(readOnly = true)
    public void assertCanManageClassFeatures(AuthenticatedUserPrincipal principal, Long teachingClassId) {
        TeachingClassEntity teachingClass = requireTeachingClass(teachingClassId);
        assertPermission(
                principal,
                PermissionCode.CLASS_MANAGE,
                resolveTeachingClassScope(teachingClass.getOfferingId(), teachingClassId),
                "当前用户无权管理该教学班");
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
        assertPermission(
                principal, PermissionCode.GRADE_EXPORT_OFFERING, resolveOfferingScope(offeringId), "当前用户无权查看课程成绩册");
    }

    @Transactional(readOnly = true)
    public void assertCanViewTeachingClassGradebook(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertAnyPermission(
                principal,
                List.of(PermissionCode.GRADE_EXPORT_CLASS, PermissionCode.GRADE_EXPORT_OFFERING),
                resolveTeachingClassScope(offeringId, teachingClassId),
                "当前用户无权查看教学班成绩册");
    }

    @Transactional(readOnly = true)
    public void assertCanPublishGrades(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermission(
                principal,
                PermissionCode.GRADE_PUBLISH,
                resolveAssignmentScope(offeringId, teachingClassId),
                "当前用户无权发布成绩");
    }

    @Transactional(readOnly = true)
    public void assertCanOverrideGrade(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        assertPermission(
                principal,
                PermissionCode.GRADE_OVERRIDE,
                resolveAssignmentScope(offeringId, teachingClassId),
                "当前用户无权覆盖成绩");
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

    private void assertPermission(
            AuthenticatedUserPrincipal principal, PermissionCode permission, ScopeRef scope, String message) {
        if (!hasPermission(principal, permission, scope)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
        }
    }

    private void assertAnyPermission(
            AuthenticatedUserPrincipal principal, List<PermissionCode> permissions, ScopeRef scope, String message) {
        if (!hasAnyPermission(principal, permissions, scope)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
        }
    }

    private boolean hasPermission(AuthenticatedUserPrincipal principal, PermissionCode permission, ScopeRef scope) {
        return authorizationService
                .decide(AuthorizationRequest.forPermission(principal, permission, scope))
                .allowed();
    }

    private boolean hasAnyPermission(
            AuthenticatedUserPrincipal principal, List<PermissionCode> permissions, ScopeRef scope) {
        return permissions.stream().anyMatch(permission -> hasPermission(principal, permission, scope));
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
