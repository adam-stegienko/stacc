package com.adam_stegienko.stacc_api_gateway.services;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.adam_stegienko.stacc_api_gateway.config.GatewayRoutesProperties;

@ExtendWith(MockitoExtension.class)
class ProxyServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private GatewayRoutesProperties routesProperties;
    private ProxyService proxyService;

    @BeforeEach
    void setUp() {
        routesProperties = new GatewayRoutesProperties();
        routesProperties.setRoutes(Map.of("campaign-api", "http://campaign-api-service:8081"));
        proxyService = new ProxyService(restTemplate, routesProperties);
    }

    @Test
    void proxyForwardsRequestToCorrectDownstreamUri() {
        byte[] expectedBody = "{\"data\":[]}".getBytes();
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(expectedBody));

        ResponseEntity<byte[]> response = proxyService.proxy(
                "campaign-api", "/v1/api/campaigns",
                HttpMethod.GET, new HttpHeaders(), new byte[0]);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedBody);
    }

    @Test
    void proxyBuildsCorrectDownstreamUrl() {
        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        when(restTemplate.exchange(uriCaptor.capture(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(new byte[0]));

        proxyService.proxy("campaign-api", "/v1/api/campaigns",
                HttpMethod.GET, new HttpHeaders(), new byte[0]);

        assertThat(uriCaptor.getValue().toString())
                .isEqualTo("http://campaign-api-service:8081/v1/api/campaigns");
    }

    @Test
    void proxyReturnsNotFoundForUnknownService() {
        ResponseEntity<byte[]> response = proxyService.proxy(
                "unknown-service", "/v1/api/data",
                HttpMethod.GET, new HttpHeaders(), new byte[0]);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void proxyReturnsBadGatewayWhenDownstreamUnreachable() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        ResponseEntity<byte[]> response = proxyService.proxy(
                "campaign-api", "/v1/api/campaigns",
                HttpMethod.GET, new HttpHeaders(), new byte[0]);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void proxyForwardsDownstreamErrorStatus() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        ResponseEntity<byte[]> response = proxyService.proxy(
                "campaign-api", "/v1/api/campaigns",
                HttpMethod.GET, new HttpHeaders(), new byte[0]);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void proxyStripsHopByHopHeadersBeforeForwarding() {
        ArgumentCaptor<HttpEntity<byte[]>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), requestCaptor.capture(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(new byte[0]));

        HttpHeaders incomingHeaders = new HttpHeaders();
        incomingHeaders.add("host", "gateway.example.com");
        incomingHeaders.add("content-length", "42");
        incomingHeaders.add("X-Custom-Header", "my-value");

        proxyService.proxy("campaign-api", "/v1/api/campaigns",
                HttpMethod.GET, incomingHeaders, new byte[0]);

        HttpHeaders forwarded = requestCaptor.getValue().getHeaders();
        assertThat(forwarded.containsKey("host")).isFalse();
        assertThat(forwarded.containsKey("content-length")).isFalse();
        assertThat(forwarded.get("X-Custom-Header")).containsExactly("my-value");
    }

    @Test
    void proxyIsCaseInsensitiveForServiceId() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(new byte[0]));

        ResponseEntity<byte[]> response = proxyService.proxy(
                "CAMPAIGN-API", "/v1/api/campaigns",
                HttpMethod.GET, new HttpHeaders(), new byte[0]);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void proxyAppliesConfiguredResponseHeaderFilters() {
        routesProperties.setRoutes(Map.of(
                "campaign-api",
                Map.of(
                        "uri", "http://campaign-api-service:8081",
                        "filters", List.of(
                                "DeduplicateResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials Vary RETAIN_LAST",
                                "RemoveResponseHeader=Transfer-Encoding"
                        )
                )
        ));

        HttpHeaders downstreamHeaders = new HttpHeaders();
        downstreamHeaders.add("Access-Control-Allow-Origin", "https://old.example");
        downstreamHeaders.add("Access-Control-Allow-Origin", "https://new.example");
        downstreamHeaders.add("Vary", "Origin");
        downstreamHeaders.add("Vary", "Access-Control-Request-Method");
        downstreamHeaders.add("Transfer-Encoding", "chunked");

        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok().headers(downstreamHeaders).body(new byte[0]));

        ResponseEntity<byte[]> response = proxyService.proxy(
                "campaign-api", "/v1/api/campaigns",
                HttpMethod.GET, new HttpHeaders(), new byte[0]);

        assertThat(response.getHeaders().get("Access-Control-Allow-Origin"))
                .containsExactly("https://new.example");
        assertThat(response.getHeaders().get("Vary"))
                .containsExactly("Access-Control-Request-Method");
        assertThat(response.getHeaders().containsKey("Transfer-Encoding")).isFalse();
    }
}
