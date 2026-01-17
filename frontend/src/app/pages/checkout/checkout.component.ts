import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { CartItem, CartService } from '../../service/cart.service';

@Component({
  selector: 'app-checkout',
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.scss',
  standalone: false
})
export class CheckoutComponent implements OnInit, OnDestroy {
  items: CartItem[] = [];
  selectedIds: number[] = [];
  private subscriptions = new Subscription();

  constructor(
    private cartService: CartService,
    private route: ActivatedRoute,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const paramsSub = this.route.queryParamMap.subscribe(params => {
      const raw = params.get('items') || '';
      this.selectedIds = raw
        .split(',')
        .map(value => Number(value))
        .filter(value => !Number.isNaN(value));
    });
    this.subscriptions.add(paramsSub);

    const itemsSub = this.cartService.items$.subscribe(items => {
      if (this.selectedIds.length === 0) {
        this.items = items;
      } else {
        const idSet = new Set(this.selectedIds);
        this.items = items.filter(item => idSet.has(item.product.id));
      }
      this.cdr.detectChanges();
    });
    this.subscriptions.add(itemsSub);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  getTotal(): number {
    return this.items.reduce((sum, item) => sum + item.product.price * item.quantity, 0);
  }
}
