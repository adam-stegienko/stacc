package com.adam_stegienko.stacc_scheduler.dto;

public class CampaignStatusDto {

    private Long id;
    private String name;
    private String status;

    public CampaignStatusDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
