package com.aubb.server.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("aubb.security.jwt")
@Validated
@Getter
@Setter
public class JwtSecurityProperties {

    @NotBlank(message = "aubb.security.jwt.issuer 不能为空")
    private String issuer;

    @NotNull(message = "aubb.security.jwt.ttl 必须配置")
    private Duration ttl;

    @NotNull(message = "aubb.security.jwt.refresh-ttl 必须配置")
    private Duration refreshTtl;

    @NotBlank(message = "aubb.security.jwt.secret 必须通过环境变量或外部配置提供")
    @Size(min = 32, message = "aubb.security.jwt.secret 至少需要 32 个字符")
    private String secret;

    public SecretKey secretKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @AssertTrue(message = "aubb.security.jwt.ttl 必须为正时长")
    public boolean isTtlPositive() {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    @AssertTrue(message = "aubb.security.jwt.refresh-ttl 必须为正时长")
    public boolean isRefreshTtlPositive() {
        return refreshTtl != null && !refreshTtl.isZero() && !refreshTtl.isNegative();
    }
}
