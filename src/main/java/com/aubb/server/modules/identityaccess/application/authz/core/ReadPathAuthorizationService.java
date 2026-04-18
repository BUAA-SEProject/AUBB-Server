package com.aubb.server.modules.identityaccess.application.authz.core;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.assignment.infrastructure.AssignmentEntity;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberEntity;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingCollegeMapEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingCollegeMapMapper;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingMapper;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassEntity;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.AuthzScopeResolutionService;
import com.aubb.server.modules.identityaccess.application.authz.ScopeRef;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import com.aubb.server.modules.organization.infrastructure.OrgUnitEntity;
import com.aubb.server.modules.organization.infrastructure.OrgUnitMapper;
import com.aubb.server.modules.submission.infrastructure.SubmissionEntity;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
public class ReadPathAuthorizationService {

    private static final String ACTIVE = "ACTIVE";
    private static final Set<String> TEACHING_READ_EXCLUDED_ROLES = Set.of("student");

    private final PermissionAuthorizationService permissionAuthorizationService;
    private final AuthzScopeResolutionService authzScopeResolutionService;
    private final CourseOfferingMapper courseOfferingMapper;
    private final CourseOfferingCollegeMapMapper courseOfferingCollegeMapMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseMemberMapper courseMemberMapper;
    private final OrgUnitMapper orgUnitMapper;

    @Transactional(readOnly = true)
    public Set<Long> loadReadableOfferingIds(AuthenticatedUserPrincipal principal, String permissionCode) {
        List<AuthorizationScopeFilterClause> clauses = loadClauses(principal, permissionCode);
        if (clauses.isEmpty()) {
            return Set.of();
        }
        if (clauses.stream().anyMatch(clause -> clause.scope().type() == AuthorizationScopeType.PLATFORM)) {
            return selectOfferingIds(
                    Wrappers.<CourseOfferingEntity>lambdaQuery().select(CourseOfferingEntity::getId));
        }
        ExpandedOrgScopes expandedOrgScopes = expandOrgScopes(clauses);
        Set<Long> offeringIds = clauses.stream()
                .map(AuthorizationScopeFilterClause::scope)
                .filter(scope -> scope.type() == AuthorizationScopeType.OFFERING)
                .map(AuthorizationScope::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!expandedOrgScopes.courseIds().isEmpty()) {
            offeringIds.addAll(selectOfferingIds(Wrappers.<CourseOfferingEntity>lambdaQuery()
                    .select(CourseOfferingEntity::getId)
                    .in(CourseOfferingEntity::getOrgCourseUnitId, expandedOrgScopes.courseIds())));
        }
        if (!expandedOrgScopes.collegeIds().isEmpty()) {
            offeringIds.addAll(selectOfferingIds(Wrappers.<CourseOfferingEntity>lambdaQuery()
                    .select(CourseOfferingEntity::getId)
                    .in(CourseOfferingEntity::getPrimaryCollegeUnitId, expandedOrgScopes.collegeIds())));
            offeringIds.addAll(courseOfferingCollegeMapMapper
                    .selectList(Wrappers.<CourseOfferingCollegeMapEntity>lambdaQuery()
                            .select(CourseOfferingCollegeMapEntity::getOfferingId)
                            .in(CourseOfferingCollegeMapEntity::getCollegeUnitId, expandedOrgScopes.collegeIds()))
                    .stream()
                    .map(CourseOfferingCollegeMapEntity::getOfferingId)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        return Set.copyOf(offeringIds);
    }

    @Transactional(readOnly = true)
    public TeachingReadScope resolveTeachingReadScope(
            AuthenticatedUserPrincipal principal, String permissionCode, Long offeringId) {
        List<AuthorizationScopeFilterClause> clauses = loadTeachingClauses(principal, permissionCode);
        if (clauses.isEmpty()) {
            return TeachingReadScope.none(offeringId);
        }
        ScopeRef offeringScope = authzScopeResolutionService.resolveScope(AuthorizationScopeType.OFFERING, offeringId);
        if (clauses.stream().anyMatch(clause -> isCoveredBy(offeringScope, clause.scope()))) {
            return TeachingReadScope.offeringWide(offeringId);
        }
        Set<Long> classIds = teachingClassMapper
                .selectList(Wrappers.<TeachingClassEntity>lambdaQuery()
                        .select(TeachingClassEntity::getId)
                        .eq(TeachingClassEntity::getOfferingId, offeringId))
                .stream()
                .map(TeachingClassEntity::getId)
                .filter(classId -> isAnyClauseCovered(clauses, AuthorizationScopeType.CLASS, classId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new TeachingReadScope(offeringId, false, Set.copyOf(classIds));
    }

    @Transactional(readOnly = true)
    public void assertCanReadOffering(
            AuthenticatedUserPrincipal principal, String permissionCode, Long offeringId, String message) {
        AuthorizationResult result = permissionAuthorizationService.authorize(
                principal,
                permissionCode,
                new AuthorizationResourceRef(AuthorizationResourceType.OFFERING, offeringId),
                currentContext());
        if (!result.allowed()) {
            throw forbidden(message);
        }
    }

    @Transactional(readOnly = true)
    public void assertCanReadClass(
            AuthenticatedUserPrincipal principal, String permissionCode, Long teachingClassId, String message) {
        AuthorizationResult result = permissionAuthorizationService.authorize(
                principal,
                permissionCode,
                new AuthorizationResourceRef(AuthorizationResourceType.CLASS, teachingClassId),
                currentContext());
        if (!result.allowed()) {
            throw forbidden(message);
        }
    }

    @Transactional(readOnly = true)
    public void assertCanReadAssignment(
            AuthenticatedUserPrincipal principal, String permissionCode, AssignmentEntity assignment, String message) {
        if (!canReadAssignment(principal, permissionCode, assignment)) {
            throw forbidden(message);
        }
    }

    @Transactional(readOnly = true)
    public boolean canReadAssignment(
            AuthenticatedUserPrincipal principal, String permissionCode, AssignmentEntity assignment) {
        TeachingReadScope scope = resolveTeachingReadScope(principal, permissionCode, assignment.getOfferingId());
        if (assignment.getTeachingClassId() == null) {
            return scope.canReadSharedOfferingResource();
        }
        return scope.canReadClass(assignment.getTeachingClassId());
    }

    @Transactional(readOnly = true)
    public void assertCanReadSubmission(
            AuthenticatedUserPrincipal principal, String permissionCode, SubmissionEntity submission, String message) {
        if (!canReadSubmission(principal, permissionCode, submission)) {
            throw forbidden(message);
        }
    }

    @Transactional(readOnly = true)
    public boolean canReadSubmission(
            AuthenticatedUserPrincipal principal, String permissionCode, SubmissionEntity submission) {
        TeachingReadScope scope = resolveTeachingReadScope(principal, permissionCode, submission.getOfferingId());
        if (submission.getTeachingClassId() != null) {
            return scope.canReadClass(submission.getTeachingClassId());
        }
        return scope.offeringReadable()
                || hasActiveMemberInClasses(
                        submission.getSubmitterUserId(), submission.getOfferingId(), scope.classIds());
    }

    @Transactional(readOnly = true)
    public boolean canReadSensitiveSubmission(AuthenticatedUserPrincipal principal, SubmissionEntity submission) {
        return canReadSubmission(principal, "submission.read_source", submission);
    }

    @Transactional(readOnly = true)
    public boolean canReadHiddenJudgeFields(AuthenticatedUserPrincipal principal, AssignmentEntity assignment) {
        return permissionAuthorizationService
                .authorize(
                        principal,
                        "judge.view_hidden",
                        new AuthorizationResourceRef(AuthorizationResourceType.ASSIGNMENT, assignment.getId()),
                        currentContext())
                .allowed();
    }

    @Transactional(readOnly = true)
    public boolean canReadSensitiveJudgeConfig(AuthenticatedUserPrincipal principal, AssignmentEntity assignment) {
        return permissionAuthorizationService
                .authorize(
                        principal,
                        "judge.config",
                        new AuthorizationResourceRef(AuthorizationResourceType.ASSIGNMENT, assignment.getId()),
                        currentContext())
                .allowed();
    }

    @Transactional(readOnly = true)
    public void assertCanReadScopedOfferingData(
            AuthenticatedUserPrincipal principal,
            String permissionCode,
            Long offeringId,
            Long teachingClassId,
            String message) {
        TeachingReadScope scope = resolveTeachingReadScope(principal, permissionCode, offeringId);
        boolean allowed = teachingClassId == null ? scope.offeringReadable() : scope.canReadClass(teachingClassId);
        if (!allowed) {
            throw forbidden(message);
        }
    }

    @Transactional(readOnly = true)
    public void assertCanReadStudentInOffering(
            AuthenticatedUserPrincipal principal,
            String permissionCode,
            Long offeringId,
            Long studentUserId,
            String message) {
        TeachingReadScope scope = resolveTeachingReadScope(principal, permissionCode, offeringId);
        if (scope.offeringReadable()) {
            return;
        }
        if (!hasActiveMemberInClasses(studentUserId, offeringId, scope.classIds())) {
            throw forbidden(message);
        }
    }

    private List<AuthorizationScopeFilterClause> loadClauses(
            AuthenticatedUserPrincipal principal, String permissionCode) {
        return permissionAuthorizationService
                .buildScopeFilter(principal, permissionCode, currentContext())
                .clauses();
    }

    private List<AuthorizationScopeFilterClause> loadTeachingClauses(
            AuthenticatedUserPrincipal principal, String permissionCode) {
        // `/teacher/**` 读路径只能消费教学侧授权，不能复用 student 的 own/class grant，
        // 否则会把“学生本人可读”错误放大为“教师接口可读”，形成明显 IDOR 风险。
        return loadClauses(principal, permissionCode).stream()
                .filter(clause -> clause.roleCodes().stream().noneMatch(TEACHING_READ_EXCLUDED_ROLES::contains))
                .toList();
    }

    private AuthorizationContext currentContext() {
        return AuthorizationContext.of(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private boolean isAnyClauseCovered(
            List<AuthorizationScopeFilterClause> clauses, AuthorizationScopeType scopeType, Long scopeId) {
        ScopeRef scope = authzScopeResolutionService.resolveScope(scopeType, scopeId);
        return clauses.stream().anyMatch(clause -> isCoveredBy(scope, clause.scope()));
    }

    private boolean isCoveredBy(ScopeRef targetScope, AuthorizationScope candidateScope) {
        return targetScope.isCoveredBy(new ScopeRef(candidateScope.type(), candidateScope.id()));
    }

    private boolean hasActiveMemberInClasses(Long userId, Long offeringId, Collection<Long> classIds) {
        if (userId == null || classIds == null || classIds.isEmpty()) {
            return false;
        }
        return courseMemberMapper.selectCount(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getUserId, userId)
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .eq(CourseMemberEntity::getMemberStatus, ACTIVE)
                        .in(CourseMemberEntity::getTeachingClassId, classIds)
                        .last("LIMIT 1"))
                > 0;
    }

    private ExpandedOrgScopes expandOrgScopes(List<AuthorizationScopeFilterClause> clauses) {
        Set<Long> collegeIds = new LinkedHashSet<>();
        Set<Long> courseIds = new LinkedHashSet<>();
        List<OrgUnitEntity> orgUnits = orgUnitMapper.selectList(Wrappers.<OrgUnitEntity>lambdaQuery()
                .select(OrgUnitEntity::getId, OrgUnitEntity::getParentId, OrgUnitEntity::getType));
        Map<Long, List<OrgUnitEntity>> childrenByParent = orgUnits.stream()
                .filter(orgUnit -> orgUnit.getParentId() != null)
                .collect(Collectors.groupingBy(OrgUnitEntity::getParentId, LinkedHashMap::new, Collectors.toList()));
        for (AuthorizationScopeFilterClause clause : clauses) {
            switch (clause.scope().type()) {
                case SCHOOL -> collectDescendantOrgIds(clause.scope().id(), childrenByParent, collegeIds, courseIds);
                case COLLEGE -> {
                    collegeIds.add(clause.scope().id());
                    collectDescendantOrgIds(clause.scope().id(), childrenByParent, null, courseIds);
                }
                case COURSE -> courseIds.add(clause.scope().id());
                default -> {
                    // offering/class/platform 作用域不参与组织级开课展开。
                }
            }
        }
        return new ExpandedOrgScopes(Set.copyOf(collegeIds), Set.copyOf(courseIds));
    }

    private void collectDescendantOrgIds(
            Long rootId, Map<Long, List<OrgUnitEntity>> childrenByParent, Set<Long> collegeIds, Set<Long> courseIds) {
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long currentId = queue.removeFirst();
            for (OrgUnitEntity child : childrenByParent.getOrDefault(currentId, List.of())) {
                AuthorizationScopeType scopeType = AuthorizationScopeType.fromDatabaseValue(child.getType());
                if (scopeType == AuthorizationScopeType.COLLEGE && collegeIds != null) {
                    collegeIds.add(child.getId());
                }
                if (scopeType == AuthorizationScopeType.COURSE) {
                    courseIds.add(child.getId());
                }
                queue.addLast(child.getId());
            }
        }
    }

    private Set<Long> selectOfferingIds(
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseOfferingEntity> query) {
        return courseOfferingMapper.selectList(query).stream()
                .map(CourseOfferingEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private BusinessException forbidden(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    private record ExpandedOrgScopes(Set<Long> collegeIds, Set<Long> courseIds) {}

    public record TeachingReadScope(Long offeringId, boolean offeringReadable, Set<Long> classIds) {

        public TeachingReadScope {
            classIds = classIds == null ? Set.of() : Set.copyOf(classIds);
        }

        public static TeachingReadScope none(Long offeringId) {
            return new TeachingReadScope(offeringId, false, Set.of());
        }

        public static TeachingReadScope offeringWide(Long offeringId) {
            return new TeachingReadScope(offeringId, true, Set.of());
        }

        public boolean canReadClass(Long teachingClassId) {
            return offeringReadable || (teachingClassId != null && classIds.contains(teachingClassId));
        }

        public boolean canReadSharedOfferingResource() {
            return offeringReadable || !classIds.isEmpty();
        }

        public boolean hasAnyAccess() {
            return offeringReadable || !classIds.isEmpty();
        }
    }
}
