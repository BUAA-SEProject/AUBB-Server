package com.aubb.server.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("aubb.redis")
@Validated
@Getter
@Setter
public class RedisEnhancementProperties {

    private boolean enabled = false;

    private String host = "localhost";

    @Min(1)
    @Max(65535)
    private int port = 6379;

    private String password;

    @Min(0)
    private int database = 0;

    private String namespace = "aubb";

    private String environment = "local";

    private Duration connectTimeout = Duration.ofSeconds(2);

    private Duration commandTimeout = Duration.ofSeconds(2);

    private Cache cache = new Cache();

    private RateLimit rateLimit = new RateLimit();

    private Realtime realtime = new Realtime();

    @AssertTrue(message = "启用 Redis 时 aubb.redis.host 不能为空")
    public boolean isHostValidWhenEnabled() {
        return !enabled || StringUtils.hasText(host);
    }

    @AssertTrue(message = "启用 Redis 时 aubb.redis.namespace 不能为空")
    public boolean isNamespaceValidWhenEnabled() {
        return !enabled || StringUtils.hasText(namespace);
    }

    @AssertTrue(message = "启用 Redis 时 aubb.redis.environment 不能为空")
    public boolean isEnvironmentValidWhenEnabled() {
        return !enabled || StringUtils.hasText(environment);
    }

    @Getter
    @Setter
    public static class Cache {

        private Duration authSessionActiveTtl = Duration.ofSeconds(30);

        private Duration judgeJobReportTtl = Duration.ofMinutes(2);

        private Duration notificationUnreadTtl = Duration.ofMinutes(1);

        private Duration myCoursesTtl = Duration.ofMinutes(2);

        private Duration questionBankDictionaryTtl = Duration.ofMinutes(20);

        private Duration orgScopeTtl = Duration.ofMinutes(1);
    }

    @Getter
    @Setter
    public static class RateLimit {

        private boolean enabled = true;

        private Map<String, Policy> policies = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    public static class Policy {

        @Min(1)
        private int limit = 10;

        private Duration window = Duration.ofMinutes(1);
    }

    @Getter
    @Setter
    public static class Realtime {

        private boolean enabled = true;
    }
}
