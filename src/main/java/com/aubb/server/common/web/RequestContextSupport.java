package com.aubb.server.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RequestContextSupport {

    public String requestId() {
        HttpServletRequest request = currentRequest();
        return request == null ? "unknown" : (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }

    public String clientIp() {
        HttpServletRequest request = currentRequest();
        return request == null ? "unknown" : request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }
}
