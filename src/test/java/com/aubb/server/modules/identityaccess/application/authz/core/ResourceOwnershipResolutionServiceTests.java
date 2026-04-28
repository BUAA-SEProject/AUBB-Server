package com.aubb.server.modules.identityaccess.application.authz.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.aubb.server.modules.assignment.infrastructure.AssignmentEntity;
import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingMapper;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassEntity;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.grading.infrastructure.appeal.GradeAppealEntity;
import com.aubb.server.modules.grading.infrastructure.appeal.GradeAppealMapper;
import com.aubb.server.modules.organization.infrastructure.OrgUnitEntity;
import com.aubb.server.modules.organization.infrastructure.OrgUnitMapper;
import com.aubb.server.modules.submission.infrastructure.SubmissionEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceOwnershipResolutionServiceTests {

    @Mock
    private OrgUnitMapper orgUnitMapper;

    @Mock
    private CourseOfferingMapper courseOfferingMapper;

    @Mock
    private TeachingClassMapper teachingClassMapper;

    @Mock
    private AssignmentMapper assignmentMapper;

    @Mock
    private SubmissionMapper submissionMapper;

    @Mock
    private GradeAppealMapper gradeAppealMapper;

    private ResourceOwnershipResolutionService service;

    @BeforeEach
    void setUp() {
        service = new ResourceOwnershipResolutionService(
                orgUnitMapper,
                courseOfferingMapper,
                teachingClassMapper,
                assignmentMapper,
                submissionMapper,
                gradeAppealMapper);
    }

    @Test
    void shouldResolveAssignmentOwnershipChainAndPublicationState() {
        mockOfferingChain();
        AssignmentEntity assignment = new AssignmentEntity();
        assignment.setId(301L);
        assignment.setOfferingId(10L);
        assignment.setTeachingClassId(100L);
        assignment.setStatus("PUBLISHED");
        assignment.setPublishedAt(OffsetDateTime.parse("2026-04-15T10:00:00+08:00"));
        assignment.setOpenAt(OffsetDateTime.parse("2026-04-15T10:00:00+08:00"));
        assignment.setDueAt(OffsetDateTime.parse("2026-04-20T10:00:00+08:00"));
        when(assignmentMapper.selectById(301L)).thenReturn(assignment);

        ResolvedAuthorizationResource resolved =
                service.resolve(new AuthorizationResourceRef(AuthorizationResourceType.ASSIGNMENT, 301L));

        assertThat(resolved.scopePath()).isEqualTo(AuthorizationScopePath.forClass(1L, 2L, 3L, 10L, 100L));
        assertThat(resolved.ownerUserId()).isNull();
        assertThat(resolved.published()).isTrue();
        assertThat(resolved.archived()).isFalse();
        assertThat(resolved.windowStart()).isEqualTo(assignment.getOpenAt());
        assertThat(resolved.windowEnd()).isEqualTo(assignment.getDueAt());
    }

    @Test
    void shouldResolveSubmissionOwnerAndInheritedTimeWindow() {
        mockOfferingChain();
        SubmissionEntity submission = new SubmissionEntity();
        submission.setId(501L);
        submission.setAssignmentId(301L);
        submission.setOfferingId(10L);
        submission.setTeachingClassId(100L);
        submission.setSubmitterUserId(88L);
        when(submissionMapper.selectById(501L)).thenReturn(submission);

        AssignmentEntity assignment = new AssignmentEntity();
        assignment.setId(301L);
        assignment.setOfferingId(10L);
        assignment.setTeachingClassId(100L);
        assignment.setStatus("PUBLISHED");
        assignment.setPublishedAt(OffsetDateTime.parse("2026-04-15T10:00:00+08:00"));
        assignment.setOpenAt(OffsetDateTime.parse("2026-04-15T10:00:00+08:00"));
        assignment.setDueAt(OffsetDateTime.parse("2026-04-20T10:00:00+08:00"));
        when(assignmentMapper.selectById(301L)).thenReturn(assignment);

        ResolvedAuthorizationResource resolved =
                service.resolve(new AuthorizationResourceRef(AuthorizationResourceType.SUBMISSION, 501L));

        assertThat(resolved.scopePath()).isEqualTo(AuthorizationScopePath.forClass(1L, 2L, 3L, 10L, 100L));
        assertThat(resolved.ownerUserId()).isEqualTo(88L);
        assertThat(resolved.windowStart()).isEqualTo(assignment.getOpenAt());
        assertThat(resolved.windowEnd()).isEqualTo(assignment.getDueAt());
    }

    @Test
    void shouldResolveGradeAppealOwnerAndTeachingScope() {
        mockOfferingChain();
        GradeAppealEntity appeal = new GradeAppealEntity();
        appeal.setId(701L);
        appeal.setOfferingId(10L);
        appeal.setTeachingClassId(100L);
        appeal.setStudentUserId(66L);
        when(gradeAppealMapper.selectById(701L)).thenReturn(appeal);

        ResolvedAuthorizationResource resolved =
                service.resolve(new AuthorizationResourceRef(AuthorizationResourceType.GRADE_APPEAL, 701L));

        assertThat(resolved.scopePath()).isEqualTo(AuthorizationScopePath.forClass(1L, 2L, 3L, 10L, 100L));
        assertThat(resolved.ownerUserId()).isEqualTo(66L);
    }

    private void mockOfferingChain() {
        CourseOfferingEntity offering = new CourseOfferingEntity();
        offering.setId(10L);
        offering.setOrgCourseUnitId(3L);
        offering.setPrimaryCollegeUnitId(2L);
        when(courseOfferingMapper.selectById(10L)).thenReturn(offering);

        TeachingClassEntity teachingClass = new TeachingClassEntity();
        teachingClass.setId(100L);
        teachingClass.setOfferingId(10L);
        when(teachingClassMapper.selectById(100L)).thenReturn(teachingClass);

        when(orgUnitMapper.selectById(3L)).thenReturn(orgUnit(3L, 2L, "COURSE"));
        when(orgUnitMapper.selectById(2L)).thenReturn(orgUnit(2L, 1L, "COLLEGE"));
        when(orgUnitMapper.selectById(1L)).thenReturn(orgUnit(1L, null, "SCHOOL"));
    }

    private OrgUnitEntity orgUnit(Long id, Long parentId, String type) {
        OrgUnitEntity entity = new OrgUnitEntity();
        entity.setId(id);
        entity.setParentId(parentId);
        entity.setType(type);
        return entity;
    }
}
