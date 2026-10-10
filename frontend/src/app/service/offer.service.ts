import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';

/** Offers below this share of the asking price are refused. */
export const MIN_OFFER_RATIO = 0.75;

export type OfferStatus = 'PENDING' | 'COUNTERED' | 'ACCEPTED' | 'DECLINED' | 'WITHDRAWN' | 'EXPIRED' | 'PURCHASED';

export interface Offer {
  id: number;
  productId: number;
  productName: string;
  productImageUrl: string | null;
  listingPrice: number;
  /** Latest proposal: the buyer's offer, or the seller's counter offer. */
  amount: number;
  currency: string;
  status: OfferStatus;
  /** Until when an accepted offer can be bought. */
  expiresAt: string | null;
  otherUserId: number;
  otherUserName: string | null;
  /** The viewer is the one who has to answer. */
  awaitingMe: boolean;
  updatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class OfferService {
  private readonly baseUrl = `${environment.apiUrl}/offers`;

  constructor(private http: HttpClient) {}

  list(role: 'buyer' | 'seller'): Observable<Offer[]> {
    return this.http.get<Offer[]>(this.baseUrl, { params: { role } });
  }

  /** The current user's open offer on a listing, or null. */
  mine(productId: number): Observable<Offer | null> {
    return this.http.get<Offer>(`${this.baseUrl}/product/${productId}`, { observe: 'response' })
      .pipe(map(response => response.status === 204 ? null : response.body));
  }

  make(productId: number, amount: number): Observable<Offer> {
    return this.http.post<Offer>(this.baseUrl, { productId, amount });
  }

  accept(id: number): Observable<Offer> {
    return this.http.post<Offer>(`${this.baseUrl}/${id}/accept`, {});
  }

  decline(id: number): Observable<Offer> {
    return this.http.post<Offer>(`${this.baseUrl}/${id}/decline`, {});
  }

  counter(id: number, amount: number): Observable<Offer> {
    return this.http.post<Offer>(`${this.baseUrl}/${id}/counter`, { amount });
  }

  withdraw(id: number): Observable<Offer> {
    return this.http.post<Offer>(`${this.baseUrl}/${id}/withdraw`, {});
  }

  /** Puts the listing in the cart at the accepted price. */
  addToCart(id: number): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/${id}/add-to-cart`, {});
  }
}
