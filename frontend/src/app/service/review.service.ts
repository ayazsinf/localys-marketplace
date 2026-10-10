import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from './product.service';

export type ReviewRole = 'SELLER' | 'BUYER';

export interface Review {
  id: number;
  rating: number;
  comment: string | null;
  reviewerId: number;
  reviewerName: string | null;
  /** SELLER when written by the buyer, BUYER when written by the seller. */
  revieweeRole: ReviewRole;
  productName: string;
  createdAt: string;
}

export interface Rating {
  average: number;
  count: number;
}

export interface UserReviews {
  overall: Rating;
  asSeller: Rating;
  asBuyer: Rating;
  reviews: PageResponse<Review>;
}

@Injectable({ providedIn: 'root' })
export class ReviewService {
  constructor(private http: HttpClient) {}

  /** Buyer rates the seller, or seller rates the buyer, of a delivered item (once each). */
  createReview(orderItemId: number, rating: number, comment: string): Observable<Review> {
    return this.http.post<Review>('/api/reviews', { orderItemId, rating, comment });
  }

  getUserReviews(userId: number, role: ReviewRole | null, page = 0, size = 10): Observable<UserReviews> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (role) {
      params = params.set('role', role);
    }
    return this.http.get<UserReviews>(`/api/users/${userId}/reviews`, { params });
  }
}
