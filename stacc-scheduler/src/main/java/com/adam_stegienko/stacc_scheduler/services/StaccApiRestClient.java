package com.adam_stegienko.stacc_scheduler.services;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.adam_stegienko.stacc_scheduler.dto.PlannerBookDto;

/**
 * HTTP client for the cc-api-rest service.
 * Reads plannerbooks and deletes them after successful dispatch.
 */
@Component
public class StaccApiRestClient {

    private static final Logger log = LoggerFactory.getLogger(StaccApiRestClient.class);

    private final RestClient restClient;

    public StaccApiRestClient(RestClient.Builder builder,
                           @Value("${cc-api-rest.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public List<PlannerBookDto> getPlannerBooks() {
        List<PlannerBookDto> result = restClient.get()
                .uri("/v1/api/plannerbooks")
                .retrieve()
                .body(new ParameterizedTypeReference<List<PlannerBookDto>>() {});
        return result != null ? result : List.of();
    }

    public void deletePlannerBook(UUID id) {
        restClient.delete()
                .uri("/v1/api/plannerbooks/{id}", id)
                .retrieve()
                .toBodilessEntity();
    }
}
