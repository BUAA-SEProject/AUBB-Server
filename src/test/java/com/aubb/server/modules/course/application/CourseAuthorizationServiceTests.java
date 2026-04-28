package com.aubb.server.modules.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.audit.application.SensitiveOperationAuditService;
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
import com.aubb.server.modules.identityaccess.application.authz.AuthorizationService;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResult;
import com.aubb.server.modules.identityaccess.application.authz.core.PermissionAuthorizationService;
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
    private PermissionAuthorizationService permissionAuthorizationService;

    @Mock
    private SensitiveOperationAuditService sensitiveOperationAuditService;

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
    void assertCanManageAssignmentsShouldNotFallbackToLegacyWhenPrincipalUsesRoleBindingSnapshot() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                true);
        when(permissionAuthorizationService.authorize(eq(principal), eq("task.edit"), any(), any()))
                .thenReturn(AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), false));

        assertThatThrownBy(() -> service.assertCanManageAssignments(principal, 12L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前用户无权管理该课程作业");
        verify(authorizationService, never()).decide(any());
    }

    @Test
    void assertCanManageAssignmentsShouldNotFallbackToLegacyWhenPrincipalHasNoRoleBindingSnapshot() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                false);
        when(permissionAuthorizationService.authorize(eq(principal), eq("task.edit"), any(), any()))
                .thenReturn(AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), false));

        assertThatThrownBy(() -> service.assertCanManageAssignments(principal, 12L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前用户无权管理该课程作业");
        verify(authorizationService, never()).decide(any());
    }

    @Test
    void assertCanManageMembersShouldNotFallbackToLegacyWhenPrincipalHasNoRoleBindingSnapshot() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                false);
        when(permissionAuthorizationService.authorize(eq(principal), eq("member.manage"), any(), any()))
                .thenReturn(AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), false));

        assertThatThrownBy(() -> service.assertCanManageMembers(principal, 12L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前用户无权管理课程成员");
        verify(authorizationService, never()).decide(any());
    }

    @Test
    void canManageMembersShouldNotFallbackToLegacyWhenPrincipalHasNoRoleBindingSnapshot() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                false);
        when(permissionAuthorizationService.authorize(eq(principal), eq("member.manage"), any(), any()))
                .thenReturn(AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), false));

        assertThat(service.canManageMembers(principal, 12L)).isFalse();
        verify(authorizationService, never()).decide(any());
    }

    @Test
    void assertCanManageQuestionBankShouldNotFallbackToLegacyWhenPrincipalHasNoRoleBindingSnapshot() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                false);
        when(permissionAuthorizationService.authorize(eq(principal), eq("question_bank.manage"), any(), any()))
                .thenReturn(AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), false));

        assertThatThrownBy(() -> service.assertCanManageQuestionBank(principal, 12L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前用户无权管理题库");
        verify(authorizationService, never()).decide(any());
    }

    @Test
    void assertCanManageClassFeaturesShouldNotFallbackToLegacyWhenPrincipalHasNoRoleBindingSnapshot() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                false);
        when(teachingClassMapper.selectById(34L)).thenReturn(teachingClass(34L, 12L, true));
        when(permissionAuthorizationService.authorize(eq(principal), eq("class.manage"), any(), any()))
                .thenReturn(AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), false));

        assertThatThrownBy(() -> service.assertCanManageClassFeatures(principal, 34L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前用户无权管理该教学班");
        verify(authorizationService, never()).decide(any());
    }

    @Test
    void assertCanOverrideGradeShouldNotFallbackToLegacyWhenPrincipalHasNoRoleBindingSnapshot() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                false);
        when(permissionAuthorizationService.authorize(eq(principal), eq("grade.override"), any(), any()))
                .thenReturn(AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), true));

        assertThatThrownBy(() -> service.assertCanOverrideGrade(principal, 12L, 34L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前用户无权覆盖成绩");
        verify(authorizationService, never()).decide(any());
    }

    @Test
    void assertCanManageJudgeProfilesShouldNotFallbackToLegacyWhenPrincipalHasNoRoleBindingSnapshot() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                false);
        when(permissionAuthorizationService.authorize(eq(principal), eq("judge.config"), any(), any()))
                .thenReturn(AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), true));

        assertThatThrownBy(() -> service.assertCanManageJudgeProfiles(principal, 12L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前用户无权管理评测环境");
        verify(authorizationService, never()).decide(any());
    }

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
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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

    @Test
    void assertCanReadAppealsShouldUseModernPermissionBridgeForRoleBindingSnapshotPrincipal() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                true);
        when(permissionAuthorizationService.authorize(eq(principal), eq("appeal.read"), any(), any()))
                .thenReturn(
                        AuthorizationResult.allow("ALLOW_BY_SCOPE_ROLE", List.of("offering_teacher"), List.of(), true));

        service.assertCanReadAppeals(principal, 12L, 34L);

        verify(authorizationService, never()).decide(any());
    }

    @Test
    void assertCanReadAppealsShouldNotFallbackToLegacyWhenPrincipalHasNoRoleBindingSnapshot() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                false);
        when(permissionAuthorizationService.authorize(eq(principal), eq("appeal.read"), any(), any()))
                .thenReturn(AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), false));

        assertThatThrownBy(() -> service.assertCanReadAppeals(principal, 12L, 34L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前用户无权查看成绩申诉");

        verify(authorizationService, never()).decide(any());
    }

    @Test
    void canViewOfferingGradebookShouldUseModernPermissionBridgeForRoleBindingSnapshotPrincipal() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                true);
        when(permissionAuthorizationService.authorize(eq(principal), eq("grade.export"), any(), any()))
                .thenReturn(
                        AuthorizationResult.allow("ALLOW_BY_SCOPE_ROLE", List.of("offering_teacher"), List.of(), true));

        assertThat(service.canViewOfferingGradebook(principal, 12L)).isTrue();

        verify(authorizationService, never()).decide(any());
    }

    @Test
    void canViewOfferingGradebookShouldNotFallbackToLegacyWhenPrincipalHasNoRoleBindingSnapshot() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                false);
        when(permissionAuthorizationService.authorize(eq(principal), eq("grade.export"), any(), any()))
                .thenReturn(AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), false));

        assertThat(service.canViewOfferingGradebook(principal, 12L)).isFalse();

        verify(authorizationService, never()).decide(any());
    }

    @Test
    void canViewTeachingClassGradebookShouldNotFallbackToLegacyWhenPrincipalHasNoRoleBindingSnapshot() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                false);
        when(teachingClassMapper.selectById(34L)).thenReturn(teachingClass(34L, 12L, true));
        when(permissionAuthorizationService.authorize(eq(principal), eq("grade.export"), any(), any()))
                .thenReturn(AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), false));

        assertThat(service.canViewTeachingClassGradebook(principal, 12L, 34L)).isFalse();

        verify(authorizationService, never()).decide(any());
    }

    @Test
    void canReadSensitiveSubmissionShouldNotFallbackToLegacyWhenPrincipalHasNoRoleBindingSnapshot() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                false);
        when(teachingClassMapper.selectById(34L)).thenReturn(teachingClass(34L, 12L, true));
        when(permissionAuthorizationService.authorize(eq(principal), eq("submission.read_source"), any(), any()))
                .thenReturn(AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), false));

        assertThat(service.canReadSensitiveSubmission(principal, 12L, 34L)).isFalse();

        verify(authorizationService, never()).decide(any());
    }

    @Test
    void assertCanViewLabShouldUseModernPermissionBridgeForRoleBindingSnapshotPrincipal() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                true);
        when(teachingClassMapper.selectById(34L)).thenReturn(teachingClass(34L, 12L, true));
        when(permissionAuthorizationService.authorize(eq(principal), eq("lab.read"), any(), any()))
                .thenReturn(AuthorizationResult.allow("ALLOW_BY_SCOPE_ROLE", List.of("student"), List.of(), true));

        service.assertCanViewLab(principal, 12L, 34L);

        verify(authorizationService, never()).decide(any());
    }

    @Test
    void assertCanViewLabShouldNotFallbackToLegacyWhenPrincipalHasNoRoleBindingSnapshot() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                false);
        when(teachingClassMapper.selectById(34L)).thenReturn(teachingClass(34L, 12L, true));
        when(permissionAuthorizationService.authorize(eq(principal), eq("lab.read"), any(), any()))
                .thenReturn(AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), false));

        assertThatThrownBy(() -> service.assertCanViewLab(principal, 12L, 34L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前用户无权查看该实验");
        verify(authorizationService, never()).decide(any());
    }

    @Test
    void assertCanManageAnnouncementsShouldUseModernPermissionBridgeForRoleBindingSnapshotPrincipal() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                true);
        when(teachingClassMapper.selectById(34L)).thenReturn(teachingClass(34L, 12L, true, true, true, true));
        when(permissionAuthorizationService.authorize(eq(principal), eq("announcement.publish"), any(), any()))
                .thenReturn(
                        AuthorizationResult.allow("ALLOW_BY_SCOPE_ROLE", List.of("offering_teacher"), List.of(), true));

        service.assertCanManageAnnouncements(principal, 12L, 34L);

        verify(authorizationService, never()).decide(any());
    }

    @Test
    void assertCanManageAnnouncementsShouldNotFallbackToLegacyWhenPrincipalHasNoRoleBindingSnapshot() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                false);
        when(teachingClassMapper.selectById(34L)).thenReturn(teachingClass(34L, 12L, true, true, true, true));
        when(permissionAuthorizationService.authorize(eq(principal), eq("announcement.publish"), any(), any()))
                .thenReturn(AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), false));

        assertThatThrownBy(() -> service.assertCanManageAnnouncements(principal, 12L, 34L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前用户无权管理该课程公告");
        verify(authorizationService, never()).decide(any());
    }

    void assertCanManageLabsShouldNotFallbackToLegacyWhenPrincipalHasNoRoleBindingSnapshot() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                false);
        when(teachingClassMapper.selectById(34L)).thenReturn(teachingClass(34L, 12L, true));
        when(permissionAuthorizationService.authorize(eq(principal), eq("lab.manage"), any(), any()))
                .thenReturn(AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), false));

        assertThatThrownBy(() -> service.assertCanManageLabs(principal, 12L, 34L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前用户无权管理该实验");
        verify(authorizationService, never()).decide(any());
    }

    @Test
    void assertCanReviewAppealShouldNotFallbackToLegacyWhenPrincipalHasNoRoleBindingSnapshot() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                false);
        when(permissionAuthorizationService.authorize(eq(principal), eq("appeal.review"), any(), any()))
                .thenReturn(AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), false));

        assertThatThrownBy(() -> service.assertCanReviewAppeal(principal, 12L, 34L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前用户无权处理成绩申诉");
        verify(authorizationService, never()).decide(any());
    }

    @Test
    void assertCanParticipateDiscussionsForClassShouldUseModernPermissionBridgeForRoleBindingSnapshotPrincipal() {
        CourseAuthorizationService service = new CourseAuthorizationService(
                authorizationService,
                permissionAuthorizationService,
                sensitiveOperationAuditService,
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
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                true);
        when(teachingClassMapper.selectById(34L)).thenReturn(teachingClass(34L, 12L, true, true, true, true));
        when(permissionAuthorizationService.authorize(eq(principal), eq("discussion.participate"), any(), any()))
                .thenReturn(AuthorizationResult.allow("ALLOW_BY_SCOPE_ROLE", List.of("student"), List.of(), true));

        service.assertCanParticipateDiscussionsForClass(principal, 12L, 34L);

        verify(authorizationService, never()).decide(any());
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

    private TeachingClassEntity teachingClass(Long id, Long offeringId, boolean labEnabled) {
        return teachingClass(id, offeringId, true, true, true, labEnabled);
    }

    private TeachingClassEntity teachingClass(
            Long id,
            Long offeringId,
            boolean announcementEnabled,
            boolean resourceEnabled,
            boolean discussionEnabled,
            boolean labEnabled) {
        TeachingClassEntity entity = new TeachingClassEntity();
        entity.setId(id);
        entity.setOfferingId(offeringId);
        entity.setAnnouncementEnabled(announcementEnabled);
        entity.setResourceEnabled(resourceEnabled);
        entity.setDiscussionEnabled(discussionEnabled);
        entity.setLabEnabled(labEnabled);
        return entity;
    }
}
