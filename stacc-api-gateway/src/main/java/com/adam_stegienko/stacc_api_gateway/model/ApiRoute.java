package com.adam_stegienko.stacc_api_gateway.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ApiRoute {

    private UUID id;
    private String routeId;
    private String targetUri;
    private boolean enabled = true;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }
    public String getTargetUri() { return targetUri; }
    public void setTargetUri(String targetUri) { this.targetUri = targetUri; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
