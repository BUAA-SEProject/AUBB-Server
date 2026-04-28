package com.aubb.server.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RequestContextSupport {

    private final ClientIpResolver clientIpResolver;

    public RequestContextSupport(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    public String requestId() {
        HttpServletRequest request = currentRequest();
        return request == null ? "unknown" : (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }

    public String clientIp() {
        return clientIpResolver.resolve(currentRequest());
    }

    public String userAgent() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "unknown";
        }
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        return userAgent == null || userAgent.isBlank() ? "unknown" : userAgent;
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }
}
