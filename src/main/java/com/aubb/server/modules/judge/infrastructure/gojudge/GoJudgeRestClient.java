package com.aubb.server.modules.judge.infrastructure.gojudge;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class GoJudgeRestClient implements GoJudgeClient {

    private final RestClient restClient;

    public GoJudgeRestClient(RestClient goJudgeRestClient) {
        this.restClient = goJudgeRestClient;
    }

    @Override
    public List<RunResult> run(RunRequest request) {
        RunResult[] results = restClient
                .post()
                .uri("/run")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(RunResult[].class);
        return results == null ? List.of() : List.of(results);
    }
}
