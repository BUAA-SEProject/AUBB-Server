package com.aubb.server.modules.assignment.application.paper;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import com.aubb.server.modules.assignment.domain.question.ProgrammingJudgeMode;
import com.aubb.server.modules.assignment.infrastructure.bank.QuestionBankQuestionOptionEntity;
import com.aubb.server.modules.assignment.infrastructure.paper.AssignmentQuestionOptionEntity;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
                        null)
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
                    null);
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
                    null, null);
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
                        : null);
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
}
