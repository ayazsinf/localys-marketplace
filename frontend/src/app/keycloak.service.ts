import Keycloak from 'keycloak-js';
import { environment } from '../environments/environment';

export const keycloak = new Keycloak({
  url: environment.keycloak.url,
  realm: environment.keycloak.realm,
  clientId: environment.keycloak.clientId
});

export async function initKeycloak(): Promise<void> {
  try {
    await keycloak.init({
      onLoad: 'check-sso',   // **Burada login-required YASAK!**
      redirectUri: window.location.origin,
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
