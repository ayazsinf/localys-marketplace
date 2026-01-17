import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface OrderCreatePayload {
  productIds: number[];
  addressId: number;
  shippingMethod: string;
  shippingPrice: number;
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

@Injectable({ providedIn: 'root' })
export class OrderService {
  constructor(private http: HttpClient) {}

  createOrder(payload: OrderCreatePayload): Observable<OrderResponse> {
    return this.http.post<OrderResponse>('/api/orders', payload);
  }

  createStripeCheckout(orderId: number): Observable<StripeCheckoutResponse> {
    return this.http.post<StripeCheckoutResponse>(`/api/payments/stripe/checkout/${orderId}`, {});
  }
}
