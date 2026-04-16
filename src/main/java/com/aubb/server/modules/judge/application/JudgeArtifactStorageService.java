package com.aubb.server.modules.judge.application;

import com.aubb.server.common.storage.ObjectStorageException;
import com.aubb.server.common.storage.ObjectStorageService;
import com.aubb.server.common.storage.StoredObject;
import com.aubb.server.modules.judge.application.sample.ProgrammingSampleRunStoredSource;
import com.aubb.server.modules.judge.infrastructure.JudgeJobEntity;
import com.aubb.server.modules.judge.infrastructure.sample.ProgrammingSampleRunEntity;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class JudgeArtifactStorageService {

    private static final String JSON_CONTENT_TYPE = MediaType.APPLICATION_JSON_VALUE;

    private final ObjectProvider<ObjectStorageService> objectStorageServiceProvider;
    private final ObjectMapper objectMapper;

    public String storeJudgeJobDetailReport(Long judgeJobId, JudgeJobStoredReport detailReport) {
        return writeJsonArtifact(judgeJobDetailReportKey(judgeJobId), detailReport, "评测详细报告");
    }

    public String storeProgrammingSampleRunDetailReport(Long sampleRunId, JudgeJobStoredReport detailReport) {
        return writeJsonArtifact(programmingSampleRunDetailReportKey(sampleRunId), detailReport, "样例试运行详细报告");
    }

    public String storeProgrammingSampleRunSourceSnapshot(
            Long sampleRunId, ProgrammingSampleRunStoredSource sourceSnapshot) {
        return writeJsonArtifact(programmingSampleRunSourceKey(sampleRunId), sourceSnapshot, "样例试运行源码快照");
    }

    public boolean hasJudgeJobDetailReport(JudgeJobEntity entity) {
        return entity != null
                && (StringUtils.hasText(entity.getDetailReportObjectKey())
                        || StringUtils.hasText(entity.getDetailReportJson()));
    }

    public JudgeJobStoredReport loadJudgeJobDetailReport(JudgeJobEntity entity) {
        if (entity == null) {
            return null;
        }
        return readJsonArtifact(
                entity.getDetailReportObjectKey(), entity.getDetailReportJson(), JudgeJobStoredReport.class, "评测详细报告");
    }

    public JudgeJobStoredReport loadProgrammingSampleRunDetailReport(ProgrammingSampleRunEntity entity) {
        if (entity == null) {
            return null;
        }
        return readJsonArtifact(
                entity.getDetailReportObjectKey(),
                entity.getDetailReportJson(),
                JudgeJobStoredReport.class,
                "样例试运行详细报告");
    }

    public ProgrammingSampleRunStoredSource loadProgrammingSampleRunSourceSnapshot(ProgrammingSampleRunEntity entity) {
        if (entity == null || !StringUtils.hasText(entity.getSourceSnapshotObjectKey())) {
            return null;
        }
        return readJsonObject(entity.getSourceSnapshotObjectKey(), ProgrammingSampleRunStoredSource.class, "样例试运行源码快照");
    }

    private String writeJsonArtifact(String objectKey, Object payload, String artifactLabel) {
        if (payload == null) {
            return null;
        }
        ObjectStorageService storageService = objectStorageServiceProvider.getIfAvailable();
        if (storageService == null) {
            return null;
        }
        try {
            byte[] content = objectMapper.writeValueAsBytes(payload);
            storageService.putObject(objectKey, content, JSON_CONTENT_TYPE);
            return objectKey;
        } catch (JacksonException exception) {
            throw new IllegalStateException(artifactLabel + "无法序列化", exception);
        } catch (ObjectStorageException exception) {
            log.warn("{}写入对象存储失败，将回退到数据库内联存储，objectKey={}, error={}", artifactLabel, objectKey, exception.getMessage());
            return null;
        }
    }

    private <T> T readJsonArtifact(String objectKey, String legacyJson, Class<T> type, String artifactLabel) {
        if (StringUtils.hasText(objectKey)) {
            try {
                return readJsonObject(objectKey, type, artifactLabel);
            } catch (RuntimeException exception) {
                if (!StringUtils.hasText(legacyJson)) {
                    throw exception;
                }
                log.warn(
                        "{}对象读取失败，回退到数据库内联字段，objectKey={}, error={}", artifactLabel, objectKey, exception.getMessage());
            }
        }
        if (!StringUtils.hasText(legacyJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(legacyJson, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException(artifactLabel + "无法读取", exception);
        }
    }

    private <T> T readJsonObject(String objectKey, Class<T> type, String artifactLabel) {
        ObjectStorageService storageService = objectStorageServiceProvider.getIfAvailable();
        if (storageService == null) {
            throw new IllegalStateException("对象存储未启用，无法读取" + artifactLabel);
        }
        try {
            StoredObject storedObject = storageService.getObject(objectKey);
            return objectMapper.readValue(new String(storedObject.content(), StandardCharsets.UTF_8), type);
        } catch (ObjectStorageException exception) {
            throw new IllegalStateException(artifactLabel + "对象无法读取", exception);
        } catch (JacksonException exception) {
            throw new IllegalStateException(artifactLabel + "对象内容无法解析", exception);
        }
    }

    private String judgeJobDetailReportKey(Long judgeJobId) {
        return "judge-jobs/%s/detail-report.json".formatted(judgeJobId);
    }

    private String programmingSampleRunDetailReportKey(Long sampleRunId) {
        return "programming-sample-runs/%s/detail-report.json".formatted(sampleRunId);
    }

    private String programmingSampleRunSourceKey(Long sampleRunId) {
        return "programming-sample-runs/%s/source-snapshot.json".formatted(sampleRunId);
    }
}
