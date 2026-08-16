package com.adam_stegienko.stacc_consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StaccConsumer {

  public static void main(String[] args) {
    SpringApplication.run(StaccConsumer.class, args);
  }

}
