package com.aubb.server.modules.judge.infrastructure.gojudge;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.web.client.RestClient;

class GoJudgeHealthIndicatorTests {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void reportsUpWhenVersionEndpointReturnsBuildMetadata() throws Exception {
        URI baseUrl = startServer(200, """
                {"buildVersion":"v1.11.4","goVersion":"go1.25.8","platform":"amd64","os":"linux"}
                """);

        GoJudgeHealthIndicator indicator = new GoJudgeHealthIndicator(
                RestClient.builder().baseUrl(baseUrl.toString()).build(), baseUrl);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("baseUrl", baseUrl.toString())
                .containsEntry("buildVersion", "v1.11.4")
                .containsEntry("goVersion", "go1.25.8");
    }

    @Test
    void reportsDownWithReasonWhenEndpointReturnsUnexpectedResponse() throws Exception {
        URI baseUrl = startServer(503, """
                {"error":"judge unavailable"}
                """);

        GoJudgeHealthIndicator indicator = new GoJudgeHealthIndicator(
                RestClient.builder().baseUrl(baseUrl.toString()).build(), baseUrl);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("baseUrl", baseUrl.toString())
                .containsEntry("httpStatus", 503)
                .containsEntry("reason", "unexpected_response");
    }

    private URI startServer(int statusCode, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/version", exchange -> {
            byte[] content = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, content.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(content);
            }
        });
        server.start();
        return URI.create("http://localhost:" + server.getAddress().getPort());
    }
}
