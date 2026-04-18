package com.aubb.server.modules.course.application;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.cache.CacheService;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.config.RedisEnhancementProperties;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.application.AuditLogCommand;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditDecision;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.command.CourseMemberCommand;
import com.aubb.server.modules.course.application.result.CourseMemberBatchError;
import com.aubb.server.modules.course.application.result.CourseMemberBatchResult;
import com.aubb.server.modules.course.application.result.CourseMemberImportResult;
import com.aubb.server.modules.course.application.view.CourseMemberView;
import com.aubb.server.modules.course.application.view.MyCourseClassView;
import com.aubb.server.modules.course.application.view.MyCourseView;
import com.aubb.server.modules.course.application.view.TeachingClassFeaturesView;
import com.aubb.server.modules.course.application.view.TeachingClassView;
import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.course.domain.member.CourseMemberSourceType;
import com.aubb.server.modules.course.domain.member.CourseMemberStatus;
import com.aubb.server.modules.course.domain.offering.CourseOfferingStatus;
import com.aubb.server.modules.course.domain.teaching.TeachingClassStatus;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberEntity;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberListRow;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingMapper;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassEntity;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthSessionApplicationService;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.core.ReadPathAuthorizationService;
import com.aubb.server.modules.identityaccess.application.user.UserDirectoryApplicationService;
import com.aubb.server.modules.identityaccess.application.user.UserOrgMembershipApplicationService;
import com.aubb.server.modules.identityaccess.application.user.view.AcademicProfileView;
import com.aubb.server.modules.identityaccess.application.user.view.UserDirectoryEntryView;
import com.aubb.server.modules.identityaccess.domain.membership.MembershipSourceType;
import com.aubb.server.modules.identityaccess.domain.membership.MembershipStatus;
import com.aubb.server.modules.identityaccess.domain.profile.AcademicIdentityType;
import com.aubb.server.modules.identityaccess.domain.profile.AcademicProfileStatus;
import com.aubb.server.modules.organization.application.OrgUnitSummaryView;
import com.aubb.server.modules.organization.application.OrganizationApplicationService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CourseTeachingApplicationService {

    static final String MY_COURSES_CACHE_NAME = "myCoursesSummary";
    private static final Set<CourseMemberRole> TEACHER_MEMBER_ROLES =
            Set.of(CourseMemberRole.INSTRUCTOR, CourseMemberRole.CLASS_INSTRUCTOR);

    private final CourseOfferingMapper courseOfferingMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseMemberMapper courseMemberMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final ReadPathAuthorizationService readPathAuthorizationService;
    private final OrganizationApplicationService organizationApplicationService;
    private final UserDirectoryApplicationService userDirectoryApplicationService;
    private final UserOrgMembershipApplicationService userOrgMembershipApplicationService;
    private final CourseMemberRoleBindingSyncService courseMemberRoleBindingSyncService;
    private final AuditLogApplicationService auditLogApplicationService;
    private final AuthSessionApplicationService authSessionApplicationService;
    private final CacheService cacheService;
    private final RedisEnhancementProperties redisEnhancementProperties;

    @Transactional
    public TeachingClassView createTeachingClass(
            Long offeringId,
            String classCode,
            String className,
            Integer entryYear,
            Integer capacity,
            String scheduleSummary,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageOffering(principal, offeringId);
        CourseOfferingEntity offering = requireOffering(offeringId);
        if (CourseOfferingStatus.ARCHIVED.name().equals(offering.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_ARCHIVED", "归档课程不可再创建教学班");
        }
        String normalizedClassCode = normalizeCode(classCode);
        if (teachingClassMapper.selectOne(Wrappers.<TeachingClassEntity>lambdaQuery()
                        .eq(TeachingClassEntity::getOfferingId, offeringId)
                        .eq(TeachingClassEntity::getClassCode, normalizedClassCode)
                        .last("LIMIT 1"))
                != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "TEACHING_CLASS_CODE_DUPLICATED", "教学班编码已存在");
        }
        String orgClassCode = generateOrgClassCode(offering.getOfferingCode(), normalizedClassCode);
        OrgUnitSummaryView orgClass = organizationApplicationService.createCourseManagedClassUnit(
                className, orgClassCode, offering.getOrgCourseUnitId(), 0, principal.getUserId());
        TeachingClassEntity entity = new TeachingClassEntity();
        entity.setOfferingId(offeringId);
        entity.setClassCode(normalizedClassCode);
        entity.setClassName(className);
        entity.setEntryYear(entryYear);
        entity.setOrgClassUnitId(orgClass.id());
        entity.setCapacity(capacity);
        entity.setStatus(TeachingClassStatus.ACTIVE.name());
        entity.setScheduleSummary(scheduleSummary);
        entity.setAnnouncementEnabled(true);
        entity.setDiscussionEnabled(true);
        entity.setResourceEnabled(true);
        entity.setLabEnabled(true);
        entity.setAssignmentEnabled(true);
        teachingClassMapper.insert(entity);
        auditLogApplicationService.record(new AuditLogCommand(
                principal.getUserId(),
                AuditAction.TEACHING_CLASS_CREATED,
                "TEACHING_CLASS",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                "class",
                entity.getId(),
                AuditDecision.ALLOW,
                Map.of("offeringId", offeringId, "classCode", normalizedClassCode, "entryYear", entryYear)));
        return toTeachingClassView(entity, orgClass);
    }

    @Transactional(readOnly = true)
    public List<TeachingClassView> listTeachingClasses(Long offeringId, AuthenticatedUserPrincipal principal) {
        ReadPathAuthorizationService.TeachingReadScope scope =
                readPathAuthorizationService.resolveTeachingReadScope(principal, "class.read", offeringId);
        if (!scope.hasAnyAccess()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看教学班");
        }
        List<TeachingClassEntity> visibleClasses =
                teachingClassMapper.selectList(Wrappers.<TeachingClassEntity>lambdaQuery()
                        .eq(TeachingClassEntity::getOfferingId, offeringId)
                        .in(!scope.offeringReadable(), TeachingClassEntity::getId, scope.classIds())
                        .orderByAsc(TeachingClassEntity::getEntryYear)
                        .orderByAsc(TeachingClassEntity::getClassCode));
        Map<Long, OrgUnitSummaryView> orgClassSummaries =
                organizationApplicationService.loadSummaryMap(visibleClasses.stream()
                        .map(TeachingClassEntity::getOrgClassUnitId)
                        .toList());
        return visibleClasses.stream()
                .map(entity -> toTeachingClassView(entity, orgClassSummaries.get(entity.getOrgClassUnitId())))
                .toList();
    }

    @Transactional
    public TeachingClassView updateTeachingClassFeatures(
            Long teachingClassId,
            boolean announcementEnabled,
            boolean discussionEnabled,
            boolean resourceEnabled,
            boolean labEnabled,
            boolean assignmentEnabled,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageClassFeatures(principal, teachingClassId);
        TeachingClassEntity teachingClass = requireTeachingClass(teachingClassId);
        teachingClass.setAnnouncementEnabled(announcementEnabled);
        teachingClass.setDiscussionEnabled(discussionEnabled);
        teachingClass.setResourceEnabled(resourceEnabled);
        teachingClass.setLabEnabled(labEnabled);
        teachingClass.setAssignmentEnabled(assignmentEnabled);
        teachingClassMapper.updateById(teachingClass);
        auditLogApplicationService.record(new AuditLogCommand(
                principal.getUserId(),
                AuditAction.TEACHING_CLASS_FEATURES_UPDATED,
                "TEACHING_CLASS",
                String.valueOf(teachingClassId),
                AuditResult.SUCCESS,
                "class",
                teachingClassId,
                AuditDecision.ALLOW,
                Map.of("announcementEnabled", announcementEnabled, "discussionEnabled", discussionEnabled)));
        OrgUnitSummaryView orgClass = organizationApplicationService
                .loadSummaryMap(List.of(teachingClass.getOrgClassUnitId()))
                .get(teachingClass.getOrgClassUnitId());
        return toTeachingClassView(teachingClass, orgClass);
    }

    @Transactional
    public CourseMemberBatchResult addMembersBatch(
            Long offeringId, Collection<CourseMemberCommand> commands, AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageMembers(principal, offeringId);
        CourseOfferingEntity offering = requireOffering(offeringId);
        assertOfferingWritable(offering);
        List<CourseMemberBatchError> errors = new ArrayList<>();
        Set<Long> affectedUserIds = new LinkedHashSet<>();
        int success = 0;
        int row = 0;
        for (CourseMemberCommand command : commands == null ? List.<CourseMemberCommand>of() : commands) {
            row++;
            try {
                addSingleMember(
                        offering,
                        command.userId(),
                        command.memberRole(),
                        command.teachingClassId(),
                        command.remark(),
                        CourseMemberSourceType.MANUAL);
                affectedUserIds.add(command.userId());
                success++;
            } catch (BusinessException exception) {
                errors.add(new CourseMemberBatchError(row, command.userId(), null, exception.getMessage()));
            }
        }
        auditLogApplicationService.record(new AuditLogCommand(
                principal.getUserId(),
                AuditAction.COURSE_MEMBERS_BATCH_ADDED,
                "COURSE_OFFERING",
                String.valueOf(offeringId),
                errors.isEmpty() ? AuditResult.SUCCESS : AuditResult.FAILURE,
                "offering",
                offeringId,
                AuditDecision.ALLOW,
                Map.of("successCount", success, "failCount", errors.size())));
        affectedUserIds.forEach(
                userId -> invalidateUserSessions(userId, principal.getUserId(), "COURSE_MEMBER_BATCH_UPDATED"));
        return new CourseMemberBatchResult(success, errors.size(), errors);
    }

    @Transactional
    public CourseMemberImportResult importMembers(
            Long offeringId, MultipartFile file, String importType, AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanImportMembers(principal, offeringId);
        if (!"csv".equalsIgnoreCase(importType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IMPORT_TYPE_UNSUPPORTED", "当前仅支持 csv 导入");
        }
        CourseOfferingEntity offering = requireOffering(offeringId);
        assertOfferingWritable(offering);
        Map<String, TeachingClassEntity> classByCode = teachingClassMapper
                .selectList(
                        Wrappers.<TeachingClassEntity>lambdaQuery().eq(TeachingClassEntity::getOfferingId, offeringId))
                .stream()
                .collect(Collectors.toMap(
                        teachingClass -> teachingClass.getClassCode().toUpperCase(Locale.ROOT),
                        teachingClass -> teachingClass,
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<CourseMemberBatchError> errors = new ArrayList<>();
        Set<Long> affectedUserIds = new LinkedHashSet<>();
        int total = 0;
        int success = 0;
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "IMPORT_FILE_EMPTY", "导入文件不能为空");
            }
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) {
                    continue;
                }
                total++;
                String[] columns = line.split(",", -1);
                if (columns.length < 4) {
                    errors.add(new CourseMemberBatchError(
                            rowNumber, null, null, "列数不足，必须包含 username,memberRole,classCode,remark"));
                    continue;
                }
                String username = columns[0].trim().toLowerCase(Locale.ROOT);
                try {
                    UserDirectoryEntryView user = userDirectoryApplicationService
                            .loadByUsernames(List.of(username))
                            .get(username);
                    if (user == null) {
                        throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_MEMBER_USER_NOT_FOUND", "导入用户不存在");
                    }
                    CourseMemberRole role =
                            CourseMemberRole.valueOf(columns[1].trim().toUpperCase(Locale.ROOT));
                    Long teachingClassId = resolveTeachingClassId(role, columns[2].trim(), classByCode);
                    addSingleMember(
                            offering,
                            user.id(),
                            role,
                            teachingClassId,
                            columns[3].trim(),
                            CourseMemberSourceType.IMPORT);
                    affectedUserIds.add(user.id());
                    success++;
                } catch (BusinessException exception) {
                    errors.add(new CourseMemberBatchError(rowNumber, null, username, exception.getMessage()));
                }
            }
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IMPORT_FILE_READ_FAILED", "无法读取导入文件");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("total", total);
        metadata.put("success", success);
        metadata.put("failed", errors.size());
        metadata.put("importType", importType);
        auditLogApplicationService.record(new AuditLogCommand(
                principal.getUserId(),
                AuditAction.COURSE_MEMBERS_IMPORTED,
                "COURSE_OFFERING",
                String.valueOf(offeringId),
                errors.isEmpty() ? AuditResult.SUCCESS : AuditResult.FAILURE,
                "offering",
                offeringId,
                AuditDecision.ALLOW,
                metadata));
        affectedUserIds.forEach(
                userId -> invalidateUserSessions(userId, principal.getUserId(), "COURSE_MEMBER_IMPORT_UPDATED"));
        return new CourseMemberImportResult(total, success, errors.size(), errors);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseMemberView> listMembers(
            Long offeringId,
            Long teachingClassId,
            CourseMemberRole memberRole,
            CourseMemberStatus memberStatus,
            String keyword,
            long page,
            long pageSize,
            AuthenticatedUserPrincipal principal) {
        ReadPathAuthorizationService.TeachingReadScope scope =
                readPathAuthorizationService.resolveTeachingReadScope(principal, "member.read", offeringId);
        boolean authorizedAll = scope.offeringReadable();
        if (teachingClassId != null && !scope.canReadClass(teachingClassId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看成员");
        }
        if (teachingClassId == null && !authorizedAll && scope.classIds().isEmpty()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看成员");
        }
        String normalizedKeyword =
                keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase(Locale.ROOT);
        String memberRoleCode = memberRole == null ? null : memberRole.name();
        String memberStatusCode = memberStatus == null ? null : memberStatus.name();
        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePage - 1) * safePageSize;
        long total = courseMemberMapper.countMemberPage(
                offeringId,
                teachingClassId,
                memberRoleCode,
                memberStatusCode,
                normalizedKeyword,
                authorizedAll,
                scope.classIds());
        if (total == 0) {
            return new PageResponse<>(List.of(), 0, safePage, safePageSize);
        }
        boolean revealSensitiveFields = canRevealSensitiveMemberFields(principal, offeringId);
        List<CourseMemberView> items = courseMemberMapper
                .selectMemberPage(
                        offeringId,
                        teachingClassId,
                        memberRoleCode,
                        memberStatusCode,
                        normalizedKeyword,
                        authorizedAll,
                        scope.classIds(),
                        offset,
                        safePageSize)
                .stream()
                .map(row -> toCourseMemberView(row, revealSensitiveFields))
                .toList();
        return new PageResponse<>(items, total, safePage, safePageSize);
    }

    @Transactional
    public CourseMemberView updateMemberStatus(
            Long offeringId,
            Long memberId,
            CourseMemberStatus targetStatus,
            String remark,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageMembers(principal, offeringId);
        CourseOfferingEntity offering = requireOffering(offeringId);
        assertOfferingWritable(offering);
        if (targetStatus == CourseMemberStatus.TRANSFERRED) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "COURSE_MEMBER_TRANSFER_REQUIRES_TARGET_CLASS", "转班必须指定目标教学班");
        }

        CourseMemberEntity member = requireMember(offeringId, memberId);
        CourseMemberRole role = CourseMemberRole.valueOf(member.getMemberRole());
        TeachingClassEntity teachingClass =
                member.getTeachingClassId() == null ? null : requireTeachingClass(member.getTeachingClassId());
        CourseMemberStatus currentStatus = CourseMemberStatus.valueOf(member.getMemberStatus());
        if (targetStatus == CourseMemberStatus.ACTIVE && currentStatus != CourseMemberStatus.ACTIVE) {
            validateMemberRoleBinding(offering, role, teachingClass);
            assertNoTeacherStudentRoleConflict(offering.getId(), member.getUserId(), role);
            if (role == CourseMemberRole.STUDENT) {
                assertOfferingCapacity(offering);
                assertTeachingClassCapacity(teachingClass);
            }
        }

        applyMemberStatus(offering, member, targetStatus, remark, OffsetDateTime.now());
        auditLogApplicationService.record(new AuditLogCommand(
                principal.getUserId(),
                AuditAction.COURSE_MEMBER_STATUS_CHANGED,
                "COURSE_MEMBER",
                String.valueOf(member.getId()),
                AuditResult.SUCCESS,
                member.getTeachingClassId() == null ? "offering" : "class",
                member.getTeachingClassId() == null ? offeringId : member.getTeachingClassId(),
                AuditDecision.ALLOW,
                Map.of(
                        "userId", member.getUserId(),
                        "memberRole", member.getMemberRole(),
                        "previousStatus", currentStatus.name(),
                        "currentStatus", targetStatus.name())));
        invalidateUserSessions(member.getUserId(), principal.getUserId(), "COURSE_MEMBER_STATUS_CHANGED");
        return toCourseMemberView(member, true);
    }

    @Transactional
    public CourseMemberView transferStudent(
            Long offeringId,
            Long memberId,
            Long targetTeachingClassId,
            String remark,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageMembers(principal, offeringId);
        CourseOfferingEntity offering = requireOffering(offeringId);
        assertOfferingWritable(offering);

        CourseMemberEntity member = requireMember(offeringId, memberId);
        CourseMemberRole role = CourseMemberRole.valueOf(member.getMemberRole());
        if (role != CourseMemberRole.STUDENT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_MEMBER_TRANSFER_ONLY_STUDENT", "当前仅支持学生转班");
        }
        if (!CourseMemberStatus.ACTIVE.name().equals(member.getMemberStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "COURSE_MEMBER_TRANSFER_REQUIRES_ACTIVE", "只有在读学生可以执行转班");
        }
        if (member.getTeachingClassId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_MEMBER_CLASS_REQUIRED", "学生成员必须绑定教学班");
        }
        if (Objects.equals(member.getTeachingClassId(), targetTeachingClassId)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "COURSE_MEMBER_TRANSFER_TARGET_SAME", "目标教学班不能与当前教学班相同");
        }

        Long sourceTeachingClassId = member.getTeachingClassId();
        TeachingClassEntity targetTeachingClass = requireTeachingClass(targetTeachingClassId);
        validateMemberRoleBinding(offering, role, targetTeachingClass);
        OffsetDateTime now = OffsetDateTime.now();
        applyMemberStatus(offering, member, CourseMemberStatus.TRANSFERRED, remark, now);
        CourseMemberEntity transferredMember = addSingleMember(
                offering,
                member.getUserId(),
                CourseMemberRole.STUDENT,
                targetTeachingClassId,
                remark,
                CourseMemberSourceType.valueOf(member.getSourceType()));
        auditLogApplicationService.record(new AuditLogCommand(
                principal.getUserId(),
                AuditAction.COURSE_MEMBER_TRANSFERRED,
                "COURSE_MEMBER",
                String.valueOf(member.getId()),
                AuditResult.SUCCESS,
                "offering",
                offeringId,
                AuditDecision.ALLOW,
                Map.of(
                        "userId",
                        member.getUserId(),
                        "fromTeachingClassId",
                        sourceTeachingClassId,
                        "toTeachingClassId",
                        targetTeachingClassId,
                        "transferredMemberId",
                        transferredMember.getId())));
        invalidateUserSessions(member.getUserId(), principal.getUserId(), "COURSE_MEMBER_TRANSFERRED");
        return toCourseMemberView(transferredMember, true);
    }

    @Transactional(readOnly = true)
    public List<MyCourseView> listMyCourses(AuthenticatedUserPrincipal principal) {
        return cacheService.getOrLoadList(
                MY_COURSES_CACHE_NAME,
                myCoursesCacheKey(principal.getUserId()),
                redisEnhancementProperties.getCache().getMyCoursesTtl(),
                MyCourseView.class,
                () -> loadMyCourses(principal));
    }

    static String myCoursesCacheKey(Long userId) {
        return "user:%d".formatted(userId);
    }

    void evictMyCoursesCache(Long userId) {
        cacheService.evict(MY_COURSES_CACHE_NAME, myCoursesCacheKey(userId));
    }

    void evictMyCoursesCacheByOffering(Long offeringId) {
        List<String> keySuffixes = courseMemberMapper
                .selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .eq(CourseMemberEntity::getMemberStatus, CourseMemberStatus.ACTIVE.name()))
                .stream()
                .map(CourseMemberEntity::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .map(CourseTeachingApplicationService::myCoursesCacheKey)
                .toList();
        cacheService.evictAll(MY_COURSES_CACHE_NAME, keySuffixes);
    }

    private List<MyCourseView> loadMyCourses(AuthenticatedUserPrincipal principal) {
        List<CourseMemberEntity> memberships = courseMemberMapper.selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                .eq(CourseMemberEntity::getUserId, principal.getUserId())
                .eq(CourseMemberEntity::getMemberStatus, CourseMemberStatus.ACTIVE.name())
                .orderByAsc(CourseMemberEntity::getOfferingId)
                .orderByAsc(CourseMemberEntity::getId));
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<Long, CourseOfferingEntity> offerings =
                courseOfferingMapper
                        .selectByIds(memberships.stream()
                                .map(CourseMemberEntity::getOfferingId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(
                                CourseOfferingEntity::getId,
                                offering -> offering,
                                (left, right) -> left,
                                LinkedHashMap::new));
        List<Long> teachingClassIds = memberships.stream()
                .map(CourseMemberEntity::getTeachingClassId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, TeachingClassEntity> classes = teachingClassIds.isEmpty()
                ? Map.of()
                : teachingClassMapper.selectByIds(teachingClassIds).stream()
                        .collect(Collectors.toMap(
                                TeachingClassEntity::getId,
                                teachingClass -> teachingClass,
                                (left, right) -> left,
                                LinkedHashMap::new));
        Map<Long, OrgUnitSummaryView> colleges =
                organizationApplicationService.loadSummaryMap(offerings.values().stream()
                        .map(CourseOfferingEntity::getPrimaryCollegeUnitId)
                        .toList());
        return memberships.stream()
                .collect(Collectors.groupingBy(
                        CourseMemberEntity::getOfferingId, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> toMyCourseView(
                        offerings.get(entry.getKey()),
                        colleges.get(offerings.get(entry.getKey()).getPrimaryCollegeUnitId()),
                        entry.getValue(),
                        classes))
                .toList();
    }

    private CourseMemberEntity addSingleMember(
            CourseOfferingEntity offering,
            Long userId,
            CourseMemberRole role,
            Long teachingClassId,
            String remark,
            CourseMemberSourceType sourceType) {
        UserDirectoryEntryView user =
                userDirectoryApplicationService.loadByIds(List.of(userId)).get(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_MEMBER_USER_NOT_FOUND", "用户不存在");
        }
        TeachingClassEntity teachingClass = teachingClassId == null ? null : requireTeachingClass(teachingClassId);
        validateMemberRoleBinding(offering, role, teachingClass);
        assertNoTeacherStudentRoleConflict(offering.getId(), userId, role);
        CourseMemberEntity existing = findExistingMember(offering.getId(), userId, role, teachingClassId);
        boolean newActiveStudent = existing == null && role == CourseMemberRole.STUDENT;
        if (existing != null && CourseMemberStatus.ACTIVE.name().equals(existing.getMemberStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "COURSE_MEMBER_DUPLICATED", "课程成员已存在");
        }
        if (role == CourseMemberRole.STUDENT) {
            assertOfferingCapacity(offering);
            assertTeachingClassCapacity(teachingClass);
        }

        OffsetDateTime now = OffsetDateTime.now();
        CourseMemberEntity entity = existing == null ? new CourseMemberEntity() : existing;
        entity.setOfferingId(offering.getId());
        entity.setTeachingClassId(teachingClassId);
        entity.setUserId(userId);
        entity.setMemberRole(role.name());
        entity.setMemberStatus(CourseMemberStatus.ACTIVE.name());
        entity.setSourceType(sourceType.name());
        entity.setRemark(remark == null || remark.isBlank() ? null : remark.trim());
        entity.setJoinedAt(existing == null ? now : existing.getJoinedAt());
        entity.setLeftAt(null);
        if (existing == null) {
            courseMemberMapper.insert(entity);
        } else {
            courseMemberMapper.updateById(entity);
        }
        courseMemberRoleBindingSyncService.sync(entity);

        Long membershipOrgUnitId =
                teachingClass == null ? offering.getOrgCourseUnitId() : teachingClass.getOrgClassUnitId();
        userOrgMembershipApplicationService.upsertMembership(
                userId,
                membershipOrgUnitId,
                role.toMembershipType(),
                MembershipStatus.ACTIVE,
                MembershipSourceType.valueOf(sourceType.name()),
                now,
                null);
        if (role == CourseMemberRole.STUDENT
                && (newActiveStudent
                        || !CourseMemberStatus.ACTIVE
                                .name()
                                .equals(existing == null ? null : existing.getMemberStatus()))) {
            offering.setSelectedCount(offering.getSelectedCount() + 1);
            courseOfferingMapper.updateById(offering);
        }
        evictMyCoursesCache(userId);
        return entity;
    }

    private void assertNoTeacherStudentRoleConflict(Long offeringId, Long userId, CourseMemberRole incomingRole) {
        if (incomingRole != CourseMemberRole.STUDENT && !TEACHER_MEMBER_ROLES.contains(incomingRole)) {
            return;
        }
        Set<String> conflictingRoles = incomingRole == CourseMemberRole.STUDENT
                ? TEACHER_MEMBER_ROLES.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet())
                : Set.of(CourseMemberRole.STUDENT.name());
        CourseMemberEntity conflicting = courseMemberMapper.selectOne(Wrappers.<CourseMemberEntity>lambdaQuery()
                .eq(CourseMemberEntity::getOfferingId, offeringId)
                .eq(CourseMemberEntity::getUserId, userId)
                .in(CourseMemberEntity::getMemberRole, conflictingRoles)
                .eq(CourseMemberEntity::getMemberStatus, CourseMemberStatus.ACTIVE.name())
                .last("LIMIT 1"));
        if (conflicting != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "COURSE_MEMBER_ROLE_CONFLICT", "同一用户不能在同一开课同时作为教师与学生");
        }
    }

    private CourseMemberEntity findExistingMember(
            Long offeringId, Long userId, CourseMemberRole role, Long teachingClassId) {
        var query = Wrappers.<CourseMemberEntity>lambdaQuery()
                .eq(CourseMemberEntity::getOfferingId, offeringId)
                .eq(CourseMemberEntity::getUserId, userId)
                .eq(CourseMemberEntity::getMemberRole, role.name());
        if (teachingClassId != null) {
            query.eq(CourseMemberEntity::getTeachingClassId, teachingClassId);
        }
        return courseMemberMapper.selectOne(query.last("LIMIT 1"));
    }

    private void validateMemberRoleBinding(
            CourseOfferingEntity offering, CourseMemberRole role, TeachingClassEntity teachingClass) {
        if (role.requiresTeachingClass() && teachingClass == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_MEMBER_CLASS_REQUIRED", "当前角色必须绑定教学班");
        }
        if (!role.requiresTeachingClass() && teachingClass != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_MEMBER_CLASS_UNEXPECTED", "当前角色不能直接绑定教学班");
        }
        if (teachingClass != null && !Objects.equals(teachingClass.getOfferingId(), offering.getId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_MEMBER_CLASS_SCOPE_INVALID", "教学班不属于当前课程");
        }
    }

    private Long resolveTeachingClassId(
            CourseMemberRole role, String rawClassCode, Map<String, TeachingClassEntity> classByCode) {
        if (!role.requiresTeachingClass()) {
            return null;
        }
        String normalizedClassCode = normalizeCode(rawClassCode);
        TeachingClassEntity teachingClass = classByCode.get(normalizedClassCode);
        if (teachingClass == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "TEACHING_CLASS_NOT_FOUND", "教学班编码不存在");
        }
        return teachingClass.getId();
    }

    private void assertOfferingCapacity(CourseOfferingEntity offering) {
        if (offering.getSelectedCount() >= offering.getCapacity()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_CAPACITY_EXCEEDED", "课程容量已满");
        }
    }

    private void assertTeachingClassCapacity(TeachingClassEntity teachingClass) {
        long activeStudents = courseMemberMapper.selectCount(Wrappers.<CourseMemberEntity>lambdaQuery()
                .eq(CourseMemberEntity::getTeachingClassId, teachingClass.getId())
                .eq(CourseMemberEntity::getMemberRole, CourseMemberRole.STUDENT.name())
                .eq(CourseMemberEntity::getMemberStatus, CourseMemberStatus.ACTIVE.name()));
        if (activeStudents >= teachingClass.getCapacity()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "TEACHING_CLASS_CAPACITY_EXCEEDED", "教学班容量已满");
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CODE_REQUIRED", "编码不能为空");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String generateOrgClassCode(String offeringCode, String classCode) {
        String generated = normalizeCode(offeringCode) + "-" + normalizeCode(classCode);
        if (generated.length() > 64) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ORG_CODE_TOO_LONG", "教学班组织编码过长");
        }
        return generated;
    }

    private CourseOfferingEntity requireOffering(Long offeringId) {
        CourseOfferingEntity offering = courseOfferingMapper.selectById(offeringId);
        if (offering == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COURSE_OFFERING_NOT_FOUND", "课程开设实例不存在");
        }
        return offering;
    }

    private void assertOfferingWritable(CourseOfferingEntity offering) {
        if (CourseOfferingStatus.ARCHIVED.name().equals(offering.getStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "COURSE_ARCHIVED", "归档课程仅允许只读访问");
        }
    }

    private TeachingClassEntity requireTeachingClass(Long teachingClassId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(teachingClassId);
        if (teachingClass == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "TEACHING_CLASS_NOT_FOUND", "教学班不存在");
        }
        return teachingClass;
    }

    private CourseMemberEntity requireMember(Long offeringId, Long memberId) {
        CourseMemberEntity member = courseMemberMapper.selectOne(Wrappers.<CourseMemberEntity>lambdaQuery()
                .eq(CourseMemberEntity::getId, memberId)
                .eq(CourseMemberEntity::getOfferingId, offeringId)
                .last("LIMIT 1"));
        if (member == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COURSE_MEMBER_NOT_FOUND", "课程成员不存在");
        }
        return member;
    }

    private void applyMemberStatus(
            CourseOfferingEntity offering,
            CourseMemberEntity member,
            CourseMemberStatus targetStatus,
            String remark,
            OffsetDateTime now) {
        String previousStatus = member.getMemberStatus();
        CourseMemberRole role = CourseMemberRole.valueOf(member.getMemberRole());
        member.setMemberStatus(targetStatus.name());
        member.setRemark(remark == null || remark.isBlank() ? member.getRemark() : remark.trim());
        if (targetStatus == CourseMemberStatus.ACTIVE) {
            member.setLeftAt(null);
            if (member.getJoinedAt() == null) {
                member.setJoinedAt(now);
            }
        } else {
            member.setLeftAt(now);
        }
        courseMemberMapper.updateById(member);
        courseMemberRoleBindingSyncService.sync(member);

        TeachingClassEntity teachingClass =
                member.getTeachingClassId() == null ? null : requireTeachingClass(member.getTeachingClassId());
        MembershipStatus membershipStatus = toMembershipStatus(targetStatus);
        userOrgMembershipApplicationService.upsertMembership(
                member.getUserId(),
                teachingClass == null ? offering.getOrgCourseUnitId() : teachingClass.getOrgClassUnitId(),
                role.toMembershipType(),
                membershipStatus,
                MembershipSourceType.valueOf(member.getSourceType()),
                member.getJoinedAt() == null ? now : member.getJoinedAt(),
                membershipStatus == MembershipStatus.ACTIVE ? null : now);
        adjustStudentSelectedCount(offering, role, previousStatus, targetStatus.name());
        evictMyCoursesCache(member.getUserId());
    }

    private MembershipStatus toMembershipStatus(CourseMemberStatus targetStatus) {
        return switch (targetStatus) {
            case ACTIVE -> MembershipStatus.ACTIVE;
            case COMPLETED -> MembershipStatus.COMPLETED;
            case REMOVED -> MembershipStatus.REMOVED;
            case PENDING, DROPPED, TRANSFERRED -> MembershipStatus.INACTIVE;
        };
    }

    private void adjustStudentSelectedCount(
            CourseOfferingEntity offering, CourseMemberRole role, String previousStatus, String currentStatus) {
        if (role != CourseMemberRole.STUDENT) {
            return;
        }
        boolean wasActive = CourseMemberStatus.ACTIVE.name().equals(previousStatus);
        boolean isActive = CourseMemberStatus.ACTIVE.name().equals(currentStatus);
        if (wasActive == isActive) {
            return;
        }
        int nextSelectedCount = offering.getSelectedCount() + (isActive ? 1 : -1);
        offering.setSelectedCount(Math.max(nextSelectedCount, 0));
        courseOfferingMapper.updateById(offering);
    }

    private void invalidateUserSessions(Long userId, Long actorUserId, String reason) {
        authSessionApplicationService.invalidateAllSessionsForUser(userId, actorUserId, reason);
        evictMyCoursesCache(userId);
    }

    private TeachingClassView toTeachingClassView(TeachingClassEntity entity, OrgUnitSummaryView orgClass) {
        return new TeachingClassView(
                entity.getId(),
                entity.getOfferingId(),
                entity.getClassCode(),
                entity.getClassName(),
                entity.getEntryYear(),
                orgClass,
                entity.getCapacity(),
                TeachingClassStatus.valueOf(entity.getStatus()),
                entity.getScheduleSummary(),
                new TeachingClassFeaturesView(
                        Boolean.TRUE.equals(entity.getAnnouncementEnabled()),
                        Boolean.TRUE.equals(entity.getDiscussionEnabled()),
                        Boolean.TRUE.equals(entity.getResourceEnabled()),
                        Boolean.TRUE.equals(entity.getLabEnabled()),
                        Boolean.TRUE.equals(entity.getAssignmentEnabled())),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private boolean canRevealSensitiveMemberFields(AuthenticatedUserPrincipal principal, Long offeringId) {
        return courseAuthorizationService.canManageMembers(principal, offeringId);
    }

    private CourseMemberView toCourseMemberView(CourseMemberListRow row, boolean revealSensitiveFields) {
        AcademicProfileView academicProfile = row.getAcademicProfileId() == null
                ? null
                : new AcademicProfileView(
                        row.getAcademicProfileId(),
                        row.getUserId(),
                        row.getAcademicId(),
                        row.getAcademicRealName(),
                        AcademicIdentityType.valueOf(row.getAcademicIdentityType()),
                        AcademicProfileStatus.valueOf(row.getAcademicProfileStatus()),
                        revealSensitiveFields ? row.getAcademicPhone() : null);
        UserDirectoryEntryView user = new UserDirectoryEntryView(
                row.getUserId(),
                row.getUsername(),
                row.getDisplayName(),
                revealSensitiveFields ? row.getEmail() : null,
                revealSensitiveFields ? row.getUserPhone() : null,
                academicProfile);
        return new CourseMemberView(
                row.getId(),
                row.getOfferingId(),
                row.getTeachingClassId(),
                row.getClassCode(),
                row.getClassName(),
                user,
                CourseMemberRole.valueOf(row.getMemberRole()),
                CourseMemberStatus.valueOf(row.getMemberStatus()),
                CourseMemberSourceType.valueOf(row.getSourceType()),
                row.getRemark(),
                row.getJoinedAt(),
                row.getLeftAt());
    }

    private CourseMemberView toCourseMemberView(CourseMemberEntity entity, boolean revealSensitiveFields) {
        TeachingClassEntity teachingClass =
                entity.getTeachingClassId() == null ? null : requireTeachingClass(entity.getTeachingClassId());
        UserDirectoryEntryView user = userDirectoryApplicationService
                .loadByIds(List.of(entity.getUserId()))
                .get(entity.getUserId());
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COURSE_MEMBER_USER_NOT_FOUND", "课程成员用户不存在");
        }
        AcademicProfileView academicProfile = user.academicProfile();
        if (!revealSensitiveFields && academicProfile != null) {
            academicProfile = new AcademicProfileView(
                    academicProfile.id(),
                    academicProfile.userId(),
                    academicProfile.academicId(),
                    academicProfile.realName(),
                    academicProfile.identityType(),
                    academicProfile.profileStatus(),
                    null);
        }
        UserDirectoryEntryView sanitizedUser = revealSensitiveFields
                ? user
                : new UserDirectoryEntryView(
                        user.id(), user.username(), user.displayName(), null, null, academicProfile);
        return new CourseMemberView(
                entity.getId(),
                entity.getOfferingId(),
                entity.getTeachingClassId(),
                teachingClass == null ? null : teachingClass.getClassCode(),
                teachingClass == null ? null : teachingClass.getClassName(),
                sanitizedUser,
                CourseMemberRole.valueOf(entity.getMemberRole()),
                CourseMemberStatus.valueOf(entity.getMemberStatus()),
                CourseMemberSourceType.valueOf(entity.getSourceType()),
                entity.getRemark(),
                entity.getJoinedAt(),
                entity.getLeftAt());
    }

    private MyCourseView toMyCourseView(
            CourseOfferingEntity offering,
            OrgUnitSummaryView primaryCollege,
            List<CourseMemberEntity> memberships,
            Map<Long, TeachingClassEntity> classes) {
        List<String> roles = memberships.stream()
                .map(CourseMemberEntity::getMemberRole)
                .distinct()
                .sorted()
                .toList();
        List<MyCourseClassView> classViews = memberships.stream()
                .filter(member -> member.getTeachingClassId() != null)
                .map(member -> {
                    TeachingClassEntity teachingClass = classes.get(member.getTeachingClassId());
                    return new MyCourseClassView(
                            member.getTeachingClassId(),
                            teachingClass == null ? null : teachingClass.getClassCode(),
                            teachingClass == null ? null : teachingClass.getClassName(),
                            CourseMemberRole.valueOf(member.getMemberRole()));
                })
                .toList();
        return new MyCourseView(
                offering.getId(),
                offering.getOfferingCode(),
                offering.getOfferingName(),
                CourseOfferingStatus.valueOf(offering.getStatus()),
                primaryCollege,
                roles,
                classViews);
    }
}
