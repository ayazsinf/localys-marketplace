import { AfterViewInit, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../service/auth.service';
import { CartService } from '../../service/cart.service';
import { CurrentUserService } from '../../service/current-user.service';
import { MessagingService } from '../../service/messaging.service';
import { ProductDetail, ProductService } from '../../service/product.service';
import { environment } from '../../../environments/environment';
import * as L from 'leaflet';
import { Product } from '../../modules/product.model';

@Component({
  selector: 'app-product-detail',
  templateUrl: './product-detail.component.html',
  styleUrl: './product-detail.component.scss',
  standalone: false
})
export class ProductDetailComponent implements OnInit, OnDestroy, AfterViewInit {
  product: ProductDetail | null = null;
  selectedImage: string | null = null;
  private map: L.Map | null = null;
  private mapObserver: ResizeObserver | null = null;
  private viewReady = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private productService: ProductService,
    private messagingService: MessagingService,
    private authService: AuthService,
    private cartService: CartService,
    private currentUserService: CurrentUserService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isFinite(id)) {
      this.router.navigate(['/']);
      return;
    }

    this.productService.getProductDetail(id).subscribe({
      next: product => {
        this.product = product;
        this.selectedImage = product.imageUrls?.[0] ?? null;
        this.cdr.detectChanges();
        this.tryInitMap();
      },
      error: () => {
        this.router.navigate(['/']);
      }
    });
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.tryInitMap();
  }

  ngOnDestroy(): void {
    this.destroyMap();
  }

  selectImage(url: string): void {
    this.selectedImage = url;
  }

  messageSeller(): void {
    if (!this.product?.vendorUserId) {
      return;
    }
    if (!this.authService.isAuthenticated) {
      this.authService.login().subscribe();
      return;
    }
    this.messagingService.createConversationWith(this.product.vendorUserId).subscribe({
      next: conversation => {
        this.router.navigate(['/messages'], { queryParams: { conversationId: conversation.id } });
      }
    });
  }

  addToCart(): void {
    if (!this.product) {
      return;
    }
    if (this.isOwner()) {
      return;
    }
    this.cartService.addToCart(this.toCartProduct(this.product)).subscribe();
  }

  buyNow(): void {
    if (!this.product) {
      return;
    }
    if (this.isOwner()) {
      return;
    }
    if (!this.authService.isAuthenticated) {
      this.authService.login().subscribe();
      return;
    }
    this.cartService.addToCart(this.toCartProduct(this.product)).subscribe({
      next: () => {
        this.router.navigate(['/checkout'], {
          queryParams: { items: this.product?.id }
        });
      }
    });
  }

  isOwner(): boolean {
    const userId = this.currentUserService.userId;
    return !!(userId && this.product?.vendorUserId && userId === this.product.vendorUserId);
  }

  private initMap(): void {
    this.destroyMap();

    const container = document.getElementById('listing-map');
    if (!container) {
      return;
    }

    const lat = this.product?.latitude;
    const lng = this.product?.longitude;
    const hasLocation = typeof lat === 'number' && typeof lng === 'number';
    const fallbackLat = 41.0082;
    const fallbackLng = 28.9784;
    const centerLat = hasLocation ? lat : fallbackLat;
    const centerLng = hasLocation ? lng : fallbackLng;

    this.map = L.map(container, {
      zoomControl: true,
      attributionControl: true
    }).setView([centerLat, centerLng], hasLocation ? 13 : 10);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors'
    }).addTo(this.map);

    if (hasLocation) {
      L.marker([centerLat, centerLng], { icon: this.createMarkerIcon() }).addTo(this.map);
    }

    if (typeof ResizeObserver !== 'undefined') {
      this.mapObserver = new ResizeObserver(() => {
        if (this.map) {
          this.map.invalidateSize();
        }
      });
      this.mapObserver.observe(container);
    }

    setTimeout(() => {
      if (this.map) {
        this.map.invalidateSize();
      }
    }, 300);
  }

  private destroyMap(): void {
    if (this.mapObserver) {
      this.mapObserver.disconnect();
      this.mapObserver = null;
    }
    if (this.map) {
      this.map.remove();
      this.map = null;
    }
  }

  private tryInitMap(): void {
    if (!this.viewReady || !this.product) {
      return;
    }
    requestAnimationFrame(() => this.initMap());
  }

  private createMarkerIcon(): L.Icon {
    const iconUrl = new URL('/assets/leaflet/marker-icon.png', window.location.origin).toString();
    const iconRetinaUrl = new URL('/assets/leaflet/marker-icon-2x.png', window.location.origin).toString();
    const shadowUrl = new URL('/assets/leaflet/marker-shadow.png', window.location.origin).toString();
    return L.icon({
      iconUrl,
      iconRetinaUrl,
      shadowUrl,
      iconSize: [25, 41],
      iconAnchor: [12, 41],
      shadowSize: [41, 41]
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

  private toCartProduct(product: ProductDetail): Product {
    return {
      id: product.id,
      name: product.name,
      description: product.description,
      price: product.price,
      currency: product.currency,
      imageUrls: product.imageUrls ?? [],
      categoryName: product.categoryName ?? '',
      inStock: product.active,
      stockQty: product.stockQty ?? undefined,
      vendorUserId: product.vendorUserId ?? null
    };
  }
}
