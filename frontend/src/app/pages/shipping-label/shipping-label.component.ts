import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Order, OrderService } from '../../service/order.service';
import { UserAddress, UserService } from '../../service/user.service';

/**
 * A printable address label for a sale: sender, recipient, order number and carrier. It does
 * not include postage; the seller pays it at the carrier's drop-off point.
 */
@Component({
  selector: 'app-shipping-label',
  templateUrl: './shipping-label.component.html',
  styleUrl: './shipping-label.component.scss',
  standalone: false
})
export class ShippingLabelComponent implements OnInit {
  order: Order | null = null;
  sender: UserAddress | null = null;
  senderName = '';
  isLoading = true;
  notFound = false;

  constructor(
    private route: ActivatedRoute,
    private orderService: OrderService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    const orderId = Number(this.route.snapshot.paramMap.get('id'));
    forkJoin({ sales: this.orderService.listMySales(), me: this.userService.getMe() }).subscribe({
      next: ({ sales, me }) => {
        this.order = sales.find(order => order.id === orderId) ?? null;
        this.notFound = !this.order;
        this.sender = me.addresses.find(address => address.defaultShipping) ?? me.addresses[0] ?? null;
        this.senderName = this.sender?.fullName || me.displayName || '';
        this.isLoading = false;
      },
      error: () => {
        this.notFound = true;
        this.isLoading = false;
      }
    });
  }

  get carrierKey(): string {
    return 'CHECKOUT.SHIPPING_' + (this.order?.shippingMethod ?? '').toUpperCase();
  }

  /** Items of this seller still to be sent. */
  get itemsToShip() {
    return (this.order?.items ?? []).filter(item => !item.shippedAt && item.escrowStatus !== 'REFUNDED');
  }

  print(): void {
    window.print();
  }
}
