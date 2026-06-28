import { Injectable } from "@angular/core";
import { Product } from "../modules/product.model";
import { BehaviorSubject, EMPTY, map, Observable, tap, throwError } from "rxjs";
import { catchError } from "rxjs/operators";
import { HttpClient } from "@angular/common/http";
import { AuthService } from "./auth.service";
import { environment } from "../../environments/environment";

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
  type: "success" | "error" | "warning";
  messageKey: string;
  productName?: string;
  quantity?: number;
}

@Injectable({ providedIn: "root" })
export class CartService {
  private readonly itemsSubject = new BehaviorSubject<CartItem[]>([]);
  readonly items$ = this.itemsSubject.asObservable();
  private readonly noticeSubject = new BehaviorSubject<CartNotice | null>(null);
  readonly notice$ = this.noticeSubject.asObservable();

  // navbar için toplam adet
  readonly count$ = this.items$.pipe(
    map((items) => items.reduce((sum, item) => sum + item.quantity, 0)),
  );

  constructor(
    private http: HttpClient,
    private authService: AuthService,
  ) {}

  refresh(): Observable<CartItem[]> {
    if (!this.authService.isAuthenticated) {
      this.itemsSubject.next([]);
      return this.items$;
    }
    return this.http.get<CartResponse>(`${environment.apiUrl}/cart`).pipe(
      tap((cart) => this.applyCart(cart)),
      map(() => this.itemsSubject.value),
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
    return this.http
      .post<CartResponse>(`${environment.apiUrl}/cart/items`, {
        productId: product.id,
        quantity,
      })
      .pipe(
        tap((cart) => {
          const items = this.applyCart(cart);
          const updatedItem = items.find(
            (item) => item.product.id === product.id,
          );
          this.emitStockNotice(
            product,
            previousQty,
            updatedItem?.quantity ?? 0,
            updatedItem?.product.stockQty ?? product.stockQty ?? null,
            quantity,
          );
        }),
        map(() => this.itemsSubject.value),
        catchError((err) => {
          this.noticeSubject.next({
            type: "error",
            messageKey: this.readCartError(err, "CART.ADD_FAILED"),
            productName: product.name,
          });
          return throwError(() => err);
        }),
      );
  }

  updateQuantity(productId: number, quantity: number): void {
    if (!this.authService.isAuthenticated) {
      this.authService.login().subscribe();
      return;
    }
    const previousQty = this.getQuantity(productId);
    this.http
      .put<CartResponse>(`${environment.apiUrl}/cart/items/${productId}`, {
        quantity,
      })
      .subscribe({
        next: (cart) => {
          const items = this.applyCart(cart);
          const updatedItem = items.find(
            (item) => item.product.id === productId,
          );
          const product = updatedItem?.product;
          if (!product || !updatedItem) {
            return;
          }
          if (updatedItem.quantity > previousQty) {
            this.emitStockNotice(
              product,
              previousQty,
              updatedItem.quantity,
              product.stockQty ?? null,
              updatedItem.quantity - previousQty,
            );
          }
        },
        error: (err) => {
          this.noticeSubject.next({
            type: "error",
            messageKey: this.readCartError(err, "CART.UPDATE_FAILED"),
          });
        },
      });
  }

  remove(productId: number): void {
    if (!this.authService.isAuthenticated) {
      this.authService.login().subscribe();
      return;
    }
    this.http
      .delete<CartResponse>(`${environment.apiUrl}/cart/items/${productId}`)
      .subscribe({
        next: (cart) => this.applyCart(cart),
        error: (err) => {
          this.noticeSubject.next({
            type: "error",
            messageKey: this.readCartError(err, "CART.REMOVE_FAILED"),
          });
        },
      });
  }

  clear(): void {
    this.itemsSubject.next([]);
  }

  clearNotice(): void {
    this.noticeSubject.next(null);
  }

  // Kept for future use.
  getSnapshot(): CartItem[] {
    return this.itemsSubject.value;
  }

  private applyCart(cart: CartResponse): CartItem[] {
    const items = cart.items.map((item) => ({
      product: this.toProduct(item),
      quantity: item.quantity,
    }));
    this.itemsSubject.next(items);
    return items;
  }

  private toProduct(item: CartItemResponse): Product {
    return {
      id: item.productId,
      name: item.productName,
      description: "",
      price: item.unitPrice,
      currency: item.currency,
      imageUrls: item.imageUrl ? [item.imageUrl] : [],
      categoryName: "",
      inStock: true,
      stockQty: item.stockQty ?? undefined,
    };
  }

  private readCartError(error: any, fallback: string): string {
    const code = error?.error?.code;
    if (code === "OUT_OF_STOCK") {
      return "CART.OUT_OF_STOCK";
    }
    if (code === "ALREADY_IN_CART") {
      return "CART.ALREADY_IN_CART";
    }
    if (code === "OWN_PRODUCT") {
      return "CART.OWN_PRODUCT";
    }
    if (code === "PRODUCT_NOT_FOUND") {
      return "CART.PRODUCT_NOT_FOUND";
    }
    return error?.error?.message || fallback;
  }

  private getQuantity(productId: number): number {
    return (
      this.itemsSubject.value.find((item) => item.product.id === productId)
        ?.quantity ?? 0
    );
  }

  private emitStockNotice(
    product: Product,
    previousQty: number,
    currentQty: number,
    stockQty: number | null,
    attemptedQuantity: number,
  ): void {
    if (stockQty == null) {
      this.noticeSubject.next({
        type: "success",
        messageKey: "CART.ADDED_TO_CART",
        productName: product.name,
        quantity: attemptedQuantity,
      });
      return;
    }

    if (previousQty >= stockQty && currentQty >= stockQty) {
      this.noticeSubject.next({
        type: "warning",
        messageKey: "CART.ALREADY_IN_CART_NO_MORE_STOCK",
        productName: product.name,
        quantity: currentQty,
      });
      return;
    }

    const remaining = Math.max(stockQty - currentQty, 0);
    if (remaining === 0) {
      this.noticeSubject.next({
        type: "warning",
        messageKey: "CART.NO_ITEMS_LEFT_IN_STOCK",
        productName: product.name,
        quantity: currentQty,
      });
      return;
    }

    if (remaining === 1) {
      this.noticeSubject.next({
        type: "warning",
        messageKey: "CART.ONLY_ONE_ITEM_LEFT_IN_STOCK",
        productName: product.name,
        quantity: currentQty,
      });
      return;
    }

    this.noticeSubject.next({
      type: "success",
      messageKey: "CART.ADDED_TO_CART",
      productName: product.name,
      quantity: attemptedQuantity,
    });
  }
}
