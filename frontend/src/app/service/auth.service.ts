import { Injectable } from '@angular/core';
import { from, Observable } from 'rxjs';
import { keycloak } from '../keycloak.service';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  username?: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  constructor() {}

  /** GiriY */
  login(): Observable<void> {
    return from(keycloak.login());
  }

  /** KayŽñt */
  register(): Observable<void> {
    return from(keycloak.register());
  }

  /** AØŽñkŽñY */
  logout(): void {
    keycloak.logout({ redirectUri: window.location.origin });
  }

  /** Token getter */
  get token(): string | null {
    return keycloak.token ?? null;
  }

  /** KullanŽñcŽñ adŽñ getter */
  get username(): string | null {
    return keycloak.tokenParsed?.['preferred_username'] ?? null;
  }

  /** Auth durumu */
  get isAuthenticated(): boolean {
    return !!keycloak.authenticated;
  }
}
