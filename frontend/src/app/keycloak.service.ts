import Keycloak from 'keycloak-js';

export const keycloak = new Keycloak({
  url: 'http://localhost:8081/',
  realm: 'localys-realm',
  clientId: 'localys-frontend'
});

export async function initKeycloak(): Promise<void> {
  await keycloak.init({
    onLoad: 'check-sso',   // **Burada login-required YASAK!**
    redirectUri: window.location.origin,
    silentCheckSsoRedirectUri: window.location.origin + '/assets/silent-check-sso.html',
    pkceMethod: 'S256'
  });
}

export function hasRole(role: string): boolean {
  const roles = keycloak.realmAccess?.roles || [];
  return roles.includes(role);
}
