package com.adam_stegienko.stacc_scheduler.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.adam_stegienko.stacc_scheduler.dto.CampaignStatusDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP client for the campaign-controller-google-api service.
 * Resolves Google Ads campaign state before a scheduled action is dispatched.
 */
@Component
public class StaccGoogleApiRestClient {

    private static final Logger log = LoggerFactory.getLogger(StaccGoogleApiRestClient.class);

    private final RestClient restClient;

    @Value("${google-ads-api.customer-id:}")
    private String customerId;

    public StaccGoogleApiRestClient(RestClient.Builder builder,
                              @Value("${google-ads-api.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public List<CampaignStatusDto> getCampaignStatus(String campaignName) {
        try {
            List<CampaignStatusDto> result = restClient.get()
                    .uri("/v1/api/google-ads/campaigns/status/{name}?customerId={customerId}",
                            campaignName, customerId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<CampaignStatusDto>>() {});
            return result != null ? result : List.of();
        } catch (RestClientException e) {
            log.warn("Could not fetch campaign status for '{}' from google-ads-api: {}",
                    campaignName, e.getMessage());
            return List.of();
        }
    }
}
