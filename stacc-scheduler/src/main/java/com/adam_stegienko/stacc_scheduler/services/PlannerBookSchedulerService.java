package com.adam_stegienko.stacc_scheduler.services;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.adam_stegienko.stacc_scheduler.dto.CampaignStatusDto;
import com.adam_stegienko.stacc_scheduler.dto.PlannerBookDto;
import com.adam_stegienko.stacc_scheduler.dto.PlannerBookMessage;

/**
 * Polls cc-api-rest for plannerbooks whose {@code execution_date} has passed,
 * checks the Google Ads campaign status, publishes a message to RabbitMQ, then
 * deletes the plannerbook via cc-api-rest so it is not processed again.
 *
 * <p>Configurable properties:
 * <ul>
 *   <li>{@code scheduler.check-interval-ms} – poll interval (default 10 000 ms)</li>
 *   <li>{@code scheduler.initial-delay-ms}  – start-up delay  (default  5 000 ms)</li>
 *   <li>{@code cc-api-rest.base-url}        – cc-api-rest base URL</li>
 *   <li>{@code google-ads-api.base-url}    – Google Ads API base URL</li>
 *   <li>{@code google-ads-api.customer-id} – Google Ads customer ID</li>
 * </ul>
 */
@Service
public class PlannerBookSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(PlannerBookSchedulerService.class);

    private final StaccApiRestClient ccApiRestClient;
    private final StaccGoogleApiRestClient googleAdsApiClient;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    @Value("${rabbitmq.dlx}")
    private String dlx;

    @Value("${rabbitmq.error-routing-key}")
    private String errorRoutingKey;

    public PlannerBookSchedulerService(StaccApiRestClient ccApiRestClient,
                                       StaccGoogleApiRestClient googleAdsApiClient,
                                       RabbitTemplate rabbitTemplate) {
        this.ccApiRestClient = ccApiRestClient;
        this.googleAdsApiClient = googleAdsApiClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(
        fixedDelayString   = "${scheduler.check-interval-ms:10000}",
        initialDelayString = "${scheduler.initial-delay-ms:5000}"
    )
    public void checkAndDispatch() {
        List<PlannerBookDto> allBooks;
        try {
            allBooks = ccApiRestClient.getPlannerBooks();
        } catch (Exception e) {
            log.error("Failed to fetch planner books from cc-api-rest – will retry on next poll cycle", e);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        log.debug("Fetched {} planner book(s) from cc-api-rest", allBooks.size());
        allBooks.forEach(pb -> log.debug("  id={} campaign={} execution_date={} due={}",
                pb.getId(), pb.getCampaign(), pb.getExecutionDate(),
                pb.getExecutionDate() != null && !pb.getExecutionDate().isAfter(now)));

        List<PlannerBookDto> due = allBooks.stream()
                .filter(pb -> pb.getExecutionDate() != null && !pb.getExecutionDate().isAfter(now))
                .toList();

        if (due.isEmpty()) {
            return;
        }

        log.info("Found {} due planner book(s) to dispatch", due.size());

        for (PlannerBookDto pb : due) {
            // Campaign status is fetched best-effort; GoogleAdsApiClient never throws.
            List<CampaignStatusDto> statuses = googleAdsApiClient.getCampaignStatus(pb.getCampaign());
            PlannerBookMessage message = new PlannerBookMessage(
                    pb.getId(), pb.getCampaign(), pb.getAction(), pb.getExecutionDate(), statuses);

            try {
                rabbitTemplate.convertAndSend(exchange, routingKey, message);

                try {
                    ccApiRestClient.deletePlannerBook(pb.getId());
                    log.info("Dispatched and deleted planner book id={} campaign={}",
                            pb.getId(), pb.getCampaign());
                } catch (Exception e) {
                    log.error("Message sent but failed to delete planner book id={} – manual cleanup may be required",
                            pb.getId(), e);
                }
            } catch (Exception e) {
                log.error("Failed to dispatch planner book id={} – routing to error queue", pb.getId(), e);
                try {
                    rabbitTemplate.convertAndSend(dlx, errorRoutingKey, message);
                    log.warn("Planner book id={} routed to error queue", pb.getId());
                } catch (Exception dlxError) {
                    log.error("Also failed to publish planner book id={} to error queue", pb.getId(), dlxError);
                }
            }
        }
    }
}
