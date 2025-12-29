import {Product} from '../modules/product.model';
import {computed, Injectable, signal} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {tap} from "rxjs";


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
        tap(list => {
          console.log('HTTP arrived', list.length);
          this._products.set(list);
          console.log('signal after set', this._products().length);
        })
    );
  }



  setProducts(list: Product[]) {
    this._products.set(list);
  }


  addProduct(p: Product) {
    this._products.update(arr => [...arr, p]);
  }

  getProducts(): Product[] {
    return this._products();
  }

  getProductsByCategory(category: string): Product[] {
    return this._products().filter(product => product.categoryName === category);
  }

 getProductsByRating(rating: number): Product[] {
   return this._products().filter(p => (p.rating ?? 0) >= rating);
 }

  getCategories(): string[] {
    return [...new Set(this._products().map(product => product.categoryName))];
  }
}
