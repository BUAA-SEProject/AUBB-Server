package com.aubb.server.modules.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.aubb.server.common.cache.CacheService;
import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.aubb.server.modules.judge.infrastructure.JudgeJobEntity;
import com.aubb.server.modules.notification.infrastructure.NotificationMapper;
import com.aubb.server.modules.notification.infrastructure.NotificationReceiptMapper;
import com.aubb.server.modules.submission.infrastructure.SubmissionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTests {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private NotificationReceiptMapper notificationReceiptMapper;

    @Mock
    private CourseMemberMapper courseMemberMapper;

    @Mock
    private SubmissionMapper submissionMapper;

    @Mock
    private AssignmentMapper assignmentMapper;

    @Mock
    private CacheService cacheService;

    @Mock
    private NotificationRealtimeService notificationRealtimeService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Test
    void notifyJudgeCompletedDropsNullMetadataEntriesBeforePublishingEvent() {
        NotificationDispatchService service = new NotificationDispatchService(
                notificationMapper,
                notificationReceiptMapper,
                courseMemberMapper,
                submissionMapper,
                assignmentMapper,
                cacheService,
                notificationRealtimeService,
                applicationEventPublisher);
        JudgeJobEntity job = new JudgeJobEntity();
        job.setId(42L);
        job.setRequestedByUserId(7L);
        job.setStatus("SUCCEEDED");

        service.notifyJudgeCompleted(job);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        NotificationFanoutRequestedEvent event = (NotificationFanoutRequestedEvent) eventCaptor.getValue();

        assertThat(event.command().metadata())
                .containsEntry("judgeJobId", 42L)
                .containsEntry("status", "SUCCEEDED")
                .doesNotContainKeys("assignmentId", "submissionId", "submissionAnswerId", "verdict");
        assertThat(event.command().recipientUserIds()).containsExactly(7L);
    }
}
