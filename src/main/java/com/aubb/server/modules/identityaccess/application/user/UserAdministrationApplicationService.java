package com.aubb.server.modules.identityaccess.application.user;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.identityaccess.application.auth.AuthSessionApplicationService;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.iam.GovernanceAuthorizationService;
import com.aubb.server.modules.identityaccess.application.iam.IdentityAssignmentCommand;
import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityService;
import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityView;
import com.aubb.server.modules.identityaccess.application.user.command.AcademicProfileCommand;
import com.aubb.server.modules.identityaccess.application.user.command.UserOrgMembershipCommand;
import com.aubb.server.modules.identityaccess.application.user.result.BulkUserImportError;
import com.aubb.server.modules.identityaccess.application.user.result.BulkUserImportResult;
import com.aubb.server.modules.identityaccess.application.user.view.AcademicProfileView;
import com.aubb.server.modules.identityaccess.application.user.view.UserOrgMembershipView;
import com.aubb.server.modules.identityaccess.application.user.view.UserView;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.aubb.server.modules.identityaccess.domain.account.PasswordPolicy;
import com.aubb.server.modules.identityaccess.domain.account.PasswordValidationResult;
import com.aubb.server.modules.identityaccess.domain.governance.GovernanceRole;
import com.aubb.server.modules.identityaccess.domain.membership.MembershipSourceType;
import com.aubb.server.modules.identityaccess.domain.membership.MembershipStatus;
import com.aubb.server.modules.identityaccess.domain.membership.MembershipType;
import com.aubb.server.modules.identityaccess.domain.profile.AcademicIdentityType;
import com.aubb.server.modules.identityaccess.domain.profile.AcademicProfileStatus;
import com.aubb.server.modules.identityaccess.infrastructure.membership.UserOrgMembershipEntity;
import com.aubb.server.modules.identityaccess.infrastructure.membership.UserOrgMembershipMapper;
import com.aubb.server.modules.identityaccess.infrastructure.profile.AcademicProfileEntity;
import com.aubb.server.modules.identityaccess.infrastructure.profile.AcademicProfileMapper;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserEntity;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserMapper;
import com.aubb.server.modules.organization.application.OrgUnitSummaryView;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserAdministrationApplicationService {

    private final UserMapper userMapper;
    private final AcademicProfileMapper academicProfileMapper;
    private final UserOrgMembershipMapper userOrgMembershipMapper;
    private final ScopeIdentityService scopeIdentityService;
    private final com.aubb.server.modules.organization.application.OrganizationApplicationService
            organizationApplicationService;
    private final GovernanceAuthorizationService governanceAuthorizationService;
    private final AuditLogApplicationService auditLogApplicationService;
    private final AuthSessionApplicationService authSessionApplicationService;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTransactionManager transactionManager;
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    @Transactional
    public UserView createUser(
            String username,
            String displayName,
            String email,
            String password,
            String phone,
            AcademicProfileCommand academicProfile,
            Collection<UserOrgMembershipCommand> memberships,
            Long primaryOrgUnitId,
            Collection<IdentityAssignmentCommand> identityAssignments,
            AccountStatus accountStatus,
            AuthenticatedUserPrincipal principal) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedEmail = normalizeEmail(email);
        List<IdentityAssignmentCommand> normalizedAssignments = normalizeAssignments(identityAssignments);
        List<UserOrgMembershipCommand> normalizedMemberships = normalizeMemberships(memberships);

        validatePassword(password);
        validatePrimaryOrg(primaryOrgUnitId);
        validateAcademicProfile(academicProfile, null);
        validateMemberships(principal, normalizedMemberships);
        ensureUsernameAndEmailUniqueness(normalizedUsername, normalizedEmail, null);
        governanceAuthorizationService.assertCanManageUserAt(principal, primaryOrgUnitId);
        governanceAuthorizationService.assertCanGrantIdentities(principal, normalizedAssignments);

        UserEntity entity = new UserEntity();
        entity.setUsername(normalizedUsername);
        entity.setDisplayName(displayName);
        entity.setEmail(normalizedEmail);
        entity.setPhone(normalizeOptionalText(phone));
        entity.setPasswordHash(passwordEncoder.encode(password));
        entity.setPrimaryOrgUnitId(primaryOrgUnitId);
        entity.setAccountStatus((accountStatus == null ? AccountStatus.ACTIVE : accountStatus).name());
        entity.setFailedLoginAttempts(0);
        userMapper.insert(entity);

        scopeIdentityService.replace(entity.getId(), normalizedAssignments);
        upsertAcademicProfileInternal(entity.getId(), academicProfile);
        replaceMembershipsInternal(entity.getId(), normalizedMemberships);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.USER_CREATED,
                "USER",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of(
                        "username",
                        normalizedUsername,
                        "identityCount",
                        normalizedAssignments.size(),
                        "membershipCount",
                        normalizedMemberships.size(),
                        "hasAcademicProfile",
                        academicProfile != null));
        return toView(entity);
    }

    public BulkUserImportResult importUsers(MultipartFile file, AuthenticatedUserPrincipal principal) {
        List<BulkUserImportError> errors = new ArrayList<>();
        int success = 0;
        int total = 0;
        TransactionTemplate rowTransaction = new TransactionTemplate(transactionManager);
        rowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

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
                if (columns.length < 7) {
                    errors.add(new BulkUserImportError(rowNumber, null, "列数不足，必须包含 7 列"));
                    continue;
                }
                String rowUsername = columns[0].trim();
                try {
                    importSingleRow(rowTransaction, columns, rowUsername, principal);
                    success++;
                } catch (BusinessException exception) {
                    errors.add(new BulkUserImportError(rowNumber, rowUsername, exception.getMessage()));
                }
            }
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IMPORT_FILE_READ_FAILED", "无法读取导入文件");
        }

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.USER_IMPORTED,
                "USER",
                null,
                errors.isEmpty() ? AuditResult.SUCCESS : AuditResult.FAILURE,
                Map.of("total", total, "success", success, "failed", errors.size()));
        return new BulkUserImportResult(total, success, errors.size(), errors);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserView> listUsers(
            AuthenticatedUserPrincipal principal,
            String keyword,
            String academicId,
            AcademicIdentityType identityType,
            AccountStatus accountStatus,
            GovernanceRole roleCode,
            Long orgUnitId,
            long page,
            long pageSize) {
        if (orgUnitId != null) {
            governanceAuthorizationService.assertCanManageUserAt(principal, orgUnitId);
        }

        LambdaQueryWrapper<UserEntity> query = Wrappers.<UserEntity>lambdaQuery()
                .orderByDesc(UserEntity::getCreatedAt)
                .orderByDesc(UserEntity::getId);
        if (accountStatus != null) {
            query.eq(UserEntity::getAccountStatus, accountStatus.name());
        }
        if (orgUnitId != null) {
            query.eq(UserEntity::getPrimaryOrgUnitId, orgUnitId);
        }
        List<UserEntity> candidates = userMapper.selectList(query);
        Map<Long, List<ScopeIdentityView>> identitiesByUserId = scopeIdentityService.loadForUsers(
                candidates.stream().map(UserEntity::getId).toList());
        Map<Long, AcademicProfileView> profilesByUserId =
                loadAcademicProfiles(candidates.stream().map(UserEntity::getId).toList());

        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedAcademicId = normalizeOptionalLowercase(academicId);
        List<UserEntity> visibleUsers = candidates.stream()
                .filter(user -> governanceAuthorizationService.canManageUserAt(principal, user.getPrimaryOrgUnitId()))
                .filter(user -> matchesKeyword(user, profilesByUserId.get(user.getId()), normalizedKeyword))
                .filter(user -> matchesAcademicId(profilesByUserId.get(user.getId()), normalizedAcademicId))
                .filter(user -> matchesIdentityType(profilesByUserId.get(user.getId()), identityType))
                .filter(user -> matchesRoleCode(identitiesByUserId.getOrDefault(user.getId(), List.of()), roleCode))
                .toList();

        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePage - 1) * safePageSize;
        List<UserEntity> pagedUsers =
                visibleUsers.stream().skip(offset).limit(safePageSize).toList();
        // 先按数据库条件取候选集，再统一用组织树作用域规则做过滤，避免把层级授权逻辑散落在 SQL 里。
        Map<Long, OrgUnitSummaryView> primaryOrgById = organizationApplicationService.loadSummaryMap(pagedUsers.stream()
                .map(UserEntity::getPrimaryOrgUnitId)
                .filter(Objects::nonNull)
                .toList());
        Map<Long, List<UserOrgMembershipView>> membershipsByUserId =
                loadMembershipViews(pagedUsers.stream().map(UserEntity::getId).toList());
        List<UserView> items = pagedUsers.stream()
                .map(user -> toView(
                        user,
                        identitiesByUserId.getOrDefault(user.getId(), List.of()),
                        profilesByUserId.get(user.getId()),
                        primaryOrgById.get(user.getPrimaryOrgUnitId()),
                        membershipsByUserId.getOrDefault(user.getId(), List.of())))
                .toList();
        return new PageResponse<>(items, visibleUsers.size(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public UserView getUser(Long userId, AuthenticatedUserPrincipal principal) {
        UserEntity user = requireUser(userId);
        governanceAuthorizationService.assertCanManageUserAt(principal, user.getPrimaryOrgUnitId());
        OrgUnitSummaryView primaryOrgUnit = user.getPrimaryOrgUnitId() == null
                ? null
                : organizationApplicationService
                        .loadSummaryMap(List.of(user.getPrimaryOrgUnitId()))
                        .get(user.getPrimaryOrgUnitId());
        return toView(
                user,
                scopeIdentityService.loadForUser(userId),
                loadAcademicProfiles(List.of(userId)).get(userId),
                primaryOrgUnit,
                loadMembershipViews(List.of(userId)).getOrDefault(userId, List.of()));
    }

    @Transactional
    public UserView updateIdentities(
            Long userId,
            Collection<IdentityAssignmentCommand> identityAssignments,
            AuthenticatedUserPrincipal principal) {
        UserEntity user = requireUser(userId);
        List<IdentityAssignmentCommand> normalizedAssignments = normalizeAssignments(identityAssignments);
        governanceAuthorizationService.assertCanManageUserAt(principal, user.getPrimaryOrgUnitId());
        governanceAuthorizationService.assertCanGrantIdentities(principal, normalizedAssignments);
        scopeIdentityService.replace(userId, normalizedAssignments);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.USER_IDENTITIES_CHANGED,
                "USER",
                String.valueOf(userId),
                AuditResult.SUCCESS,
                Map.of("identityCount", normalizedAssignments.size()));
        return toView(user);
    }

    @Transactional
    public UserView upsertAcademicProfile(
            Long userId, AcademicProfileCommand academicProfile, AuthenticatedUserPrincipal principal) {
        UserEntity user = requireUser(userId);
        governanceAuthorizationService.assertCanManageUserAt(principal, user.getPrimaryOrgUnitId());
        validateAcademicProfile(academicProfile, userId);
        upsertAcademicProfileInternal(userId, academicProfile);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.USER_PROFILE_UPDATED,
                "USER",
                String.valueOf(userId),
                AuditResult.SUCCESS,
                Map.of(
                        "academicId",
                        academicProfile.academicId(),
                        "identityType",
                        academicProfile.identityType().name()));
        return toView(user);
    }

    @Transactional
    public UserView replaceMemberships(
            Long userId, Collection<UserOrgMembershipCommand> memberships, AuthenticatedUserPrincipal principal) {
        UserEntity user = requireUser(userId);
        governanceAuthorizationService.assertCanManageUserAt(principal, user.getPrimaryOrgUnitId());
        List<UserOrgMembershipCommand> normalizedMemberships = normalizeMemberships(memberships);
        validateMemberships(principal, normalizedMemberships);
        replaceMembershipsInternal(userId, normalizedMemberships);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.USER_MEMBERSHIPS_CHANGED,
                "USER",
                String.valueOf(userId),
                AuditResult.SUCCESS,
                Map.of("membershipCount", normalizedMemberships.size()));
        return toView(user);
    }

    @Transactional
    public UserView updateStatus(
            Long userId, AccountStatus accountStatus, String reason, AuthenticatedUserPrincipal principal) {
        UserEntity user = requireUser(userId);
        governanceAuthorizationService.assertCanManageUserAt(principal, user.getPrimaryOrgUnitId());
        user.setAccountStatus(accountStatus.name());
        if (accountStatus == AccountStatus.ACTIVE) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
        if (accountStatus == AccountStatus.LOCKED) {
            user.setLockedUntil(null);
        }
        userMapper.updateById(user);
        if (accountStatus != AccountStatus.ACTIVE) {
            authSessionApplicationService.invalidateAllSessionsForUser(
                    userId, principal.getUserId(), "ACCOUNT_STATUS_" + accountStatus.name());
        }
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.USER_STATUS_CHANGED,
                "USER",
                String.valueOf(userId),
                AuditResult.SUCCESS,
                buildStatusAuditMetadata(accountStatus, reason));
        return toView(user);
    }

    @Transactional
    public void invalidateSessions(Long userId, String reason, AuthenticatedUserPrincipal principal) {
        UserEntity user = requireUser(userId);
        governanceAuthorizationService.assertCanManageUserAt(principal, user.getPrimaryOrgUnitId());
        authSessionApplicationService.invalidateAllSessionsForUser(
                userId, principal.getUserId(), normalizeInvalidationReason(reason));
    }

    private UserEntity requireUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        return user;
    }

    private String normalizeInvalidationReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "ADMIN_FORCED_INVALIDATION";
        }
        return reason.trim();
    }

    private void validatePassword(String password) {
        PasswordValidationResult validationResult = passwordPolicy.validate(password);
        if (!validationResult.valid()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY_VIOLATION", validationResult.reason());
        }
    }

    private void validatePrimaryOrg(Long primaryOrgUnitId) {
        if (primaryOrgUnitId != null && !organizationApplicationService.existsById(primaryOrgUnitId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ORG_NOT_FOUND", "指定组织不存在");
        }
    }

    private void ensureUsernameAndEmailUniqueness(String username, String email, Long existingUserId) {
        UserEntity existingByUsername = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, username)
                .ne(existingUserId != null, UserEntity::getId, existingUserId)
                .last("LIMIT 1"));
        if (existingByUsername != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "USERNAME_DUPLICATED", "用户名已存在");
        }
        UserEntity existingByEmail = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getEmail, email)
                .ne(existingUserId != null, UserEntity::getId, existingUserId)
                .last("LIMIT 1"));
        if (existingByEmail != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "EMAIL_DUPLICATED", "邮箱已存在");
        }
    }

    private void validateAcademicProfile(AcademicProfileCommand academicProfile, Long existingUserId) {
        if (academicProfile == null) {
            return;
        }
        String normalizedAcademicId = normalizeAcademicId(academicProfile.academicId());
        AcademicProfileEntity existingByAcademicId =
                academicProfileMapper.selectOne(Wrappers.<AcademicProfileEntity>lambdaQuery()
                        .apply("lower(academic_id) = {0}", normalizedAcademicId.toLowerCase(Locale.ROOT))
                        .ne(existingUserId != null, AcademicProfileEntity::getUserId, existingUserId)
                        .last("LIMIT 1"));
        if (existingByAcademicId != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "USER_DUPLICATE_ACADEMIC_ID", "学号或工号已存在");
        }
    }

    private void validateMemberships(
            AuthenticatedUserPrincipal principal, Collection<UserOrgMembershipCommand> memberships) {
        if (memberships == null || memberships.isEmpty()) {
            return;
        }
        for (UserOrgMembershipCommand membership : memberships) {
            if (!organizationApplicationService.existsById(membership.orgUnitId())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ORG_NOT_FOUND", "指定组织不存在");
            }
            governanceAuthorizationService.assertCanManageUserAt(principal, membership.orgUnitId());
            if (membership.startAt() != null
                    && membership.endAt() != null
                    && membership.endAt().isBefore(membership.startAt())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "MEMBERSHIP_TIME_RANGE_INVALID", "成员关系结束时间不能早于开始时间");
            }
        }
    }

    private List<IdentityAssignmentCommand> parseAssignments(String rawAssignments) {
        if (rawAssignments == null || rawAssignments.isBlank()) {
            return List.of();
        }
        List<IdentityAssignmentCommand> assignments = new ArrayList<>();
        for (String token : rawAssignments.split("\\|")) {
            String[] parts = token.trim().split("@", 2);
            if (parts.length != 2) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "IDENTITY_FORMAT_INVALID", "身份格式必须为 ROLE@ORG_CODE");
            }
            Long orgUnitId = organizationApplicationService.findOrgUnitIdByCode(parts[1].trim());
            if (orgUnitId == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ORG_NOT_FOUND", "组织编码不存在");
            }
            assignments.add(new IdentityAssignmentCommand(orgUnitId, parseGovernanceRole(parts[0])));
        }
        return assignments;
    }

    private void importSingleRow(
            TransactionTemplate rowTransaction,
            String[] columns,
            String rowUsername,
            AuthenticatedUserPrincipal principal) {
        Long orgUnitId = resolvePrimaryOrgUnitId(columns[4]);
        List<IdentityAssignmentCommand> assignments = parseAssignments(columns[5]);
        AccountStatus accountStatus = parseAccountStatus(columns[6]);
        String phone = columns.length > 7 ? normalizeOptionalText(columns[7]) : null;
        AcademicProfileCommand academicProfile = parseAcademicProfile(columns, phone);
        List<UserOrgMembershipCommand> memberships = parseMemberships(columns);
        rowTransaction.executeWithoutResult(status -> createUser(
                rowUsername,
                columns[1].trim(),
                columns[2].trim(),
                columns[3].trim(),
                phone,
                academicProfile,
                memberships,
                orgUnitId,
                assignments,
                accountStatus,
                principal));
    }

    private AcademicProfileCommand parseAcademicProfile(String[] columns, String fallbackPhone) {
        if (columns.length <= 10) {
            return null;
        }
        String academicId = normalizeOptionalText(columns[8]);
        String realName = normalizeOptionalText(columns[9]);
        String identityType = normalizeOptionalText(columns[10]);
        if (academicId == null && realName == null && identityType == null) {
            return null;
        }
        if (academicId == null || realName == null || identityType == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "ACADEMIC_PROFILE_COLUMNS_INCOMPLETE",
                    "导入画像列必须同时提供 academicId、realName、identityType");
        }
        return new AcademicProfileCommand(
                academicId,
                realName,
                parseAcademicIdentityType(identityType),
                AcademicProfileStatus.ACTIVE,
                fallbackPhone);
    }

    private List<UserOrgMembershipCommand> parseMemberships(String[] columns) {
        if (columns.length <= 11 || columns[11] == null || columns[11].isBlank()) {
            return List.of();
        }
        List<UserOrgMembershipCommand> memberships = new ArrayList<>();
        for (String token : columns[11].split("\\|")) {
            String[] parts = token.trim().split("@", 2);
            if (parts.length != 2) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "MEMBERSHIP_FORMAT_INVALID", "成员关系格式必须为 TYPE@ORG_CODE");
            }
            Long orgUnitId = organizationApplicationService.findOrgUnitIdByCode(parts[1].trim());
            if (orgUnitId == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ORG_NOT_FOUND", "组织编码不存在");
            }
            memberships.add(new UserOrgMembershipCommand(
                    orgUnitId,
                    parseMembershipType(parts[0]),
                    MembershipStatus.ACTIVE,
                    MembershipSourceType.IMPORT,
                    null,
                    null));
        }
        return memberships;
    }

    private Long resolvePrimaryOrgUnitId(String rawPrimaryOrgCode) {
        if (rawPrimaryOrgCode == null || rawPrimaryOrgCode.isBlank()) {
            return null;
        }
        Long orgUnitId = organizationApplicationService.findOrgUnitIdByCode(rawPrimaryOrgCode.trim());
        if (orgUnitId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ORG_NOT_FOUND", "组织编码不存在");
        }
        return orgUnitId;
    }

    private GovernanceRole parseGovernanceRole(String rawRoleCode) {
        try {
            return GovernanceRole.from(rawRoleCode.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ROLE_CODE_INVALID", "角色编码不支持");
        }
    }

    private AccountStatus parseAccountStatus(String rawAccountStatus) {
        try {
            return AccountStatus.valueOf(rawAccountStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ACCOUNT_STATUS_INVALID", "账号状态不支持");
        }
    }

    private AcademicIdentityType parseAcademicIdentityType(String rawIdentityType) {
        try {
            return AcademicIdentityType.valueOf(rawIdentityType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IDENTITY_TYPE_INVALID", "画像身份类型不支持");
        }
    }

    private MembershipType parseMembershipType(String rawMembershipType) {
        try {
            return MembershipType.valueOf(rawMembershipType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MEMBERSHIP_TYPE_INVALID", "成员关系类型不支持");
        }
    }

    private List<IdentityAssignmentCommand> normalizeAssignments(Collection<IdentityAssignmentCommand> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }
        return assignments.stream()
                .filter(Objects::nonNull)
                .map(assignment -> new IdentityAssignmentCommand(assignment.scopeOrgUnitId(), assignment.roleCode()))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private List<UserOrgMembershipCommand> normalizeMemberships(Collection<UserOrgMembershipCommand> memberships) {
        if (memberships == null || memberships.isEmpty()) {
            return List.of();
        }
        return memberships.stream()
                .filter(Objects::nonNull)
                .map(membership -> new UserOrgMembershipCommand(
                        membership.orgUnitId(),
                        membership.membershipType(),
                        membership.membershipStatus(),
                        membership.sourceType(),
                        membership.startAt(),
                        membership.endAt()))
                .collect(Collectors.toMap(
                        membership -> membership.orgUnitId() + ":"
                                + membership.membershipType().name(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }

    private UserView toView(UserEntity entity) {
        OrgUnitSummaryView primaryOrgUnit = entity.getPrimaryOrgUnitId() == null
                ? null
                : organizationApplicationService
                        .loadSummaryMap(List.of(entity.getPrimaryOrgUnitId()))
                        .get(entity.getPrimaryOrgUnitId());
        return toView(
                entity,
                scopeIdentityService.loadForUser(entity.getId()),
                loadAcademicProfiles(List.of(entity.getId())).get(entity.getId()),
                primaryOrgUnit,
                loadMembershipViews(List.of(entity.getId())).getOrDefault(entity.getId(), List.of()));
    }

    private UserView toView(
            UserEntity entity,
            List<ScopeIdentityView> identities,
            AcademicProfileView academicProfile,
            OrgUnitSummaryView primaryOrgUnit,
            List<UserOrgMembershipView> memberships) {
        return new UserView(
                entity.getId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getPhone(),
                academicProfile,
                entity.getPrimaryOrgUnitId(),
                primaryOrgUnit,
                AccountStatus.valueOf(entity.getAccountStatus()),
                identities,
                memberships,
                entity.getLastLoginAt(),
                entity.getLockedUntil(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "USERNAME_REQUIRED", "用户名不能为空");
        }
        return username.trim().toLowerCase();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EMAIL_REQUIRED", "邮箱不能为空");
        }
        return email.trim().toLowerCase();
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalLowercase(String value) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeAcademicId(String academicId) {
        if (academicId == null || academicId.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ACADEMIC_ID_REQUIRED", "学号或工号不能为空");
        }
        return academicId.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean matchesKeyword(UserEntity entity, AcademicProfileView academicProfile, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return containsIgnoreCase(entity.getUsername(), keyword)
                || containsIgnoreCase(entity.getDisplayName(), keyword)
                || containsIgnoreCase(entity.getEmail(), keyword)
                || (academicProfile != null && containsIgnoreCase(academicProfile.realName(), keyword))
                || (academicProfile != null && containsIgnoreCase(academicProfile.academicId(), keyword));
    }

    private boolean matchesAcademicId(AcademicProfileView academicProfile, String academicId) {
        if (academicId == null || academicId.isBlank()) {
            return true;
        }
        return academicProfile != null && containsIgnoreCase(academicProfile.academicId(), academicId);
    }

    private boolean matchesIdentityType(AcademicProfileView academicProfile, AcademicIdentityType identityType) {
        if (identityType == null) {
            return true;
        }
        return academicProfile != null && identityType == academicProfile.identityType();
    }

    private boolean matchesRoleCode(List<ScopeIdentityView> identities, GovernanceRole roleCode) {
        if (roleCode == null) {
            return true;
        }
        return identities.stream().anyMatch(identity -> roleCode.name().equals(identity.roleCode()));
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private void upsertAcademicProfileInternal(Long userId, AcademicProfileCommand academicProfile) {
        if (academicProfile == null) {
            return;
        }
        String normalizedAcademicId = normalizeAcademicId(academicProfile.academicId());
        AcademicProfileEntity existing = academicProfileMapper.selectOne(Wrappers.<AcademicProfileEntity>lambdaQuery()
                .eq(AcademicProfileEntity::getUserId, userId)
                .last("LIMIT 1"));
        AcademicProfileEntity entity = existing == null ? new AcademicProfileEntity() : existing;
        entity.setUserId(userId);
        entity.setAcademicId(normalizedAcademicId);
        entity.setRealName(academicProfile.realName().trim());
        entity.setIdentityType(academicProfile.identityType().name());
        entity.setProfileStatus(academicProfile.profileStatus().name());
        entity.setPhone(normalizeOptionalText(academicProfile.phone()));
        if (existing == null) {
            academicProfileMapper.insert(entity);
        } else {
            academicProfileMapper.updateById(entity);
        }
    }

    private Map<String, Object> buildStatusAuditMetadata(AccountStatus accountStatus, String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("accountStatus", accountStatus.name());
        String normalizedReason = normalizeOptionalText(reason);
        if (normalizedReason != null) {
            metadata.put("reason", normalizedReason);
        }
        return metadata;
    }

    private void replaceMembershipsInternal(Long userId, List<UserOrgMembershipCommand> memberships) {
        userOrgMembershipMapper.delete(
                Wrappers.<UserOrgMembershipEntity>lambdaQuery().eq(UserOrgMembershipEntity::getUserId, userId));
        if (memberships == null || memberships.isEmpty()) {
            return;
        }
        for (UserOrgMembershipCommand membership : memberships) {
            UserOrgMembershipEntity entity = new UserOrgMembershipEntity();
            entity.setUserId(userId);
            entity.setOrgUnitId(membership.orgUnitId());
            entity.setMembershipType(membership.membershipType().name());
            entity.setMembershipStatus(membership.membershipStatus().name());
            entity.setSourceType(membership.sourceType().name());
            entity.setStartAt(membership.startAt());
            entity.setEndAt(membership.endAt());
            userOrgMembershipMapper.insert(entity);
        }
    }

    private Map<Long, AcademicProfileView> loadAcademicProfiles(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return academicProfileMapper
                .selectList(Wrappers.<AcademicProfileEntity>lambdaQuery().in(AcademicProfileEntity::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(
                        AcademicProfileEntity::getUserId,
                        this::toAcademicProfileView,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private Map<Long, List<UserOrgMembershipView>> loadMembershipViews(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserOrgMembershipEntity> memberships =
                userOrgMembershipMapper.selectList(Wrappers.<UserOrgMembershipEntity>lambdaQuery()
                        .in(UserOrgMembershipEntity::getUserId, userIds)
                        .orderByAsc(UserOrgMembershipEntity::getId));
        Map<Long, OrgUnitSummaryView> orgUnitSummaries =
                organizationApplicationService.loadSummaryMap(memberships.stream()
                        .map(UserOrgMembershipEntity::getOrgUnitId)
                        .filter(Objects::nonNull)
                        .toList());
        return memberships.stream()
                .collect(Collectors.groupingBy(
                        UserOrgMembershipEntity::getUserId,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                membership ->
                                        toMembershipView(membership, orgUnitSummaries.get(membership.getOrgUnitId())),
                                Collectors.toList())));
    }

    private AcademicProfileView toAcademicProfileView(AcademicProfileEntity entity) {
        return new AcademicProfileView(
                entity.getId(),
                entity.getUserId(),
                entity.getAcademicId(),
                entity.getRealName(),
                AcademicIdentityType.valueOf(entity.getIdentityType()),
                AcademicProfileStatus.valueOf(entity.getProfileStatus()),
                entity.getPhone());
    }

    private UserOrgMembershipView toMembershipView(UserOrgMembershipEntity entity, OrgUnitSummaryView orgUnitSummary) {
        return new UserOrgMembershipView(
                entity.getId(),
                entity.getOrgUnitId(),
                orgUnitSummary,
                MembershipType.valueOf(entity.getMembershipType()),
                MembershipStatus.valueOf(entity.getMembershipStatus()),
                MembershipSourceType.valueOf(entity.getSourceType()),
                entity.getStartAt(),
                entity.getEndAt());
    }
}
