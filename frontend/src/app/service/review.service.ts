import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from './product.service';

export interface Review {
  id: number;
  rating: number;
  comment: string | null;
  reviewerName: string | null;
  productName: string;
  createdAt: string;
}

export interface VendorReviews {
  averageRating: number;
  reviewCount: number;
  reviews: PageResponse<Review>;
}

@Injectable({ providedIn: 'root' })
export class ReviewService {
  constructor(private http: HttpClient) {}

  /** Buyer: rate the seller of a delivered order item (once per item). */
  createReview(orderItemId: number, rating: number, comment: string): Observable<Review> {
    return this.http.post<Review>('/api/reviews', { orderItemId, rating, comment });
  }

  getVendorReviews(vendorId: number, page = 0, size = 5): Observable<VendorReviews> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<VendorReviews>(`/api/vendors/${vendorId}/reviews`, { params });
  }
}
