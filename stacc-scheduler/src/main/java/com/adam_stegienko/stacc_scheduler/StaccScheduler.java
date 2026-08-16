package com.adam_stegienko.stacc_scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StaccScheduler {

  public static void main(String[] args) {
    SpringApplication.run(StaccScheduler.class, args);
  }

}
