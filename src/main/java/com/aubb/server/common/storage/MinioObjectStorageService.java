package com.aubb.server.common.storage;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class MinioObjectStorageService implements ObjectStorageService {

    private static final Duration MAX_PRESIGNED_EXPIRY = Duration.ofDays(7);

    private final MinioClient minioClient;
    private final String bucket;

    @Override
    public void putObject(String key, byte[] content, String contentType) {
        String normalizedKey = normalizeKey(key);
        byte[] normalizedContent = normalizeContent(content);
        String normalizedContentType =
                StringUtils.hasText(contentType) ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(normalizedContent)) {
            minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(normalizedKey).stream(
                            inputStream, normalizedContent.length, -1)
                    .contentType(normalizedContentType)
                    .build());
        } catch (Exception exception) {
            throw new ObjectStorageException("写入 MinIO 对象失败: " + normalizedKey, exception);
        }
    }

    @Override
    public StoredObject getObject(String key) {
        String normalizedKey = normalizeKey(key);

        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(normalizedKey)
                    .build());

            try (GetObjectResponse response = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(normalizedKey).build())) {
                byte[] content = response.readAllBytes();
                String contentType = StringUtils.hasText(stat.contentType())
                        ? stat.contentType()
                        : MediaType.APPLICATION_OCTET_STREAM_VALUE;
                return new StoredObject(normalizedKey, content, contentType, stat.size());
            }
        } catch (Exception exception) {
            if (isMissingObject(exception)) {
                throw new ObjectStorageException("对象不存在: " + normalizedKey, exception);
            }
            throw new ObjectStorageException("读取 MinIO 对象失败: " + normalizedKey, exception);
        }
    }

    @Override
    public void deleteObject(String key) {
        String normalizedKey = normalizeKey(key);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(normalizedKey)
                    .build());
        } catch (Exception exception) {
            throw new ObjectStorageException("删除 MinIO 对象失败: " + normalizedKey, exception);
        }
    }

    @Override
    public boolean objectExists(String key) {
        String normalizedKey = normalizeKey(key);
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(normalizedKey)
                    .build());
            return true;
        } catch (Exception exception) {
            if (isMissingObject(exception)) {
                return false;
            }
            throw new ObjectStorageException("检查 MinIO 对象失败: " + normalizedKey, exception);
        }
    }

    @Override
    public URI createPresignedGetUrl(String key, Duration expiry) {
        return createPresignedUrl(Method.GET, key, expiry);
    }

    @Override
    public URI createPresignedPutUrl(String key, Duration expiry) {
        return createPresignedUrl(Method.PUT, key, expiry);
    }

    private URI createPresignedUrl(Method method, String key, Duration expiry) {
        String normalizedKey = normalizeKey(key);
        int expirySeconds = normalizeExpiry(expiry);
        try {
            String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(method)
                    .bucket(bucket)
                    .object(normalizedKey)
                    .expiry(expirySeconds)
                    .build());
            return URI.create(url);
        } catch (Exception exception) {
            throw new ObjectStorageException("生成 MinIO 预签名链接失败: " + normalizedKey, exception);
        }
    }

    private String normalizeKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("对象键不能为空");
        }
        String normalizedKey = key.trim();
        if (normalizedKey.startsWith("/")) {
            normalizedKey = normalizedKey.substring(1);
        }
        return normalizedKey;
    }

    private byte[] normalizeContent(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("对象内容不能为空");
        }
        return content;
    }

    private int normalizeExpiry(Duration expiry) {
        if (expiry == null || expiry.isZero() || expiry.isNegative()) {
            throw new IllegalArgumentException("预签名链接有效期必须大于 0");
        }
        // MinIO/S3 预签名链接最长只允许 7 天，这里统一在基础设施层收口。
        if (expiry.compareTo(MAX_PRESIGNED_EXPIRY) > 0) {
            throw new IllegalArgumentException("预签名链接有效期不能超过 7 天");
        }
        return Math.toIntExact(expiry.getSeconds());
    }

    private boolean isMissingObject(Exception exception) {
        if (exception instanceof ErrorResponseException errorResponseException) {
            String code = errorResponseException.errorResponse().code();
            return "NoSuchKey".equals(code) || "NoSuchObject".equals(code);
        }
        return false;
    }
}
