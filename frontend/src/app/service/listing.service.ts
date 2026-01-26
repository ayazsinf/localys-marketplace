import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Listing {
  id: number;
  name: string;
  description: string | null;
  price: number;
  currency: string;
  stockQty: number;
  active: boolean;
  sku: string;
  brand: string | null;
  imageUrls?: string[];
  categoryId?: number | null;
  categoryName?: string | null;
  parentCategoryId?: number | null;
  locationText?: string | null;
  latitude?: number | null;
  longitude?: number | null;
}

export interface ListingRequest {
  name: string;
  description?: string | null;
  price: number;
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

  deleteListing(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/listings/${id}`);
  }

  uploadListingImages(id: number, files: File[]): Observable<string[]> {
    const formData = new FormData();
    files.forEach(file => formData.append('images', file, file.name));
    return this.http.post<string[]>(`${environment.apiUrl}/listings/${id}/images`, formData);
  }
}
