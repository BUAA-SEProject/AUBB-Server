package com.aubb.server.common.storage;

import java.net.URI;
import java.time.Duration;

public interface ObjectStorageService {

    void putObject(String key, byte[] content, String contentType);

    StoredObject getObject(String key);

    void deleteObject(String key);

    boolean objectExists(String key);

    URI createPresignedGetUrl(String key, Duration expiry);

    URI createPresignedPutUrl(String key, Duration expiry);
}
