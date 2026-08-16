package com.adam_stegienko.stacc_google_api_rest.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.adam_stegienko.stacc_google_api_rest.dto.CampaignInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.ads.googleads.lib.utils.FieldMasks;
import com.google.ads.googleads.v25.enums.CampaignStatusEnum.CampaignStatus;
import com.google.ads.googleads.v25.resources.Campaign;
import com.google.ads.googleads.v25.services.CampaignOperation;
import com.google.ads.googleads.v25.services.CampaignServiceClient;
import com.google.ads.googleads.v25.services.GoogleAdsRow;
import com.google.ads.googleads.v25.services.GoogleAdsServiceClient;
import com.google.ads.googleads.v25.services.GoogleAdsServiceClient.SearchPagedResponse;
import com.google.ads.googleads.v25.services.MutateCampaignResult;
import com.google.ads.googleads.v25.services.MutateCampaignsResponse;
import com.google.ads.googleads.v25.services.SearchGoogleAdsRequest;
import com.google.ads.googleads.v25.utils.ResourceNames;

@Service
@ConditionalOnProperty(name = "api.googleads.enabled", havingValue = "true", matchIfMissing = false)
public class GoogleAdsApiService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleAdsApiService.class);

    private final GoogleAdsServiceClient googleAdsServiceClient;

    private final CampaignServiceClient campaignServiceClient;

    private final ObjectMapper objectMapper;

    @Autowired
    public GoogleAdsApiService(GoogleAdsServiceClient googleAdsServiceClient, CampaignServiceClient campaignServiceClient, ObjectMapper objectMapper) {
        this.googleAdsServiceClient = googleAdsServiceClient;
        this.campaignServiceClient = campaignServiceClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Escapes a user-supplied string for safe embedding in a GAQL literal.
     * Backslashes must be doubled first, then single quotes escaped with backslash.
     */
    private static String escapeGaqlValue(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    public String getCampaignStatusByName(String customerId, String campaignName) {
        if (googleAdsServiceClient == null) {
            return "Google Ads API not configured - please provide valid credentials";
        }
        
        String query = String.format("SELECT campaign.id, campaign.name, campaign.status FROM campaign WHERE campaign.name = '%s'", escapeGaqlValue(campaignName));
        SearchGoogleAdsRequest request = SearchGoogleAdsRequest.newBuilder()
                .setCustomerId(customerId)
                .setQuery(query)
                .build();

        SearchPagedResponse response = googleAdsServiceClient.search(request);
        List<CampaignInfo> campaignInfoList = new ArrayList<>();

        for (GoogleAdsRow row : response.iterateAll()) {
            campaignInfoList.add(new CampaignInfo(
                    row.getCampaign().getId(),
                    row.getCampaign().getName(),
                    row.getCampaign().getStatus().name()
            ));
        }

        try {
            String jsonResult = objectMapper.writeValueAsString(campaignInfoList);
            logger.info("Google Ads API response: {}", jsonResult);
            return jsonResult;
        } catch (JsonProcessingException e) {
            String errorMessage = "Failed to convert campaign info to JSON: " + e.getMessage();
            logger.error(errorMessage, e);
            return errorMessage;
        }
    }

    public String getCampaignStatusByNamesList(List<String> campaignNames, String customerId) {
        String names = campaignNames.stream()
                .map(name -> String.format("'%s'", escapeGaqlValue(name)))
                .collect(Collectors.joining(", "));
        String query = String.format("SELECT campaign.id, campaign.name, campaign.status FROM campaign WHERE campaign.name IN (%s)", names);
        SearchGoogleAdsRequest request = SearchGoogleAdsRequest.newBuilder()
                .setCustomerId(customerId)
                .setQuery(query)
                .build();

        SearchPagedResponse response = googleAdsServiceClient.search(request);
        List<CampaignInfo> campaignInfoList = new ArrayList<>();

        for (GoogleAdsRow row : response.iterateAll()) {
            campaignInfoList.add(new CampaignInfo(
                    row.getCampaign().getId(),
                    row.getCampaign().getName(),
                    row.getCampaign().getStatus().name()
            ));
        }

        try {
            String jsonResult = objectMapper.writeValueAsString(campaignInfoList);
            logger.info("Google Ads API response: {}", jsonResult);
            return jsonResult;
        } catch (JsonProcessingException e) {
            String errorMessage = "Failed to convert campaign info to JSON: " + e.getMessage();
            logger.error(errorMessage, e);
            return errorMessage;
        }
    }

    @SuppressWarnings("UseSpecificCatch")
    public String updateCampaignStatusByName(String customerId, String campaignName, String status) {
        try {
            // Retrieve the campaign ID by name
            String campaignId = getCampaignIdByName(customerId, campaignName);
            if (campaignId == null) {
                String message = String.format("No campaign found with name '%s' for customer ID '%s'", campaignName, customerId);
                logger.error(message);
                return message;
            }

            // Convert status string to CampaignStatus enum
            CampaignStatus campaignStatus = CampaignStatus.valueOf(status.toUpperCase());

            // Create the campaign with the updated status
            Campaign campaign = Campaign.newBuilder()
                    .setResourceName(ResourceNames.campaign(Long.parseLong(customerId), Long.parseLong(campaignId)))
                    .setStatus(campaignStatus)
                    .build();

            // Create the operation
            CampaignOperation operation = CampaignOperation.newBuilder()
                    .setUpdate(campaign)
                    .setUpdateMask(FieldMasks.allSetFieldsOf(campaign))
                    .build();

            // Update the campaign
            MutateCampaignsResponse response = campaignServiceClient.mutateCampaigns(customerId, Arrays.asList(operation));
            MutateCampaignResult result = response.getResults(0);

            logger.info(result.toString());
            logger.info("Google Ads API response: Campaign status updated successfully for campaign '{}'", campaignName);
            return String.format("Campaign status updated successfully for campaign '%s'", campaignName);
        } catch (Exception e) {
            String errorMessage = String.format("Failed to update campaign status for campaign '%s' with customer ID '%s': %s", campaignName, customerId, e.getMessage());
            logger.error(errorMessage, e);
            return errorMessage;
        }
    }

    public String suspendCampaign(String customerId, String campaignName) {
        String result = updateCampaignStatusByName(customerId, campaignName, "PAUSED");
        if (result.startsWith("Campaign status updated successfully")) {
            String message = String.format("Campaign '%s' suspended successfully", campaignName);
            logger.info(message);
            return message;
        }
        return result;
    }

    public String resumeCampaign(String customerId, String campaignName) {
        String result = updateCampaignStatusByName(customerId, campaignName, "ENABLED");
        if (result.startsWith("Campaign status updated successfully")) {
            String message = String.format("Campaign '%s' resumed successfully", campaignName);
            logger.info(message);
            return message;
        }
        return result;
    }

    private String getCampaignIdByName(String customerId, String campaignName) {
        String query = String.format("SELECT campaign.id FROM campaign WHERE campaign.name = '%s'", escapeGaqlValue(campaignName));
        SearchGoogleAdsRequest request = SearchGoogleAdsRequest.newBuilder()
                .setCustomerId(customerId)
                .setQuery(query)
                .build();

        SearchPagedResponse response = googleAdsServiceClient.search(request);
        for (GoogleAdsRow row : response.iterateAll()) {
            return String.valueOf(row.getCampaign().getId());
        }
        return null;
    }
}
