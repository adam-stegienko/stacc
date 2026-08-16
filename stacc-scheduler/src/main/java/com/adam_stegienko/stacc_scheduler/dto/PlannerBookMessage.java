package com.adam_stegienko.stacc_scheduler.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Message payload published to RabbitMQ when a PlannerBook's execution_date
 * has been reached. Consumed by the campaign-controller microservice.
 */
public class PlannerBookMessage {

    private UUID id;
    private String campaign;
    private Integer action;
    private LocalDateTime executionDate;
    private List<CampaignStatusDto> campaignStatuses;

    public PlannerBookMessage() {}

    public PlannerBookMessage(UUID id, String campaign, Integer action,
                              LocalDateTime executionDate,
                              List<CampaignStatusDto> campaignStatuses) {
        this.id = id;
        this.campaign = campaign;
        this.action = action;
        this.executionDate = executionDate;
        this.campaignStatuses = campaignStatuses;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCampaign() { return campaign; }
    public void setCampaign(String campaign) { this.campaign = campaign; }

    public Integer getAction() { return action; }
    public void setAction(Integer action) { this.action = action; }

    public LocalDateTime getExecutionDate() { return executionDate; }
    public void setExecutionDate(LocalDateTime executionDate) { this.executionDate = executionDate; }

    public List<CampaignStatusDto> getCampaignStatuses() { return campaignStatuses; }
    public void setCampaignStatuses(List<CampaignStatusDto> campaignStatuses) { this.campaignStatuses = campaignStatuses; }
}
