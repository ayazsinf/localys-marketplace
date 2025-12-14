import {Injectable} from '@angular/core';
import {Product} from '../modules/product.model';
import {BehaviorSubject, map} from 'rxjs';

export interface CartItem {
  product: Product;
  quantity: number;
}

@Injectable({ providedIn: 'root' })
export class CartService {

  private readonly itemsSubject = new BehaviorSubject<CartItem[]>([]);
  readonly items$ = this.itemsSubject.asObservable();

  // navbar için toplam adet
  readonly count$ = this.items$.pipe(
    map(items => items.reduce((sum, item) => sum + item.quantity, 0))
  );

  add(product: Product, quantity: number = 1): void {
    const items = [...this.itemsSubject.value];
    const index = items.findIndex(i => i.product.id === product.id);

    if (index > -1) {
      items[index] = {
        ...items[index],
        quantity: items[index].quantity + quantity
      };
    } else {
      items.push({ product, quantity });
    }

    this.itemsSubject.next(items);
  }

  clear(): void {
    this.itemsSubject.next([]);
  }

  // ileride lazım olur
  getSnapshot(): CartItem[] {
    return this.itemsSubject.value;
  }
}
