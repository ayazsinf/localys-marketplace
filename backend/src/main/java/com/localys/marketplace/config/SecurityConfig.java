package com.localys.marketplace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@Order(1)
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtFilter jwtFilter;

  public SecurityConfig(JwtFilter jwtFilter) {
    this.jwtFilter = jwtFilter;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Missing/expired token -> 401 so the SPA refreshes its session; 403 stays for real permission denials.
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
              .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
              .requestMatchers("/", "/index.html", "/favicon.ico", "/error",
                  "/assets/**", "/*.js", "/*.css", "/*.png", "/*.svg").permitAll()
              .requestMatchers("/auth/**").permitAll()
              .requestMatchers("/ws/**").permitAll()
              .requestMatchers("/uploads/**").permitAll()
              .requestMatchers("/api/uploads/**").permitAll()
              .requestMatchers("/api/webhooks/**").permitAll()
              .requestMatchers("/api/categories/**").permitAll()
              .requestMatchers("/api/products/**").permitAll()
              .requestMatchers(HttpMethod.GET, "/api/users/*/reviews", "/api/users/*/public").permitAll()
              .requestMatchers("/api/admin/**").hasRole("ADMIN")
              .requestMatchers("/api/vendor/**").hasRole("USER")
              .requestMatchers("/api/**").authenticated()
              .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
