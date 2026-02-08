package com.localys.marketplace.config;

import com.localys.marketplace.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Order(1)
@EnableMethodSecurity
public class SecurityConfig {

  private final CustomUserDetailsService userDetailsService;

  public SecurityConfig(CustomUserDetailsService userDetailsService) {
    this.userDetailsService = userDetailsService;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
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
              .requestMatchers("/api/vendor/**").hasRole("VENDOR")
              .requestMatchers("/api/**").authenticated()
              .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(
                            new KeycloakJwtAuthConverter(userDetailsService)
                    ))
            );

    // http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
