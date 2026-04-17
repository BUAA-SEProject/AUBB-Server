package com.aubb.server.modules.judge.infrastructure.gojudge;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@RequiredArgsConstructor
public class GoJudgeHealthIndicator implements HealthIndicator {

    private final RestClient restClient;
    private final URI baseUrl;

    @Override
    public Health health() {
        try {
            GoJudgeVersionResponse response =
                    restClient.get().uri("/version").retrieve().body(GoJudgeVersionResponse.class);
            if (response == null || !StringUtils.hasText(response.buildVersion())) {
                return Health.down()
                        .withDetail("baseUrl", baseUrl.toString())
                        .withDetail("reason", "empty_version_response")
                        .build();
            }
            return Health.up()
                    .withDetail("baseUrl", baseUrl.toString())
                    .withDetail("buildVersion", response.buildVersion())
                    .withDetail("goVersion", response.goVersion())
                    .withDetail("platform", response.platform())
                    .withDetail("os", response.os())
                    .build();
        } catch (RestClientResponseException exception) {
            return Health.down(exception)
                    .withDetail("baseUrl", baseUrl.toString())
                    .withDetail("httpStatus", exception.getStatusCode().value())
                    .withDetail("reason", "unexpected_response")
                    .build();
        } catch (RestClientException exception) {
            return Health.down(exception)
                    .withDetail("baseUrl", baseUrl.toString())
                    .withDetail("reason", "go_judge_unreachable")
                    .build();
        }
    }

    private record GoJudgeVersionResponse(String buildVersion, String goVersion, String platform, String os) {}
}
