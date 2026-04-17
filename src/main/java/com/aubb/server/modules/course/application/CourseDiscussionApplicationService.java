package com.aubb.server.modules.course.application;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.view.CourseDiscussionAuthorView;
import com.aubb.server.modules.course.application.view.CourseDiscussionDetailView;
import com.aubb.server.modules.course.application.view.CourseDiscussionPostView;
import com.aubb.server.modules.course.application.view.CourseDiscussionSummaryView;
import com.aubb.server.modules.course.infrastructure.discussion.CourseDiscussionEntity;
import com.aubb.server.modules.course.infrastructure.discussion.CourseDiscussionMapper;
import com.aubb.server.modules.course.infrastructure.discussion.CourseDiscussionPostEntity;
import com.aubb.server.modules.course.infrastructure.discussion.CourseDiscussionPostMapper;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberEntity;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassEntity;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserEntity;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserMapper;
import com.aubb.server.modules.notification.application.NotificationDispatchService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CourseDiscussionApplicationService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_BODY_LENGTH = 5_000;

    private final CourseDiscussionMapper courseDiscussionMapper;
    private final CourseDiscussionPostMapper courseDiscussionPostMapper;
    private final CourseMemberMapper courseMemberMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final UserMapper userMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final AuditLogApplicationService auditLogApplicationService;
    private final NotificationDispatchService notificationDispatchService;

    @Transactional
    public CourseDiscussionSummaryView createTeacherDiscussion(
            Long offeringId, Long teachingClassId, String title, String body, AuthenticatedUserPrincipal principal) {
        if (teachingClassId != null) {
            courseAuthorizationService.assertCanManageDiscussions(principal, offeringId, teachingClassId);
            courseAuthorizationService.requireTeachingClassInOffering(offeringId, teachingClassId);
        } else {
            courseAuthorizationService.assertCanManageOffering(principal, offeringId);
        }
        CourseDiscussionEntity discussion =
                createDiscussion(offeringId, teachingClassId, principal.getUserId(), title, body, true);
        return toSummary(discussion, loadUsers(Set.of(principal.getUserId())), Map.of(discussion.getId(), 1L));
    }

    @Transactional
    public CourseDiscussionSummaryView createMyDiscussion(
            Long teachingClassId, String title, String body, AuthenticatedUserPrincipal principal) {
        TeachingClassEntity teachingClass = requireTeachingClass(teachingClassId);
        courseAuthorizationService.assertCanParticipateDiscussionsForClass(
                principal, teachingClass.getOfferingId(), teachingClassId);
        CourseDiscussionEntity discussion = createDiscussion(
                teachingClass.getOfferingId(), teachingClassId, principal.getUserId(), title, body, false);
        return toSummary(discussion, loadUsers(Set.of(principal.getUserId())), Map.of(discussion.getId(), 1L));
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseDiscussionSummaryView> listTeacherDiscussions(
            Long offeringId, Long teachingClassId, long page, long pageSize, AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageOffering(principal, offeringId);
        if (teachingClassId != null) {
            courseAuthorizationService.requireTeachingClassInOffering(offeringId, teachingClassId);
        }
        List<CourseDiscussionEntity> discussions =
                courseDiscussionMapper.selectList(Wrappers.<CourseDiscussionEntity>lambdaQuery()
                        .eq(CourseDiscussionEntity::getOfferingId, offeringId)
                        .eq(teachingClassId != null, CourseDiscussionEntity::getTeachingClassId, teachingClassId)
                        .orderByDesc(CourseDiscussionEntity::getLastActivityAt)
                        .orderByDesc(CourseDiscussionEntity::getId));
        return toSummaryPage(discussions, page, pageSize);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseDiscussionSummaryView> listMyDiscussions(
            Long teachingClassId, long page, long pageSize, AuthenticatedUserPrincipal principal) {
        TeachingClassEntity teachingClass = requireTeachingClass(teachingClassId);
        courseAuthorizationService.assertCanParticipateDiscussionsForClass(
                principal, teachingClass.getOfferingId(), teachingClassId);
        List<CourseDiscussionEntity> discussions =
                courseDiscussionMapper.selectList(Wrappers.<CourseDiscussionEntity>lambdaQuery()
                        .eq(CourseDiscussionEntity::getOfferingId, teachingClass.getOfferingId())
                        .and(wrapper -> wrapper.isNull(CourseDiscussionEntity::getTeachingClassId)
                                .or()
                                .eq(CourseDiscussionEntity::getTeachingClassId, teachingClassId))
                        .orderByDesc(CourseDiscussionEntity::getLastActivityAt)
                        .orderByDesc(CourseDiscussionEntity::getId));
        return toSummaryPage(discussions, page, pageSize);
    }

    @Transactional(readOnly = true)
    public CourseDiscussionDetailView getTeacherDiscussion(Long discussionId, AuthenticatedUserPrincipal principal) {
        CourseDiscussionEntity discussion = requireDiscussion(discussionId);
        courseAuthorizationService.assertCanManageOffering(principal, discussion.getOfferingId());
        return toDetail(discussion);
    }

    @Transactional(readOnly = true)
    public CourseDiscussionDetailView getMyDiscussion(Long discussionId, AuthenticatedUserPrincipal principal) {
        CourseDiscussionEntity discussion = requireDiscussion(discussionId);
        assertCanAccessDiscussion(principal, discussion);
        return toDetail(discussion);
    }

    @Transactional
    public CourseDiscussionPostView replyAsTeacher(
            Long discussionId, Long replyToPostId, String body, AuthenticatedUserPrincipal principal) {
        CourseDiscussionEntity discussion = requireDiscussion(discussionId);
        courseAuthorizationService.assertCanManageOffering(principal, discussion.getOfferingId());
        return createReply(discussion, replyToPostId, body, principal.getUserId(), true);
    }

    @Transactional
    public CourseDiscussionPostView replyAsMy(
            Long discussionId, Long replyToPostId, String body, AuthenticatedUserPrincipal principal) {
        CourseDiscussionEntity discussion = requireDiscussion(discussionId);
        assertCanAccessDiscussion(principal, discussion);
        if (Boolean.TRUE.equals(discussion.getLocked())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "DISCUSSION_LOCKED", "当前讨论已锁定，暂不允许继续回复");
        }
        return createReply(discussion, replyToPostId, body, principal.getUserId(), false);
    }

    @Transactional
    public CourseDiscussionSummaryView updateTeacherLockState(
            Long discussionId, boolean locked, AuthenticatedUserPrincipal principal) {
        CourseDiscussionEntity discussion = requireDiscussion(discussionId);
        courseAuthorizationService.assertCanManageOffering(principal, discussion.getOfferingId());
        discussion.setLocked(locked);
        courseDiscussionMapper.updateById(discussion);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.COURSE_DISCUSSION_LOCK_STATE_CHANGED,
                "COURSE_DISCUSSION",
                String.valueOf(discussionId),
                AuditResult.SUCCESS,
                buildAuditDetails(discussion.getOfferingId(), discussion.getTeachingClassId(), null, locked));
        return toSummary(
                discussion, loadUsers(Set.of(discussion.getCreatedByUserId())), loadPostCounts(Set.of(discussionId)));
    }

    private CourseDiscussionEntity createDiscussion(
            Long offeringId,
            Long teachingClassId,
            Long authorUserId,
            String title,
            String body,
            boolean notifyStudents) {
        OffsetDateTime now = OffsetDateTime.now();
        CourseDiscussionEntity discussion = new CourseDiscussionEntity();
        discussion.setOfferingId(offeringId);
        discussion.setTeachingClassId(teachingClassId);
        discussion.setCreatedByUserId(authorUserId);
        discussion.setTitle(normalizeTitle(title));
        discussion.setLocked(false);
        discussion.setLastActivityAt(now);
        courseDiscussionMapper.insert(discussion);
        CourseDiscussionPostEntity firstPost = new CourseDiscussionPostEntity();
        firstPost.setDiscussionId(discussion.getId());
        firstPost.setAuthorUserId(authorUserId);
        firstPost.setBody(normalizeBody(body));
        courseDiscussionPostMapper.insert(firstPost);
        auditLogApplicationService.record(
                authorUserId,
                AuditAction.COURSE_DISCUSSION_CREATED,
                "COURSE_DISCUSSION",
                String.valueOf(discussion.getId()),
                AuditResult.SUCCESS,
                buildAuditDetails(offeringId, teachingClassId, null, null));
        notificationDispatchService.notifyCourseDiscussionUpdated(discussion, authorUserId, notifyStudents);
        return discussion;
    }

    private CourseDiscussionPostView createReply(
            CourseDiscussionEntity discussion,
            Long replyToPostId,
            String body,
            Long actorUserId,
            boolean notifyStudents) {
        if (replyToPostId != null) {
            CourseDiscussionPostEntity replyTarget = courseDiscussionPostMapper.selectById(replyToPostId);
            if (replyTarget == null || !Objects.equals(replyTarget.getDiscussionId(), discussion.getId())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "DISCUSSION_REPLY_TARGET_INVALID", "引用的回复不存在");
            }
        }
        CourseDiscussionPostEntity post = new CourseDiscussionPostEntity();
        post.setDiscussionId(discussion.getId());
        post.setAuthorUserId(actorUserId);
        post.setReplyToPostId(replyToPostId);
        post.setBody(normalizeBody(body));
        courseDiscussionPostMapper.insert(post);
        discussion.setLastActivityAt(OffsetDateTime.now());
        courseDiscussionMapper.updateById(discussion);
        auditLogApplicationService.record(
                actorUserId,
                AuditAction.COURSE_DISCUSSION_REPLIED,
                "COURSE_DISCUSSION",
                String.valueOf(discussion.getId()),
                AuditResult.SUCCESS,
                buildAuditDetails(discussion.getOfferingId(), discussion.getTeachingClassId(), post.getId(), null));
        notificationDispatchService.notifyCourseDiscussionUpdated(discussion, actorUserId, notifyStudents);
        UserEntity author = userMapper.selectById(actorUserId);
        return new CourseDiscussionPostView(
                post.getId(),
                discussion.getId(),
                post.getReplyToPostId(),
                post.getBody(),
                toAuthor(author, actorUserId),
                post.getCreatedAt());
    }

    private void assertCanAccessDiscussion(AuthenticatedUserPrincipal principal, CourseDiscussionEntity discussion) {
        if (discussion.getTeachingClassId() != null) {
            courseAuthorizationService.assertCanParticipateDiscussionsForClass(
                    principal, discussion.getOfferingId(), discussion.getTeachingClassId());
            return;
        }
        assertCanAccessOfferingWideDiscussion(principal, discussion.getOfferingId());
    }

    private void assertCanAccessOfferingWideDiscussion(AuthenticatedUserPrincipal principal, Long offeringId) {
        if (courseAuthorizationService.canManageOfferingAsAdmin(principal, offeringId)
                || courseAuthorizationService.isInstructor(principal.getUserId(), offeringId)) {
            return;
        }
        if (!courseAuthorizationService.isActiveCourseMember(principal.getUserId(), offeringId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该课程讨论");
        }
        Set<Long> activeClassIds = courseMemberMapper
                .selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getUserId, principal.getUserId())
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .isNotNull(CourseMemberEntity::getTeachingClassId)
                        .eq(CourseMemberEntity::getMemberStatus, "ACTIVE")
                        .select(CourseMemberEntity::getTeachingClassId))
                .stream()
                .map(CourseMemberEntity::getTeachingClassId)
                .collect(Collectors.toSet());
        if (!activeClassIds.isEmpty()
                && teachingClassMapper.selectBatchIds(activeClassIds).stream()
                        .noneMatch(teachingClass -> Boolean.TRUE.equals(teachingClass.getDiscussionEnabled()))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "DISCUSSION_DISABLED", "当前教学班未启用课程讨论功能");
        }
    }

    private CourseDiscussionEntity requireDiscussion(Long discussionId) {
        CourseDiscussionEntity discussion = courseDiscussionMapper.selectById(discussionId);
        if (discussion == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COURSE_DISCUSSION_NOT_FOUND", "课程讨论不存在");
        }
        return discussion;
    }

    private TeachingClassEntity requireTeachingClass(Long teachingClassId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(teachingClassId);
        if (teachingClass == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "TEACHING_CLASS_NOT_FOUND", "教学班不存在");
        }
        return teachingClass;
    }

    private PageResponse<CourseDiscussionSummaryView> toSummaryPage(
            List<CourseDiscussionEntity> discussions, long page, long pageSize) {
        List<CourseDiscussionSummaryView> items = toSummaries(discussions);
        int fromIndex = (int) Math.min((Math.max(page, 1) - 1) * Math.max(pageSize, 1), items.size());
        int toIndex = (int) Math.min(fromIndex + Math.max(pageSize, 1), items.size());
        return new PageResponse<>(
                items.subList(fromIndex, toIndex), items.size(), Math.max(page, 1), Math.max(pageSize, 1));
    }

    private List<CourseDiscussionSummaryView> toSummaries(List<CourseDiscussionEntity> discussions) {
        Set<Long> discussionIds =
                discussions.stream().map(CourseDiscussionEntity::getId).collect(Collectors.toSet());
        Set<Long> userIds = discussions.stream()
                .map(CourseDiscussionEntity::getCreatedByUserId)
                .collect(Collectors.toSet());
        Map<Long, Long> postCounts = loadPostCounts(discussionIds);
        Map<Long, UserEntity> users = loadUsers(userIds);
        return discussions.stream()
                .map(discussion -> toSummary(discussion, users, postCounts))
                .toList();
    }

    private CourseDiscussionSummaryView toSummary(
            CourseDiscussionEntity discussion, Map<Long, UserEntity> users, Map<Long, Long> postCounts) {
        long postCount = postCounts.getOrDefault(discussion.getId(), 1L);
        return new CourseDiscussionSummaryView(
                discussion.getId(),
                discussion.getOfferingId(),
                discussion.getTeachingClassId(),
                discussion.getTitle(),
                Boolean.TRUE.equals(discussion.getLocked()),
                (int) Math.max(postCount - 1, 0),
                toAuthor(users.get(discussion.getCreatedByUserId()), discussion.getCreatedByUserId()),
                discussion.getLastActivityAt(),
                discussion.getCreatedAt());
    }

    private CourseDiscussionDetailView toDetail(CourseDiscussionEntity discussion) {
        List<CourseDiscussionPostEntity> posts =
                courseDiscussionPostMapper.selectList(Wrappers.<CourseDiscussionPostEntity>lambdaQuery()
                        .eq(CourseDiscussionPostEntity::getDiscussionId, discussion.getId())
                        .orderByAsc(CourseDiscussionPostEntity::getCreatedAt)
                        .orderByAsc(CourseDiscussionPostEntity::getId));
        Map<Long, UserEntity> users = loadUsers(
                posts.stream().map(CourseDiscussionPostEntity::getAuthorUserId).collect(Collectors.toSet()));
        return new CourseDiscussionDetailView(
                discussion.getId(),
                discussion.getOfferingId(),
                discussion.getTeachingClassId(),
                discussion.getTitle(),
                Boolean.TRUE.equals(discussion.getLocked()),
                toAuthor(userMapper.selectById(discussion.getCreatedByUserId()), discussion.getCreatedByUserId()),
                Math.max(posts.size() - 1, 0),
                discussion.getLastActivityAt(),
                discussion.getCreatedAt(),
                posts.stream()
                        .map(post -> new CourseDiscussionPostView(
                                post.getId(),
                                post.getDiscussionId(),
                                post.getReplyToPostId(),
                                post.getBody(),
                                toAuthor(users.get(post.getAuthorUserId()), post.getAuthorUserId()),
                                post.getCreatedAt()))
                        .toList());
    }

    private Map<Long, Long> loadPostCounts(Collection<Long> discussionIds) {
        if (discussionIds == null || discussionIds.isEmpty()) {
            return Map.of();
        }
        return courseDiscussionPostMapper
                .selectList(Wrappers.<CourseDiscussionPostEntity>lambdaQuery()
                        .in(CourseDiscussionPostEntity::getDiscussionId, discussionIds)
                        .select(CourseDiscussionPostEntity::getDiscussionId))
                .stream()
                .collect(Collectors.groupingBy(CourseDiscussionPostEntity::getDiscussionId, Collectors.counting()));
    }

    private Map<Long, UserEntity> loadUsers(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, user -> user, (left, right) -> left, LinkedHashMap::new));
    }

    private CourseDiscussionAuthorView toAuthor(UserEntity user, Long fallbackUserId) {
        return new CourseDiscussionAuthorView(
                user == null ? fallbackUserId : user.getId(),
                user == null ? "用户#" + fallbackUserId : user.getDisplayName());
    }

    private String normalizeTitle(String title) {
        String normalized = title == null ? null : title.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_DISCUSSION_TITLE_REQUIRED", "课程讨论标题不能为空");
        }
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_DISCUSSION_TITLE_TOO_LONG", "课程讨论标题过长");
        }
        return normalized;
    }

    private String normalizeBody(String body) {
        String normalized = body == null ? null : body.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_DISCUSSION_BODY_REQUIRED", "课程讨论正文不能为空");
        }
        if (normalized.length() > MAX_BODY_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_DISCUSSION_BODY_TOO_LONG", "课程讨论正文过长");
        }
        return normalized;
    }

    private Map<String, Object> buildAuditDetails(Long offeringId, Long teachingClassId, Long replyId, Boolean locked) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("offeringId", offeringId);
        if (teachingClassId != null) {
            details.put("teachingClassId", teachingClassId);
        }
        if (replyId != null) {
            details.put("replyId", replyId);
        }
        if (locked != null) {
            details.put("locked", locked);
        }
        return details;
    }
}
