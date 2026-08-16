package com.adam_stegienko.stacc_consumer.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CampaignActionMessage {

    private String id;
    private String campaign;
    private int action;
    private List<Integer> executionDate;
    private List<Object> campaignStatuses;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCampaign() {
        return campaign;
    }

    public void setCampaign(String campaign) {
        this.campaign = campaign;
    }

    public int getAction() {
        return action;
    }

    public void setAction(int action) {
        this.action = action;
    }

    public List<Integer> getExecutionDate() {
        return executionDate;
    }

    public void setExecutionDate(List<Integer> executionDate) {
        this.executionDate = executionDate;
    }

    public List<Object> getCampaignStatuses() {
        return campaignStatuses;
    }

    public void setCampaignStatuses(List<Object> campaignStatuses) {
        this.campaignStatuses = campaignStatuses;
    }
}
