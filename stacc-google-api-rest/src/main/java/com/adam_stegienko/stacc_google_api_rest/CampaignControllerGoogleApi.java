package com.adam_stegienko.stacc_google_api_rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.adam_stegienko.stacc_google_api_rest"})
@EnableScheduling
public class CampaignControllerGoogleApi {

  public static void main(String[] args) {
    SpringApplication.run(CampaignControllerGoogleApi.class, args);
  }

}
