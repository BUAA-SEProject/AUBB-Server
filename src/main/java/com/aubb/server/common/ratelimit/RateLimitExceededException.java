package com.aubb.server.common.ratelimit;

import com.aubb.server.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT_EXCEEDED",
                retryAfterSeconds > 0 ? "请求过于频繁，请在 %d 秒后重试".formatted(retryAfterSeconds) : "请求过于频繁，请稍后重试");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
