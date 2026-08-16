package com.adam_stegienko.stacc_scheduler.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

  @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
  public String staccSchedulerMessage() {
    return "ST Automated Campaign Controller Scheduler is running!";
  }
}
