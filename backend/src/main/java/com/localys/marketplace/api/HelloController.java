package com.localys.marketplace.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HelloController {

  @GetMapping("/public/ping")
  public String ping() {
    return "pong";
  }

  @GetMapping("/hello")
  public String hello() {
    return "Hello local mode (no JWT)";
  }

  @GetMapping("/admin/hello")
  public String adminHello() {
    return "Hello admin local mode (no JWT)";
  }

  @GetMapping("/env")
  public String env() {
    return "ACTIVE PROFILE = " + System.getProperty("spring.profiles.active");
  }
}
