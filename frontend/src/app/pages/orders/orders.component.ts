import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { Order, OrderService } from '../../service/order.service';
import { getLocaleForLanguage } from '../../shared/market';

type OrdersTab = 'purchases' | 'sales';

@Component({
  selector: 'app-orders',
  templateUrl: './orders.component.html',
  styleUrl: './orders.component.scss',
  standalone: false
})
export class OrdersComponent implements OnInit, OnDestroy {
  activeTab: OrdersTab = 'purchases';
  orders: Order[] = [];
  isLoading = false;
  errorMessage = '';
  trackingInputs: Record<number, string> = {};
  shipErrors: Record<number, string> = {};
  shippingOrderId: number | null = null;
  private subscriptions = new Subscription();
  private loadSub?: Subscription;

  constructor(
    private orderService: OrderService,
    private route: ActivatedRoute,
    private router: Router,
    private translateService: TranslateService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const paramsSub = this.route.queryParamMap.subscribe(params => {
      this.activeTab = params.get('tab') === 'sales' ? 'sales' : 'purchases';
      this.loadOrders();
    });
    this.subscriptions.add(paramsSub);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.loadSub?.unsubscribe();
  }

  selectTab(tab: OrdersTab): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: tab === 'sales' ? 'sales' : null },
      queryParamsHandling: 'merge'
    });
  }

  openOrder(order: Order): void {
    if (this.activeTab === 'purchases') {
      this.router.navigate(['/orders', order.id]);
    }
  }

  formatDate(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    return date.toLocaleDateString(getLocaleForLanguage(
      this.translateService.currentLang || this.translateService.getDefaultLang()
    ));
  }

  trackByOrder(_: number, order: Order): number {
    return order.id;
  }

  canShip(order: Order): boolean {
    return this.activeTab === 'sales'
      && order.status === 'PAID'
      && order.items.some(item => !item.shippedAt);
  }

  ship(order: Order): void {
    const tracking = (this.trackingInputs[order.id] ?? '').trim();
    if (!tracking) {
      this.shipErrors[order.id] = 'ORDERS.ERROR_TRACKING_REQUIRED';
      return;
    }
    this.shippingOrderId = order.id;
    delete this.shipErrors[order.id];
    const shipSub = this.orderService.shipOrder(order.id, tracking).subscribe({
      next: updated => {
        this.orders = this.orders.map(item => item.id === updated.id ? updated : item);
        this.shippingOrderId = null;
        this.cdr.detectChanges();
      },
      error: () => {
        this.shipErrors[order.id] = 'ORDERS.ERROR_SHIP';
        this.shippingOrderId = null;
        this.cdr.detectChanges();
      }
    });
    this.subscriptions.add(shipSub);
  }

  private loadOrders(): void {
    this.loadSub?.unsubscribe();
    this.isLoading = true;
    this.errorMessage = '';
    const request$ = this.activeTab === 'sales'
      ? this.orderService.listMySales()
      : this.orderService.listMyOrders();
    this.loadSub = request$.subscribe({
      next: orders => {
        this.orders = orders;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.orders = [];
        this.isLoading = false;
        this.errorMessage = 'ORDERS.ERROR_LOAD';
        this.cdr.detectChanges();
      }
    });
  }
}
