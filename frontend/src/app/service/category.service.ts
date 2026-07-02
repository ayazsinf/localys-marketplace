import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CategoryNode {
  id: number;
  name: string;
  slug: string;
  parentId?: number | null;
  pathIds: number[];
  pathNames: string[];
  children: CategoryNode[];
}

@Injectable({
  providedIn: 'root'
})
export class CategoryService {
  constructor(private http: HttpClient) {}

  loadTree(): Observable<CategoryNode[]> {
    return this.http.get<CategoryNode[]>('/api/categories/tree');
  }
}
