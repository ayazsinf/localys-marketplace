package com.example.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class Phase1Application {
  public static void main(String[] args) {
    SpringApplication.run(Phase1Application.class, args);
    PasswordEncoder encoder = new BCryptPasswordEncoder();

    String rawPassword = "usertest"; // BURAYA İSTEDİĞİN YENİ ŞİFREYİ YAZ
    String encoded = encoder.encode(rawPassword);

    System.out.println("Raw     : " + rawPassword);
    System.out.println("Encoded : " + encoded);
  }

}
