import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Category {
  id: number;
  name: string;
  parentId?: number | null;
}

@Injectable({
  providedIn: 'root'
})
export class CategoryService {
  constructor(private http: HttpClient) {}

  loadRootCategories(): Observable<Category[]> {
    return this.http.get<Category[]>('/api/categories');
  }

  loadChildren(parentId: number): Observable<Category[]> {
    return this.http.get<Category[]>(`/api/categories/${parentId}/children`);
  }
}
