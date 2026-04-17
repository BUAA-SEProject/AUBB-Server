package com.aubb.server.common.programming;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProgrammingSourceFile(String path, String content) {}
