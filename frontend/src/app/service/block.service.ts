import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';

export interface BlockedUser {
  userId: number;
  displayName: string | null;
  blockedAt: string;
}

/** Blocking stops messages and offers between two users, in both directions. */
@Injectable({ providedIn: 'root' })
export class BlockService {
  private readonly baseUrl = `${environment.apiUrl}/blocks`;

  constructor(private http: HttpClient) {}

  list(): Observable<BlockedUser[]> {
    return this.http.get<BlockedUser[]>(this.baseUrl);
  }

  /** Whether the current user blocked this user. */
  isBlocked(userId: number): Observable<boolean> {
    return this.http.get<{ blocked: boolean }>(`${this.baseUrl}/${userId}`).pipe(map(result => result.blocked));
  }

  block(userId: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${userId}`, {});
  }

  unblock(userId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${userId}`);
  }
}
