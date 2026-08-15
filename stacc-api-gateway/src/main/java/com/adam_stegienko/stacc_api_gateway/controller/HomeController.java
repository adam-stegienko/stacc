package com.adam_stegienko.stacc_api_gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

  @GetMapping("/")
  public String campaignControllerMessage() {
    return "ST Automated Campaign Controller Api Gateway";
  }
}
