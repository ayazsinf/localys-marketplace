package com.localys.marketplace.config;

import com.localys.marketplace.repository.ConversationParticipantRepository;
import com.localys.marketplace.service.KeycloakPrincipalService;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final KeycloakPrincipalService keycloakPrincipalService;
    private final ConversationParticipantRepository participantRepository;

    public WebSocketConfig(
            KeycloakPrincipalService keycloakPrincipalService,
            ConversationParticipantRepository participantRepository
    ) {
        this.keycloakPrincipalService = keycloakPrincipalService;
        this.participantRepository = participantRepository;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new StompSubscriptionInterceptor(participantRepository));
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
