package com.localys.marketplace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class LocalysApplication {
  public static void main(String[] args) {
      SpringApplication.run(LocalysApplication.class, args);
  }

}
