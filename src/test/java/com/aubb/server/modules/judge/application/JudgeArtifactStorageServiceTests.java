package com.aubb.server.modules.judge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.common.storage.ObjectStorageException;
import com.aubb.server.common.storage.ObjectStorageService;
import com.aubb.server.common.storage.StoredObject;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.judge.application.sample.ProgrammingSampleRunStoredSource;
import com.aubb.server.modules.judge.domain.JudgeVerdict;
import com.aubb.server.modules.judge.infrastructure.JudgeJobEntity;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class JudgeArtifactStorageServiceTests {

    @Mock
    private ObjectStorageService objectStorageService;

    @Test
    void storesAndLoadsJudgeJobDetailReportFromObjectStorage() {
        JudgeArtifactStorageService service = new JudgeArtifactStorageService(
                storageProvider(), JsonMapper.builder().build());
        JudgeJobStoredReport report = sampleReport();
        String objectKey = "judge-jobs/42/detail-report.json";
        byte[] content = JsonMapper.builder().build().writeValueAsBytes(report);

        when(objectStorageService.getObject(objectKey))
                .thenReturn(new StoredObject(objectKey, content, "application/json", content.length));

        String storedKey = service.storeJudgeJobDetailReport(42L, report);

        assertThat(storedKey).isEqualTo(objectKey);
        verify(objectStorageService).putObject(eq(objectKey), any(byte[].class), eq("application/json"));

        JudgeJobEntity entity = new JudgeJobEntity();
        entity.setDetailReportObjectKey(objectKey);
        JudgeJobStoredReport restored = service.loadJudgeJobDetailReport(entity);

        assertThat(restored.executionMetadata()).containsEntry("mode", "STRUCTURED_PROGRAMMING");
        assertThat(restored.caseReports()).hasSize(1);
        assertThat(restored.stdoutText()).isEqualTo("42\n");
    }

    @Test
    void fallsBackToLegacyJsonWhenObjectStorageReadFails() {
        JudgeArtifactStorageService service = new JudgeArtifactStorageService(
                storageProvider(), JsonMapper.builder().build());
        JudgeJobStoredReport report = sampleReport();
        String legacyJson = JsonMapper.builder().build().writeValueAsString(report);

        doThrow(new ObjectStorageException("boom"))
                .when(objectStorageService)
                .getObject("judge-jobs/42/detail-report.json");

        JudgeJobEntity entity = new JudgeJobEntity();
        entity.setDetailReportObjectKey("judge-jobs/42/detail-report.json");
        entity.setDetailReportJson(legacyJson);

        JudgeJobStoredReport restored = service.loadJudgeJobDetailReport(entity);

        assertThat(restored.caseReports().getFirst().verdict()).isEqualTo(JudgeVerdict.ACCEPTED);
        assertThat(restored.stderrText()).isEmpty();
    }

    @Test
    void storesJudgeJobSourceSnapshotAndArtifactManifestWithDigestSummary() {
        JudgeArtifactStorageService service = new JudgeArtifactStorageService(
                storageProvider(), JsonMapper.builder().build());
        JudgeJobStoredSource sourceSnapshot = new JudgeJobStoredSource(
                ProgrammingLanguage.CPP17,
                "src/main.cpp",
                List.of(
                        new ProgrammingSourceFile("src/main.cpp", "#include <iostream>\nint main(){std::cout<<42;}"),
                        new ProgrammingSourceFile("src/math_utils.cpp", "int add(int a, int b){ return a + b; }")),
                List.of(31L, 32L),
                11L,
                13L,
                17L);

        StoredJudgeArtifact sourceArtifact = service.storeJudgeJobSourceSnapshotArtifact(9L, sourceSnapshot);
        StoredJudgeArtifact manifestArtifact = service.storeJudgeJobArtifactManifestArtifact(
                9L,
                new JudgeJobArtifactTraceView(
                        "OBJECT_STORAGE",
                        java.time.OffsetDateTime.parse("2026-04-17T10:00:00Z"),
                        9L,
                        11L,
                        13L,
                        15L,
                        17L,
                        List.of("DETAIL_REPORT", "SOURCE_SNAPSHOT_OR_REF"),
                        new JudgeArtifactTraceItemView(true, "application/json", 256L, "abcd"),
                        new JudgeArtifactTraceItemView(true, "application/json", 128L, "efgh"),
                        null));

        assertThat(sourceArtifact.objectKey()).isEqualTo("judge-jobs/9/source-snapshot.json");
        assertThat(sourceArtifact.storedInObjectStorage()).isTrue();
        assertThat(sourceArtifact.contentType()).isEqualTo("application/json");
        assertThat(sourceArtifact.sizeBytes()).isPositive();
        assertThat(sourceArtifact.sha256Hex()).hasSize(64);
        assertThat(manifestArtifact.objectKey()).isEqualTo("judge-jobs/9/artifact-manifest.json");
        assertThat(manifestArtifact.sha256Hex()).hasSize(64);

        verify(objectStorageService)
                .putObject(eq("judge-jobs/9/source-snapshot.json"), any(byte[].class), eq("application/json"));
        verify(objectStorageService)
                .putObject(eq("judge-jobs/9/artifact-manifest.json"), any(byte[].class), eq("application/json"));
    }

    @Test
    void storesAndLoadsProgrammingSampleRunSourceSnapshotFromObjectStorage() {
        JudgeArtifactStorageService service = new JudgeArtifactStorageService(
                storageProvider(), JsonMapper.builder().build());
        ProgrammingSampleRunStoredSource sourceSnapshot = new ProgrammingSampleRunStoredSource(
                ProgrammingLanguage.PYTHON3,
                "src/main.py",
                List.of(
                        new ProgrammingSourceFile("src/main.py", "print(42)"),
                        new ProgrammingSourceFile("src/helper.py", "def add(a, b):\n    return a + b")),
                List.of("src"),
                List.of(11L, 12L),
                3L);
        String objectKey = "programming-sample-runs/7/source-snapshot.json";
        byte[] content = JsonMapper.builder().build().writeValueAsBytes(sourceSnapshot);

        when(objectStorageService.getObject(objectKey))
                .thenReturn(new StoredObject(objectKey, content, "application/json", content.length));

        String storedKey = service.storeProgrammingSampleRunSourceSnapshot(7L, sourceSnapshot);

        assertThat(storedKey).isEqualTo(objectKey);
        verify(objectStorageService).putObject(eq(objectKey), any(byte[].class), eq("application/json"));

        var entity = new com.aubb.server.modules.judge.infrastructure.sample.ProgrammingSampleRunEntity();
        entity.setSourceSnapshotObjectKey(objectKey);
        ProgrammingSampleRunStoredSource restored = service.loadProgrammingSampleRunSourceSnapshot(entity);

        assertThat(restored.programmingLanguage()).isEqualTo(ProgrammingLanguage.PYTHON3);
        assertThat(restored.entryFilePath()).isEqualTo("src/main.py");
        assertThat(restored.files())
                .extracting(ProgrammingSourceFile::path)
                .containsExactly("src/main.py", "src/helper.py");
        assertThat(new String(content, StandardCharsets.UTF_8)).contains("\"workspaceRevisionId\":3");
    }

    private ObjectProvider<ObjectStorageService> storageProvider() {
        return new StaticListableBeanFactory(Map.of("objectStorageService", objectStorageService))
                .getBeanProvider(ObjectStorageService.class);
    }

    private JudgeJobStoredReport sampleReport() {
        return new JudgeJobStoredReport(
                Map.of("mode", "STRUCTURED_PROGRAMMING", "submissionAnswerId", 42L),
                List.of(new JudgeJobCaseReportView(
                        1,
                        JudgeVerdict.ACCEPTED,
                        100,
                        100,
                        "1 2\n",
                        "42\n",
                        "42\n",
                        "",
                        15L,
                        1024L,
                        null,
                        "Accepted",
                        0,
                        List.of("g++", "main.cpp"),
                        List.of("./main"))),
                "42\n",
                "");
    }
}
