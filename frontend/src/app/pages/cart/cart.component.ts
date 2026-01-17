import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { CartItem, CartService } from '../../service/cart.service';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-cart',
  templateUrl: './cart.component.html',
  styleUrl: './cart.component.scss',
  standalone: false
})
export class CartComponent implements OnInit, OnDestroy {
  items: CartItem[] = [];
  isLoading = false;
  errorMessage = '';
  selectedIds = new Set<number>();
  private subscriptions = new Subscription();

  constructor(
    private cartService: CartService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.isLoading = true;
    const refreshSub = this.cartService.refresh().subscribe({
      next: () => {
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = 'Failed to load cart.';
        this.cdr.detectChanges();
      }
    });
    this.subscriptions.add(refreshSub);

    const itemsSub = this.cartService.items$.subscribe(items => {
      this.items = items;
      this.syncSelection(items);
      this.cdr.detectChanges();
    });
    this.subscriptions.add(itemsSub);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  toggleAll(): void {
    if (this.isAllSelected()) {
      this.selectedIds.clear();
      return;
    }
    this.items.forEach(item => this.selectedIds.add(item.product.id));
  }

  toggleSelection(productId: number): void {
    if (this.selectedIds.has(productId)) {
      this.selectedIds.delete(productId);
      return;
    }
    this.selectedIds.add(productId);
  }

  isSelected(productId: number): boolean {
    return this.selectedIds.has(productId);
  }

  isAllSelected(): boolean {
    return this.items.length > 0 && this.items.every(item => this.selectedIds.has(item.product.id));
  }

  changeQuantity(item: CartItem, delta: number): void {
    const nextQty = item.quantity + delta;
    if (nextQty <= 0) {
      this.cartService.remove(item.product.id);
      return;
    }
    this.cartService.updateQuantity(item.product.id, nextQty);
  }

  removeItem(item: CartItem): void {
    this.cartService.remove(item.product.id);
  }

  getLineTotal(item: CartItem): number {
    return item.product.price * item.quantity;
  }

  getSelectedTotal(): number {
    return this.items
      .filter(item => this.selectedIds.has(item.product.id))
      .reduce((sum, item) => sum + this.getLineTotal(item), 0);
  }

  checkout(): void {
    const ids = Array.from(this.selectedIds.values());
    this.router.navigate(['/checkout'], {
      queryParams: {
        items: ids.join(',')
      }
    });
  }

  getImageUrl(url?: string): string {
    if (!url) {
      return '';
    }
    const origin = window.location.origin;
    if (url.startsWith(origin) && environment.apiUrl.startsWith('http')) {
      return `${environment.apiUrl}${url.substring(origin.length)}`;
    }
    if (url.startsWith('http://') || url.startsWith('https://')) {
      return url;
    }
    const normalized = url.startsWith('/') ? url : `/${url}`;
    return `${environment.apiUrl}${normalized}`;
  }

  private syncSelection(items: CartItem[]): void {
    if (items.length === 0) {
      this.selectedIds.clear();
      return;
    }
    const currentIds = new Set(items.map(item => item.product.id));
    Array.from(this.selectedIds).forEach(id => {
      if (!currentIds.has(id)) {
        this.selectedIds.delete(id);
      }
    });
    if (this.selectedIds.size === 0) {
      items.forEach(item => this.selectedIds.add(item.product.id));
    }
  }
}
