import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';

export type WalletEntryState = 'PENDING' | 'DISPUTED' | 'AVAILABLE' | 'PAID_OUT' | 'REFUNDED';

export interface WalletEntry {
  orderId: number;
  orderItemId: number;
  productName: string;
  lineTotal: number;
  commission: number;
  payout: number;
  currency: string;
  state: WalletEntryState;
  soldAt: string;
  paidOutAt: string | null;
}

export interface Wallet {
  accountConnected: boolean;
  payoutsEnabled: boolean;
  /** e.g. 0.05 for 5%. */
  commissionRate: number;
  currency: string;
  /** Held under buyer protection. */
  pending: number;
  /** Released, waiting to be paid out. */
  available: number;
  paidOut: number;
  entries: WalletEntry[];
}

@Injectable({ providedIn: 'root' })
export class WalletService {
  private readonly baseUrl = `${environment.apiUrl}/wallet`;

  constructor(private http: HttpClient) {}

  get(): Observable<Wallet> {
    return this.http.get<Wallet>(this.baseUrl);
  }

  /** The Stripe page where the seller sets up payouts. */
  onboardingUrl(): Observable<string> {
    return this.http.post<{ url: string }>(`${this.baseUrl}/onboarding`, {}).pipe(map(result => result.url));
  }
}
