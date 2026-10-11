import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ItemCondition } from './listing.service';
import { DeliveryFilter } from './product.service';

export interface SavedSearch {
  id: number;
  name: string;
  query: string | null;
  categoryId: number | null;
  categoryName: string | null;
  minPrice: number | null;
  maxPrice: number | null;
  conditions: ItemCondition[];
  delivery: DeliveryFilter | null;
  latitude: number | null;
  longitude: number | null;
  radiusKm: number | null;
  locationLabel: string | null;
  alertsEnabled: boolean;
  createdAt: string;
}

export interface SavedSearchPayload {
  name: string;
  query: string | null;
  categoryId: number | null;
  minPrice: number | null;
  maxPrice: number | null;
  conditions: ItemCondition[];
  delivery: DeliveryFilter | null;
  latitude: number | null;
  longitude: number | null;
  radiusKm: number | null;
  locationLabel: string | null;
  alertsEnabled: boolean;
}

@Injectable({ providedIn: 'root' })
export class SavedSearchService {
  private readonly baseUrl = `${environment.apiUrl}/saved-searches`;

  constructor(private http: HttpClient) {}

  list(): Observable<SavedSearch[]> {
    return this.http.get<SavedSearch[]>(this.baseUrl);
  }

  create(payload: SavedSearchPayload): Observable<SavedSearch> {
    return this.http.post<SavedSearch>(this.baseUrl, payload);
  }

  setAlerts(id: number, alertsEnabled: boolean): Observable<SavedSearch> {
    return this.http.patch<SavedSearch>(`${this.baseUrl}/${id}`, { alertsEnabled });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  /** Query params that make the home page apply this search's filters. */
  toQueryParams(search: SavedSearch): Record<string, string | number> {
    const params: Record<string, string | number> = {};
    if (search.query) {
      params['q'] = search.query;
    }
    if (search.categoryId != null) {
      params['categoryId'] = search.categoryId;
    }
    if (search.minPrice != null) {
      params['minPrice'] = search.minPrice;
    }
    if (search.maxPrice != null) {
      params['maxPrice'] = search.maxPrice;
    }
    if (search.conditions?.length) {
      params['condition'] = search.conditions.join(',');
    }
    if (search.delivery) {
      params['delivery'] = search.delivery;
    }
    if (search.latitude != null && search.longitude != null && search.radiusKm != null) {
      params['lat'] = search.latitude;
      params['lng'] = search.longitude;
      params['radiusKm'] = search.radiusKm;
      if (search.locationLabel) {
        params['place'] = search.locationLabel;
      }
    }
    return params;
  }
}
