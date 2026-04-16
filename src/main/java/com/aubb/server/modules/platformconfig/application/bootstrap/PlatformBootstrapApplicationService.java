package com.aubb.server.modules.platformconfig.application.bootstrap;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.config.PlatformBootstrapProperties;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.aubb.server.modules.identityaccess.domain.account.PasswordPolicy;
import com.aubb.server.modules.identityaccess.domain.account.PasswordValidationResult;
import com.aubb.server.modules.identityaccess.domain.governance.GovernanceRole;
import com.aubb.server.modules.identityaccess.domain.governance.GovernanceRolePolicy;
import com.aubb.server.modules.identityaccess.domain.profile.AcademicIdentityType;
import com.aubb.server.modules.identityaccess.domain.profile.AcademicProfileStatus;
import com.aubb.server.modules.identityaccess.infrastructure.profile.AcademicProfileEntity;
import com.aubb.server.modules.identityaccess.infrastructure.profile.AcademicProfileMapper;
import com.aubb.server.modules.identityaccess.infrastructure.role.UserScopeRoleEntity;
import com.aubb.server.modules.identityaccess.infrastructure.role.UserScopeRoleMapper;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserEntity;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserMapper;
import com.aubb.server.modules.organization.domain.OrgUnitType;
import com.aubb.server.modules.organization.domain.OrganizationPolicy;
import com.aubb.server.modules.organization.domain.OrganizationValidationResult;
import com.aubb.server.modules.organization.infrastructure.OrgUnitEntity;
import com.aubb.server.modules.organization.infrastructure.OrgUnitMapper;
import com.aubb.server.modules.platformconfig.application.PlatformConfigApplicationService;
import com.aubb.server.modules.platformconfig.infrastructure.PlatformConfigEntity;
import com.aubb.server.modules.platformconfig.infrastructure.PlatformConfigMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PlatformBootstrapApplicationService {

    private final OrgUnitMapper orgUnitMapper;
    private final UserMapper userMapper;
    private final UserScopeRoleMapper userScopeRoleMapper;
    private final AcademicProfileMapper academicProfileMapper;
    private final PlatformConfigMapper platformConfigMapper;
    private final PlatformConfigApplicationService platformConfigApplicationService;
    private final AuditLogApplicationService auditLogApplicationService;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationPolicy organizationPolicy = new OrganizationPolicy();
    private final GovernanceRolePolicy governanceRolePolicy = new GovernanceRolePolicy();
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    @Transactional
    public PlatformBootstrapResult bootstrap(PlatformBootstrapProperties properties) {
        SchoolState schoolState = ensureSchoolRoot(properties);
        UserState adminState = ensureAdminUser(properties, schoolState.entity());
        boolean roleCreated = ensureSchoolAdminRole(
                adminState.entity().getId(), schoolState.entity().getId());
        boolean profileCreated = ensureAcademicProfile(adminState.entity().getId(), properties);
        boolean platformConfigCreated = ensurePlatformConfig(adminState.entity().getId(), properties);

        if (schoolState.created()) {
            auditLogApplicationService.record(
                    adminState.entity().getId(),
                    AuditAction.ORG_UNIT_CREATED,
                    "ORG_UNIT",
                    String.valueOf(schoolState.entity().getId()),
                    AuditResult.SUCCESS,
                    Map.of(
                            "source", "BOOTSTRAP",
                            "code", schoolState.entity().getCode(),
                            "name", schoolState.entity().getName(),
                            "type", schoolState.entity().getType()));
        }
        if (adminState.created()) {
            auditLogApplicationService.record(
                    adminState.entity().getId(),
                    AuditAction.USER_CREATED,
                    "USER",
                    String.valueOf(adminState.entity().getId()),
                    AuditResult.SUCCESS,
                    Map.of(
                            "source",
                            "BOOTSTRAP",
                            "username",
                            adminState.entity().getUsername(),
                            "identityCount",
                            1,
                            "membershipCount",
                            0,
                            "hasAcademicProfile",
                            true));
        }
        if (roleCreated) {
            auditLogApplicationService.record(
                    adminState.entity().getId(),
                    AuditAction.USER_IDENTITIES_CHANGED,
                    "USER",
                    String.valueOf(adminState.entity().getId()),
                    AuditResult.SUCCESS,
                    Map.of("source", "BOOTSTRAP", "identityCount", 1));
        }
        if (profileCreated) {
            auditLogApplicationService.record(
                    adminState.entity().getId(),
                    AuditAction.USER_PROFILE_UPDATED,
                    "USER",
                    String.valueOf(adminState.entity().getId()),
                    AuditResult.SUCCESS,
                    Map.of(
                            "source", "BOOTSTRAP",
                            "academicId",
                                    normalizeAcademicId(properties.getAdmin().getAcademicId()),
                            "identityType", AcademicIdentityType.ADMIN.name()));
        }

        return new PlatformBootstrapResult(
                schoolState.entity().getId(),
                adminState.entity().getId(),
                schoolState.created(),
                adminState.created(),
                roleCreated,
                profileCreated,
                platformConfigCreated);
    }

    private SchoolState ensureSchoolRoot(PlatformBootstrapProperties properties) {
        String normalizedCode = normalizeOrgCode(properties.getSchool().getCode());
        List<OrgUnitEntity> schoolRoots = orgUnitMapper.selectList(Wrappers.<OrgUnitEntity>lambdaQuery()
                .isNull(OrgUnitEntity::getParentId)
                .eq(OrgUnitEntity::getType, OrgUnitType.SCHOOL.name())
                .orderByAsc(OrgUnitEntity::getId));
        if (schoolRoots.size() > 1) {
            throw new IllegalStateException("bootstrap 失败：当前数据库存在多个 SCHOOL 根节点，无法确定首个学校");
        }

        OrgUnitEntity schoolRoot = schoolRoots.isEmpty() ? null : schoolRoots.getFirst();
        if (schoolRoot != null) {
            if (!normalizedCode.equals(schoolRoot.getCode())) {
                throw new IllegalStateException("bootstrap 失败：已存在学校根节点 code=%s，与配置 code=%s 不一致"
                        .formatted(schoolRoot.getCode(), normalizedCode));
            }
            return new SchoolState(schoolRoot, false);
        }

        OrgUnitEntity existingByCode = orgUnitMapper.selectOne(Wrappers.<OrgUnitEntity>lambdaQuery()
                .eq(OrgUnitEntity::getCode, normalizedCode)
                .last("LIMIT 1"));
        if (existingByCode != null) {
            throw new IllegalStateException("bootstrap 失败：组织编码 %s 已被非学校根节点占用".formatted(normalizedCode));
        }

        OrganizationValidationResult validationResult = organizationPolicy.validateRoot(OrgUnitType.SCHOOL);
        if (!validationResult.valid()) {
            throw new IllegalStateException("bootstrap 失败：" + validationResult.reason());
        }

        OrgUnitEntity entity = new OrgUnitEntity();
        entity.setParentId(null);
        entity.setCode(normalizedCode);
        entity.setName(properties.getSchool().getName().trim());
        entity.setType(OrgUnitType.SCHOOL.name());
        entity.setLevel(validationResult.childLevel());
        entity.setSortOrder(properties.getSchool().getSortOrder());
        entity.setStatus("ACTIVE");
        orgUnitMapper.insert(entity);
        return new SchoolState(entity, true);
    }

    private UserState ensureAdminUser(PlatformBootstrapProperties properties, OrgUnitEntity schoolRoot) {
        String normalizedUsername = normalizeUsername(properties.getAdmin().getUsername());
        UserEntity existing = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, normalizedUsername)
                .last("LIMIT 1"));
        if (existing != null) {
            return new UserState(existing, false);
        }

        String normalizedEmail = normalizeEmail(properties.getAdmin().getEmail());
        ensureEmailAvailable(normalizedEmail);
        validatePassword(properties.getAdmin().getPassword());

        UserEntity entity = new UserEntity();
        entity.setPrimaryOrgUnitId(schoolRoot.getId());
        entity.setUsername(normalizedUsername);
        entity.setDisplayName(properties.getAdmin().getDisplayName().trim());
        entity.setEmail(normalizedEmail);
        entity.setPhone(blankToNull(properties.getAdmin().getPhone()));
        entity.setPasswordHash(passwordEncoder.encode(properties.getAdmin().getPassword()));
        entity.setAccountStatus(AccountStatus.ACTIVE.name());
        entity.setFailedLoginAttempts(0);
        userMapper.insert(entity);
        return new UserState(entity, true);
    }

    private boolean ensureSchoolAdminRole(Long userId, Long schoolOrgUnitId) {
        var validationResult = governanceRolePolicy.validateScope(GovernanceRole.SCHOOL_ADMIN, OrgUnitType.SCHOOL);
        if (!validationResult.valid()) {
            throw new IllegalStateException("bootstrap 失败：" + validationResult.reason());
        }

        UserScopeRoleEntity existing = userScopeRoleMapper.selectOne(Wrappers.<UserScopeRoleEntity>lambdaQuery()
                .eq(UserScopeRoleEntity::getUserId, userId)
                .eq(UserScopeRoleEntity::getScopeOrgUnitId, schoolOrgUnitId)
                .eq(UserScopeRoleEntity::getRoleCode, GovernanceRole.SCHOOL_ADMIN.name())
                .last("LIMIT 1"));
        if (existing != null) {
            return false;
        }

        UserScopeRoleEntity entity = new UserScopeRoleEntity();
        entity.setUserId(userId);
        entity.setScopeOrgUnitId(schoolOrgUnitId);
        entity.setRoleCode(GovernanceRole.SCHOOL_ADMIN.name());
        userScopeRoleMapper.insert(entity);
        return true;
    }

    private boolean ensureAcademicProfile(Long userId, PlatformBootstrapProperties properties) {
        AcademicProfileEntity existing = academicProfileMapper.selectOne(Wrappers.<AcademicProfileEntity>lambdaQuery()
                .eq(AcademicProfileEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (existing != null) {
            return false;
        }

        String normalizedAcademicId = normalizeAcademicId(properties.getAdmin().getAcademicId());
        AcademicProfileEntity duplicateAcademicId =
                academicProfileMapper.selectOne(Wrappers.<AcademicProfileEntity>lambdaQuery()
                        .apply("lower(academic_id) = {0}", normalizedAcademicId.toLowerCase(Locale.ROOT))
                        .last("LIMIT 1"));
        if (duplicateAcademicId != null) {
            throw new IllegalStateException("bootstrap 失败：管理员学工号 %s 已被其他用户占用".formatted(normalizedAcademicId));
        }

        AcademicProfileEntity entity = new AcademicProfileEntity();
        entity.setUserId(userId);
        entity.setAcademicId(normalizedAcademicId);
        entity.setRealName(properties.resolvedAdminRealName());
        entity.setIdentityType(AcademicIdentityType.ADMIN.name());
        entity.setProfileStatus(AcademicProfileStatus.ACTIVE.name());
        entity.setPhone(blankToNull(properties.getAdmin().getPhone()));
        academicProfileMapper.insert(entity);
        return true;
    }

    private boolean ensurePlatformConfig(Long actorUserId, PlatformBootstrapProperties properties) {
        PlatformConfigEntity current = platformConfigMapper.selectOne(Wrappers.<PlatformConfigEntity>lambdaQuery()
                .orderByAsc(PlatformConfigEntity::getId)
                .last("LIMIT 1"));
        if (current != null) {
            return false;
        }

        platformConfigApplicationService.upsertCurrent(
                properties.resolvedPlatformName(),
                properties.resolvedPlatformShortName(),
                blankToNull(properties.getPlatformConfig().getLogoUrl()),
                blankToNull(properties.getPlatformConfig().getFooterText()),
                properties.resolvedDefaultHomePath(),
                properties.resolvedThemeKey(),
                blankToNull(properties.getPlatformConfig().getLoginNotice()),
                properties.getPlatformConfig().getModuleFlags() == null
                        ? Map.of()
                        : Map.copyOf(properties.getPlatformConfig().getModuleFlags()),
                actorUserId);
        return true;
    }

    private void validatePassword(String rawPassword) {
        PasswordValidationResult validationResult = passwordPolicy.validate(rawPassword);
        if (!validationResult.valid()) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "PASSWORD_POLICY_VIOLATION",
                    validationResult.reason());
        }
    }

    private void ensureEmailAvailable(String normalizedEmail) {
        UserEntity duplicateEmail = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .apply("lower(email) = {0}", normalizedEmail.toLowerCase(Locale.ROOT))
                .last("LIMIT 1"));
        if (duplicateEmail != null) {
            throw new IllegalStateException("bootstrap 失败：管理员邮箱 %s 已被其他用户占用".formatted(normalizedEmail));
        }
    }

    private String normalizeOrgCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalStateException("bootstrap 失败：school.code 不能为空");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalStateException("bootstrap 失败：admin.username 不能为空");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalStateException("bootstrap 失败：admin.email 不能为空");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAcademicId(String academicId) {
        if (!StringUtils.hasText(academicId)) {
            throw new IllegalStateException("bootstrap 失败：admin.academic-id 不能为空");
        }
        return academicId.trim();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record SchoolState(OrgUnitEntity entity, boolean created) {}

    private record UserState(UserEntity entity, boolean created) {}
}
