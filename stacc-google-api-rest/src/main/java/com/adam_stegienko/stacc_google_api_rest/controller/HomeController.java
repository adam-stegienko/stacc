package com.adam_stegienko.stacc_google_api_rest.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

  @GetMapping("/")
  public String staccGoogleApiRestMessage() {
    return "ST Automated Campaign Controller Google Api Rest is running!";
  }
}
