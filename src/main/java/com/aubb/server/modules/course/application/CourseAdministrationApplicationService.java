package com.aubb.server.modules.course.application;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.view.AcademicTermView;
import com.aubb.server.modules.course.application.view.CourseCatalogView;
import com.aubb.server.modules.course.application.view.CourseOfferingView;
import com.aubb.server.modules.course.domain.catalog.CourseCatalogStatus;
import com.aubb.server.modules.course.domain.catalog.CourseDeliveryMode;
import com.aubb.server.modules.course.domain.catalog.CourseLanguage;
import com.aubb.server.modules.course.domain.catalog.CourseType;
import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.course.domain.member.CourseMemberSourceType;
import com.aubb.server.modules.course.domain.member.CourseMemberStatus;
import com.aubb.server.modules.course.domain.offering.CollegeRelationType;
import com.aubb.server.modules.course.domain.offering.CourseOfferingStatus;
import com.aubb.server.modules.course.domain.term.AcademicTermSemester;
import com.aubb.server.modules.course.domain.term.AcademicTermStatus;
import com.aubb.server.modules.course.infrastructure.catalog.CourseCatalogEntity;
import com.aubb.server.modules.course.infrastructure.catalog.CourseCatalogMapper;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberEntity;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingCollegeMapEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingCollegeMapMapper;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingMapper;
import com.aubb.server.modules.course.infrastructure.term.AcademicTermEntity;
import com.aubb.server.modules.course.infrastructure.term.AcademicTermMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.user.UserDirectoryApplicationService;
import com.aubb.server.modules.identityaccess.application.user.UserOrgMembershipApplicationService;
import com.aubb.server.modules.identityaccess.application.user.view.UserDirectoryEntryView;
import com.aubb.server.modules.identityaccess.domain.membership.MembershipSourceType;
import com.aubb.server.modules.identityaccess.domain.membership.MembershipStatus;
import com.aubb.server.modules.organization.application.OrgUnitSummaryView;
import com.aubb.server.modules.organization.application.OrganizationApplicationService;
import com.aubb.server.modules.organization.domain.OrgUnitType;
import com.aubb.server.modules.organization.infrastructure.OrgUnitEntity;
import com.aubb.server.modules.organization.infrastructure.OrgUnitMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
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

@Service
@RequiredArgsConstructor
public class CourseAdministrationApplicationService {

    private final AcademicTermMapper academicTermMapper;
    private final CourseCatalogMapper courseCatalogMapper;
    private final CourseOfferingMapper courseOfferingMapper;
    private final CourseOfferingCollegeMapMapper courseOfferingCollegeMapMapper;
    private final CourseMemberMapper courseMemberMapper;
    private final OrgUnitMapper orgUnitMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final OrganizationApplicationService organizationApplicationService;
    private final UserDirectoryApplicationService userDirectoryApplicationService;
    private final UserOrgMembershipApplicationService userOrgMembershipApplicationService;
    private final AuditLogApplicationService auditLogApplicationService;

    @Transactional
    public AcademicTermView createTerm(
            String termCode,
            String termName,
            String schoolYear,
            AcademicTermSemester semester,
            LocalDate startDate,
            LocalDate endDate,
            AuthenticatedUserPrincipal principal) {
        String normalizedTermCode = normalizeCode(termCode);
        if (academicTermMapper.selectOne(Wrappers.<AcademicTermEntity>lambdaQuery()
                        .eq(AcademicTermEntity::getTermCode, normalizedTermCode)
                        .last("LIMIT 1"))
                != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "TERM_CODE_DUPLICATED", "学期编码已存在");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "TERM_DATE_RANGE_INVALID", "学期结束日期不能早于开始日期");
        }
        AcademicTermEntity entity = new AcademicTermEntity();
        entity.setTermCode(normalizedTermCode);
        entity.setTermName(termName);
        entity.setSchoolYear(schoolYear);
        entity.setSemester(semester.name());
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);
        entity.setStatus(AcademicTermStatus.PLANNING.name());
        academicTermMapper.insert(entity);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.ACADEMIC_TERM_CREATED,
                "ACADEMIC_TERM",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of("termCode", normalizedTermCode, "termName", termName));
        return toTermView(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<AcademicTermView> listTerms(
            String keyword, AcademicTermStatus status, long page, long pageSize) {
        String normalizedKeyword = normalizeKeyword(keyword);
        List<AcademicTermEntity> matched = academicTermMapper
                .selectList(Wrappers.<AcademicTermEntity>lambdaQuery()
                        .orderByDesc(AcademicTermEntity::getStartDate)
                        .orderByDesc(AcademicTermEntity::getId))
                .stream()
                .filter(term -> status == null || status.name().equals(term.getStatus()))
                .filter(term -> matchesTerm(term, normalizedKeyword))
                .toList();
        return pageTerms(matched, page, pageSize);
    }

    @Transactional
    public CourseCatalogView createCatalog(
            String courseCode,
            String courseName,
            CourseType courseType,
            BigDecimal credit,
            Integer totalHours,
            Long departmentUnitId,
            String description,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanCreateCatalog(principal, departmentUnitId);
        String normalizedCourseCode = normalizeCode(courseCode);
        if (courseCatalogMapper.selectOne(Wrappers.<CourseCatalogEntity>lambdaQuery()
                        .eq(CourseCatalogEntity::getCourseCode, normalizedCourseCode)
                        .last("LIMIT 1"))
                != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "COURSE_CODE_DUPLICATED", "课程编码已存在");
        }
        CourseCatalogEntity entity = new CourseCatalogEntity();
        entity.setCourseCode(normalizedCourseCode);
        entity.setCourseName(courseName);
        entity.setCourseType(courseType.name());
        entity.setCredit(credit);
        entity.setTotalHours(totalHours);
        entity.setDepartmentUnitId(departmentUnitId);
        entity.setDescription(description);
        entity.setStatus(CourseCatalogStatus.ACTIVE.name());
        courseCatalogMapper.insert(entity);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.COURSE_CATALOG_CREATED,
                "COURSE_CATALOG",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of("courseCode", normalizedCourseCode, "departmentUnitId", departmentUnitId));
        return toCatalogView(
                entity,
                organizationApplicationService
                        .loadSummaryMap(List.of(departmentUnitId))
                        .get(departmentUnitId));
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseCatalogView> listCatalogs(
            AuthenticatedUserPrincipal principal,
            String keyword,
            CourseType courseType,
            Long departmentUnitId,
            CourseCatalogStatus status,
            long page,
            long pageSize) {
        if (departmentUnitId != null) {
            courseAuthorizationService.assertCanCreateCatalog(principal, departmentUnitId);
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        List<CourseCatalogEntity> matched = courseCatalogMapper
                .selectList(Wrappers.<CourseCatalogEntity>lambdaQuery().orderByAsc(CourseCatalogEntity::getCourseCode))
                .stream()
                .filter(catalog ->
                        departmentUnitId == null || Objects.equals(departmentUnitId, catalog.getDepartmentUnitId()))
                .filter(catalog -> courseType == null || courseType.name().equals(catalog.getCourseType()))
                .filter(catalog -> status == null || status.name().equals(catalog.getStatus()))
                .filter(catalog -> matchesCatalog(catalog, normalizedKeyword))
                .toList();
        Map<Long, OrgUnitSummaryView> departments = organizationApplicationService.loadSummaryMap(matched.stream()
                .map(CourseCatalogEntity::getDepartmentUnitId)
                .filter(Objects::nonNull)
                .toList());
        return pageCatalogs(matched, departments, page, pageSize);
    }

    @Transactional
    public CourseOfferingView createOffering(
            Long catalogId,
            Long termId,
            String offeringCode,
            String offeringName,
            Long primaryCollegeUnitId,
            Collection<Long> secondaryCollegeUnitIds,
            CourseDeliveryMode deliveryMode,
            CourseLanguage language,
            Integer capacity,
            Collection<Long> instructorUserIds,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanCreateOffering(principal, primaryCollegeUnitId);
        CourseCatalogEntity catalog = requireCatalog(catalogId);
        AcademicTermEntity term = requireTerm(termId);
        validatePrimaryCollege(catalog, primaryCollegeUnitId);
        courseAuthorizationService.assertSecondaryCollegesCompatible(
                primaryCollegeUnitId,
                secondaryCollegeUnitIds == null
                        ? List.of()
                        : secondaryCollegeUnitIds.stream()
                                .filter(Objects::nonNull)
                                .toList());
        String normalizedOfferingCode = normalizeCode(offeringCode);
        if (courseOfferingMapper.selectOne(Wrappers.<CourseOfferingEntity>lambdaQuery()
                        .eq(CourseOfferingEntity::getOfferingCode, normalizedOfferingCode)
                        .last("LIMIT 1"))
                != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "COURSE_OFFERING_CODE_DUPLICATED", "开课编码已存在");
        }
        Set<Long> normalizedInstructorIds = normalizeIdSet(instructorUserIds);
        if (normalizedInstructorIds.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_INSTRUCTOR_REQUIRED", "开课实例至少需要一名教师");
        }
        Map<Long, UserDirectoryEntryView> instructors =
                userDirectoryApplicationService.loadByIds(normalizedInstructorIds);
        if (instructors.size() != normalizedInstructorIds.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_INSTRUCTOR_NOT_FOUND", "存在教师用户不存在");
        }
        var courseNode = organizationApplicationService.createOrgUnit(
                offeringName, normalizedOfferingCode, OrgUnitType.COURSE, primaryCollegeUnitId, 0, principal);

        CourseOfferingEntity entity = new CourseOfferingEntity();
        entity.setCatalogId(catalogId);
        entity.setTermId(termId);
        entity.setOfferingCode(normalizedOfferingCode);
        entity.setOfferingName(offeringName);
        entity.setPrimaryCollegeUnitId(primaryCollegeUnitId);
        entity.setOrgCourseUnitId(courseNode.getId());
        entity.setDeliveryMode(deliveryMode.name());
        entity.setLanguage(language.name());
        entity.setCapacity(capacity);
        entity.setSelectedCount(0);
        entity.setStatus(CourseOfferingStatus.DRAFT.name());
        entity.setStartAt(startAt);
        entity.setEndAt(endAt);
        entity.setCreatedByUserId(principal.getUserId());
        courseOfferingMapper.insert(entity);

        persistOfferingCollegeLinks(entity.getId(), primaryCollegeUnitId, secondaryCollegeUnitIds);
        persistInitialInstructors(entity, normalizedInstructorIds);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.COURSE_OFFERING_CREATED,
                "COURSE_OFFERING",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of("offeringCode", normalizedOfferingCode, "catalogId", catalogId, "termId", termId));
        return toOfferingView(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseOfferingView> listOfferings(
            AuthenticatedUserPrincipal principal,
            Long collegeUnitId,
            CourseOfferingStatus status,
            String keyword,
            long page,
            long pageSize) {
        if (collegeUnitId != null) {
            courseAuthorizationService.assertCanCreateCatalog(principal, collegeUnitId);
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        List<CourseOfferingEntity> visible = courseOfferingMapper
                .selectList(Wrappers.<CourseOfferingEntity>lambdaQuery()
                        .orderByDesc(CourseOfferingEntity::getCreatedAt)
                        .orderByDesc(CourseOfferingEntity::getId))
                .stream()
                .filter(offering -> status == null || status.name().equals(offering.getStatus()))
                .filter(offering -> collegeUnitId == null
                        || loadManagingCollegeIds(offering.getId()).contains(collegeUnitId))
                .filter(offering -> courseAuthorizationService.canManageOfferingAsAdmin(principal, offering.getId()))
                .filter(offering -> matchesOffering(offering, normalizedKeyword))
                .toList();
        return pageOfferings(visible, page, pageSize);
    }

    @Transactional(readOnly = true)
    public CourseOfferingView getOffering(Long offeringId, AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanViewOfferingAsAdmin(principal, offeringId);
        return toOfferingView(requireOffering(offeringId));
    }

    private void persistOfferingCollegeLinks(
            Long offeringId, Long primaryCollegeUnitId, Collection<Long> secondaryCollegeUnitIds) {
        CourseOfferingCollegeMapEntity primary = new CourseOfferingCollegeMapEntity();
        primary.setOfferingId(offeringId);
        primary.setCollegeUnitId(primaryCollegeUnitId);
        primary.setRelationType(CollegeRelationType.PRIMARY.name());
        courseOfferingCollegeMapMapper.insert(primary);
        if (secondaryCollegeUnitIds == null) {
            return;
        }
        for (Long collegeUnitId : secondaryCollegeUnitIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))) {
            CourseOfferingCollegeMapEntity secondary = new CourseOfferingCollegeMapEntity();
            secondary.setOfferingId(offeringId);
            secondary.setCollegeUnitId(collegeUnitId);
            secondary.setRelationType(CollegeRelationType.CROSS_LISTED.name());
            courseOfferingCollegeMapMapper.insert(secondary);
        }
    }

    private void persistInitialInstructors(CourseOfferingEntity offering, Collection<Long> instructorUserIds) {
        for (Long instructorUserId : instructorUserIds) {
            CourseMemberEntity member = new CourseMemberEntity();
            member.setOfferingId(offering.getId());
            member.setUserId(instructorUserId);
            member.setMemberRole(CourseMemberRole.INSTRUCTOR.name());
            member.setMemberStatus(CourseMemberStatus.ACTIVE.name());
            member.setSourceType(CourseMemberSourceType.MANUAL.name());
            member.setJoinedAt(OffsetDateTime.now());
            courseMemberMapper.insert(member);
            userOrgMembershipApplicationService.upsertMembership(
                    instructorUserId,
                    offering.getOrgCourseUnitId(),
                    CourseMemberRole.INSTRUCTOR.toMembershipType(),
                    MembershipStatus.ACTIVE,
                    MembershipSourceType.MANUAL,
                    member.getJoinedAt(),
                    null);
        }
    }

    private boolean matchesTerm(AcademicTermEntity term, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return containsIgnoreCase(term.getTermCode(), keyword) || containsIgnoreCase(term.getTermName(), keyword);
    }

    private boolean matchesCatalog(CourseCatalogEntity catalog, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return containsIgnoreCase(catalog.getCourseCode(), keyword)
                || containsIgnoreCase(catalog.getCourseName(), keyword);
    }

    private boolean matchesOffering(CourseOfferingEntity offering, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return containsIgnoreCase(offering.getOfferingCode(), keyword)
                || containsIgnoreCase(offering.getOfferingName(), keyword);
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

    private String normalizeKeyword(String keyword) {
        return keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
    }

    private Set<Long> normalizeIdSet(Collection<Long> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void validatePrimaryCollege(CourseCatalogEntity catalog, Long primaryCollegeUnitId) {
        if (!Objects.equals(catalog.getDepartmentUnitId(), primaryCollegeUnitId)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "COURSE_PRIMARY_COLLEGE_MISMATCH", "开课主学院必须与课程模板所属学院一致");
        }
        OrgUnitEntity primaryCollege = orgUnitMapper.selectById(primaryCollegeUnitId);
        if (primaryCollege == null || !OrgUnitType.COLLEGE.name().equals(primaryCollege.getType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COLLEGE_NOT_FOUND", "主学院不存在");
        }
    }

    private AcademicTermEntity requireTerm(Long termId) {
        AcademicTermEntity term = academicTermMapper.selectById(termId);
        if (term == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ACADEMIC_TERM_NOT_FOUND", "学期不存在");
        }
        return term;
    }

    private CourseCatalogEntity requireCatalog(Long catalogId) {
        CourseCatalogEntity catalog = courseCatalogMapper.selectById(catalogId);
        if (catalog == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COURSE_CATALOG_NOT_FOUND", "课程模板不存在");
        }
        return catalog;
    }

    private CourseOfferingEntity requireOffering(Long offeringId) {
        CourseOfferingEntity offering = courseOfferingMapper.selectById(offeringId);
        if (offering == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COURSE_OFFERING_NOT_FOUND", "课程开设实例不存在");
        }
        return offering;
    }

    private List<Long> loadManagingCollegeIds(Long offeringId) {
        return courseOfferingCollegeMapMapper
                .selectList(Wrappers.<CourseOfferingCollegeMapEntity>lambdaQuery()
                        .eq(CourseOfferingCollegeMapEntity::getOfferingId, offeringId))
                .stream()
                .map(CourseOfferingCollegeMapEntity::getCollegeUnitId)
                .toList();
    }

    private PageResponse<AcademicTermView> pageTerms(List<AcademicTermEntity> terms, long page, long pageSize) {
        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePage - 1) * safePageSize;
        return new PageResponse<>(
                terms.stream()
                        .skip(offset)
                        .limit(safePageSize)
                        .map(this::toTermView)
                        .toList(),
                terms.size(),
                safePage,
                safePageSize);
    }

    private PageResponse<CourseCatalogView> pageCatalogs(
            List<CourseCatalogEntity> catalogs, Map<Long, OrgUnitSummaryView> departments, long page, long pageSize) {
        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePage - 1) * safePageSize;
        return new PageResponse<>(
                catalogs.stream()
                        .skip(offset)
                        .limit(safePageSize)
                        .map(catalog -> toCatalogView(catalog, departments.get(catalog.getDepartmentUnitId())))
                        .toList(),
                catalogs.size(),
                safePage,
                safePageSize);
    }

    private PageResponse<CourseOfferingView> pageOfferings(
            List<CourseOfferingEntity> offerings, long page, long pageSize) {
        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePage - 1) * safePageSize;
        return new PageResponse<>(
                offerings.stream()
                        .skip(offset)
                        .limit(safePageSize)
                        .map(this::toOfferingView)
                        .toList(),
                offerings.size(),
                safePage,
                safePageSize);
    }

    private AcademicTermView toTermView(AcademicTermEntity entity) {
        return new AcademicTermView(
                entity.getId(),
                entity.getTermCode(),
                entity.getTermName(),
                entity.getSchoolYear(),
                AcademicTermSemester.valueOf(entity.getSemester()),
                entity.getStartDate(),
                entity.getEndDate(),
                AcademicTermStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private CourseCatalogView toCatalogView(CourseCatalogEntity entity, OrgUnitSummaryView department) {
        return new CourseCatalogView(
                entity.getId(),
                entity.getCourseCode(),
                entity.getCourseName(),
                CourseType.valueOf(entity.getCourseType()),
                entity.getCredit(),
                entity.getTotalHours(),
                department,
                entity.getDescription(),
                CourseCatalogStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private CourseOfferingView toOfferingView(CourseOfferingEntity entity) {
        CourseCatalogEntity catalog = requireCatalog(entity.getCatalogId());
        AcademicTermEntity term = requireTerm(entity.getTermId());
        Map<Long, OrgUnitSummaryView> colleges =
                organizationApplicationService.loadSummaryMap(loadManagingCollegeIds(entity.getId()));
        OrgUnitSummaryView primaryCollege = organizationApplicationService
                .loadSummaryMap(List.of(entity.getPrimaryCollegeUnitId()))
                .get(entity.getPrimaryCollegeUnitId());
        List<OrgUnitSummaryView> managingColleges = loadManagingCollegeIds(entity.getId()).stream()
                .map(colleges::get)
                .filter(Objects::nonNull)
                .toList();
        List<Long> instructorIds = courseMemberMapper
                .selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getOfferingId, entity.getId())
                        .eq(CourseMemberEntity::getMemberRole, CourseMemberRole.INSTRUCTOR.name())
                        .eq(CourseMemberEntity::getMemberStatus, CourseMemberStatus.ACTIVE.name()))
                .stream()
                .map(CourseMemberEntity::getUserId)
                .toList();
        List<UserDirectoryEntryView> instructors = instructorIds.isEmpty()
                ? List.of()
                : instructorIds.stream()
                        .map(userDirectoryApplicationService.loadByIds(instructorIds)::get)
                        .filter(Objects::nonNull)
                        .toList();
        return new CourseOfferingView(
                entity.getId(),
                entity.getCatalogId(),
                catalog.getCourseCode(),
                catalog.getCourseName(),
                entity.getTermId(),
                term.getTermCode(),
                entity.getOfferingCode(),
                entity.getOfferingName(),
                entity.getOrgCourseUnitId(),
                primaryCollege,
                managingColleges,
                instructors,
                CourseDeliveryMode.valueOf(entity.getDeliveryMode()),
                CourseLanguage.valueOf(entity.getLanguage()),
                entity.getCapacity(),
                entity.getSelectedCount(),
                CourseOfferingStatus.valueOf(entity.getStatus()),
                entity.getIntro(),
                entity.getStartAt(),
                entity.getEndAt(),
                entity.getPublishAt(),
                entity.getArchivedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
