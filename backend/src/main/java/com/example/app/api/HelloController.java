package com.example.app.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

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
