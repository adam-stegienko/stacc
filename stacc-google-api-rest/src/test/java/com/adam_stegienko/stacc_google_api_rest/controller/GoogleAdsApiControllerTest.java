package com.adam_stegienko.stacc_google_api_rest.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.adam_stegienko.stacc_google_api_rest.controller.GoogleAdsApiController;
import com.adam_stegienko.stacc_google_api_rest.services.GoogleAdsApiService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GoogleAdsApiController.class)
@TestPropertySource(properties = "api.googleads.enabled=true")
class GoogleAdsApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GoogleAdsApiService googleAdsService;

    @Test
    void getCampaignStatusByName_returnsServiceResponse() throws Exception {
        String expected = "[{\"id\":1,\"name\":\"TestCampaign\",\"status\":\"ENABLED\"}]";
        when(googleAdsService.getCampaignStatusByName("123456789", "TestCampaign"))
                .thenReturn(expected);

        mockMvc.perform(get("/v1/api/google-ads/campaigns/status/TestCampaign")
                        .param("customerId", "123456789"))
                .andExpect(status().isOk())
                .andExpect(content().string(expected));
    }

    @Test
    void getCampaignStatusByNamesList_returnsServiceResponse() throws Exception {
        String expected = "[{\"id\":1,\"name\":\"Camp1\",\"status\":\"ENABLED\"},{\"id\":2,\"name\":\"Camp2\",\"status\":\"PAUSED\"}]";
        when(googleAdsService.getCampaignStatusByNamesList(List.of("Camp1", "Camp2"), "123456789"))
                .thenReturn(expected);

        mockMvc.perform(get("/v1/api/google-ads/campaigns/status")
                        .param("campaignNames", "Camp1", "Camp2")
                        .param("customerId", "123456789"))
                .andExpect(status().isOk())
                .andExpect(content().string(expected));
    }

    @Test
    void updateCampaignStatusByName_returnsServiceResponse() throws Exception {
        String expected = "Campaign status updated successfully for campaign 'TestCampaign'";
        when(googleAdsService.updateCampaignStatusByName("123456789", "TestCampaign", "PAUSED"))
                .thenReturn(expected);

        mockMvc.perform(put("/v1/api/google-ads/campaigns/status/TestCampaign")
                        .param("customerId", "123456789")
                        .param("status", "PAUSED"))
                .andExpect(status().isOk())
                .andExpect(content().string(expected));
    }

    @Test
    void suspendCampaign_returnsServiceResponse() throws Exception {
        String expected = "Campaign 'TestCampaign' suspended successfully";
        when(googleAdsService.suspendCampaign("123456789", "TestCampaign"))
                .thenReturn(expected);

        mockMvc.perform(put("/v1/api/google-ads/campaigns/suspend/TestCampaign")
                        .param("customerId", "123456789"))
                .andExpect(status().isOk())
                .andExpect(content().string(expected));
    }

    @Test
    void resumeCampaign_returnsServiceResponse() throws Exception {
        String expected = "Campaign 'TestCampaign' resumed successfully";
        when(googleAdsService.resumeCampaign("123456789", "TestCampaign"))
                .thenReturn(expected);

        mockMvc.perform(put("/v1/api/google-ads/campaigns/resume/TestCampaign")
                        .param("customerId", "123456789"))
                .andExpect(status().isOk())
                .andExpect(content().string(expected));
    }

    @Test
    void getCampaignStatusByName_returnsNotConfiguredMessage_whenServiceReturnsIt() throws Exception {
        String expected = "Google Ads API not configured - please provide valid credentials";
        when(googleAdsService.getCampaignStatusByName("123456789", "MyCampaign"))
                .thenReturn(expected);

        mockMvc.perform(get("/v1/api/google-ads/campaigns/status/MyCampaign")
                        .param("customerId", "123456789"))
                .andExpect(status().isOk())
                .andExpect(content().string(expected));
    }
}
