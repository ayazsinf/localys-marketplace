import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AdminListing {
  id: number;
  name: string;
  description: string | null;
  price: number;
  country: string;
  currency: string;
  stockQty: number;
  active: boolean;
  removalReason: string | null;
  removalNote: string | null;
  removedAt: string | null;
  expiresAt: string;
  moderationStatus: 'PENDING' | 'APPROVED' | 'REJECTED';
  moderationReason: string | null;
  sku: string;
  brand: string | null;
  imageUrls: string[];
  categoryId: number | null;
  categoryName: string | null;
  vendorId: number;
  vendorUserId: number;
  vendorDisplayName: string | null;
  createdAt: string;
  updatedAt: string;
  reviewedAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class AdminListingService {
  constructor(private http: HttpClient) {}

  list(status = 'PENDING'): Observable<AdminListing[]> {
    return this.http.get<AdminListing[]>(`${environment.apiUrl}/admin/listings`, {
      params: { status }
    });
  }

  approve(id: number): Observable<AdminListing> {
    return this.http.post<AdminListing>(`${environment.apiUrl}/admin/listings/${id}/approve`, {});
  }

  reject(id: number, reason: string): Observable<AdminListing> {
    return this.http.post<AdminListing>(`${environment.apiUrl}/admin/listings/${id}/reject`, { reason });
  }

  delete(id: number, note?: string | null): Observable<AdminListing> {
    return this.http.delete<AdminListing>(`${environment.apiUrl}/admin/listings/${id}`, {
      body: { note: note ?? null }
    });
  }
}
