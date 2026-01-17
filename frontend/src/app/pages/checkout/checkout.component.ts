import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { CartItem, CartService } from '../../service/cart.service';
import { AuthService } from '../../service/auth.service';
import { AddressRequest, UserAddress, UserService } from '../../service/user.service';
import { OrderService } from '../../service/order.service';

@Component({
  selector: 'app-checkout',
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.scss',
  standalone: false
})
export class CheckoutComponent implements OnInit, OnDestroy {
  items: CartItem[] = [];
  selectedIds: number[] = [];
  addresses: UserAddress[] = [];
  selectedAddressId: number | null = null;
  useNewAddress = false;
  newAddress = {
    label: '',
    fullName: '',
    phone: '',
    line1: '',
    line2: '',
    city: '',
    postalCode: '',
    country: 'FR'
  };
  locationQuery = '';
  locationResults: Array<{
    label: string;
    line1: string;
    postalCode: string;
    city: string;
  }> = [];
  isSearchingLocation = false;
  shippingOptions = [
    { id: 'mondial', label: 'Mondial Relay (3-5 days)', price: 4.9 },
    { id: 'chronopost', label: 'Chronopost (1-2 days)', price: 8.9 },
    { id: 'colissimo', label: 'Colissimo (2-3 days)', price: 6.5 }
  ];
  paymentOptions = [
    { id: 'stripe', label: 'Credit/Debit Card (Stripe)' },
    { id: 'paypal', label: 'PayPal' },
    { id: 'apple', label: 'Apple Pay' }
  ];
  selectedShippingId = '';
  selectedPaymentId = '';
  orderError = '';
  orderStatus = '';
  isSubmitting = false;
  private subscriptions = new Subscription();

  constructor(
    private cartService: CartService,
    private route: ActivatedRoute,
    private cdr: ChangeDetectorRef,
    public authService: AuthService,
    private userService: UserService,
    private orderService: OrderService,
    private http: HttpClient
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

    if (this.authService.isAuthenticated) {
      const profileSub = this.userService.getMe().subscribe({
        next: profile => {
          this.addresses = profile.addresses || [];
          const defaultAddress = this.addresses.find(address => address.defaultShipping)
            || this.addresses[0]
            || null;
          this.selectedAddressId = defaultAddress ? defaultAddress.id : null;
          this.cdr.detectChanges();
        }
      });
      this.subscriptions.add(profileSub);
    }
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  getTotal(): number {
    return this.getSubtotal() + this.getShippingPrice();
  }

  getSubtotal(): number {
    return this.items.reduce((sum, item) => sum + item.product.price * item.quantity, 0);
  }

  getShippingPrice(): number {
    const option = this.shippingOptions.find(value => value.id === this.selectedShippingId);
    return option ? option.price : 0;
  }

  getCurrency(): string {
    return this.items[0]?.product.currency || 'EUR';
  }

  login(): void {
    this.authService.login().subscribe();
  }

  register(): void {
    this.authService.register().subscribe();
  }

  selectExistingAddress(id: number): void {
    this.selectedAddressId = id;
    this.useNewAddress = false;
  }

  toggleNewAddress(): void {
    this.useNewAddress = !this.useNewAddress;
    if (this.useNewAddress) {
      this.selectedAddressId = null;
    } else {
      this.locationQuery = '';
      this.locationResults = [];
    }
  }

  searchLocation(query: string): void {
    this.locationQuery = query;
    if (!query || query.trim().length < 3) {
      this.locationResults = [];
      return;
    }
    const encoded = encodeURIComponent(query.trim());
    const url = `https://api-adresse.data.gouv.fr/search/?q=${encoded}&limit=5`;
    this.isSearchingLocation = true;
    this.http.get<{
      features?: Array<{
        properties?: { label?: string; name?: string; postcode?: string; city?: string };
      }>;
    }>(url).subscribe({
      next: response => {
        const features = response.features ?? [];
        this.locationResults = features
          .map(feature => {
            const props = feature.properties;
            if (!props?.label || !props?.postcode || !props?.city) {
              return null;
            }
            return {
              label: props.label,
              line1: props.name || props.label,
              postalCode: props.postcode,
              city: props.city
            };
          })
          .filter((item): item is { label: string; line1: string; postalCode: string; city: string } => !!item);
        this.isSearchingLocation = false;
      },
      error: () => {
        this.locationResults = [];
        this.isSearchingLocation = false;
      }
    });
  }

  selectLocation(result: { label: string; line1: string; postalCode: string; city: string }): void {
    this.locationQuery = result.label;
    this.newAddress.line1 = result.line1;
    this.newAddress.city = result.city;
    this.newAddress.postalCode = result.postalCode;
    this.newAddress.country = 'FR';
    this.locationResults = [];
  }

  canPlaceOrder(): boolean {
    if (!this.authService.isAuthenticated || this.items.length === 0) {
      return false;
    }
    if (!this.selectedShippingId || !this.selectedPaymentId) {
      return false;
    }
    if (this.selectedPaymentId !== 'stripe') {
      return false;
    }
    if (this.useNewAddress) {
      return this.isNewAddressValid();
    }
    return !!this.selectedAddressId;
  }

  placeOrder(): void {
    this.orderError = '';
    this.orderStatus = '';
    if (!this.authService.isAuthenticated) {
      return;
    }
    if (!this.canPlaceOrder()) {
      if (this.selectedPaymentId && this.selectedPaymentId !== 'stripe') {
        this.orderError = 'This payment method is not available yet. Please select card payment.';
      }
      return;
    }
    if (this.isSubmitting) {
      return;
    }
    this.isSubmitting = true;
    if (this.useNewAddress) {
      const payload: AddressRequest = {
        type: 'SHIPPING',
        label: this.newAddress.label ?? '',
        fullName: this.newAddress.fullName ?? '',
        phone: this.newAddress.phone ?? '',
        line1: this.newAddress.line1 ?? '',
        line2: this.newAddress.line2 ?? '',
        city: this.newAddress.city ?? '',
        postalCode: this.newAddress.postalCode ?? '',
        country: this.newAddress.country ?? 'FR',
        defaultShipping: false,
        defaultBilling: false
      };
      this.userService.addAddress(payload).subscribe({
        next: address => {
          this.addresses = [...this.addresses, address];
          this.selectedAddressId = address.id;
          this.useNewAddress = false;
          this.submitOrder();
        },
        error: () => {
          this.orderError = 'Unable to save the address. Please try again.';
          this.isSubmitting = false;
        }
      });
      return;
    }
    this.submitOrder();
  }

  private submitOrder(): void {
    if (!this.selectedAddressId) {
      return;
    }
    const productIds = this.items.map(item => item.product.id);
    this.orderService.createOrder({
      productIds,
      addressId: this.selectedAddressId,
      shippingMethod: this.selectedShippingId,
      shippingPrice: this.getShippingPrice(),
      paymentMethod: 'STRIPE'
    }).subscribe({
      next: response => {
        this.orderStatus = 'Redirecting to payment...';
        this.orderService.createStripeCheckout(response.id).subscribe({
          next: checkout => {
            window.location.href = checkout.checkoutUrl;
          },
          error: () => {
            this.orderError = 'Unable to start payment. Please try again.';
            this.isSubmitting = false;
          }
        });
      },
      error: () => {
        this.orderError = 'Unable to create your order. Please try again.';
        this.isSubmitting = false;
      }
    });
  }

  private isNewAddressValid(): boolean {
    return !!(this.newAddress.label
      && this.newAddress.fullName
      && this.newAddress.line1
      && this.newAddress.city
      && this.newAddress.postalCode
      && this.newAddress.country);
  }
}
