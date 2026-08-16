package com.adam_stegienko.stacc_consumer.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.adam_stegienko.stacc_consumer.dto.CampaignActionMessage;
import com.adam_stegienko.stacc_consumer.services.GoogleApiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class CampaignMessageListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CampaignMessageListener.class);

    private static final String STATUS_PAUSED = "PAUSED";
    private static final String STATUS_ENABLED = "ENABLED";

    private final GoogleApiService googleApiService;
    private final ObjectMapper objectMapper;

    public CampaignMessageListener(GoogleApiService googleApiService, ObjectMapper objectMapper) {
        this.googleApiService = googleApiService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${rabbitmq.queue}")
    public void onMessage(String messageJson) {
        CampaignActionMessage message;
        try {
            message = objectMapper.readValue(messageJson, CampaignActionMessage.class);
        } catch (JsonProcessingException e) {
        LOGGER.error("Rejecting malformed message: {}", messageJson, e);
        throw new AmqpRejectAndDontRequeueException("Malformed campaign action message", e);
        }

        LOGGER.info("Received campaign action: id={}, campaign={}, action={}",
                message.getId(), message.getCampaign(), message.getAction());

        String status = message.getAction() == 0 ? STATUS_PAUSED : STATUS_ENABLED;
        googleApiService.updateCampaignStatus(message.getCampaign(), status);
    }
}
