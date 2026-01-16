import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { Product } from '../modules/product.model';

@Injectable({
  providedIn: 'root'
})
export class FavoritesService {
  private readonly favoriteIdsSubject = new BehaviorSubject<number[]>([]);
  readonly favoriteIds$ = this.favoriteIdsSubject.asObservable();
  private favoriteIdsLoaded = false;
  private favoriteIdsLoading = false;

  constructor(private http: HttpClient) {}

  getFavoriteIds(): number[] {
    return this.favoriteIdsSubject.value;
  }

  isFavorite(productId: number): boolean {
    return this.favoriteIdsSubject.value.includes(productId);
  }

  ensureFavoriteIds(): void {
    if (this.favoriteIdsLoaded || this.favoriteIdsLoading) {
      return;
    }
    this.favoriteIdsLoading = true;
    this.loadFavoriteIds().subscribe({
      next: () => {
        this.favoriteIdsLoading = false;
      },
      error: () => {
        this.favoriteIdsLoading = false;
      }
    });
  }

  loadFavoriteIds(): Observable<number[]> {
    return this.http.get<number[]>('/api/favorites/ids').pipe(
      tap(ids => {
        this.favoriteIdsSubject.next(ids);
        this.favoriteIdsLoaded = true;
      })
    );
  }

  loadFavorites(): Observable<Product[]> {
    return this.http.get<Product[]>('/api/favorites');
  }

  toggleFavorite(productId: number): Observable<void> {
    const current = this.favoriteIdsSubject.value;
    const isFav = current.includes(productId);
    const request$ = isFav
      ? this.http.delete<void>(`/api/favorites/${productId}`)
      : this.http.post<void>(`/api/favorites/${productId}`, null);

    return request$.pipe(
      tap(() => {
        const next = isFav
          ? current.filter(id => id !== productId)
          : [...current, productId];
        this.favoriteIdsSubject.next(next);
      })
    );
  }
}
