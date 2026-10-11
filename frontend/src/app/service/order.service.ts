import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface OrderCreatePayload {
  productIds: number[];
  addressId: number;
  shippingMethod: string;
  paymentMethod: string;
}

export interface OrderResponse {
  id: number;
  status: string;
  subtotal: number;
  shippingPrice: number;
  total: number;
  currency: string;
  createdAt: string;
}

export interface StripeCheckoutResponse {
  checkoutUrl: string;
}

export type OrderStatus =
  | 'CREATED'
  | 'PENDING'
  | 'PAYMENT_PENDING'
  | 'PAYMENT_CONFIRMING'
  | 'PAID'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED';

import { DisputeStatus } from './dispute.service';

export interface OrderItem {
  orderItemId: number;
  productId: number;
  productName: string;
  imageUrl: string | null;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  trackingNumber: string | null;
  /** The carrier's tracking page. */
  trackingUrl: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  /** The rating the viewer already gave the other party for this item. */
  myRating: number | null;
  /** Buyer protection: HELD until the buyer confirms, DISPUTED, RELEASED or REFUNDED. */
  escrowStatus: 'HELD' | 'DISPUTED' | 'RELEASED' | 'REFUNDED' | null;
  refundAmount: number | null;
  disputeId: number | null;
  disputeStatus: DisputeStatus | null;
  /** Seller view only. */
  commissionAmount: number | null;
  payoutAmount: number | null;
}

export interface OrderShippingAddress {
  fullName: string | null;
  phone: string | null;
  line1: string;
  line2: string | null;
  postalCode: string;
  city: string;
  country: string;
}

export interface Order {
  id: number;
  status: OrderStatus;
  subtotal: number;
  shippingPrice: number;
  total: number;
  currency: string;
  shippingMethod: string | null;
  createdAt: string;
  buyerDisplayName: string | null;
  /** Seller view only: links to the buyer's public profile. */
  buyerUserId: number | null;
  shippingAddress: OrderShippingAddress | null;
  items: OrderItem[];
}

@Injectable({ providedIn: 'root' })
export class OrderService {
  constructor(private http: HttpClient) {}

  createOrder(payload: OrderCreatePayload): Observable<OrderResponse> {
    return this.http.post<OrderResponse>('/api/orders', payload);
  }

  createStripeCheckout(orderId: number): Observable<StripeCheckoutResponse> {
    return this.http.post<StripeCheckoutResponse>(`/api/payments/stripe/checkout/${orderId}`, {});
  }

  listMyOrders(): Observable<Order[]> {
    return this.http.get<Order[]>('/api/orders');
  }

  listMySales(): Observable<Order[]> {
    return this.http.get<Order[]>('/api/orders/sales');
  }

  getOrder(orderId: number): Observable<Order> {
    return this.http.get<Order>(`/api/orders/${orderId}`);
  }

  /** Seller: returns the order as seen from the sales tab. */
  shipOrder(orderId: number, trackingNumber: string): Observable<Order> {
    return this.http.post<Order>(`/api/orders/${orderId}/ship`, { trackingNumber });
  }

  /** Buyer: confirms receiving the items shipped so far. */
  confirmDelivery(orderId: number): Observable<Order> {
    return this.http.post<Order>(`/api/orders/${orderId}/confirm-delivery`, {});
  }
}
