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

  getProductsAll() {
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

  // private products: Product[] = [
  //   {
  //     id: 1,
  //     name: 'Wireless Bluetooth Headphones',
  //     price: 79.99,
  //     description: 'High-quality wireless headphones with noise cancellation',
  //     image: 'https://images.pexels.com/photos/30563921/pexels-photo-30563921.jpeg',
  //     category: 'Electronics',
  //     rating: 4.5,
  //     inStock: true
  //   },
  //   {
  //     id: 2,
  //     name: 'Smart Watch',
  //     price: 199.99,
  //     description: 'Feature-rich smartwatch with health monitoring',
  //     image: 'https://images.pexels.com/photos/267394/pexels-photo-267394.jpeg',
  //     category: 'Electronics',
  //     rating: 4.2,
  //     inStock: true
  //   },
  //   {
  //     id: 3,
  //     name: 'Laptop Backpack',
  //     price: 49.99,
  //     description: 'Durable backpack with laptop compartment',
  //     image: 'https://media.istockphoto.com/id/2164305018/fr/photo/ordinateur-portable-avec-des-gadgets-et-des-accessoires-modernes-pour-le-travail-et-les.jpg?s=1024x1024&w=is&k=20&c=XXJaKa7OkRSt8pL4EsAJkIRksROcKrN6wkrL9WFWsBg=',
  //     category: 'Accessories',
  //     rating: 4.7,
  //     inStock: true
  //   },
  //   {
  //     id: 4,
  //     name: 'Mechanical Keyboard',
  //     price: 129.99,
  //     description: 'RGB mechanical keyboard with customizable keys',
  //     image: 'https://images.pexels.com/photos/34563105/pexels-photo-34563105.jpeg',
  //     category: 'Electronics',
  //     rating: 4.8,
  //     inStock: false
  //   },
  //   {
  //     id: 5,
  //     name: 'Wireless Mouse',
  //     price: 39.99,
  //     description: 'Ergonomic wireless mouse with long battery life',
  //     image: 'https://images.pexels.com/photos/27647938/pexels-photo-27647938.jpeg',
  //     category: 'Electronics',
  //     rating: 4.3,
  //     inStock: true
  //   },
  //   {
  //     id: 6,
  //     name: 'Phone Case',
  //     price: 19.99,
  //     description: 'Protective case for smartphones',
  //     image: 'https://media.istockphoto.com/id/2153692980/fr/photo/coques-de-t%C3%A9l%C3%A9phone-color%C3%A9es-couch%C3%A9es-sur-fond-blanc.jpg?s=1024x1024&w=is&k=20&c=2KjIFhMtKZgllEdm8DeXopMDCCNzE7IaYX-_Bfpc-No=',
  //     category: 'Accessories',
  //     rating: 4.1,
  //     inStock: true
  //   }
  // ];

  getProducts(): Product[] {
    return this._products();
  }

  getProductsByCategory(category: string): Product[] {
    return this._products().filter(product => product.categoryName === category);
  }

 getProductsByRating(rating: number): Product[] {
   return this._products().filter(product => product.rating ? product.rating < rating : 5);
  }

  getCategories(): string[] {
    return [...new Set(this._products().map(product => product.categoryName))];
  }
}
