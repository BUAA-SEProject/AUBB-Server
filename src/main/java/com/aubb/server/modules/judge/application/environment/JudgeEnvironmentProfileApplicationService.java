package com.aubb.server.modules.judge.application.environment;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.modules.assignment.application.paper.ProgrammingExecutionEnvironmentInput;
import com.aubb.server.modules.assignment.application.paper.ProgrammingExecutionEnvironmentView;
import com.aubb.server.modules.assignment.application.paper.StructuredQuestionSupport;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.CourseAuthorizationService;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.judge.infrastructure.environment.JudgeEnvironmentProfileEntity;
import com.aubb.server.modules.judge.infrastructure.environment.JudgeEnvironmentProfileMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class JudgeEnvironmentProfileApplicationService {

    private static final String PROFILE_SCOPE = "OFFERING";

    private final JudgeEnvironmentProfileMapper judgeEnvironmentProfileMapper;
    private final CourseOfferingMapper courseOfferingMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final StructuredQuestionSupport structuredQuestionSupport;
    private final AuditLogApplicationService auditLogApplicationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public JudgeEnvironmentProfileView createProfile(
            Long offeringId,
            String profileCode,
            String profileName,
            String description,
            ProgrammingLanguage programmingLanguage,
            ProgrammingExecutionEnvironmentInput environment,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageAssignments(principal, offeringId);
        requireOffering(offeringId);
        ProgrammingLanguage safeLanguage = requireProgrammingLanguage(programmingLanguage);
        ProgrammingExecutionEnvironmentInput normalizedEnvironment = normalizeDirectEnvironment(environment);
        structuredQuestionSupport.validateProgrammingExecutionEnvironment(
                normalizedEnvironment, "JUDGE_ENVIRONMENT_PROFILE");

        JudgeEnvironmentProfileEntity entity = new JudgeEnvironmentProfileEntity();
        entity.setOfferingId(offeringId);
        entity.setProfileCode(normalizeProfileCode(profileCode));
        entity.setNormalizedCode(normalizeProfileCodeKey(profileCode));
        entity.setProfileName(normalizeProfileName(profileName));
        entity.setDescription(normalizeDescription(description));
        entity.setProgrammingLanguage(safeLanguage.name());
        entity.setLanguageVersion(blankToNull(normalizedEnvironment.languageVersion()));
        entity.setWorkingDirectory(blankToNull(normalizedEnvironment.workingDirectory()));
        entity.setInitScript(blankToNull(normalizedEnvironment.initScript()));
        entity.setCompileCommand(blankToNull(normalizedEnvironment.compileCommand()));
        entity.setRunCommand(blankToNull(normalizedEnvironment.runCommand()));
        entity.setEnvironmentVariablesJson(writeEnvironmentVariables(normalizedEnvironment.environmentVariables()));
        entity.setCpuRateLimit(normalizedEnvironment.cpuRateLimit());
        entity.setSupportFilesJson(writeSupportFiles(normalizedEnvironment.supportFiles()));
        entity.setCreatedByUserId(principal.getUserId());
        insertEntity(entity);

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.JUDGE_ENVIRONMENT_PROFILE_CREATED,
                "JUDGE_ENVIRONMENT_PROFILE",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of(
                        "offeringId", offeringId,
                        "programmingLanguage", safeLanguage.name(),
                        "profileCode", entity.getProfileCode()));
        return toView(entity);
    }

    @Transactional(readOnly = true)
    public List<JudgeEnvironmentProfileView> listProfiles(
            Long offeringId,
            ProgrammingLanguage programmingLanguage,
            boolean includeArchived,
            AuthenticatedUserPrincipal principal) {
        courseAuthorizationService.assertCanManageAssignments(principal, offeringId);
        requireOffering(offeringId);
        String languageCode = programmingLanguage == null ? null : programmingLanguage.name();
        return judgeEnvironmentProfileMapper
                .selectList(Wrappers.<JudgeEnvironmentProfileEntity>lambdaQuery()
                        .eq(JudgeEnvironmentProfileEntity::getOfferingId, offeringId)
                        .eq(languageCode != null, JudgeEnvironmentProfileEntity::getProgrammingLanguage, languageCode)
                        .isNull(!includeArchived, JudgeEnvironmentProfileEntity::getArchivedAt)
                        .orderByAsc(JudgeEnvironmentProfileEntity::getProgrammingLanguage)
                        .orderByAsc(JudgeEnvironmentProfileEntity::getProfileCode)
                        .orderByAsc(JudgeEnvironmentProfileEntity::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public JudgeEnvironmentProfileView getProfile(Long profileId, AuthenticatedUserPrincipal principal) {
        JudgeEnvironmentProfileEntity entity = requireProfile(profileId);
        courseAuthorizationService.assertCanManageAssignments(principal, entity.getOfferingId());
        return toView(entity);
    }

    @Transactional
    public JudgeEnvironmentProfileView updateProfile(
            Long profileId,
            String profileCode,
            String profileName,
            String description,
            ProgrammingExecutionEnvironmentInput environment,
            AuthenticatedUserPrincipal principal) {
        JudgeEnvironmentProfileEntity entity = requireProfile(profileId);
        courseAuthorizationService.assertCanManageAssignments(principal, entity.getOfferingId());
        assertActiveProfile(entity);
        ProgrammingExecutionEnvironmentInput normalizedEnvironment = normalizeDirectEnvironment(environment);
        structuredQuestionSupport.validateProgrammingExecutionEnvironment(
                normalizedEnvironment, "JUDGE_ENVIRONMENT_PROFILE");

        entity.setProfileCode(normalizeProfileCode(profileCode));
        entity.setNormalizedCode(normalizeProfileCodeKey(profileCode));
        entity.setProfileName(normalizeProfileName(profileName));
        entity.setDescription(normalizeDescription(description));
        entity.setLanguageVersion(blankToNull(normalizedEnvironment.languageVersion()));
        entity.setWorkingDirectory(blankToNull(normalizedEnvironment.workingDirectory()));
        entity.setInitScript(blankToNull(normalizedEnvironment.initScript()));
        entity.setCompileCommand(blankToNull(normalizedEnvironment.compileCommand()));
        entity.setRunCommand(blankToNull(normalizedEnvironment.runCommand()));
        entity.setEnvironmentVariablesJson(writeEnvironmentVariables(normalizedEnvironment.environmentVariables()));
        entity.setCpuRateLimit(normalizedEnvironment.cpuRateLimit());
        entity.setSupportFilesJson(writeSupportFiles(normalizedEnvironment.supportFiles()));
        updateEntity(entity);

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.JUDGE_ENVIRONMENT_PROFILE_UPDATED,
                "JUDGE_ENVIRONMENT_PROFILE",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of(
                        "offeringId", entity.getOfferingId(),
                        "programmingLanguage", entity.getProgrammingLanguage(),
                        "profileCode", entity.getProfileCode()));
        return toView(entity);
    }

    @Transactional
    public JudgeEnvironmentProfileView archiveProfile(Long profileId, AuthenticatedUserPrincipal principal) {
        JudgeEnvironmentProfileEntity entity = requireProfile(profileId);
        courseAuthorizationService.assertCanManageAssignments(principal, entity.getOfferingId());
        if (entity.getArchivedAt() == null) {
            entity.setArchivedAt(OffsetDateTime.now());
            entity.setArchivedByUserId(principal.getUserId());
            judgeEnvironmentProfileMapper.updateById(entity);
            auditLogApplicationService.record(
                    principal.getUserId(),
                    AuditAction.JUDGE_ENVIRONMENT_PROFILE_ARCHIVED,
                    "JUDGE_ENVIRONMENT_PROFILE",
                    String.valueOf(entity.getId()),
                    AuditResult.SUCCESS,
                    Map.of(
                            "offeringId", entity.getOfferingId(),
                            "programmingLanguage", entity.getProgrammingLanguage(),
                            "profileCode", entity.getProfileCode()));
        }
        return toView(requireProfile(profileId));
    }

    @Transactional(readOnly = true)
    public ProgrammingExecutionEnvironmentInput resolveEnvironmentReference(
            Long offeringId,
            ProgrammingLanguage programmingLanguage,
            ProgrammingExecutionEnvironmentInput environment) {
        ProgrammingExecutionEnvironmentInput normalizedEnvironment = normalizeDirectEnvironment(environment);
        if (normalizedEnvironment == null) {
            return null;
        }
        JudgeEnvironmentProfileEntity profile =
                findReferencedProfile(offeringId, programmingLanguage, normalizedEnvironment);
        if (profile == null) {
            return normalizedEnvironment;
        }
        ProgrammingExecutionEnvironmentInput profileEnvironment = fromProfile(profile);
        return mergeEnvironment(profileEnvironment, normalizedEnvironment);
    }

    private JudgeEnvironmentProfileEntity findReferencedProfile(
            Long offeringId,
            ProgrammingLanguage programmingLanguage,
            ProgrammingExecutionEnvironmentInput environment) {
        boolean hasProfileId = environment.profileId() != null;
        boolean hasProfileCode = StringUtils.hasText(environment.profileCode());
        if (!hasProfileId && !hasProfileCode) {
            return null;
        }
        JudgeEnvironmentProfileEntity profile;
        if (hasProfileId) {
            profile = requireProfile(environment.profileId());
            if (!Objects.equals(profile.getOfferingId(), offeringId)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "JUDGE_ENVIRONMENT_PROFILE_SCOPE_INVALID", "评测环境模板不属于当前开课实例");
            }
            if (hasProfileCode
                    && !Objects.equals(
                            profile.getNormalizedCode(), normalizeProfileCodeKey(environment.profileCode()))) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "JUDGE_ENVIRONMENT_PROFILE_MISMATCH", "评测环境模板标识与编码不匹配");
            }
        } else {
            profile = judgeEnvironmentProfileMapper.selectOne(Wrappers.<JudgeEnvironmentProfileEntity>lambdaQuery()
                    .eq(JudgeEnvironmentProfileEntity::getOfferingId, offeringId)
                    .eq(
                            JudgeEnvironmentProfileEntity::getNormalizedCode,
                            normalizeProfileCodeKey(environment.profileCode()))
                    .last("LIMIT 1"));
            if (profile == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "JUDGE_ENVIRONMENT_PROFILE_NOT_FOUND", "评测环境模板不存在");
            }
        }
        if (profile.getArchivedAt() != null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "JUDGE_ENVIRONMENT_PROFILE_ARCHIVED", "已归档评测环境模板不能继续引用");
        }
        if (programmingLanguage != null
                && !Objects.equals(profile.getProgrammingLanguage(), programmingLanguage.name())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "JUDGE_ENVIRONMENT_PROFILE_LANGUAGE_INVALID", "评测环境模板语言与题目语言不一致");
        }
        return profile;
    }

    private ProgrammingExecutionEnvironmentInput mergeEnvironment(
            ProgrammingExecutionEnvironmentInput profile, ProgrammingExecutionEnvironmentInput override) {
        Map<String, String> mergedVariables = new LinkedHashMap<>();
        if (profile.environmentVariables() != null) {
            mergedVariables.putAll(profile.environmentVariables());
        }
        if (override.environmentVariables() != null) {
            mergedVariables.putAll(override.environmentVariables());
        }
        LinkedHashMap<String, ProgrammingSourceFile> mergedSupportFiles = new LinkedHashMap<>();
        if (profile.supportFiles() != null) {
            for (ProgrammingSourceFile supportFile : profile.supportFiles()) {
                mergedSupportFiles.put(supportFile.path(), supportFile);
            }
        }
        if (override.supportFiles() != null) {
            for (ProgrammingSourceFile supportFile : override.supportFiles()) {
                mergedSupportFiles.put(supportFile.path(), supportFile);
            }
        }
        return new ProgrammingExecutionEnvironmentInput(
                profile.profileId(),
                profile.profileCode(),
                profile.profileName(),
                profile.profileScope(),
                firstNonBlank(override.languageVersion(), profile.languageVersion()),
                firstNonBlank(override.workingDirectory(), profile.workingDirectory()),
                firstNonBlank(override.initScript(), profile.initScript()),
                firstNonBlank(override.compileCommand(), profile.compileCommand()),
                firstNonBlank(override.runCommand(), profile.runCommand()),
                mergedVariables.isEmpty() ? null : Map.copyOf(mergedVariables),
                override.cpuRateLimit() == null ? profile.cpuRateLimit() : override.cpuRateLimit(),
                mergedSupportFiles.isEmpty() ? List.of() : new ArrayList<>(mergedSupportFiles.values()));
    }

    private ProgrammingExecutionEnvironmentInput fromProfile(JudgeEnvironmentProfileEntity entity) {
        return new ProgrammingExecutionEnvironmentInput(
                entity.getId(),
                entity.getProfileCode(),
                entity.getProfileName(),
                PROFILE_SCOPE,
                entity.getLanguageVersion(),
                entity.getWorkingDirectory(),
                entity.getInitScript(),
                entity.getCompileCommand(),
                entity.getRunCommand(),
                readEnvironmentVariables(entity.getEnvironmentVariablesJson()),
                entity.getCpuRateLimit(),
                readSupportFiles(entity.getSupportFilesJson()));
    }

    private ProgrammingExecutionEnvironmentInput normalizeDirectEnvironment(
            ProgrammingExecutionEnvironmentInput environment) {
        if (environment == null) {
            return null;
        }
        Map<String, String> environmentVariables = null;
        if (environment.environmentVariables() != null
                && !environment.environmentVariables().isEmpty()) {
            LinkedHashMap<String, String> normalizedVariables = new LinkedHashMap<>();
            environment.environmentVariables().forEach((key, value) -> normalizedVariables.put(key.trim(), value));
            environmentVariables = Map.copyOf(normalizedVariables);
        }
        List<ProgrammingSourceFile> supportFiles = environment.supportFiles() == null
                ? List.of()
                : environment.supportFiles().stream()
                        .filter(Objects::nonNull)
                        .map(file -> new ProgrammingSourceFile(file.path(), file.content()))
                        .toList();
        return new ProgrammingExecutionEnvironmentInput(
                environment.profileId(),
                blankToNull(environment.profileCode()),
                blankToNull(environment.profileName()),
                blankToNull(environment.profileScope()),
                blankToNull(environment.languageVersion()),
                blankToNull(environment.workingDirectory()),
                blankToNull(environment.initScript()),
                blankToNull(environment.compileCommand()),
                blankToNull(environment.runCommand()),
                environmentVariables,
                environment.cpuRateLimit(),
                supportFiles);
    }

    private JudgeEnvironmentProfileView toView(JudgeEnvironmentProfileEntity entity) {
        ProgrammingExecutionEnvironmentInput environment = fromProfile(entity);
        return new JudgeEnvironmentProfileView(
                entity.getId(),
                entity.getOfferingId(),
                entity.getProfileCode(),
                entity.getProfileName(),
                entity.getDescription(),
                ProgrammingLanguage.valueOf(entity.getProgrammingLanguage()),
                new ProgrammingExecutionEnvironmentView(
                        environment.profileId(),
                        environment.profileCode(),
                        environment.profileName(),
                        environment.profileScope(),
                        environment.languageVersion(),
                        environment.workingDirectory(),
                        environment.environmentVariables(),
                        environment.cpuRateLimit(),
                        environment.compileCommand(),
                        environment.runCommand(),
                        environment.initScript(),
                        environment.supportFiles()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getArchivedAt());
    }

    private void insertEntity(JudgeEnvironmentProfileEntity entity) {
        try {
            judgeEnvironmentProfileMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "JUDGE_ENVIRONMENT_PROFILE_CODE_DUPLICATED", "同一开课实例下评测环境模板编码不能重复");
        }
    }

    private void updateEntity(JudgeEnvironmentProfileEntity entity) {
        try {
            judgeEnvironmentProfileMapper.updateById(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "JUDGE_ENVIRONMENT_PROFILE_CODE_DUPLICATED", "同一开课实例下评测环境模板编码不能重复");
        }
    }

    private CourseOfferingEntity requireOffering(Long offeringId) {
        CourseOfferingEntity offering = courseOfferingMapper.selectById(offeringId);
        if (offering == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COURSE_OFFERING_NOT_FOUND", "开课实例不存在");
        }
        return offering;
    }

    private JudgeEnvironmentProfileEntity requireProfile(Long profileId) {
        JudgeEnvironmentProfileEntity entity = judgeEnvironmentProfileMapper.selectById(profileId);
        if (entity == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "JUDGE_ENVIRONMENT_PROFILE_NOT_FOUND", "评测环境模板不存在");
        }
        return entity;
    }

    private void assertActiveProfile(JudgeEnvironmentProfileEntity entity) {
        if (entity.getArchivedAt() != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "JUDGE_ENVIRONMENT_PROFILE_ARCHIVED", "已归档评测环境模板不能再编辑");
        }
    }

    private ProgrammingLanguage requireProgrammingLanguage(ProgrammingLanguage programmingLanguage) {
        if (programmingLanguage == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "JUDGE_ENVIRONMENT_PROFILE_LANGUAGE_REQUIRED", "评测环境模板必须指定语言");
        }
        return programmingLanguage;
    }

    private String normalizeProfileCode(String profileCode) {
        if (!StringUtils.hasText(profileCode)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "JUDGE_ENVIRONMENT_PROFILE_CODE_REQUIRED", "评测环境模板编码不能为空");
        }
        String normalized = profileCode.trim();
        if (normalized.length() > 64) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "JUDGE_ENVIRONMENT_PROFILE_CODE_INVALID", "评测环境模板编码长度不能超过 64");
        }
        return normalized;
    }

    private String normalizeProfileCodeKey(String profileCode) {
        return normalizeProfileCode(profileCode).toLowerCase(Locale.ROOT);
    }

    private String normalizeProfileName(String profileName) {
        if (!StringUtils.hasText(profileName)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "JUDGE_ENVIRONMENT_PROFILE_NAME_REQUIRED", "评测环境模板名称不能为空");
        }
        String normalized = profileName.trim();
        if (normalized.length() > 128) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "JUDGE_ENVIRONMENT_PROFILE_NAME_INVALID", "评测环境模板名称长度不能超过 128");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }
        String normalized = description.trim();
        if (normalized.length() > 500) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "JUDGE_ENVIRONMENT_PROFILE_DESCRIPTION_INVALID", "评测环境模板说明长度不能超过 500");
        }
        return normalized;
    }

    private String writeEnvironmentVariables(Map<String, String> environmentVariables) {
        if (environmentVariables == null || environmentVariables.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(environmentVariables);
        } catch (JacksonException exception) {
            throw new IllegalStateException("评测环境变量无法序列化", exception);
        }
    }

    private Map<String, String> readEnvironmentVariables(String environmentVariablesJson) {
        if (!StringUtils.hasText(environmentVariablesJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(
                    environmentVariablesJson, new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (JacksonException exception) {
            throw new IllegalStateException("评测环境变量无法读取", exception);
        }
    }

    private String writeSupportFiles(List<ProgrammingSourceFile> supportFiles) {
        if (supportFiles == null || supportFiles.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(supportFiles);
        } catch (JacksonException exception) {
            throw new IllegalStateException("评测环境支持文件无法序列化", exception);
        }
    }

    private List<ProgrammingSourceFile> readSupportFiles(String supportFilesJson) {
        if (!StringUtils.hasText(supportFilesJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    supportFilesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ProgrammingSourceFile.class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("评测环境支持文件无法读取", exception);
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred.trim() : blankToNull(fallback);
    }
}
