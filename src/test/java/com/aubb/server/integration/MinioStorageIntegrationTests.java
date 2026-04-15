package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aubb.server.TestcontainersConfiguration;
import com.aubb.server.common.storage.ObjectStorageService;
import com.aubb.server.common.storage.StoredObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        properties = {
            "spring.docker.compose.enabled=false",
            "aubb.storage.minio.enabled=true",
            "aubb.storage.minio.auto-create-bucket=true"
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Testcontainers
class MinioStorageIntegrationTests {

    private static final DockerImageName MINIO_IMAGE =
            DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z");

    private static final String MINIO_ACCESS_KEY = "aubbminio";
    private static final String MINIO_SECRET_KEY = "aubbminio-secret";
    private static final String MINIO_BUCKET = "aubb-test-assets";

    @Container
    static final GenericContainer<?> MINIO_CONTAINER = new GenericContainer<>(MINIO_IMAGE)
            .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
            .withCommand("server", "/data", "--console-address", ":9001")
            .withExposedPorts(9000, 9001)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).forStatusCode(200));

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Autowired
    private ObjectStorageService objectStorageService;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "aubb.storage.minio.endpoint",
                () -> "http://" + MINIO_CONTAINER.getHost() + ":" + MINIO_CONTAINER.getMappedPort(9000));
        registry.add("aubb.storage.minio.access-key", () -> MINIO_ACCESS_KEY);
        registry.add("aubb.storage.minio.secret-key", () -> MINIO_SECRET_KEY);
        registry.add("aubb.storage.minio.bucket", () -> MINIO_BUCKET);
    }

    @Test
    void objectStorageSupportsDirectAndPresignedAccess() throws Exception {
        String directKey = "submissions/direct/answer.txt";
        String directContent = "hello minio";

        objectStorageService.putObject(
                directKey, directContent.getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");

        assertThat(objectStorageService.objectExists(directKey)).isTrue();

        StoredObject storedObject = objectStorageService.getObject(directKey);
        assertThat(storedObject.key()).isEqualTo(directKey);
        assertThat(storedObject.contentType()).startsWith("text/plain");
        assertThat(new String(storedObject.content(), StandardCharsets.UTF_8)).isEqualTo(directContent);

        URI presignedGetUrl = objectStorageService.createPresignedGetUrl(directKey, Duration.ofMinutes(5));
        HttpResponse<String> getResponse = HTTP_CLIENT.send(
                HttpRequest.newBuilder(presignedGetUrl).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(getResponse.body()).isEqualTo(directContent);

        String presignedKey = "submissions/presigned/answer.txt";
        URI presignedPutUrl = objectStorageService.createPresignedPutUrl(presignedKey, Duration.ofMinutes(5));
        HttpResponse<String> putResponse = HTTP_CLIENT.send(
                HttpRequest.newBuilder(presignedPutUrl)
                        .PUT(HttpRequest.BodyPublishers.ofString("uploaded-via-presigned-url"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(putResponse.statusCode()).isEqualTo(200);
        assertThat(new String(objectStorageService.getObject(presignedKey).content(), StandardCharsets.UTF_8))
                .isEqualTo("uploaded-via-presigned-url");

        objectStorageService.deleteObject(directKey);
        assertThat(objectStorageService.objectExists(directKey)).isFalse();
    }

    @Test
    void healthEndpointStaysPublicWhenMinioIsEnabled() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
