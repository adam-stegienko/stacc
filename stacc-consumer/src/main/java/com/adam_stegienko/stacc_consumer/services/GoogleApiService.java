package com.adam_stegienko.stacc_consumer.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class GoogleApiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleApiService.class);

    private final RestTemplate restTemplate;

    @Value("${google-ads-api.base-url}")
    private String baseUrl;

    @Value("${google-ads-api.customer-id}")
    private String customerId;

    public GoogleApiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void updateCampaignStatus(String campaign, String status) {
        String uri = UriComponentsBuilder
                .fromUriString(baseUrl)
                .path("/v1/api/google-ads/campaigns/status/{campaign}")
                .queryParam("customerId", customerId)
                .queryParam("status", status)
                .buildAndExpand(campaign)
                .toUriString();

        LOGGER.info("Calling Google API: PUT {} (campaign='{}', status='{}')", uri, campaign, status);
        restTemplate.put(uri, null);
        LOGGER.info("Google API call succeeded for campaign='{}', status='{}'", campaign, status);
    }
}
