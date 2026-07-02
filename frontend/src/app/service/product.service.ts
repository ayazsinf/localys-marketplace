import {Product} from '../modules/product.model';
import {computed, Injectable, signal} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {tap} from "rxjs";

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
}


@Injectable({
  providedIn: 'root'
})
export class ProductService {

  private readonly _products = signal<Product[]>([]);
  readonly products = this._products.asReadonly();
  readonly inStokProducts = computed(() => this._products().filter(p => p.inStock))

  constructor(private http: HttpClient) {
  }

  loadProducts() {
    return this.http.get<Product[]>('/api/products').pipe(
        tap(list => this._products.set(list))
    );
  }



  setProducts(list: Product[]) {
    this._products.set(list);
  }


  addProduct(p: Product) {
    this._products.update(arr => [...arr, p]);
  }

  getProductDetail(id: number) {
    return this.http.get<ProductDetail>(`/api/products/${id}`);
  }

  getProducts(): Product[] {
    return this._products();
  }
}
