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
import com.aubb.server.modules.identityaccess.application.iam.GovernanceAuthorizationService;
import com.aubb.server.modules.organization.domain.OrgUnitType;
import com.aubb.server.modules.organization.infrastructure.OrgUnitEntity;
import com.aubb.server.modules.organization.infrastructure.OrgUnitMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
        if (!canManageOfferingAsAdmin(principal, offeringId) && !isInstructor(principal.getUserId(), offeringId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权管理该课程");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanManageMembers(AuthenticatedUserPrincipal principal, Long offeringId) {
        assertCanManageOffering(principal, offeringId);
    }

    @Transactional(readOnly = true)
    public void assertCanManageAssignments(AuthenticatedUserPrincipal principal, Long offeringId) {
        assertCanManageOffering(principal, offeringId);
    }

    @Transactional(readOnly = true)
    public void assertCanGradeSubmission(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        if (canManageOfferingAsAdmin(principal, offeringId) || isInstructor(principal.getUserId(), offeringId)) {
            return;
        }
        if (teachingClassId != null
                && isTeachingAssistantForClass(principal.getUserId(), offeringId, teachingClassId)) {
            return;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权批改该提交");
    }

    @Transactional(readOnly = true)
    public void assertCanManageClassFeatures(AuthenticatedUserPrincipal principal, Long teachingClassId) {
        TeachingClassEntity teachingClass = requireTeachingClass(teachingClassId);
        assertCanManageOffering(principal, teachingClass.getOfferingId());
    }

    @Transactional(readOnly = true)
    public void assertCanViewMembers(AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId) {
        if (canManageOfferingAsAdmin(principal, offeringId) || isInstructor(principal.getUserId(), offeringId)) {
            return;
        }
        if (teachingClassId != null
                && isTeachingAssistantForClass(principal.getUserId(), offeringId, teachingClassId)) {
            return;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该课程成员");
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
        if (canManageOfferingAsAdmin(principal, offeringId) || isInstructor(principal.getUserId(), offeringId)) {
            return true;
        }
        if (teachingClassId == null) {
            return isActiveCourseMember(principal.getUserId(), offeringId);
        }
        return isActiveClassMember(principal.getUserId(), offeringId, teachingClassId);
    }

    @Transactional(readOnly = true)
    public void assertSecondaryCollegesCompatible(Long primaryCollegeUnitId, List<Long> secondaryCollegeUnitIds) {
        if (secondaryCollegeUnitIds == null || secondaryCollegeUnitIds.isEmpty()) {
            return;
        }
        Map<Long, OrgUnitEntity> orgUnitIndex = governanceAuthorizationService.loadOrgUnitIndex();
        OrgUnitEntity primaryCollege = orgUnitIndex.get(primaryCollegeUnitId);
        if (primaryCollege == null || !OrgUnitType.COLLEGE.name().equals(primaryCollege.getType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COLLEGE_NOT_FOUND", "主学院不存在");
        }
        Long primarySchoolId = rootSchoolId(primaryCollege.getId(), orgUnitIndex);
        for (Long collegeUnitId : secondaryCollegeUnitIds) {
            OrgUnitEntity secondaryCollege = orgUnitIndex.get(collegeUnitId);
            if (secondaryCollege == null || !OrgUnitType.COLLEGE.name().equals(secondaryCollege.getType())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "COLLEGE_NOT_FOUND", "共享学院不存在");
            }
            if (collegeUnitId.equals(primaryCollegeUnitId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "COLLEGE_LINK_DUPLICATED", "共享学院不能与主学院重复");
            }
            Long secondarySchoolId = rootSchoolId(secondaryCollege.getId(), orgUnitIndex);
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

    private Long rootSchoolId(Long orgUnitId, Map<Long, OrgUnitEntity> index) {
        Long cursor = orgUnitId;
        Long latest = cursor;
        while (cursor != null) {
            latest = cursor;
            OrgUnitEntity current = index.get(cursor);
            if (current == null) {
                current = orgUnitMapper.selectById(cursor);
            }
            cursor = current == null ? null : current.getParentId();
        }
        return latest;
    }
}
