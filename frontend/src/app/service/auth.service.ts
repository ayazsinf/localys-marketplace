import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { tap } from 'rxjs';

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

  private readonly apiUrl = environment.apiUrl;
  private readonly TOKEN_KEY = 'token';
  private readonly USERNAME_KEY = 'username';

  constructor(private http: HttpClient) {}

  /** Giriş */
  login(username: string, password: string) {
    const body: LoginRequest = { username, password };

    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/login`, body)
      .pipe(
        tap(res => {
          // backend sadece token döndürüyorsa username'i body'den set edebiliriz
          this.setSession(res.token, res.username ?? username);
        })
      );
  }

  /** Kayıt */
  register(request: RegisterRequest) {
    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/register`, request)
      .pipe(
        tap(res => {
          // İstersen register sonrası direkt login yapma,
          // şimdilik user'ı otomatik login edelim:
          this.setSession(res.token, res.username ?? request.username);
        })
      );
  }

  /** Çıkış */
  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USERNAME_KEY);
  }

  /** Token getter */
  get token(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  /** Kullanıcı adı getter */
  get username(): string | null {
    return localStorage.getItem(this.USERNAME_KEY);
  }

  /** Auth durumu */
  get isAuthenticated(): boolean {
    return !!this.token;
  }

  /** Ortak session set metodu */
  private setSession(token: string, username: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
    localStorage.setItem(this.USERNAME_KEY, username);
  }
}
