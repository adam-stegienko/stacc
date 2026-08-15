package com.adam_stegienko.stacc_api_gateway.controller;

import java.io.IOException;
import java.util.Enumeration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.adam_stegienko.stacc_api_gateway.services.ProxyService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/gw")
public class GatewayController {

    private final ProxyService proxyService;

    public GatewayController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @RequestMapping(
            value = "/{serviceId}/**",
            method = {
                RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD,
                RequestMethod.OPTIONS
            }
    )
    public ResponseEntity<byte[]> proxy(
            @PathVariable String serviceId,
            HttpServletRequest request) throws IOException {

        String downstreamPath = extractDownstreamPath(serviceId, request);
        HttpHeaders headers = extractHeaders(request);
        byte[] body = request.getInputStream().readAllBytes();
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        return proxyService.proxy(serviceId, downstreamPath, method, headers, body);
    }

    private String extractDownstreamPath(String serviceId, HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String prefix = contextPath + "/gw/" + serviceId;
        String path = requestUri.length() > prefix.length()
                ? requestUri.substring(prefix.length())
                : "/";

        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            path = path + "?" + queryString;
        }
        return path;
    }

    private HttpHeaders extractHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                Enumeration<String> values = request.getHeaders(name);
                while (values.hasMoreElements()) {
                    headers.add(name, values.nextElement());
                }
            }
        }
        return headers;
    }
}
