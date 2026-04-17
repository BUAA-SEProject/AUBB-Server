package com.aubb.server.common.storage;

public record StoredObject(String key, byte[] content, String contentType, long size) {}
