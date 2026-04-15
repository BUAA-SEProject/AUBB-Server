package com.aubb.server.modules.identityaccess.application.user;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.iam.GovernanceAuthorizationService;
import com.aubb.server.modules.identityaccess.application.iam.IdentityAssignmentCommand;
import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityService;
import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityView;
import com.aubb.server.modules.identityaccess.domain.AccountStatus;
import com.aubb.server.modules.identityaccess.domain.GovernanceRole;
import com.aubb.server.modules.identityaccess.domain.PasswordPolicy;
import com.aubb.server.modules.identityaccess.domain.PasswordValidationResult;
import com.aubb.server.modules.identityaccess.infrastructure.UserEntity;
import com.aubb.server.modules.identityaccess.infrastructure.UserMapper;
import com.aubb.server.modules.organization.application.OrgUnitSummaryView;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private final ScopeIdentityService scopeIdentityService;
    private final com.aubb.server.modules.organization.application.OrganizationApplicationService
            organizationApplicationService;
    private final GovernanceAuthorizationService governanceAuthorizationService;
    private final AuditLogApplicationService auditLogApplicationService;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTransactionManager transactionManager;
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    @Transactional
    public UserView createUser(
            String username,
            String displayName,
            String email,
            String password,
            Long primaryOrgUnitId,
            Collection<IdentityAssignmentCommand> identityAssignments,
            AccountStatus accountStatus,
            AuthenticatedUserPrincipal principal) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedEmail = normalizeEmail(email);
        List<IdentityAssignmentCommand> normalizedAssignments = normalizeAssignments(identityAssignments);

        validatePassword(password);
        validatePrimaryOrg(primaryOrgUnitId);
        ensureUsernameAndEmailUniqueness(normalizedUsername, normalizedEmail, null);
        governanceAuthorizationService.assertCanManageUserAt(principal, primaryOrgUnitId);
        governanceAuthorizationService.assertCanGrantIdentities(principal, normalizedAssignments);

        UserEntity entity = new UserEntity();
        entity.setUsername(normalizedUsername);
        entity.setDisplayName(displayName);
        entity.setEmail(normalizedEmail);
        entity.setPasswordHash(passwordEncoder.encode(password));
        entity.setPrimaryOrgUnitId(primaryOrgUnitId);
        entity.setAccountStatus((accountStatus == null ? AccountStatus.ACTIVE : accountStatus).name());
        entity.setFailedLoginAttempts(0);
        userMapper.insert(entity);

        scopeIdentityService.replace(entity.getId(), normalizedAssignments);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.USER_CREATED,
                "USER",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of("username", normalizedUsername, "identityCount", normalizedAssignments.size()));
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
            AccountStatus accountStatus,
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

        String normalizedKeyword = normalizeKeyword(keyword);
        List<UserEntity> visibleUsers = candidates.stream()
                .filter(user -> governanceAuthorizationService.canManageUserAt(principal, user.getPrimaryOrgUnitId()))
                .filter(user -> matchesKeyword(user, normalizedKeyword))
                .toList();

        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePage - 1) * safePageSize;
        List<UserEntity> pagedUsers =
                visibleUsers.stream().skip(offset).limit(safePageSize).toList();
        // 先按数据库条件取候选集，再统一用组织树作用域规则做过滤，避免把层级授权逻辑散落在 SQL 里。
        Map<Long, List<ScopeIdentityView>> identitiesByUserId = scopeIdentityService.loadForUsers(
                pagedUsers.stream().map(UserEntity::getId).toList());
        Map<Long, OrgUnitSummaryView> primaryOrgById = organizationApplicationService.loadSummaryMap(pagedUsers.stream()
                .map(UserEntity::getPrimaryOrgUnitId)
                .filter(Objects::nonNull)
                .toList());
        List<UserView> items = pagedUsers.stream()
                .map(user -> toView(
                        user,
                        identitiesByUserId.getOrDefault(user.getId(), List.of()),
                        primaryOrgById.get(user.getPrimaryOrgUnitId())))
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
        return toView(user, scopeIdentityService.loadForUser(userId), primaryOrgUnit);
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
    public UserView updateStatus(Long userId, AccountStatus accountStatus, AuthenticatedUserPrincipal principal) {
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
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.USER_STATUS_CHANGED,
                "USER",
                String.valueOf(userId),
                AuditResult.SUCCESS,
                Map.of("accountStatus", accountStatus.name()));
        return toView(user);
    }

    private UserEntity requireUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        return user;
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
        rowTransaction.executeWithoutResult(status -> createUser(
                rowUsername,
                columns[1].trim(),
                columns[2].trim(),
                columns[3].trim(),
                orgUnitId,
                assignments,
                accountStatus,
                principal));
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

    private UserView toView(UserEntity entity) {
        OrgUnitSummaryView primaryOrgUnit = entity.getPrimaryOrgUnitId() == null
                ? null
                : organizationApplicationService
                        .loadSummaryMap(List.of(entity.getPrimaryOrgUnitId()))
                        .get(entity.getPrimaryOrgUnitId());
        return toView(entity, scopeIdentityService.loadForUser(entity.getId()), primaryOrgUnit);
    }

    private UserView toView(UserEntity entity, List<ScopeIdentityView> identities, OrgUnitSummaryView primaryOrgUnit) {
        return new UserView(
                entity.getId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getPrimaryOrgUnitId(),
                primaryOrgUnit,
                AccountStatus.valueOf(entity.getAccountStatus()),
                identities,
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

    private boolean matchesKeyword(UserEntity entity, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return containsIgnoreCase(entity.getUsername(), keyword)
                || containsIgnoreCase(entity.getDisplayName(), keyword)
                || containsIgnoreCase(entity.getEmail(), keyword);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }
}
