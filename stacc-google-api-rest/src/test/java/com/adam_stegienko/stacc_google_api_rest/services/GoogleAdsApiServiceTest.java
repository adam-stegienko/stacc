package com.adam_stegienko.stacc_google_api_rest.services;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.ads.googleads.v25.enums.CampaignStatusEnum.CampaignStatus;
import com.google.ads.googleads.v25.resources.Campaign;
import com.google.ads.googleads.v25.services.CampaignServiceClient;
import com.google.ads.googleads.v25.services.GoogleAdsRow;
import com.google.ads.googleads.v25.services.GoogleAdsServiceClient;
import com.google.ads.googleads.v25.services.GoogleAdsServiceClient.SearchPagedResponse;
import com.google.ads.googleads.v25.services.MutateCampaignResult;
import com.google.ads.googleads.v25.services.MutateCampaignsResponse;
import com.google.ads.googleads.v25.services.SearchGoogleAdsRequest;

@ExtendWith(MockitoExtension.class)
class GoogleAdsApiServiceTest {

    @Mock
    private GoogleAdsServiceClient googleAdsServiceClient;

    @Mock
    private CampaignServiceClient campaignServiceClient;

    private GoogleAdsApiService service;

    @BeforeEach
    void setUp() {
        service = new GoogleAdsApiService(googleAdsServiceClient, campaignServiceClient, new ObjectMapper());
    }

    @Test
    void getCampaignStatusByName_returnsNotConfiguredMessage_whenClientIsNull() {
        service = new GoogleAdsApiService(null, campaignServiceClient, new ObjectMapper());

        String result = service.getCampaignStatusByName("123456789", "MyCampaign");

        assertThat(result).contains("not configured");
    }

    @Test
    void getCampaignStatusByName_returnsCampaignJson_whenFound() {
        SearchPagedResponse mockResponse = mock(SearchPagedResponse.class);
        Campaign campaign = Campaign.newBuilder()
                .setId(42L)
                .setName("MyCampaign")
                .setStatus(CampaignStatus.ENABLED)
                .build();
        GoogleAdsRow row = GoogleAdsRow.newBuilder().setCampaign(campaign).build();
        when(mockResponse.iterateAll()).thenReturn(List.of(row));
        when(googleAdsServiceClient.search(any(SearchGoogleAdsRequest.class))).thenReturn(mockResponse);

        String result = service.getCampaignStatusByName("123456789", "MyCampaign");

        assertThat(result).contains("MyCampaign");
        assertThat(result).contains("ENABLED");
        assertThat(result).contains("42");
    }

    @Test
    void getCampaignStatusByName_returnsEmptyJson_whenNotFound() {
        SearchPagedResponse mockResponse = mock(SearchPagedResponse.class);
        when(mockResponse.iterateAll()).thenReturn(List.of());
        when(googleAdsServiceClient.search(any(SearchGoogleAdsRequest.class))).thenReturn(mockResponse);

        String result = service.getCampaignStatusByName("123456789", "Unknown");

        assertThat(result).isEqualTo("[]");
    }

    @Test
    void getCampaignStatusByNamesList_returnsCampaignJson_whenFound() {
        SearchPagedResponse mockResponse = mock(SearchPagedResponse.class);
        Campaign camp1 = Campaign.newBuilder().setId(1L).setName("Camp1").setStatus(CampaignStatus.ENABLED).build();
        Campaign camp2 = Campaign.newBuilder().setId(2L).setName("Camp2").setStatus(CampaignStatus.PAUSED).build();
        GoogleAdsRow row1 = GoogleAdsRow.newBuilder().setCampaign(camp1).build();
        GoogleAdsRow row2 = GoogleAdsRow.newBuilder().setCampaign(camp2).build();
        when(mockResponse.iterateAll()).thenReturn(List.of(row1, row2));
        when(googleAdsServiceClient.search(any(SearchGoogleAdsRequest.class))).thenReturn(mockResponse);

        String result = service.getCampaignStatusByNamesList(List.of("Camp1", "Camp2"), "123456789");

        assertThat(result).contains("Camp1");
        assertThat(result).contains("Camp2");
        assertThat(result).contains("ENABLED");
        assertThat(result).contains("PAUSED");
    }

    @Test
    void updateCampaignStatusByName_returnsCampaignNotFoundMessage_whenCampaignMissing() {
        SearchPagedResponse mockResponse = mock(SearchPagedResponse.class);
        when(mockResponse.iterateAll()).thenReturn(List.of());
        when(googleAdsServiceClient.search(any(SearchGoogleAdsRequest.class))).thenReturn(mockResponse);

        String result = service.updateCampaignStatusByName("123456789", "NonExistent", "PAUSED");

        assertThat(result).contains("No campaign found");
        assertThat(result).contains("NonExistent");
    }

    @Test
    void updateCampaignStatusByName_returnsSuccessMessage_whenUpdated() {
        SearchPagedResponse idSearchResponse = mock(SearchPagedResponse.class);
        Campaign campaign = Campaign.newBuilder().setId(99L).setName("MyCampaign").build();
        GoogleAdsRow idRow = GoogleAdsRow.newBuilder().setCampaign(campaign).build();
        when(idSearchResponse.iterateAll()).thenReturn(List.of(idRow));
        when(googleAdsServiceClient.search(any(SearchGoogleAdsRequest.class))).thenReturn(idSearchResponse);

        MutateCampaignResult mutateResult = MutateCampaignResult.newBuilder().build();
        MutateCampaignsResponse mutateResponse = MutateCampaignsResponse.newBuilder()
                .addResults(mutateResult)
                .build();
        when(campaignServiceClient.mutateCampaigns(eq("123456789"), anyList())).thenReturn(mutateResponse);

        String result = service.updateCampaignStatusByName("123456789", "MyCampaign", "PAUSED");

        assertThat(result).contains("updated successfully");
        assertThat(result).contains("MyCampaign");
    }

    @Test
    void suspendCampaign_returnsSuspendedMessage_onSuccess() {
        SearchPagedResponse idSearchResponse = mock(SearchPagedResponse.class);
        Campaign campaign = Campaign.newBuilder().setId(99L).setName("MyCampaign").build();
        GoogleAdsRow idRow = GoogleAdsRow.newBuilder().setCampaign(campaign).build();
        when(idSearchResponse.iterateAll()).thenReturn(List.of(idRow));
        when(googleAdsServiceClient.search(any(SearchGoogleAdsRequest.class))).thenReturn(idSearchResponse);

        MutateCampaignResult mutateResult = MutateCampaignResult.newBuilder().build();
        MutateCampaignsResponse mutateResponse = MutateCampaignsResponse.newBuilder()
                .addResults(mutateResult)
                .build();
        when(campaignServiceClient.mutateCampaigns(eq("123456789"), anyList())).thenReturn(mutateResponse);

        String result = service.suspendCampaign("123456789", "MyCampaign");

        assertThat(result).isEqualTo("Campaign 'MyCampaign' suspended successfully");
    }

    @Test
    void suspendCampaign_returnsCampaignNotFoundMessage_whenCampaignMissing() {
        SearchPagedResponse mockResponse = mock(SearchPagedResponse.class);
        when(mockResponse.iterateAll()).thenReturn(List.of());
        when(googleAdsServiceClient.search(any(SearchGoogleAdsRequest.class))).thenReturn(mockResponse);

        String result = service.suspendCampaign("123456789", "NonExistent");

        assertThat(result).contains("No campaign found");
    }

    @Test
    void resumeCampaign_returnsResumedMessage_onSuccess() {
        SearchPagedResponse idSearchResponse = mock(SearchPagedResponse.class);
        Campaign campaign = Campaign.newBuilder().setId(99L).setName("MyCampaign").build();
        GoogleAdsRow idRow = GoogleAdsRow.newBuilder().setCampaign(campaign).build();
        when(idSearchResponse.iterateAll()).thenReturn(List.of(idRow));
        when(googleAdsServiceClient.search(any(SearchGoogleAdsRequest.class))).thenReturn(idSearchResponse);

        MutateCampaignResult mutateResult = MutateCampaignResult.newBuilder().build();
        MutateCampaignsResponse mutateResponse = MutateCampaignsResponse.newBuilder()
                .addResults(mutateResult)
                .build();
        when(campaignServiceClient.mutateCampaigns(eq("123456789"), anyList())).thenReturn(mutateResponse);

        String result = service.resumeCampaign("123456789", "MyCampaign");

        assertThat(result).isEqualTo("Campaign 'MyCampaign' resumed successfully");
    }

    @Test
    void resumeCampaign_returnsCampaignNotFoundMessage_whenCampaignMissing() {
        SearchPagedResponse mockResponse = mock(SearchPagedResponse.class);
        when(mockResponse.iterateAll()).thenReturn(List.of());
        when(googleAdsServiceClient.search(any(SearchGoogleAdsRequest.class))).thenReturn(mockResponse);

        String result = service.resumeCampaign("123456789", "NonExistent");

        assertThat(result).contains("No campaign found");
    }
}
