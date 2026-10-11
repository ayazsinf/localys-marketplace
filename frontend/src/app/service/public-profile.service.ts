import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Rating } from './review.service';

export interface PublicProfile {
  id: number;
  displayName: string | null;
  memberSince: string;
  location: string | null;
  activeListingCount: number;
  rating: Rating;
  ratingAsSeller: Rating;
  ratingAsBuyer: Rating;
  /** 0-100, null when there are too few conversations to tell. */
  responseRate: number | null;
  responseTime: 'WITHIN_HOUR' | 'WITHIN_DAY' | 'WITHIN_DAYS' | null;
  away: boolean;
  awayMessage: string | null;
}

@Injectable({ providedIn: 'root' })
export class PublicProfileService {
  constructor(private http: HttpClient) {}

  get(userId: number): Observable<PublicProfile> {
    return this.http.get<PublicProfile>(`/api/users/${userId}/public`);
  }
}
