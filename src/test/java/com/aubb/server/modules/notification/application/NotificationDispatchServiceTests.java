package com.aubb.server.modules.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aubb.server.common.cache.CacheService;
import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberEntity;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.aubb.server.modules.judge.infrastructure.JudgeJobEntity;
import com.aubb.server.modules.lab.infrastructure.LabEntity;
import com.aubb.server.modules.lab.infrastructure.LabReportEntity;
import com.aubb.server.modules.notification.infrastructure.NotificationMapper;
import com.aubb.server.modules.notification.infrastructure.NotificationReceiptMapper;
import com.aubb.server.modules.submission.infrastructure.SubmissionMapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTests {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, CourseMemberEntity.class);
        LambdaUtils.installCache(TableInfoHelper.getTableInfo(CourseMemberEntity.class));
    }

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

    @Test
    void notifyLabReportSubmittedShouldNotFanoutToOtherClassesClassInstructor() {
        NotificationDispatchService service = new NotificationDispatchService(
                notificationMapper,
                notificationReceiptMapper,
                courseMemberMapper,
                submissionMapper,
                assignmentMapper,
                cacheService,
                notificationRealtimeService,
                applicationEventPublisher);
        AtomicInteger invocationCount = new AtomicInteger();
        when(courseMemberMapper.selectList(any()))
                .thenAnswer(invocation -> invocationCount.incrementAndGet() == 1
                        ? List.of(member(2L), member(6L))
                        : List.of(member(3L), member(4L)));

        LabEntity lab = new LabEntity();
        lab.setId(300L);
        lab.setTitle("网络实验");
        LabReportEntity report = new LabReportEntity();
        report.setId(400L);
        report.setLabId(300L);
        report.setOfferingId(10L);
        report.setTeachingClassId(99L);
        report.setStudentUserId(1000L);

        service.notifyLabReportSubmitted(report, lab, 1L);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        NotificationFanoutRequestedEvent event = (NotificationFanoutRequestedEvent) eventCaptor.getValue();
        ArgumentCaptor<Wrapper<CourseMemberEntity>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(courseMemberMapper, times(2)).selectList(queryCaptor.capture());

        assertThat(event.command().recipientUserIds()).containsExactlyInAnyOrder(2L, 3L, 4L, 6L);
        assertThat(invocationCount).hasValue(2);
        assertThat(wrapperSqlSegment(queryCaptor.getAllValues().get(0))).doesNotContain("teachingClassId");
        assertThat(wrapperSqlSegment(queryCaptor.getAllValues().get(1))).contains("teachingClassId");
    }

    private CourseMemberEntity member(Long userId) {
        CourseMemberEntity entity = new CourseMemberEntity();
        entity.setUserId(userId);
        return entity;
    }

    private String wrapperSqlSegment(Wrapper<CourseMemberEntity> wrapper) {
        if (wrapper instanceof AbstractWrapper<?, ?, ?> abstractWrapper) {
            return abstractWrapper.getSqlSegment();
        }
        return "";
    }
}
