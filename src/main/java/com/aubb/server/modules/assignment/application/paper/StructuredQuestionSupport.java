package com.aubb.server.modules.assignment.application.paper;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.common.programming.ProgrammingSourceSnapshot;
import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import com.aubb.server.modules.assignment.domain.question.ProgrammingJudgeMode;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.assignment.infrastructure.bank.QuestionBankQuestionOptionEntity;
import com.aubb.server.modules.assignment.infrastructure.paper.AssignmentQuestionOptionEntity;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class StructuredQuestionSupport {

    private static final int MAX_CODE_TEXT_LENGTH = 50_000;
    private static final int MAX_TEMPLATE_FILE_COUNT = 20;
    private static final int MAX_TEMPLATE_DIRECTORY_COUNT = 40;
    private static final int MAX_ENVIRONMENT_SUPPORT_FILE_COUNT = 20;
    private static final int MAX_ENVIRONMENT_VARIABLE_COUNT = 20;
    private static final int MAX_ENVIRONMENT_SCRIPT_LENGTH = 4_000;
    private static final int MAX_SOURCE_FILE_PATH_LENGTH = 200;
    private static final Pattern SAFE_SOURCE_FILE_PATH = Pattern.compile("^[A-Za-z0-9._/-]+$");
    private static final Pattern SAFE_ENVIRONMENT_VARIABLE_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final ObjectMapper objectMapper;

    public String normalizeTitle(String title, String code) {
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, code, "题目标题不能为空");
        }
        String normalized = title.trim();
        if (normalized.length() > 128) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, code, "题目标题长度不能超过 128");
        }
        return normalized;
    }

    public String normalizePrompt(String prompt, String code) {
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, code, "题目内容不能为空");
        }
        return prompt.trim();
    }

    public int normalizeScore(Integer score, String code) {
        if (score == null || score <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, code, "题目分值必须大于 0");
        }
        return score;
    }

    public void validateQuestionDefinition(
            AssignmentQuestionType questionType,
            Integer score,
            List<AssignmentQuestionOptionInput> options,
            AssignmentQuestionConfigInput config,
            String validationCodePrefix) {
        if (questionType == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, validationCodePrefix + "_TYPE_REQUIRED", "题目类型不能为空");
        }
        List<AssignmentQuestionOptionInput> safeOptions = options == null ? List.of() : options;
        switch (questionType) {
            case SINGLE_CHOICE -> validateSingleChoice(safeOptions, validationCodePrefix);
            case MULTIPLE_CHOICE -> validateMultipleChoice(safeOptions, validationCodePrefix);
            case SHORT_ANSWER -> requireNoOptions(safeOptions, validationCodePrefix, "简答题不能配置选项");
            case FILE_UPLOAD -> validateFileUpload(config, safeOptions, validationCodePrefix);
            case PROGRAMMING -> validateProgramming(score, config, safeOptions, validationCodePrefix);
            default ->
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, validationCodePrefix + "_TYPE_UNSUPPORTED", "当前题型暂不支持");
        }
    }

    public List<AssignmentQuestionOptionView> toOptionViewsFromBank(
            List<QuestionBankQuestionOptionEntity> options, boolean revealCorrect) {
        return options.stream()
                .map(option -> new AssignmentQuestionOptionView(
                        option.getOptionKey(), option.getContent(), revealCorrect ? option.getIsCorrect() : null))
                .toList();
    }

    public List<AssignmentQuestionOptionView> toOptionViewsFromPaper(
            List<AssignmentQuestionOptionEntity> options, boolean revealCorrect) {
        return options.stream()
                .map(option -> new AssignmentQuestionOptionView(
                        option.getOptionKey(), option.getContent(), revealCorrect ? option.getIsCorrect() : null))
                .toList();
    }

    public Set<String> correctOptionKeysFromBank(List<QuestionBankQuestionOptionEntity> options) {
        return options.stream()
                .filter(QuestionBankQuestionOptionEntity::getIsCorrect)
                .map(QuestionBankQuestionOptionEntity::getOptionKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> correctOptionKeysFromPaper(List<AssignmentQuestionOptionEntity> options) {
        return options.stream()
                .filter(AssignmentQuestionOptionEntity::getIsCorrect)
                .map(AssignmentQuestionOptionEntity::getOptionKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public String writeConfigJson(AssignmentQuestionConfigInput config) {
        AssignmentQuestionConfigInput safeConfig = config == null
                ? new AssignmentQuestionConfigInput(
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null)
                : config;
        try {
            return objectMapper.writeValueAsString(safeConfig);
        } catch (JacksonException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "QUESTION_CONFIG_INVALID", "题目配置无法序列化");
        }
    }

    public AssignmentQuestionConfigInput readConfigInput(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return new AssignmentQuestionConfigInput(
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null);
        }
        try {
            return objectMapper.readValue(configJson, AssignmentQuestionConfigInput.class);
        } catch (JacksonException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "QUESTION_CONFIG_BROKEN", "题目配置无法读取");
        }
    }

    public AssignmentQuestionConfigView toConfigView(
            AssignmentQuestionConfigInput config, boolean revealSensitiveFields) {
        if (config == null) {
            return new AssignmentQuestionConfigView(
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null);
        }
        return new AssignmentQuestionConfigView(
                config.supportedLanguages(),
                config.maxFileCount(),
                config.maxFileSizeMb(),
                config.acceptedExtensions(),
                config.allowMultipleFiles(),
                config.allowSampleRun(),
                config.sampleStdinText(),
                config.sampleExpectedStdout(),
                config.templateEntryFilePath(),
                config.templateDirectories(),
                config.templateFiles(),
                config.timeLimitMs(),
                config.memoryLimitMb(),
                config.outputLimitKb(),
                config.compileArgs(),
                config.runArgs(),
                config.judgeCases() == null ? 0 : config.judgeCases().size(),
                config.judgeMode(),
                revealSensitiveFields ? blankToNull(config.customJudgeScript()) : null,
                revealSensitiveFields ? blankToNull(config.referenceAnswer()) : null,
                revealSensitiveFields && config.judgeCases() != null
                        ? config.judgeCases().stream()
                                .map(caseInput -> new ProgrammingJudgeCaseView(
                                        caseInput.stdinText(), caseInput.expectedStdout(), caseInput.score()))
                                .toList()
                        : null,
                config.languageExecutionEnvironments() == null
                        ? null
                        : config.languageExecutionEnvironments().stream()
                                .map(environment -> new ProgrammingLanguageExecutionEnvironmentView(
                                        environment.programmingLanguage(),
                                        toExecutionEnvironmentView(
                                                environment.executionEnvironment(), revealSensitiveFields)))
                                .toList(),
                toExecutionEnvironmentView(config.executionEnvironment(), revealSensitiveFields));
    }

    public void validateProgrammingExecutionEnvironment(
            ProgrammingExecutionEnvironmentInput environment, String prefix) {
        validateExecutionEnvironment(environment, prefix);
    }

    private void validateSingleChoice(List<AssignmentQuestionOptionInput> options, String prefix) {
        requireChoiceOptions(options, prefix);
        long correctCount = options.stream()
                .filter(option -> Boolean.TRUE.equals(option.correct()))
                .count();
        if (correctCount != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_CORRECT_OPTION_INVALID", "单选题必须且只能有一个正确选项");
        }
    }

    private void validateMultipleChoice(List<AssignmentQuestionOptionInput> options, String prefix) {
        requireChoiceOptions(options, prefix);
        long correctCount = options.stream()
                .filter(option -> Boolean.TRUE.equals(option.correct()))
                .count();
        if (correctCount < 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_CORRECT_OPTION_INVALID", "多选题至少需要一个正确选项");
        }
    }

    private void requireChoiceOptions(List<AssignmentQuestionOptionInput> options, String prefix) {
        if (options.size() < 2) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_OPTIONS_INVALID", "客观题至少需要两个选项");
        }
        Set<String> optionKeys = new LinkedHashSet<>();
        int index = 1;
        for (AssignmentQuestionOptionInput option : options) {
            if (option == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_OPTIONS_INVALID", "选项不能为空");
            }
            if (!StringUtils.hasText(option.optionKey())
                    || option.optionKey().trim().length() > 16) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_OPTIONS_INVALID", "选项键不能为空且长度不能超过 16");
            }
            if (!StringUtils.hasText(option.content())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_OPTIONS_INVALID", "选项内容不能为空");
            }
            String normalizedKey = option.optionKey().trim();
            if (!optionKeys.add(normalizedKey)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_OPTIONS_DUPLICATED", "同一题目的选项键不能重复");
            }
            index++;
        }
    }

    private void requireNoOptions(List<AssignmentQuestionOptionInput> options, String prefix, String message) {
        if (!options.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_OPTIONS_UNEXPECTED", message);
        }
    }

    private void validateFileUpload(
            AssignmentQuestionConfigInput config, List<AssignmentQuestionOptionInput> options, String prefix) {
        requireNoOptions(options, prefix, "文件题不能配置选项");
        if (config == null || config.maxFileCount() == null || config.maxFileCount() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_FILE_COUNT_INVALID", "文件题必须配置正整数文件数量上限");
        }
        if (config.maxFileSizeMb() == null || config.maxFileSizeMb() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_FILE_SIZE_INVALID", "文件题必须配置正整数文件大小限制");
        }
    }

    private void validateProgramming(
            Integer score,
            AssignmentQuestionConfigInput config,
            List<AssignmentQuestionOptionInput> options,
            String prefix) {
        requireNoOptions(options, prefix, "编程题不能配置选项");
        if (config == null
                || config.supportedLanguages() == null
                || config.supportedLanguages().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_LANGUAGES_REQUIRED", "编程题必须至少配置一种支持语言");
        }
        if (config.timeLimitMs() != null && config.timeLimitMs() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_TIME_LIMIT_INVALID", "编程题时间限制必须大于 0");
        }
        if (config.memoryLimitMb() != null && config.memoryLimitMb() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_MEMORY_LIMIT_INVALID", "编程题内存限制必须大于 0");
        }
        if (config.outputLimitKb() != null && config.outputLimitKb() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_OUTPUT_LIMIT_INVALID", "编程题输出限制必须大于 0");
        }
        validateCommandArgs(config.compileArgs(), prefix + "_COMPILE_ARGS_INVALID", "编译参数不能为空白字符串");
        validateCommandArgs(config.runArgs(), prefix + "_RUN_ARGS_INVALID", "运行参数不能为空白字符串");
        validateProgrammingTemplate(config, prefix);
        validateLanguageExecutionEnvironments(
                config.supportedLanguages(), config.languageExecutionEnvironments(), prefix);
        validateExecutionEnvironment(config.executionEnvironment(), prefix);
        if (ProgrammingJudgeMode.CUSTOM_SCRIPT.equals(config.judgeMode())
                && !StringUtils.hasText(config.customJudgeScript())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_CUSTOM_SCRIPT_REQUIRED", "自定义脚本模式必须提供评测脚本");
        }
        List<ProgrammingJudgeCaseInput> judgeCases = config.judgeCases() == null ? List.of() : config.judgeCases();
        if (judgeCases.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_JUDGE_CASES_REQUIRED", "编程题必须至少配置一个隐藏测试用例");
        }
        int totalCaseScore = 0;
        int index = 1;
        for (ProgrammingJudgeCaseInput judgeCase : judgeCases) {
            if (judgeCase == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_JUDGE_CASE_INVALID", "测试用例不能为空");
            }
            if (!StringUtils.hasText(judgeCase.stdinText())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, prefix + "_JUDGE_CASE_STDIN_REQUIRED", "测试用例输入不能为空");
            }
            if (!StringUtils.hasText(judgeCase.expectedStdout())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, prefix + "_JUDGE_CASE_STDOUT_REQUIRED", "测试用例期望输出不能为空");
            }
            if (judgeCase.score() == null || judgeCase.score() < 0) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, prefix + "_JUDGE_CASE_SCORE_INVALID", "测试用例分值必须是非负整数");
            }
            totalCaseScore += judgeCase.score();
            index++;
        }
        if (score != null && totalCaseScore != score) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, prefix + "_JUDGE_CASE_SCORE_MISMATCH", "编程题测试点分值之和必须等于题目分值");
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void validateCommandArgs(List<String> args, String errorCode, String message) {
        if (args == null) {
            return;
        }
        for (String arg : args) {
            if (!StringUtils.hasText(arg)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, errorCode, message);
            }
        }
    }

    private void validateLanguageExecutionEnvironments(
            List<ProgrammingLanguage> supportedLanguages,
            List<ProgrammingLanguageExecutionEnvironmentInput> languageEnvironments,
            String prefix) {
        if (languageEnvironments == null || languageEnvironments.isEmpty()) {
            return;
        }
        LinkedHashSet<ProgrammingLanguage> seenLanguages = new LinkedHashSet<>();
        for (ProgrammingLanguageExecutionEnvironmentInput languageEnvironment : languageEnvironments) {
            if (languageEnvironment == null || languageEnvironment.programmingLanguage() == null) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, prefix + "_LANGUAGE_ENVIRONMENT_LANGUAGE_REQUIRED", "按语言评测环境必须指定语言");
            }
            if (languageEnvironment.executionEnvironment() == null) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, prefix + "_LANGUAGE_ENVIRONMENT_REQUIRED", "按语言评测环境不能为空");
            }
            if (supportedLanguages != null && !supportedLanguages.contains(languageEnvironment.programmingLanguage())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, prefix + "_LANGUAGE_ENVIRONMENT_SCOPE_INVALID", "按语言评测环境必须属于题目支持语言");
            }
            if (!seenLanguages.add(languageEnvironment.programmingLanguage())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, prefix + "_LANGUAGE_ENVIRONMENT_DUPLICATED", "同一语言只能配置一个评测环境");
            }
            validateExecutionEnvironment(
                    languageEnvironment.executionEnvironment(),
                    prefix + "_LANGUAGE_"
                            + languageEnvironment.programmingLanguage().name());
        }
    }

    private void validateExecutionEnvironment(ProgrammingExecutionEnvironmentInput environment, String prefix) {
        if (environment == null) {
            return;
        }
        if (environment.profileId() != null && environment.profileId() <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, prefix + "_ENVIRONMENT_PROFILE_INVALID", "评测环境模板标识必须大于 0");
        }
        if (StringUtils.hasText(environment.profileCode())
                && environment.profileCode().trim().length() > 64) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, prefix + "_ENVIRONMENT_PROFILE_CODE_INVALID", "评测环境模板编码长度不能超过 64");
        }
        if (StringUtils.hasText(environment.profileName())
                && environment.profileName().trim().length() > 128) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, prefix + "_ENVIRONMENT_PROFILE_NAME_INVALID", "评测环境模板名称长度不能超过 128");
        }
        if (StringUtils.hasText(environment.workingDirectory()) && !isSafePath(environment.workingDirectory())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, prefix + "_ENVIRONMENT_WORKING_DIRECTORY_INVALID", "评测环境工作目录不合法");
        }
        if (StringUtils.hasText(environment.compileCommand())
                && !StringUtils.hasText(environment.compileCommand().trim())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, prefix + "_ENVIRONMENT_COMPILE_COMMAND_INVALID", "评测环境编译命令不能为空白字符串");
        }
        if (StringUtils.hasText(environment.runCommand())
                && !StringUtils.hasText(environment.runCommand().trim())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, prefix + "_ENVIRONMENT_RUN_COMMAND_INVALID", "评测环境运行命令不能为空白字符串");
        }
        if (StringUtils.hasText(environment.initScript())
                && environment.initScript().trim().length() > MAX_ENVIRONMENT_SCRIPT_LENGTH) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, prefix + "_ENVIRONMENT_INIT_SCRIPT_TOO_LONG", "评测环境初始化脚本长度超过限制");
        }
        if (environment.cpuRateLimit() != null && environment.cpuRateLimit() <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, prefix + "_ENVIRONMENT_CPU_RATE_INVALID", "评测环境 CPU 限制必须大于 0");
        }
        Map<String, String> environmentVariables = environment.environmentVariables();
        if (environmentVariables != null) {
            if (environmentVariables.size() > MAX_ENVIRONMENT_VARIABLE_COUNT) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, prefix + "_ENVIRONMENT_VARIABLE_LIMIT_EXCEEDED", "评测环境变量数量超过限制");
            }
            for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
                if (!StringUtils.hasText(entry.getKey())
                        || !SAFE_ENVIRONMENT_VARIABLE_NAME
                                .matcher(entry.getKey().trim())
                                .matches()) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST, prefix + "_ENVIRONMENT_VARIABLE_NAME_INVALID", "评测环境变量名不合法");
                }
                if (entry.getValue() == null) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST, prefix + "_ENVIRONMENT_VARIABLE_VALUE_INVALID", "评测环境变量值不能为空");
                }
            }
        }
        validateEnvironmentSupportFiles(environment.supportFiles(), prefix);
    }

    private void validateProgrammingTemplate(AssignmentQuestionConfigInput config, String prefix) {
        List<ProgrammingSourceFile> templateFiles = config.templateFiles() == null ? List.of() : config.templateFiles();
        List<String> templateDirectories =
                config.templateDirectories() == null ? List.of() : normalizeDirectories(config.templateDirectories());
        boolean hasTemplate = StringUtils.hasText(config.templateEntryFilePath())
                || !templateFiles.isEmpty()
                || !templateDirectories.isEmpty();
        if (!hasTemplate) {
            return;
        }
        ProgrammingSourceSnapshot templateSnapshot = ProgrammingSourceSnapshot.fromInput(
                config.supportedLanguages().getFirst(), null, config.templateEntryFilePath(), templateFiles);
        validateTemplateFiles(templateSnapshot, prefix);
        validateTemplateDirectories(templateDirectories, prefix);
        int totalFileCount = templateFiles.size();
        if (config.maxFileCount() != null && totalFileCount > config.maxFileCount()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, prefix + "_TEMPLATE_FILE_LIMIT_EXCEEDED", "模板源码文件数量超过题目限制");
        }
        if (!Boolean.TRUE.equals(config.allowMultipleFiles()) && totalFileCount > 1) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, prefix + "_TEMPLATE_MULTIPLE_FILES_DISABLED", "当前题目不允许模板包含多个源码文件");
        }
        LinkedHashSet<String> acceptedExtensions = normalizeExtensions(config.acceptedExtensions());
        if (!acceptedExtensions.isEmpty()) {
            for (ProgrammingSourceFile file : templateFiles) {
                if (!acceptedExtensions.contains(extensionOf(file.path()))) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST, prefix + "_TEMPLATE_FILE_EXTENSION_INVALID", "模板源码文件扩展名不符合题目限制");
                }
            }
        }
        if (config.maxFileSizeMb() != null) {
            long maxFileSizeBytes = config.maxFileSizeMb() * 1024L * 1024L;
            for (ProgrammingSourceFile file : templateFiles) {
                if (safeContent(file.content()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        > maxFileSizeBytes) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST, prefix + "_TEMPLATE_FILE_SIZE_EXCEEDED", "模板源码文件大小超过题目限制");
                }
            }
        }
    }

    private void validateEnvironmentSupportFiles(List<ProgrammingSourceFile> supportFiles, String prefix) {
        if (supportFiles == null || supportFiles.isEmpty()) {
            return;
        }
        if (supportFiles.size() > MAX_ENVIRONMENT_SUPPORT_FILE_COUNT) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, prefix + "_ENVIRONMENT_SUPPORT_FILE_LIMIT_EXCEEDED", "评测环境支持文件数量超过限制");
        }
        LinkedHashSet<String> normalizedPaths = new LinkedHashSet<>();
        for (ProgrammingSourceFile file : supportFiles) {
            if (file == null || !isSafePath(file.path())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, prefix + "_ENVIRONMENT_SUPPORT_FILE_PATH_INVALID", "评测环境支持文件路径不合法");
            }
            if (!normalizedPaths.add(file.path())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, prefix + "_ENVIRONMENT_SUPPORT_FILE_PATH_DUPLICATED", "评测环境支持文件路径不能重复");
            }
            if (safeContent(file.content()).length() > MAX_CODE_TEXT_LENGTH) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, prefix + "_ENVIRONMENT_SUPPORT_FILE_TOO_LONG", "评测环境支持文件内容超过限制");
            }
        }
    }

    private void validateTemplateFiles(ProgrammingSourceSnapshot sourceSnapshot, String prefix) {
        if (sourceSnapshot.files().size() > MAX_TEMPLATE_FILE_COUNT) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, prefix + "_TEMPLATE_FILE_LIMIT_EXCEEDED", "模板源码文件数量超过限制");
        }
        LinkedHashSet<String> normalizedPaths = new LinkedHashSet<>();
        for (ProgrammingSourceFile file : sourceSnapshot.files()) {
            if (!isSafePath(file.path())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, prefix + "_TEMPLATE_SOURCE_PATH_INVALID", "模板源码文件路径不合法");
            }
            if (!normalizedPaths.add(file.path())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, prefix + "_TEMPLATE_SOURCE_PATH_DUPLICATED", "模板源码文件路径不能重复");
            }
            if (safeContent(file.content()).length() > MAX_CODE_TEXT_LENGTH) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, prefix + "_TEMPLATE_CODE_TOO_LONG", "模板源码正文长度超过限制");
            }
        }
        if (!sourceSnapshot.files().isEmpty() && !normalizedPaths.contains(sourceSnapshot.entryFilePath())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, prefix + "_TEMPLATE_ENTRY_FILE_INVALID", "模板入口文件必须出现在模板源码列表中");
        }
    }

    private void validateTemplateDirectories(List<String> directories, String prefix) {
        if (directories.size() > MAX_TEMPLATE_DIRECTORY_COUNT) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, prefix + "_TEMPLATE_DIRECTORY_LIMIT_EXCEEDED", "模板目录数量超过限制");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String directory : directories) {
            if (!isSafePath(directory)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, prefix + "_TEMPLATE_DIRECTORY_INVALID", "模板目录路径不合法");
            }
            if (!normalized.add(directory)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, prefix + "_TEMPLATE_DIRECTORY_DUPLICATED", "模板目录路径不能重复");
            }
        }
    }

    private List<String> normalizeDirectories(List<String> directories) {
        if (directories == null || directories.isEmpty()) {
            return List.of();
        }
        return directories.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private LinkedHashSet<String> normalizeExtensions(List<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return extensions.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(extension -> extension.startsWith(".") ? extension.substring(1) : extension)
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private ProgrammingExecutionEnvironmentView toExecutionEnvironmentView(
            ProgrammingExecutionEnvironmentInput environment, boolean revealSensitiveFields) {
        if (environment == null) {
            return null;
        }
        return new ProgrammingExecutionEnvironmentView(
                environment.profileId(),
                blankToNull(environment.profileCode()),
                blankToNull(environment.profileName()),
                blankToNull(environment.profileScope()),
                blankToNull(environment.languageVersion()),
                blankToNull(environment.workingDirectory()),
                environment.environmentVariables() == null ? null : Map.copyOf(environment.environmentVariables()),
                environment.cpuRateLimit(),
                revealSensitiveFields ? blankToNull(environment.compileCommand()) : null,
                revealSensitiveFields ? blankToNull(environment.runCommand()) : null,
                revealSensitiveFields ? blankToNull(environment.initScript()) : null,
                revealSensitiveFields && environment.supportFiles() != null
                        ? List.copyOf(environment.supportFiles())
                        : null);
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private boolean isSafePath(String path) {
        return StringUtils.hasText(path)
                && path.length() <= MAX_SOURCE_FILE_PATH_LENGTH
                && !path.startsWith("/")
                && !path.endsWith("/")
                && !path.contains("\\")
                && !path.contains("//")
                && !path.contains("/./")
                && !path.startsWith("./")
                && !path.contains("../")
                && SAFE_SOURCE_FILE_PATH.matcher(path).matches();
    }

    private String safeContent(String content) {
        return content == null ? "" : content;
    }
}
