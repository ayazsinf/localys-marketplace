import { Injectable } from '@angular/core';
import { Product } from '../modules/product.model';
import { BehaviorSubject, EMPTY, map, Observable, tap, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';

interface CartItemResponse {
  productId: number;
  productName: string;
  brand: string | null;
  imageUrl: string | null;
  unitPrice: number;
  currency: string;
  stockQty: number | null;
  quantity: number;
  lineTotal: number;
}

interface CartResponse {
  id: number;
  status: string;
  items: CartItemResponse[];
  total: number;
}

export interface CartItem {
  product: Product;
  quantity: number;
}

export interface CartNotice {
  type: 'success' | 'error' | 'warning';
  message: string;
  productName?: string;
  quantity?: number;
}

@Injectable({ providedIn: 'root' })
export class CartService {

  private readonly itemsSubject = new BehaviorSubject<CartItem[]>([]);
  readonly items$ = this.itemsSubject.asObservable();
  private readonly noticeSubject = new BehaviorSubject<CartNotice | null>(null);
  readonly notice$ = this.noticeSubject.asObservable();

  // navbar için toplam adet
  readonly count$ = this.items$.pipe(
    map(items => items.reduce((sum, item) => sum + item.quantity, 0))
  );

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  refresh(): Observable<CartItem[]> {
    if (!this.authService.isAuthenticated) {
      this.itemsSubject.next([]);
      return this.items$;
    }
    return this.http.get<CartResponse>('/api/cart').pipe(
      tap(cart => this.applyCart(cart)),
      map(() => this.itemsSubject.value)
    );
  }

  add(product: Product, quantity: number = 1): void {
    this.addToCart(product, quantity).subscribe();
  }

  addToCart(product: Product, quantity: number = 1): Observable<CartItem[]> {
    if (!this.authService.isAuthenticated) {
      this.authService.login().subscribe();
      return EMPTY;
    }
    const previousQty = this.getQuantity(product.id);
    return this.http.post<CartResponse>('/api/cart/items', {
      productId: product.id,
      quantity
    }).pipe(
      tap(cart => {
        const items = this.applyCart(cart);
        const updatedItem = items.find(item => item.product.id === product.id);
        this.emitStockNotice(product, previousQty, updatedItem?.quantity ?? 0, updatedItem?.product.stockQty ?? product.stockQty ?? null, quantity);
      }),
      map(() => this.itemsSubject.value),
      catchError(err => {
        this.noticeSubject.next({
          type: 'error',
          message: this.readCartError(err, 'Could not add item.'),
          productName: product.name
        });
        return throwError(() => err);
      })
    );
  }

  updateQuantity(productId: number, quantity: number): void {
    if (!this.authService.isAuthenticated) {
      this.authService.login().subscribe();
      return;
    }
    const previousQty = this.getQuantity(productId);
    this.http.put<CartResponse>(`/api/cart/items/${productId}`, { quantity }).subscribe({
      next: cart => {
        const items = this.applyCart(cart);
        const updatedItem = items.find(item => item.product.id === productId);
        const product = updatedItem?.product;
        if (!product) {
          return;
        }
        if (updatedItem.quantity > previousQty) {
          this.emitStockNotice(product, previousQty, updatedItem.quantity, product.stockQty ?? null, updatedItem.quantity - previousQty);
        }
      },
      error: err => {
        this.noticeSubject.next({
          type: 'error',
          message: this.readCartError(err, 'Could not update quantity.')
        });
      }
    });
  }

  remove(productId: number): void {
    if (!this.authService.isAuthenticated) {
      this.authService.login().subscribe();
      return;
    }
    this.http.delete<CartResponse>(`/api/cart/items/${productId}`).subscribe({
      next: cart => this.applyCart(cart),
      error: err => {
        this.noticeSubject.next({
          type: 'error',
          message: this.readCartError(err, 'Could not remove item.')
        });
      }
    });
  }

  clear(): void {
    this.itemsSubject.next([]);
  }

  clearNotice(): void {
    this.noticeSubject.next(null);
  }

  // ileride lazım olur
  getSnapshot(): CartItem[] {
    return this.itemsSubject.value;
  }

  private applyCart(cart: CartResponse): CartItem[] {
    const items = cart.items.map(item => ({
      product: this.toProduct(item),
      quantity: item.quantity
    }));
    this.itemsSubject.next(items);
    return items;
  }

  private toProduct(item: CartItemResponse): Product {
    return {
      id: item.productId,
      name: item.productName,
      description: '',
      price: item.unitPrice,
      currency: item.currency,
      imageUrls: item.imageUrl ? [item.imageUrl] : [],
      categoryName: '',
      inStock: true,
      stockQty: item.stockQty ?? undefined
    };
  }

  private readCartError(error: any, fallback: string): string {
    const code = error?.error?.code;
    if (code === 'OUT_OF_STOCK') {
      return 'Sorry, no items left in stock.';
    }
    if (code === 'ALREADY_IN_CART') {
      return 'Already added to cart.';
    }
    if (code === 'OWN_PRODUCT') {
      return 'You cannot buy your own listing.';
    }
    if (code === 'PRODUCT_NOT_FOUND') {
      return 'Product not found.';
    }
    return error?.error?.message || fallback;
  }

  private getQuantity(productId: number): number {
    return this.itemsSubject.value.find(item => item.product.id === productId)?.quantity ?? 0;
  }

  private emitStockNotice(
    product: Product,
    previousQty: number,
    currentQty: number,
    stockQty: number | null,
    attemptedQuantity: number
  ): void {
    if (stockQty == null) {
      this.noticeSubject.next({
        type: 'success',
        message: 'Added to cart.',
        productName: product.name,
        quantity: attemptedQuantity
      });
      return;
    }

    if (previousQty >= stockQty && currentQty >= stockQty) {
      this.noticeSubject.next({
        type: 'warning',
        message: 'Already added to cart. No more stock left.',
        productName: product.name,
        quantity: currentQty
      });
      return;
    }

    const remaining = Math.max(stockQty - currentQty, 0);
    if (remaining === 0) {
      this.noticeSubject.next({
        type: 'warning',
        message: 'No items left in stock.',
        productName: product.name,
        quantity: currentQty
      });
      return;
    }

    if (remaining === 1) {
      this.noticeSubject.next({
        type: 'warning',
        message: 'Only one item left in stock.',
        productName: product.name,
        quantity: currentQty
      });
      return;
    }

    this.noticeSubject.next({
      type: 'success',
      message: 'Added to cart.',
      productName: product.name,
      quantity: attemptedQuantity
    });
  }
}
