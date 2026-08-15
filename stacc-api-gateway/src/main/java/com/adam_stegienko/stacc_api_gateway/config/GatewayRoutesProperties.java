package com.adam_stegienko.stacc_api_gateway.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayRoutesProperties {

    private Map<String, RouteDefinition> routes = new LinkedHashMap<>();

    public Map<String, RouteDefinition> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, ?> routes) {
        LinkedHashMap<String, RouteDefinition> normalized = new LinkedHashMap<>();
        if (routes != null) {
            routes.forEach((key, value) -> normalized.put(key, toRouteDefinition(value)));
        }
        this.routes = normalized;
    }

    private RouteDefinition toRouteDefinition(Object value) {
        if (value == null) {
            return new RouteDefinition();
        }
        if (value instanceof RouteDefinition routeDefinition) {
            return routeDefinition;
        }
        if (value instanceof String uri) {
            return new RouteDefinition(uri, List.of());
        }
        if (value instanceof Map<?, ?> routeMap) {
            Object uri = routeMap.get("uri");
            Object filters = routeMap.get("filters");
            return new RouteDefinition(uri != null ? uri.toString() : null, toStringList(filters));
        }
        return new RouteDefinition(value.toString(), List.of());
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : iterable) {
            if (item != null) {
                result.add(item.toString());
            }
        }
        return result;
    }

    public static class RouteDefinition {

        private String uri;
        private List<String> filters = new ArrayList<>();

        public RouteDefinition() {
        }

        public RouteDefinition(String uri, List<String> filters) {
            this.uri = uri;
            this.filters = filters != null ? new ArrayList<>(filters) : new ArrayList<>();
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public List<String> getFilters() {
            return filters;
        }

        public void setFilters(List<String> filters) {
            this.filters = filters != null ? new ArrayList<>(filters) : new ArrayList<>();
        }
    }
}
