package com.adam_stegienko.stacc_scheduler.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfiguration {

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.queue}")
    private String queue;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    @Value("${rabbitmq.dlx}")
    private String dlx;

    @Value("${rabbitmq.error-queue}")
    private String errorQueue;

    @Value("${rabbitmq.error-routing-key}")
    private String errorRoutingKey;

    // ── Main exchange / queue ────────────────────────────────────────────────

    @Bean
    public DirectExchange campaignExchange() {
        return ExchangeBuilder.directExchange(exchange).durable(true).build();
    }

    /**
     * Main execution queue. Dead-letters are automatically forwarded to
     * {@code campaignDlx} when a consumer NACKs or the message TTL expires.
     */
    @Bean
    public Queue campaignExecutionQueue() {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    public Binding campaignBinding(Queue campaignExecutionQueue, DirectExchange campaignExchange) {
        return BindingBuilder.bind(campaignExecutionQueue).to(campaignExchange).with(routingKey);
    }

    // ── Dead-letter exchange / error queue ───────────────────────────────────

    @Bean
    public DirectExchange campaignDlx() {
        return ExchangeBuilder.directExchange(dlx).durable(true).build();
    }

    @Bean
    public Queue campaignErrorQueue() {
        return QueueBuilder.durable(errorQueue).build();
    }

    @Bean
    public Binding campaignErrorBinding(Queue campaignErrorQueue, DirectExchange campaignDlx) {
        return BindingBuilder.bind(campaignErrorQueue).to(campaignDlx).with(errorRoutingKey);
    }

    // ── Message converter ────────────────────────────────────────────────────

    /**
     * Configures JSON serialisation for all RabbitMQ messages.
     * Spring Boot's RabbitAutoConfiguration picks this bean up automatically
     * and applies it to the auto-configured RabbitTemplate.
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
