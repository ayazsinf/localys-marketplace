import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export const REPORT_REASONS = ['SCAM', 'PROHIBITED_ITEM', 'MISLEADING', 'OFFENSIVE', 'WRONG_CATEGORY', 'OTHER'] as const;
export type ReportReason = typeof REPORT_REASONS[number];
export type ReportStatus = 'OPEN' | 'DISMISSED' | 'ACTIONED';

export interface AdminReport {
  id: number;
  productId: number;
  productName: string;
  productActive: boolean;
  reason: ReportReason;
  details: string | null;
  reporterName: string | null;
  status: ReportStatus;
  createdAt: string;
  resolvedAt: string | null;
  openReportsForListing: number;
}

@Injectable({ providedIn: 'root' })
export class ReportService {
  constructor(private http: HttpClient) {}

  /** Signed-in users flag a listing; details are required for OTHER. */
  reportListing(productId: number, reason: ReportReason, details: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/reports`, { productId, reason, details });
  }

  list(status: ReportStatus | 'ALL' = 'OPEN'): Observable<AdminReport[]> {
    return this.http.get<AdminReport[]>(`${environment.apiUrl}/admin/reports`, { params: { status } });
  }

  dismiss(reportId: number): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/admin/reports/${reportId}/dismiss`, {});
  }

  /** Removes the listing and closes every open report about it. */
  removeListing(reportId: number, note?: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/admin/reports/${reportId}/remove-listing`, { note: note ?? null });
  }
}
