package com.adam_stegienko.stacc_api_gateway.controller;

import com.adam_stegienko.stacc_api_gateway.services.ProxyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GatewayController.class)
class GatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProxyService proxyService;

    @Test
    void proxyGetRequestToKnownService() throws Exception {
        byte[] responseBody = "{\"status\":\"ok\"}".getBytes();
        when(proxyService.proxy(eq("campaign-api"), any(), eq(HttpMethod.GET), any(), any()))
                .thenReturn(ResponseEntity.ok(responseBody));

        mockMvc.perform(get("/gw/campaign-api/v1/api/campaigns"))
                .andExpect(status().isOk());
    }

    @Test
    void proxyPostRequestWithBody() throws Exception {
        byte[] responseBody = "{\"id\":\"123\"}".getBytes();
        when(proxyService.proxy(eq("campaign-api"), any(), eq(HttpMethod.POST), any(), any()))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(responseBody));

        mockMvc.perform(post("/gw/campaign-api/v1/api/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void proxyPutRequest() throws Exception {
        byte[] responseBody = "{\"id\":\"123\",\"name\":\"updated\"}".getBytes();
        when(proxyService.proxy(eq("campaign-api"), any(), eq(HttpMethod.PUT), any(), any()))
                .thenReturn(ResponseEntity.ok(responseBody));

        mockMvc.perform(put("/gw/campaign-api/v1/api/campaigns/123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"updated\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void proxyDeleteRequest() throws Exception {
        when(proxyService.proxy(eq("campaign-api"), any(), eq(HttpMethod.DELETE), any(), any()))
                .thenReturn(ResponseEntity.noContent().build());

        mockMvc.perform(delete("/gw/campaign-api/v1/api/campaigns/123"))
                .andExpect(status().isNoContent());
    }

    @Test
    void proxyReturnsNotFoundForUnknownService() throws Exception {
        when(proxyService.proxy(eq("unknown-service"), any(), eq(HttpMethod.GET), any(), any()))
                .thenReturn(ResponseEntity.notFound().build());

        mockMvc.perform(get("/gw/unknown-service/v1/api/data"))
                .andExpect(status().isNotFound());
    }

    @Test
    void proxyReturnsBadGatewayWhenDownstreamUnreachable() throws Exception {
        when(proxyService.proxy(eq("campaign-api"), any(), eq(HttpMethod.GET), any(), any()))
                .thenReturn(ResponseEntity.status(HttpStatus.BAD_GATEWAY).build());

        mockMvc.perform(get("/gw/campaign-api/v1/api/campaigns"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void proxyPreservesQueryParameters() throws Exception {
        byte[] responseBody = "[]".getBytes();
        when(proxyService.proxy(eq("campaign-api"), any(), eq(HttpMethod.GET), any(), any()))
                .thenReturn(ResponseEntity.ok(responseBody));

        mockMvc.perform(get("/gw/campaign-api/v1/api/campaigns?page=0&size=20"))
                .andExpect(status().isOk());
    }
}
