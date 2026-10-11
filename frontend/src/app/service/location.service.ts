import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, of } from 'rxjs';
import { environment } from '../../environments/environment';

export interface LocationResult {
  label: string;
  latitude: number;
  longitude: number;
  postalCode?: string | null;
  city?: string | null;
}

/** A circle to search listings in, e.g. "within 10 km of Lyon". */
export interface SearchArea {
  label: string;
  lat: number;
  lng: number;
  radiusKm: number;
}

export const SEARCH_RADII_KM = [5, 10, 20, 50, 100, 200];
export const DEFAULT_RADIUS_KM = 10;

@Injectable({ providedIn: 'root' })
export class LocationService {
  constructor(private http: HttpClient) {}

  /** Town or address suggestions; needs at least 3 characters. */
  search(query: string): Observable<LocationResult[]> {
    const q = query.trim();
    if (q.length < 3) {
      return of([]);
    }
    return this.http.get<{ results?: LocationResult[] }>(`${environment.apiUrl}/locations/search`, { params: { q } })
      .pipe(map(response => response.results ?? []));
  }

  /** The browser's position, if the user allows it. */
  currentPosition(): Promise<{ lat: number; lng: number }> {
    return new Promise((resolve, reject) => {
      if (!navigator.geolocation) {
        reject(new Error('unsupported'));
        return;
      }
      navigator.geolocation.getCurrentPosition(
        position => resolve({ lat: position.coords.latitude, lng: position.coords.longitude }),
        error => reject(error),
        { enableHighAccuracy: false, timeout: 10000, maximumAge: 600000 }
      );
    });
  }
}
