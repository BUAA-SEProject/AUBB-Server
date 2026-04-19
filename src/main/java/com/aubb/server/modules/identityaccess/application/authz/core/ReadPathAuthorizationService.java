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
import com.aubb.server.modules.identityaccess.infrastructure.permission.RoleBindingEntity;
import com.aubb.server.modules.identityaccess.infrastructure.permission.RoleBindingMapper;
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
    private static final Set<String> HISTORY_READABLE_STUDENT_STATUSES = Set.of("DROPPED", "TRANSFERRED", "COMPLETED");
    private static final Set<String> WRITE_PERMISSION_CODES = Set.of("ide.save", "ide.submit");
    private static final Set<String> TEACHING_READ_EXCLUDED_ROLES = Set.of("student");
    private static final String LEGACY_COURSE_MEMBER_SOURCE_TYPE = "LEGACY_COURSE_MEMBER";

    private final PermissionAuthorizationService permissionAuthorizationService;
    private final AuthzScopeResolutionService authzScopeResolutionService;
    private final CourseOfferingMapper courseOfferingMapper;
    private final CourseOfferingCollegeMapMapper courseOfferingCollegeMapMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseMemberMapper courseMemberMapper;
    private final RoleBindingMapper roleBindingMapper;
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
    public void assertCanAccessAssignmentResource(
            AuthenticatedUserPrincipal principal, String permissionCode, AssignmentEntity assignment, String message) {
        if (!canAccessAssignmentResource(principal, permissionCode, assignment)) {
            throw forbidden(message);
        }
    }

    @Transactional(readOnly = true)
    public boolean canAccessAssignmentResource(
            AuthenticatedUserPrincipal principal, String permissionCode, AssignmentEntity assignment) {
        return authorizeAssignmentResource(principal, permissionCode, assignment, currentContext())
                .allowed();
    }

    @Transactional(readOnly = true)
    public AuthorizationResult authorizeMyAssignmentCapability(
            AuthenticatedUserPrincipal principal, String permissionCode, AssignmentEntity assignment) {
        return authorizeMyAssignmentCapability(principal, permissionCode, assignment, false);
    }

    @Transactional(readOnly = true)
    public AuthorizationResult authorizeMySubmissionCapability(
            AuthenticatedUserPrincipal principal, AssignmentEntity assignment) {
        return authorizeMyAssignmentCapability(principal, "ide.submit", assignment, true);
    }

    private AuthorizationResult authorizeMyAssignmentCapability(
            AuthenticatedUserPrincipal principal,
            String permissionCode,
            AssignmentEntity assignment,
            boolean deferTimeWindowValidation) {
        AuthorizationContext context = currentContext();
        AuthorizationResult result = authorizeAssignmentResource(principal, permissionCode, assignment, context);
        if (result.allowed()) {
            return result;
        }
        if (deferTimeWindowValidation && "DENY_OUTSIDE_TIME_WINDOW".equals(result.reasonCode())) {
            return AuthorizationResult.allow(
                    "ALLOW_TIME_WINDOW_VALIDATION_DEFERRED",
                    result.matchedRoles(),
                    result.matchedScopes(),
                    result.needAudit());
        }
        if ("ide.submit".equals(permissionCode)
                && canSubmitByActiveMembership(principal, assignment, context, deferTimeWindowValidation)) {
            return AuthorizationResult.allow(
                    "ALLOW_ACTIVE_STUDENT_MEMBERSHIP_COMPAT",
                    result.matchedRoles(),
                    result.matchedScopes(),
                    result.needAudit());
        }
        if ("DENY_SCOPE_MISMATCH".equals(result.reasonCode())
                && canAccessAssignmentByActiveMembershipGrant(
                        principal, permissionCode, assignment, context, deferTimeWindowValidation)) {
            return AuthorizationResult.allow(
                    "ALLOW_ACTIVE_MEMBERSHIP_FALLBACK",
                    result.matchedRoles(),
                    result.matchedScopes(),
                    result.needAudit());
        }
        if (!"DENY_SCOPE_MISMATCH".equals(result.reasonCode())) {
            return result;
        }
        return result;
    }

    @Transactional(readOnly = true)
    public boolean canAccessMyAssignmentCapability(
            AuthenticatedUserPrincipal principal, String permissionCode, AssignmentEntity assignment) {
        return authorizeMyAssignmentCapability(principal, permissionCode, assignment)
                .allowed();
    }

    @Transactional(readOnly = true)
    public boolean canReadMyAssignmentHistory(AuthenticatedUserPrincipal principal, AssignmentEntity assignment) {
        return canAccessMyAssignmentCapability(principal, "task.read", assignment)
                || canReadMyAssignmentBySnapshotMembership(principal, assignment)
                || hasHistoricalReadableStudentMembership(
                        principal == null ? null : principal.getUserId(),
                        assignment == null ? null : assignment.getOfferingId(),
                        assignment == null ? null : assignment.getTeachingClassId());
    }

    @Transactional(readOnly = true)
    public boolean canReadMySubmissionHistory(
            AuthenticatedUserPrincipal principal, SubmissionEntity submission, AssignmentEntity assignment) {
        return canAccessSubmissionResource(principal, "submission.read", submission)
                || hasHistoricalReadableStudentMembership(
                        principal == null ? null : principal.getUserId(),
                        assignment == null ? null : assignment.getOfferingId(),
                        submission == null ? null : submission.getTeachingClassId());
    }

    @Transactional(readOnly = true)
    public boolean canReadMyAppealHistory(AuthenticatedUserPrincipal principal, Long offeringId) {
        return hasScopedAccess(principal, "appeal.read", offeringId, null)
                || hasHistoricalReadableStudentMembership(
                        principal == null ? null : principal.getUserId(), offeringId, null);
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
    public boolean canAccessSubmissionResource(
            AuthenticatedUserPrincipal principal, String permissionCode, SubmissionEntity submission) {
        return permissionAuthorizationService
                .authorize(
                        principal,
                        permissionCode,
                        new AuthorizationResourceRef(AuthorizationResourceType.SUBMISSION, submission.getId()),
                        currentContext())
                .allowed();
    }

    @Transactional(readOnly = true)
    public boolean canReadSubmission(
            AuthenticatedUserPrincipal principal, String permissionCode, SubmissionEntity submission) {
        TeachingReadScope scope = resolveTeachingReadScope(principal, permissionCode, submission.getOfferingId());
        if (submission.getTeachingClassId() != null) {
            return scope.canReadClass(submission.getTeachingClassId());
        }
        return scope.offeringReadable()
                || hadStudentMembershipInClassesAtTime(
                        submission.getSubmitterUserId(),
                        submission.getOfferingId(),
                        submission.getSubmittedAt() == null ? submission.getCreatedAt() : submission.getSubmittedAt(),
                        scope.classIds());
    }

    @Transactional(readOnly = true)
    public boolean canReadSensitiveSubmission(AuthenticatedUserPrincipal principal, SubmissionEntity submission) {
        return canAccessSubmissionResource(principal, "submission.read_source", submission);
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

    @Transactional(readOnly = true)
    public boolean hasScopedAccess(
            AuthenticatedUserPrincipal principal, String permissionCode, Long offeringId, Long teachingClassId) {
        List<AuthorizationScopeFilterClause> clauses = loadClauses(principal, permissionCode);
        if (clauses.isEmpty()) {
            return false;
        }
        if (teachingClassId != null) {
            return isAnyClauseCovered(clauses, AuthorizationScopeType.CLASS, teachingClassId);
        }
        if (offeringId == null) {
            return clauses.stream().anyMatch(clause -> clause.scope().type() == AuthorizationScopeType.PLATFORM);
        }
        ScopeRef offeringScope = authzScopeResolutionService.resolveScope(AuthorizationScopeType.OFFERING, offeringId);
        if (clauses.stream().anyMatch(clause -> isCoveredBy(offeringScope, clause.scope()))) {
            return true;
        }
        return teachingClassMapper
                .selectList(Wrappers.<TeachingClassEntity>lambdaQuery()
                        .select(TeachingClassEntity::getId)
                        .eq(TeachingClassEntity::getOfferingId, offeringId))
                .stream()
                .map(TeachingClassEntity::getId)
                .anyMatch(classId -> isAnyClauseCovered(clauses, AuthorizationScopeType.CLASS, classId));
    }

    private List<AuthorizationScopeFilterClause> loadClauses(
            AuthenticatedUserPrincipal principal, String permissionCode) {
        return loadClauses(principal, permissionCode, currentContext());
    }

    private List<AuthorizationScopeFilterClause> loadClauses(
            AuthenticatedUserPrincipal principal, String permissionCode, AuthorizationContext context) {
        return permissionAuthorizationService
                .buildScopeFilter(principal, permissionCode, context)
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

    private boolean hadStudentMembershipInClassesAtTime(
            Long userId, Long offeringId, OffsetDateTime eventTime, Collection<Long> classIds) {
        if (userId == null || eventTime == null || classIds == null || classIds.isEmpty()) {
            return false;
        }
        return courseMemberMapper.selectCount(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getUserId, userId)
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .eq(CourseMemberEntity::getMemberRole, "STUDENT")
                        .in(CourseMemberEntity::getTeachingClassId, classIds)
                        .le(CourseMemberEntity::getJoinedAt, eventTime)
                        .and(wrapper -> wrapper.isNull(CourseMemberEntity::getLeftAt)
                                .or()
                                .gt(CourseMemberEntity::getLeftAt, eventTime))
                        .last("LIMIT 1"))
                > 0;
    }

    private AuthorizationResult authorizeAssignmentResource(
            AuthenticatedUserPrincipal principal,
            String permissionCode,
            AssignmentEntity assignment,
            AuthorizationContext context) {
        return permissionAuthorizationService.authorize(
                principal,
                permissionCode,
                new AuthorizationResourceRef(AuthorizationResourceType.ASSIGNMENT, assignment.getId()),
                context);
    }

    private boolean canAccessAssignmentByActiveMembershipGrant(
            AuthenticatedUserPrincipal principal,
            String permissionCode,
            AssignmentEntity assignment,
            AuthorizationContext context,
            boolean deferTimeWindowValidation) {
        if (principal == null || assignment == null || assignment.getOfferingId() == null) {
            return false;
        }
        Set<Long> activeClassIds = loadActiveStudentClassIds(principal.getUserId(), assignment.getOfferingId());
        if (activeClassIds.isEmpty()) {
            return false;
        }
        if (assignment.getTeachingClassId() != null && !activeClassIds.contains(assignment.getTeachingClassId())) {
            return false;
        }
        CourseOfferingEntity offering = courseOfferingMapper.selectById(assignment.getOfferingId());
        if (offering == null) {
            return false;
        }
        return loadClauses(principal, permissionCode, context).stream()
                .anyMatch(clause -> clauseAllowsAssignmentByMembershipGrant(clause, assignment, activeClassIds)
                        && satisfiesAssignmentFallbackConstraints(
                                clause.constraints(),
                                permissionCode,
                                assignment,
                                offering,
                                context.requestTime(),
                                deferTimeWindowValidation));
    }

    private boolean clauseAllowsAssignmentByMembershipGrant(
            AuthorizationScopeFilterClause clause, AssignmentEntity assignment, Set<Long> activeClassIds) {
        if (assignment.getTeachingClassId() != null) {
            ScopeRef classScope = authzScopeResolutionService.resolveScope(
                    AuthorizationScopeType.CLASS, assignment.getTeachingClassId());
            if (isCoveredBy(classScope, clause.scope())) {
                return true;
            }
        }
        ScopeRef offeringScope =
                authzScopeResolutionService.resolveScope(AuthorizationScopeType.OFFERING, assignment.getOfferingId());
        if (isCoveredBy(offeringScope, clause.scope())) {
            return true;
        }
        if (assignment.getTeachingClassId() != null) {
            return false;
        }
        return activeClassIds.stream()
                .map(classId -> authzScopeResolutionService.resolveScope(AuthorizationScopeType.CLASS, classId))
                .anyMatch(classScope -> isCoveredBy(classScope, clause.scope()));
    }

    private boolean canSubmitByActiveMembership(
            AuthenticatedUserPrincipal principal,
            AssignmentEntity assignment,
            AuthorizationContext context,
            boolean deferTimeWindowValidation) {
        if (principal == null || assignment == null || assignment.getOfferingId() == null) {
            return false;
        }
        Set<Long> activeClassIds = loadActiveStudentClassIds(principal.getUserId(), assignment.getOfferingId());
        if (activeClassIds.isEmpty()) {
            return false;
        }
        if (assignment.getTeachingClassId() != null && !activeClassIds.contains(assignment.getTeachingClassId())) {
            return false;
        }
        CourseOfferingEntity offering = courseOfferingMapper.selectById(assignment.getOfferingId());
        if (offering == null || isAssignmentArchived(assignment, offering) || !isAssignmentPublished(assignment)) {
            return false;
        }
        return deferTimeWindowValidation || isWithinAssignmentWindow(assignment, context.requestTime());
    }

    private boolean satisfiesAssignmentFallbackConstraints(
            RoleBindingConstraints constraints,
            String permissionCode,
            AssignmentEntity assignment,
            CourseOfferingEntity offering,
            OffsetDateTime requestTime,
            boolean deferTimeWindowValidation) {
        if (constraints.ownerOnly()) {
            return false;
        }
        if (constraints.publishedOnly() && !isAssignmentPublished(assignment)) {
            return false;
        }
        if (constraints.archivedReadOnly()
                && isAssignmentArchived(assignment, offering)
                && WRITE_PERMISSION_CODES.contains(permissionCode)) {
            return false;
        }
        if (!deferTimeWindowValidation
                && constraints.timeWindowOnly()
                && !isWithinAssignmentWindow(assignment, requestTime)) {
            return false;
        }
        return true;
    }

    private Set<Long> loadActiveStudentClassIds(Long userId, Long offeringId) {
        if (userId == null || offeringId == null) {
            return Set.of();
        }
        return courseMemberMapper
                .selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .select(CourseMemberEntity::getTeachingClassId)
                        .eq(CourseMemberEntity::getUserId, userId)
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .eq(CourseMemberEntity::getMemberRole, "STUDENT")
                        .eq(CourseMemberEntity::getMemberStatus, ACTIVE)
                        .isNotNull(CourseMemberEntity::getTeachingClassId))
                .stream()
                .map(CourseMemberEntity::getTeachingClassId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean hasHistoricalReadableStudentMembership(Long userId, Long offeringId, Long teachingClassId) {
        if (userId == null || offeringId == null) {
            return false;
        }
        return courseMemberMapper.selectCount(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getUserId, userId)
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .eq(CourseMemberEntity::getMemberRole, "STUDENT")
                        .eq(teachingClassId != null, CourseMemberEntity::getTeachingClassId, teachingClassId)
                        .in(CourseMemberEntity::getMemberStatus, HISTORY_READABLE_STUDENT_STATUSES)
                        .last("LIMIT 1"))
                > 0;
    }

    private boolean canReadMyAssignmentBySnapshotMembership(
            AuthenticatedUserPrincipal principal, AssignmentEntity assignment) {
        if (principal == null
                || assignment == null
                || !principal.isRoleBindingSnapshot()
                || !principal.getPermissionCodes().contains("task.read")
                || assignment.getOfferingId() == null) {
            return false;
        }
        Set<Long> activeClassIds = loadActiveStudentClassIds(principal.getUserId(), assignment.getOfferingId());
        if (activeClassIds.isEmpty()) {
            return false;
        }
        if (assignment.getTeachingClassId() != null && !activeClassIds.contains(assignment.getTeachingClassId())) {
            return false;
        }
        if (!hasSyncedStudentRoleBinding(
                principal.getUserId(), assignment.getOfferingId(), assignment.getTeachingClassId())) {
            return false;
        }
        CourseOfferingEntity offering = courseOfferingMapper.selectById(assignment.getOfferingId());
        if (offering == null) {
            return false;
        }
        return satisfiesAssignmentFallbackConstraints(
                new RoleBindingConstraints(false, true, false, false, true),
                "task.read",
                assignment,
                offering,
                currentContext().requestTime(),
                false);
    }

    private boolean hasSyncedStudentRoleBinding(Long userId, Long offeringId, Long teachingClassId) {
        if (userId == null || offeringId == null) {
            return false;
        }
        List<Long> memberIds = courseMemberMapper
                .selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .select(CourseMemberEntity::getId)
                        .eq(CourseMemberEntity::getUserId, userId)
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .eq(CourseMemberEntity::getMemberRole, "STUDENT")
                        .eq(CourseMemberEntity::getMemberStatus, ACTIVE)
                        .eq(teachingClassId != null, CourseMemberEntity::getTeachingClassId, teachingClassId))
                .stream()
                .map(CourseMemberEntity::getId)
                .filter(Objects::nonNull)
                .toList();
        if (memberIds.isEmpty()) {
            return false;
        }
        return roleBindingMapper.selectCount(Wrappers.<RoleBindingEntity>lambdaQuery()
                        .eq(RoleBindingEntity::getUserId, userId)
                        .eq(RoleBindingEntity::getSourceType, LEGACY_COURSE_MEMBER_SOURCE_TYPE)
                        .eq(RoleBindingEntity::getStatus, ACTIVE)
                        .in(RoleBindingEntity::getSourceRefId, memberIds)
                        .last("LIMIT 1"))
                > 0;
    }

    private boolean isAssignmentPublished(AssignmentEntity assignment) {
        return assignment != null
                && (assignment.getPublishedAt() != null || !"DRAFT".equalsIgnoreCase(assignment.getStatus()));
    }

    private boolean isAssignmentArchived(AssignmentEntity assignment, CourseOfferingEntity offering) {
        return (assignment != null && "ARCHIVED".equalsIgnoreCase(assignment.getStatus()))
                || (offering != null && offering.getArchivedAt() != null);
    }

    private boolean isWithinAssignmentWindow(AssignmentEntity assignment, OffsetDateTime requestTime) {
        if (assignment == null || requestTime == null) {
            return false;
        }
        if (assignment.getOpenAt() != null && requestTime.isBefore(assignment.getOpenAt())) {
            return false;
        }
        return assignment.getDueAt() == null || !requestTime.isAfter(assignment.getDueAt());
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
