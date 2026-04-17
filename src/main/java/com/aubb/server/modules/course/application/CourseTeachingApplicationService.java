package com.aubb.server.modules.course.application;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.cache.CacheService;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.config.RedisEnhancementProperties;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
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
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingMapper;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassEntity;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.user.UserDirectoryApplicationService;
import com.aubb.server.modules.identityaccess.application.user.UserOrgMembershipApplicationService;
import com.aubb.server.modules.identityaccess.application.user.view.UserDirectoryEntryView;
import com.aubb.server.modules.identityaccess.domain.membership.MembershipSourceType;
import com.aubb.server.modules.identityaccess.domain.membership.MembershipStatus;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    private final CourseOfferingMapper courseOfferingMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseMemberMapper courseMemberMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final OrganizationApplicationService organizationApplicationService;
    private final UserDirectoryApplicationService userDirectoryApplicationService;
    private final UserOrgMembershipApplicationService userOrgMembershipApplicationService;
    private final AuditLogApplicationService auditLogApplicationService;
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
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.TEACHING_CLASS_CREATED,
                "TEACHING_CLASS",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of("offeringId", offeringId, "classCode", normalizedClassCode, "entryYear", entryYear));
        return toTeachingClassView(entity, orgClass);
    }

    @Transactional(readOnly = true)
    public List<TeachingClassView> listTeachingClasses(Long offeringId, AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageOffering(principal, offeringId);
        Map<Long, OrgUnitSummaryView> orgClassSummaries =
                organizationApplicationService.loadSummaryMap(teachingClassMapper
                        .selectList(Wrappers.<TeachingClassEntity>lambdaQuery()
                                .eq(TeachingClassEntity::getOfferingId, offeringId)
                                .orderByAsc(TeachingClassEntity::getEntryYear)
                                .orderByAsc(TeachingClassEntity::getClassCode))
                        .stream()
                        .map(TeachingClassEntity::getOrgClassUnitId)
                        .toList());
        return teachingClassMapper
                .selectList(Wrappers.<TeachingClassEntity>lambdaQuery()
                        .eq(TeachingClassEntity::getOfferingId, offeringId)
                        .orderByAsc(TeachingClassEntity::getEntryYear)
                        .orderByAsc(TeachingClassEntity::getClassCode))
                .stream()
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
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.TEACHING_CLASS_FEATURES_UPDATED,
                "TEACHING_CLASS",
                String.valueOf(teachingClassId),
                AuditResult.SUCCESS,
                Map.of("announcementEnabled", announcementEnabled, "discussionEnabled", discussionEnabled));
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
        List<CourseMemberBatchError> errors = new ArrayList<>();
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
                success++;
            } catch (BusinessException exception) {
                errors.add(new CourseMemberBatchError(row, command.userId(), null, exception.getMessage()));
            }
        }
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.COURSE_MEMBERS_BATCH_ADDED,
                "COURSE_OFFERING",
                String.valueOf(offeringId),
                errors.isEmpty() ? AuditResult.SUCCESS : AuditResult.FAILURE,
                Map.of("successCount", success, "failCount", errors.size()));
        return new CourseMemberBatchResult(success, errors.size(), errors);
    }

    @Transactional
    public CourseMemberImportResult importMembers(
            Long offeringId, MultipartFile file, String importType, AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageMembers(principal, offeringId);
        if (!"csv".equalsIgnoreCase(importType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IMPORT_TYPE_UNSUPPORTED", "当前仅支持 csv 导入");
        }
        CourseOfferingEntity offering = requireOffering(offeringId);
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
                    success++;
                } catch (BusinessException exception) {
                    errors.add(new CourseMemberBatchError(rowNumber, null, username, exception.getMessage()));
                }
            }
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IMPORT_FILE_READ_FAILED", "无法读取导入文件");
        }

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.COURSE_MEMBERS_IMPORTED,
                "COURSE_OFFERING",
                String.valueOf(offeringId),
                errors.isEmpty() ? AuditResult.SUCCESS : AuditResult.FAILURE,
                Map.of("total", total, "success", success, "failed", errors.size()));
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
        courseAuthorizationService.assertCanViewMembers(principal, offeringId, teachingClassId);
        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        String memberRoleCode = memberRole == null ? null : memberRole.name();
        String memberStatusCode = memberStatus == null ? null : memberStatus.name();
        List<CourseMemberEntity> matched = courseMemberMapper
                .selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .eq(teachingClassId != null, CourseMemberEntity::getTeachingClassId, teachingClassId)
                        .eq(memberRoleCode != null, CourseMemberEntity::getMemberRole, memberRoleCode)
                        .eq(memberStatusCode != null, CourseMemberEntity::getMemberStatus, memberStatusCode)
                        .orderByAsc(CourseMemberEntity::getMemberRole)
                        .orderByAsc(CourseMemberEntity::getId))
                .stream()
                .filter(member -> {
                    if (courseAuthorizationService.isTeachingAssistantForClass(
                            principal.getUserId(), offeringId, teachingClassId)) {
                        return Objects.equals(member.getTeachingClassId(), teachingClassId);
                    }
                    return true;
                })
                .toList();
        Map<Long, UserDirectoryEntryView> users = userDirectoryApplicationService.loadByIds(
                matched.stream().map(CourseMemberEntity::getUserId).toList());
        Map<Long, TeachingClassEntity> classes = teachingClassMapper
                .selectByIds(matched.stream()
                        .map(CourseMemberEntity::getTeachingClassId)
                        .filter(Objects::nonNull)
                        .toList())
                .stream()
                .collect(Collectors.toMap(
                        TeachingClassEntity::getId,
                        teachingClass -> teachingClass,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<CourseMemberView> views = matched.stream()
                .map(member -> toCourseMemberView(
                        member, users.get(member.getUserId()), classes.get(member.getTeachingClassId())))
                .filter(view -> matchesMemberKeyword(view, normalizedKeyword))
                .toList();
        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePage - 1) * safePageSize;
        return new PageResponse<>(
                views.stream().skip(offset).limit(safePageSize).toList(), views.size(), safePage, safePageSize);
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

    private void addSingleMember(
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
    }

    private CourseMemberEntity findExistingMember(
            Long offeringId, Long userId, CourseMemberRole role, Long teachingClassId) {
        var query = Wrappers.<CourseMemberEntity>lambdaQuery()
                .eq(CourseMemberEntity::getOfferingId, offeringId)
                .eq(CourseMemberEntity::getUserId, userId)
                .eq(CourseMemberEntity::getMemberRole, role.name());
        if (role == CourseMemberRole.TA) {
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

    private boolean matchesMemberKeyword(CourseMemberView view, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return containsIgnoreCase(view.user().username(), keyword)
                || containsIgnoreCase(view.user().displayName(), keyword)
                || (view.user().academicProfile() != null
                        && containsIgnoreCase(view.user().academicProfile().academicId(), keyword))
                || (view.user().academicProfile() != null
                        && containsIgnoreCase(view.user().academicProfile().realName(), keyword));
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
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

    private TeachingClassEntity requireTeachingClass(Long teachingClassId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(teachingClassId);
        if (teachingClass == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "TEACHING_CLASS_NOT_FOUND", "教学班不存在");
        }
        return teachingClass;
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

    private CourseMemberView toCourseMemberView(
            CourseMemberEntity entity, UserDirectoryEntryView user, TeachingClassEntity teachingClass) {
        return new CourseMemberView(
                entity.getId(),
                entity.getOfferingId(),
                entity.getTeachingClassId(),
                teachingClass == null ? null : teachingClass.getClassCode(),
                teachingClass == null ? null : teachingClass.getClassName(),
                user,
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
