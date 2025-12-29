import Keycloak from 'keycloak-js';

export const keycloak = new Keycloak({
  url: 'http://localhost:8081/',
  realm: 'localys-realm',
  clientId: 'localys-frontend'
});

export async function initKeycloak(): Promise<void> {
  try {
    await keycloak.init({
      onLoad: 'check-sso',   // **Burada login-required YASAK!**
      redirectUri: window.location.origin,
      silentCheckSsoRedirectUri: window.location.origin + '/assets/silent-check-sso.html',
      pkceMethod: 'S256',
      checkLoginIframe: false
    });
  } catch (error) {
    console.error('Keycloak init failed, continuing without auth.', error);
  }
}

export function hasRole(role: string): boolean {
  const roles = keycloak.realmAccess?.roles || [];
  return roles.includes(role);
}
