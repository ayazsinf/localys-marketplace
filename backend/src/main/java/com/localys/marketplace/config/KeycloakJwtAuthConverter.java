package com.localys.marketplace.config;

import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.CustomUserDetailsService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

public class KeycloakJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final CustomUserDetailsService userDetailsService;

    public KeycloakJwtAuthConverter(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {

        CustomUserDetails userDetails = this.userDetailsService.loadOrCreateFromJwt(jwt);

        return new UsernamePasswordAuthenticationToken(
                userDetails,
                jwt,
                userDetails.getAuthorities()
        );
    }

}
