package com.aubb.server.modules.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.course.domain.member.CourseMemberStatus;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberEntity;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingCollegeMapEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingCollegeMapMapper;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingMapper;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.AuthorizationService;
import com.aubb.server.modules.identityaccess.application.iam.GovernanceAuthorizationService;
import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityView;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.aubb.server.modules.organization.infrastructure.OrgUnitMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseAuthorizationServiceTests {

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private GovernanceAuthorizationService governanceAuthorizationService;

    @Mock
    private CourseOfferingMapper courseOfferingMapper;

    @Mock
    private CourseOfferingCollegeMapMapper courseOfferingCollegeMapMapper;

    @Mock
    private CourseMemberMapper courseMemberMapper;

    @Mock
    private TeachingClassMapper teachingClassMapper;

    @Mock
    private OrgUnitMapper orgUnitMapper;

    @Test
    void loadsFullAssignmentAccessOfferingIdsFromAdminInstructorAndOfferingTaScopes() {
        when(governanceAuthorizationService.loadManageableOrgUnitIds(any())).thenReturn(Set.of(20L, 30L));
        when(courseOfferingMapper.selectList(any())).thenReturn(List.of(offering(7L, 20L, 99L)));
        when(courseOfferingCollegeMapMapper.selectList(any())).thenReturn(List.of(collegeMap(8L, 30L)));
        when(courseMemberMapper.selectList(any()))
                .thenReturn(List.of(
                        courseMember(9L, 10L, CourseMemberRole.INSTRUCTOR),
                        courseMember(11L, 10L, CourseMemberRole.OFFERING_TA)));

        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                governanceAuthorizationService,
                courseOfferingMapper,
                courseOfferingCollegeMapMapper,
                courseMemberMapper,
                teachingClassMapper,
                orgUnitMapper);
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                10L,
                "teacher-main",
                "Teacher Main",
                20L,
                AccountStatus.ACTIVE,
                null,
                List.of(new ScopeIdentityView("COLLEGE_ADMIN", 20L, "COLLEGE", "Engineering")));

        Set<Long> offeringIds = service.loadFullAssignmentAccessOfferingIds(principal, null);

        assertThat(offeringIds).containsExactlyInAnyOrder(7L, 8L, 9L, 11L);
    }

    private CourseOfferingEntity offering(Long id, Long primaryCollegeUnitId, Long orgCourseUnitId) {
        CourseOfferingEntity entity = new CourseOfferingEntity();
        entity.setId(id);
        entity.setPrimaryCollegeUnitId(primaryCollegeUnitId);
        entity.setOrgCourseUnitId(orgCourseUnitId);
        return entity;
    }

    private CourseOfferingCollegeMapEntity collegeMap(Long offeringId, Long collegeUnitId) {
        CourseOfferingCollegeMapEntity entity = new CourseOfferingCollegeMapEntity();
        entity.setOfferingId(offeringId);
        entity.setCollegeUnitId(collegeUnitId);
        return entity;
    }

    private CourseMemberEntity courseMember(Long offeringId, Long userId, CourseMemberRole role) {
        CourseMemberEntity entity = new CourseMemberEntity();
        entity.setOfferingId(offeringId);
        entity.setUserId(userId);
        entity.setMemberRole(role.name());
        entity.setMemberStatus(CourseMemberStatus.ACTIVE.name());
        return entity;
    }
}
