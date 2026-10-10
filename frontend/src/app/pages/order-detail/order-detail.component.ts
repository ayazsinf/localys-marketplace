import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription, timer } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { Order, OrderService } from '../../service/order.service';
import { getLocaleForLanguage } from '../../shared/market';

const AWAITING_PAYMENT = ['PAYMENT_PENDING', 'PAYMENT_CONFIRMING'];
const POLL_INTERVAL_MS = 3000;
const MAX_POLLS = 10;

@Component({
  selector: 'app-order-detail',
  templateUrl: './order-detail.component.html',
  styleUrl: '../orders/orders.component.scss',
  standalone: false
})
export class OrderDetailComponent implements OnInit, OnDestroy {
  order: Order | null = null;
  isLoading = true;
  errorMessage = '';
  infoMessage = '';
  isPaying = false;
  isConfirmingDelivery = false;
  /** True while we wait for the Stripe webhook after a successful redirect. */
  isConfirming = false;
  private orderId = 0;
  private pollCount = 0;
  private pollSub?: Subscription;
  private subscriptions = new Subscription();

  constructor(
    private orderService: OrderService,
    private route: ActivatedRoute,
    private translateService: TranslateService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.orderId = Number(this.route.snapshot.paramMap.get('id'));
    const query = this.route.snapshot.queryParamMap;
    if (query.get('paid') === '1') {
      this.isConfirming = true;
    } else if (query.get('canceled') === '1') {
      this.infoMessage = 'ORDERS.PAYMENT_CANCELED';
    }
    this.loadOrder();
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
    this.subscriptions.unsubscribe();
  }

  get canPay(): boolean {
    return !!this.order && this.order.status === 'PAYMENT_PENDING' && !this.isConfirming;
  }

  get canConfirmDelivery(): boolean {
    return !!this.order && this.order.items.some(item => !!item.shippedAt && !item.deliveredAt);
  }

  confirmDelivery(): void {
    if (!this.order || this.isConfirmingDelivery) {
      return;
    }
    this.isConfirmingDelivery = true;
    this.errorMessage = '';
    const confirmSub = this.orderService.confirmDelivery(this.order.id).subscribe({
      next: order => {
        this.order = order;
        this.isConfirmingDelivery = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.isConfirmingDelivery = false;
        this.errorMessage = 'ORDERS.ERROR_CONFIRM_DELIVERY';
        this.cdr.detectChanges();
      }
    });
    this.subscriptions.add(confirmSub);
  }

  pay(): void {
    if (!this.order || this.isPaying) {
      return;
    }
    this.isPaying = true;
    this.errorMessage = '';
    const paySub = this.orderService.createStripeCheckout(this.order.id).subscribe({
      next: checkout => {
        window.location.href = checkout.checkoutUrl;
      },
      error: err => {
        this.isPaying = false;
        this.errorMessage = err?.error?.code === 'ORDER_NOT_PAYABLE'
          ? 'ORDERS.ERROR_NOT_PAYABLE'
          : 'CHECKOUT.ERROR_START_PAYMENT';
        this.loadOrder();
      }
    });
    this.subscriptions.add(paySub);
  }

  formatDate(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    return date.toLocaleString(getLocaleForLanguage(
      this.translateService.currentLang || this.translateService.getDefaultLang()
    ));
  }

  private loadOrder(): void {
    if (!Number.isFinite(this.orderId) || this.orderId <= 0) {
      this.isLoading = false;
      this.errorMessage = 'ORDERS.ERROR_NOT_FOUND';
      return;
    }
    const loadSub = this.orderService.getOrder(this.orderId).subscribe({
      next: order => {
        this.order = order;
        this.isLoading = false;
        this.handleConfirmation(order);
        this.cdr.detectChanges();
      },
      error: err => {
        this.isLoading = false;
        this.isConfirming = false;
        this.errorMessage = err?.status === 404 ? 'ORDERS.ERROR_NOT_FOUND' : 'ORDERS.ERROR_LOAD';
        this.cdr.detectChanges();
      }
    });
    this.subscriptions.add(loadSub);
  }

  /** Stripe redirects before its webhook reaches us, so poll briefly until the order is paid. */
  private handleConfirmation(order: Order): void {
    if (!this.isConfirming) {
      return;
    }
    if (!AWAITING_PAYMENT.includes(order.status) || this.pollCount >= MAX_POLLS) {
      this.isConfirming = false;
      if (AWAITING_PAYMENT.includes(order.status)) {
        this.infoMessage = 'ORDERS.CONFIRMATION_DELAYED';
      }
      return;
    }
    this.pollCount++;
    this.pollSub?.unsubscribe();
    this.pollSub = timer(POLL_INTERVAL_MS).subscribe(() => this.loadOrder());
  }
}
