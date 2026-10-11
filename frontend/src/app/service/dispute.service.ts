import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type DisputeStatus = 'OPEN' | 'CONTESTED' | 'WITHDRAWN' | 'REFUNDED' | 'RELEASED';
export const DISPUTE_REASONS = ['NOT_RECEIVED', 'NOT_AS_DESCRIBED', 'DAMAGED', 'OTHER'] as const;
export type DisputeReason = typeof DISPUTE_REASONS[number];
export const ACTIVE_DISPUTE: DisputeStatus[] = ['OPEN', 'CONTESTED'];

export interface Dispute {
  id: number;
  orderId: number;
  orderItemId: number;
  productId: number;
  productName: string;
  amount: number;
  currency: string;
  reason: DisputeReason;
  description: string;
  status: DisputeStatus;
  sellerResponse: string | null;
  resolutionNote: string | null;
  buyerName: string | null;
  sellerName: string | null;
  createdAt: string;
  resolvedAt: string | null;
}

/** Buyer protection disputes. */
@Injectable({ providedIn: 'root' })
export class DisputeService {
  private readonly baseUrl = `${environment.apiUrl}/disputes`;

  constructor(private http: HttpClient) {}

  open(orderId: number, orderItemId: number, reason: DisputeReason, description: string): Observable<Dispute> {
    return this.http.post<Dispute>(`${environment.apiUrl}/orders/${orderId}/items/${orderItemId}/dispute`, { reason, description });
  }

  list(role: 'buyer' | 'seller'): Observable<Dispute[]> {
    return this.http.get<Dispute[]>(this.baseUrl, { params: { role } });
  }

  withdraw(id: number): Observable<Dispute> {
    return this.http.post<Dispute>(`${this.baseUrl}/${id}/withdraw`, {});
  }

  refund(id: number): Observable<Dispute> {
    return this.http.post<Dispute>(`${this.baseUrl}/${id}/refund`, {});
  }

  contest(id: number, response: string): Observable<Dispute> {
    return this.http.post<Dispute>(`${this.baseUrl}/${id}/contest`, { response });
  }

  adminList(status: 'ACTIVE' | 'ALL'): Observable<Dispute[]> {
    return this.http.get<Dispute[]>(`${environment.apiUrl}/admin/disputes`, { params: { status } });
  }

  resolve(id: number, outcome: 'REFUND' | 'RELEASE', note: string): Observable<Dispute> {
    return this.http.post<Dispute>(`${environment.apiUrl}/admin/disputes/${id}/resolve`, { outcome, note });
  }
}
