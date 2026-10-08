import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, catchError, finalize, map, of, shareReplay, tap, timeout } from 'rxjs';
import { environment } from '../../environments/environment';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  username?: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

interface SessionUser {
  username: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly authBaseUrl = resolveAuthBaseUrl();
  private readonly authenticatedSubject = new BehaviorSubject<boolean>(false);
  private readonly usernameSubject = new BehaviorSubject<string | null>(null);

  readonly authState$ = this.authenticatedSubject.asObservable();
  private refreshInFlight: Observable<boolean> | null = null;

  constructor(private readonly http: HttpClient) {}

  initialize(): Observable<boolean> {
    return this.refreshSession();
  }

  login(payload: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.authBaseUrl}/login`, payload).pipe(
      timeout(12000),
      tap(response => this.setAuthenticated(response.username ?? payload.username))
    );
  }

  register(payload: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.authBaseUrl}/register`, payload).pipe(
      timeout(12000),
      tap(response => this.setAuthenticated(response.username ?? payload.username))
    );
  }

  logout(): void {
    this.clearSession();
    this.http.post<void>(`${this.authBaseUrl}/logout`, {}).subscribe({
      error: () => {}
    });
  }

  /** Concurrent 401s share one refresh call instead of racing each other with the same refresh token. */
  refreshAccess(): Observable<boolean> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http.post<void>(`${this.authBaseUrl}/refresh`, {}).pipe(
        map(() => true),
        finalize(() => {
          this.refreshInFlight = null;
        }),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.refreshInFlight;
  }

  clearSession(): void {
    this.authenticatedSubject.next(false);
    this.usernameSubject.next(null);
  }

  refreshSession(): Observable<boolean> {
    return this.http.get<SessionUser>(`${environment.apiUrl}/users/me`).pipe(
      tap(user => this.setAuthenticated(user.username)),
      map(() => true),
      catchError(() => {
        this.clearSession();
        return of(false);
      })
    );
  }

  get username(): string | null {
    return this.usernameSubject.value;
  }

  get isAuthenticated(): boolean {
    return this.authenticatedSubject.value;
  }

  private setAuthenticated(username: string | null | undefined): void {
    this.usernameSubject.next(username ?? null);
    this.authenticatedSubject.next(true);
  }
}

function resolveAuthBaseUrl(): string {
  if (environment.apiUrl.endsWith('/api')) {
    return `${environment.apiUrl.slice(0, -4)}/auth`;
  }
  return `${environment.apiUrl}/auth`;
}
