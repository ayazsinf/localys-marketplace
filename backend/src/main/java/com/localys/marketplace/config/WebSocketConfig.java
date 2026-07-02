package com.localys.marketplace.config;

import com.localys.marketplace.service.KeycloakPrincipalService;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final KeycloakPrincipalService keycloakPrincipalService;

    public WebSocketConfig(KeycloakPrincipalService keycloakPrincipalService) {
        this.keycloakPrincipalService = keycloakPrincipalService;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:4200", "http://localhost:4300")
                .setHandshakeHandler(new JwtHandshakeHandler())
                .addInterceptors(new JwtHandshakeInterceptor(keycloakPrincipalService))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }
}
