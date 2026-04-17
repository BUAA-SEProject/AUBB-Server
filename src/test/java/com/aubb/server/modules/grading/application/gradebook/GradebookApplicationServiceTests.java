package com.aubb.server.modules.grading.application.gradebook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.assignment.infrastructure.paper.AssignmentSectionMapper;
import com.aubb.server.modules.course.application.CourseAuthorizationService;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingMapper;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserMapper;
import com.aubb.server.modules.submission.application.answer.SubmissionAnswerApplicationService;
import com.aubb.server.modules.submission.infrastructure.SubmissionMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GradebookApplicationServiceTests {

    @Mock
    private CourseOfferingMapper courseOfferingMapper;

    @Mock
    private TeachingClassMapper teachingClassMapper;

    @Mock
    private CourseMemberMapper courseMemberMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AssignmentMapper assignmentMapper;

    @Mock
    private AssignmentSectionMapper assignmentSectionMapper;

    @Mock
    private SubmissionMapper submissionMapper;

    @Mock
    private SubmissionAnswerApplicationService submissionAnswerApplicationService;

    @Mock
    private CourseAuthorizationService courseAuthorizationService;

    @Mock
    private GradebookQueryRepository gradebookQueryRepository;

    @Test
    void offeringGradebookUsesDatabasePageQueryWithoutLoadingSubmissionScoreSummaries() {
        when(courseOfferingMapper.selectById(100L)).thenReturn(offering(100L));
        when(gradebookQueryRepository.loadOfferingPage(anyLong(), any(), any(), anyLong(), anyLong()))
                .thenReturn(new GradebookQueryRepository.GradebookPageAggregate(
                        new GradebookPageView.ScopeView(100L, null, null, null),
                        new GradebookPageView.SummaryView(1, 1, 1, 1, 1),
                        List.of(),
                        List.of(),
                        1));

        GradebookApplicationService service = new GradebookApplicationService(
                courseOfferingMapper,
                teachingClassMapper,
                courseMemberMapper,
                userMapper,
                assignmentMapper,
                assignmentSectionMapper,
                submissionMapper,
                submissionAnswerApplicationService,
                courseAuthorizationService,
                gradebookQueryRepository);

        service.getOfferingGradebook(100L, null, null, 1, 20, principal());

        verify(submissionAnswerApplicationService, never())
                .loadScoreSummary(anyLong(), anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void offeringGradebookReportUsesDatabaseAggregationWithoutLoadingSubmissionScoreSummaries() {
        when(courseOfferingMapper.selectById(100L)).thenReturn(offering(100L));
        when(gradebookQueryRepository.loadOfferingReport(anyLong(), any(), any(), anyBoolean()))
                .thenReturn(new GradebookQueryRepository.GradebookReportAggregate(
                        new GradebookPageView.ScopeView(100L, null, null, null),
                        new GradebookReportView.OverviewView(
                                1, 1, 1, 1, 1, 1, 1, 1.0, 1.0, 1.0, 1.0, 10.0, 1.0, 10.0, 1.0, List.of()),
                        List.of(),
                        List.of()));

        GradebookApplicationService service = new GradebookApplicationService(
                courseOfferingMapper,
                teachingClassMapper,
                courseMemberMapper,
                userMapper,
                assignmentMapper,
                assignmentSectionMapper,
                submissionMapper,
                submissionAnswerApplicationService,
                courseAuthorizationService,
                gradebookQueryRepository);

        service.getOfferingGradebookReport(100L, null, null, principal());

        verify(submissionAnswerApplicationService, never())
                .loadScoreSummary(anyLong(), anyLong(), anyBoolean(), anyBoolean());
    }

    private CourseOfferingEntity offering(Long id) {
        CourseOfferingEntity entity = new CourseOfferingEntity();
        entity.setId(id);
        entity.setStartAt(OffsetDateTime.now());
        return entity;
    }

    private AuthenticatedUserPrincipal principal() {
        return new AuthenticatedUserPrincipal(1L, "teacher", "Teacher", 1L, AccountStatus.ACTIVE, null, List.of());
    }
}
