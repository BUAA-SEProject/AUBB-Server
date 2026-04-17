package com.aubb.server.modules.course.application;

public record CourseResourceDownload(String originalFilename, String contentType, byte[] content) {}
