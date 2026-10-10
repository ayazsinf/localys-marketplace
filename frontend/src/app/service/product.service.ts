import {Product} from '../modules/product.model';
import {Injectable} from '@angular/core';
import {HttpClient, HttpParams} from "@angular/common/http";
import {Observable} from "rxjs";

export interface ProductDetail {
  id: number;
  name: string;
  description: string;
  price: number;
  country: string;
  currency: string;
  stockQty: number;
  active: boolean;
  sku: string;
  brand: string | null;
  imageUrls: string[];
  categoryId: number | null;
  categoryName: string | null;
  vendorId: number | null;
  vendorUserId: number | null;
  vendorDisplayName: string | null;
  vendorShopName: string | null;
  createdAt: string | null;
  locationText: string | null;
  latitude: number | null;
  longitude: number | null;
  vendorRating: number;
  vendorReviewCount: number;
}


export type ProductSort = 'NEWEST' | 'PRICE_ASC' | 'PRICE_DESC' | 'NAME_ASC';

export interface ProductSearchParams {
  q?: string;
  categoryId?: number | null;
  minPrice?: number | null;
  maxPrice?: number | null;
  sort?: ProductSort;
  page?: number;
  size?: number;
  /** Only this user's listings (public profile). */
  sellerId?: number;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({
  providedIn: 'root'
})
export class ProductService {

  constructor(private http: HttpClient) {
  }

  /** Filtering, sorting and paging are done by the backend. */
  search(params: ProductSearchParams): Observable<PageResponse<Product>> {
    let httpParams = new HttpParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== null && value !== undefined && value !== '') {
        httpParams = httpParams.set(key, String(value));
      }
    }
    return this.http.get<PageResponse<Product>>('/api/products', { params: httpParams });
  }

  getProductDetail(id: number) {
    return this.http.get<ProductDetail>(`/api/products/${id}`);
  }
}
