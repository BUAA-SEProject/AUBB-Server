package com.aubb.server.modules.course.application.resource;

public record CourseResourceDownload(String originalFilename, String contentType, byte[] content) {}
