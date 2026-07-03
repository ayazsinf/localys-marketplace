import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Listing {
  id: number;
  name: string;
  description: string | null;
  price: number;
  country: string;
  currency: string;
  stockQty: number;
  active: boolean;
  removalReason?: ListingRemovalReason | null;
  removalNote?: string | null;
  removedAt?: string | null;
  expiresAt: string;
  moderationStatus: 'PENDING' | 'APPROVED' | 'REJECTED';
  moderationReason?: string | null;
  sku: string;
  brand: string | null;
  imageUrls?: string[];
  categoryId?: number | null;
  categoryName?: string | null;
  parentCategoryId?: number | null;
  categoryPathIds?: number[];
  categoryPathNames?: string[];
  locationText?: string | null;
  latitude?: number | null;
  longitude?: number | null;
}

export type ListingRemovalReason = 'SOLD_ON_LOCALYS' | 'SOLD_ELSEWHERE' | 'NO_LONGER_AVAILABLE' | 'ADMIN_REMOVED' | 'OTHER';

export interface RemoveListingRequest {
  reason: ListingRemovalReason;
  note?: string | null;
}

export interface ListingRequest {
  name: string;
  description?: string | null;
  price: number;
  country?: string | null;
  currency?: string | null;
  stockQty?: number | null;
  active?: boolean | null;
  sku?: string | null;
  brand?: string | null;
  categoryId?: number | null;
  locationText?: string | null;
  latitude?: number | null;
  longitude?: number | null;
}

@Injectable({
  providedIn: 'root'
})
export class ListingService {
  constructor(private http: HttpClient) {}

  loadListings(): Observable<Listing[]> {
    return this.http.get<Listing[]>(`${environment.apiUrl}/listings`);
  }

  createListing(payload: ListingRequest): Observable<Listing> {
    return this.http.post<Listing>(`${environment.apiUrl}/listings`, payload);
  }

  updateListing(id: number, payload: ListingRequest): Observable<Listing> {
    return this.http.put<Listing>(`${environment.apiUrl}/listings/${id}`, payload);
  }

  deleteListing(id: number, payload: RemoveListingRequest): Observable<Listing> {
    return this.http.delete<Listing>(`${environment.apiUrl}/listings/${id}`, { body: payload });
  }

  uploadListingImages(id: number, files: File[]): Observable<string[]> {
    const formData = new FormData();
    files.forEach(file => formData.append('images', file, file.name));
    return this.http.post<string[]>(`${environment.apiUrl}/listings/${id}/images`, formData);
  }
}
