package com.adam_stegienko.stacc_api_gateway.services;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.adam_stegienko.stacc_api_gateway.config.GatewayRoutesProperties;
import com.adam_stegienko.stacc_api_gateway.config.GatewayRoutesProperties.RouteDefinition;

@Service
public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "host", "content-length"
    );

    private final RestTemplate restTemplate;
    private final GatewayRoutesProperties routesProperties;

    public ProxyService(RestTemplate restTemplate, GatewayRoutesProperties routesProperties) {
        this.restTemplate = restTemplate;
        this.routesProperties = routesProperties;
    }

    public ResponseEntity<byte[]> proxy(String serviceId, String downstreamPath,
                                         HttpMethod method, HttpHeaders incomingHeaders,
                                         byte[] body) {
        RouteDefinition routeDefinition = resolveRoute(serviceId);
        String targetUri = routeDefinition != null ? routeDefinition.getUri() : null;
        List<String> responseFilters = routeDefinition != null ? routeDefinition.getFilters() : List.of();
        if (targetUri == null || targetUri.isBlank()) {
            log.warn("No route found for service: {}", serviceId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(("No route configured for service: " + serviceId).getBytes());
        }

        URI destination = UriComponentsBuilder
                .fromUriString(targetUri + downstreamPath)
                .build(true)
                .toUri();

        HttpHeaders forwardHeaders = filterHeaders(incomingHeaders);
        HttpEntity<byte[]> requestEntity = (body != null && body.length > 0)
                ? new HttpEntity<>(body, forwardHeaders)
                : new HttpEntity<>(forwardHeaders);

        log.debug("Proxying {} {} -> {}", method, downstreamPath, destination);

        try {
            ResponseEntity<byte[]> downstreamResponse = restTemplate.exchange(destination, method, requestEntity, byte[].class);
            return applyConfiguredFilters(downstreamResponse, responseFilters);
        } catch (HttpStatusCodeException e) {
            HttpHeaders responseHeaders = e.getResponseHeaders() != null
                    ? new HttpHeaders(e.getResponseHeaders())
                    : new HttpHeaders();

            stripDownstreamResponseHeaders(responseHeaders);
            applyResponseFilters(responseHeaders, responseFilters);
            
            return ResponseEntity.status(e.getStatusCode())
                    .headers(responseHeaders)
                    .body(e.getResponseBodyAsByteArray());
        } catch (ResourceAccessException e) {
            log.error("Downstream service unreachable: {}", destination, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(("Downstream service unreachable: " + serviceId).getBytes());
        }
    }

    private void stripDownstreamResponseHeaders(HttpHeaders headers) {
        // Forcing removal allows Tomcat/Spring to recalculate accurate lengths
        headers.remove(HttpHeaders.CONTENT_LENGTH);
        headers.remove(HttpHeaders.TRANSFER_ENCODING);
        headers.remove(HttpHeaders.CONNECTION);
        headers.remove("Keep-Alive");
        headers.remove("Upgrade");
    }

    private RouteDefinition resolveRoute(String serviceId) {
        return routesProperties.getRoutes().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(serviceId))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private ResponseEntity<byte[]> applyConfiguredFilters(ResponseEntity<byte[]> response, List<String> filters) {
        HttpHeaders copiedHeaders = new HttpHeaders();
        copiedHeaders.putAll(response.getHeaders());
        
        // 1. CRITICAL: Strip hop-by-hop and length headers from the downstream response
        stripDownstreamResponseHeaders(copiedHeaders);
        
        applyResponseFilters(copiedHeaders, filters);

        return ResponseEntity.status(response.getStatusCode())
                .headers(copiedHeaders)
                .body(response.getBody());
    }

    private void applyResponseFilters(HttpHeaders headers, List<String> filters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }

        for (String filter : filters) {
            if (filter == null || filter.isBlank()) {
                continue;
            }
            if (filter.startsWith("RemoveResponseHeader=")) {
                String headerName = filter.substring("RemoveResponseHeader=".length()).trim();
                if (!headerName.isEmpty()) {
                    headers.remove(headerName);
                }
                continue;
            }

            if (filter.startsWith("DeduplicateResponseHeader=")) {
                String args = filter.substring("DeduplicateResponseHeader=".length()).trim();
                deduplicateResponseHeaders(headers, args);
            }
        }
    }

    private void deduplicateResponseHeaders(HttpHeaders headers, String args) {
        if (args.isBlank()) {
            return;
        }

        String[] tokens = args.split("\\s+");
        if (tokens.length == 0) {
            return;
        }

        boolean retainLast = false;
        List<String> headerNames = new ArrayList<>();
        for (String token : tokens) {
            if ("RETAIN_LAST".equalsIgnoreCase(token)) {
                retainLast = true;
                continue;
            }
            if ("RETAIN_FIRST".equalsIgnoreCase(token)) {
                continue;
            }
            headerNames.add(token);
        }

        for (String headerName : headerNames) {
            List<String> values = headers.get(headerName);
            if (values == null || values.size() <= 1) {
                continue;
            }
            String selectedValue = retainLast ? values.get(values.size() - 1) : values.get(0);
            headers.put(headerName, List.of(selectedValue));
        }
    }

    private HttpHeaders filterHeaders(HttpHeaders incoming) {
        HttpHeaders filtered = new HttpHeaders();
        incoming.forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                filtered.put(name, values);
            }
        });
        return filtered;
    }
}
