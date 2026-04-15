package com.aubb.server.integration;

import com.jayway.jsonpath.JsonPath;

final class JsonTestSupport {

    private JsonTestSupport() {}

    static String read(String json, String expression) {
        return JsonPath.read(json, expression);
    }
}
