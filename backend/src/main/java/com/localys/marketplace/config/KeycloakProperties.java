package com.localys.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.keycloak")
public class KeycloakProperties {
    private String issuerUri = "http://localhost:8081/realms/localys-realm";
    private String clientId = "localys-frontend";
    private String adminClientId = "localys-backend";
    private String adminClientSecret = "localys-backend-secret";

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getAdminClientId() {
        return adminClientId;
    }

    public void setAdminClientId(String adminClientId) {
        this.adminClientId = adminClientId;
    }

    public String getAdminClientSecret() {
        return adminClientSecret;
    }

    public void setAdminClientSecret(String adminClientSecret) {
        this.adminClientSecret = adminClientSecret;
    }

    public String tokenUri() {
        return issuerUri.replaceAll("/+$", "") + "/protocol/openid-connect/token";
    }

    public String jwkSetUri() {
        return issuerUri.replaceAll("/+$", "") + "/protocol/openid-connect/certs";
    }

    public String realmName() {
        String normalized = issuerUri.replaceAll("/+$", "");
        int marker = normalized.lastIndexOf("/realms/");
        return marker >= 0 ? normalized.substring(marker + "/realms/".length()) : "localys-realm";
    }

    public String serverUrl() {
        String normalized = issuerUri.replaceAll("/+$", "");
        int marker = normalized.lastIndexOf("/realms/");
        return marker >= 0 ? normalized.substring(0, marker) : normalized;
    }
}
