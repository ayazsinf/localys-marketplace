package com.localys.marketplace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class CorsConfig {

    // @Bean
    // public CorsConfigurationSource corsConfigurationSource() {
    //     CorsConfiguration config = new CorsConfiguration();
    //     config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
    //     config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    //     config.setAllowedHeaders(List.of("*"));
    //     config.setAllowCredentials(true);
    //     config.setExposedHeaders(List.of("Authorization", "Content-Type"));

    //     UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    //     source.registerCorsConfiguration("/**", config);
    //     return source;
    // }

      @Bean
  public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "http://localhost:4300",
                        "http://localhost:4200",
                        "https://localys.shop",
                        "https://www.localys.shop",
                        "https://app.localys.shop"
                )
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
      }
    };
  }
}
