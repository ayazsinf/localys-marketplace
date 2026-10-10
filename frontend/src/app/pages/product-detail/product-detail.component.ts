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
import { AuthDialogService } from '../../service/auth-dialog.service';
import { REPORT_REASONS, ReportReason, ReportService } from '../../service/report.service';
import { MIN_OFFER_RATIO, Offer, OfferService } from '../../service/offer.service';

@Component({
  selector: 'app-product-detail',
  templateUrl: './product-detail.component.html',
  styleUrl: './product-detail.component.scss',
  standalone: false
})
export class ProductDetailComponent implements OnInit, OnDestroy, AfterViewInit {
  product: ProductDetail | null = null;
  selectedImage: string | null = null;
  readonly reportReasons = REPORT_REASONS;
  reportReason: ReportReason = 'SCAM';
  reportDetails = '';
  isReportOpen = false;
  isReporting = false;
  reportSent = false;
  reportError = '';
  myOffer: Offer | null = null;
  isOfferFormOpen = false;
  isOfferBusy = false;
  offerAmount = 0;
  offerError = '';
  private map: L.Map | null = null;
  private mapObserver: ResizeObserver | null = null;
  private viewReady = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private productService: ProductService,
    private messagingService: MessagingService,
    private authService: AuthService,
    private authDialogService: AuthDialogService,
    private cartService: CartService,
    private reportService: ReportService,
    private offerService: OfferService,
    private currentUserService: CurrentUserService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    if (this.authService.isAuthenticated) {
      this.currentUserService.ensureLoaded();
    }
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
        this.loadMyOffer(product.id);
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

  get sellerName(): string {
    return this.product?.vendorShopName || this.product?.vendorDisplayName || '';
  }

  get sellerInitial(): string {
    return (this.sellerName.trim()[0] ?? '?').toUpperCase();
  }

  /** Offers more than 25% under the asking price are refused (same rule as the backend). */
  get minimumOffer(): number {
    // toFixed strips float noise (e.g. 7500.0000001) before rounding up to the cent.
    return this.product ? Math.ceil(Number((this.product.price * MIN_OFFER_RATIO * 100).toFixed(6))) / 100 : 0;
  }

  openOfferForm(): void {
    if (!this.authService.isAuthenticated) {
      this.authDialogService.openLogin().subscribe();
      return;
    }
    this.offerError = '';
    // Suggest a typical first offer, 10% under the asking price.
    this.offerAmount = this.product ? Math.floor(this.product.price * 0.9 * 100) / 100 : 0;
    this.isOfferFormOpen = true;
  }

  submitOffer(): void {
    if (!this.product || this.isOfferBusy) {
      return;
    }
    this.isOfferBusy = true;
    this.offerError = '';
    this.offerService.make(this.product.id, Number(this.offerAmount)).subscribe({
      next: offer => {
        this.myOffer = offer;
        this.isOfferBusy = false;
        this.isOfferFormOpen = false;
        this.cdr.detectChanges();
      },
      error: err => {
        this.isOfferBusy = false;
        this.offerError = err?.error?.code === 'INVALID_OFFER_AMOUNT' ? 'OFFERS.ERROR_AMOUNT' : 'OFFERS.ERROR_SEND';
        this.cdr.detectChanges();
      }
    });
  }

  buyWithOffer(): void {
    if (!this.myOffer || !this.product || this.isOfferBusy) {
      return;
    }
    this.isOfferBusy = true;
    const productId = this.product.id;
    this.offerService.addToCart(this.myOffer.id).subscribe({
      next: () => {
        this.cartService.refresh().subscribe();
        this.router.navigate(['/checkout'], { queryParams: { items: productId } });
      },
      error: () => {
        this.isOfferBusy = false;
        this.offerError = 'OFFERS.ERROR_BUY';
        this.cdr.detectChanges();
      }
    });
  }

  private loadMyOffer(productId: number): void {
    if (!this.authService.isAuthenticated) {
      return;
    }
    this.offerService.mine(productId).subscribe({
      next: offer => {
        this.myOffer = offer;
        this.cdr.detectChanges();
      },
      error: () => this.myOffer = null
    });
  }

  openReport(): void {
    if (!this.authService.isAuthenticated) {
      this.authDialogService.openLogin().subscribe();
      return;
    }
    this.reportError = '';
    this.isReportOpen = true;
  }

  submitReport(): void {
    if (!this.product || this.isReporting) {
      return;
    }
    this.isReporting = true;
    this.reportError = '';
    this.reportService.reportListing(this.product.id, this.reportReason, this.reportDetails.trim()).subscribe({
      next: () => this.finishReport(),
      error: err => {
        this.isReporting = false;
        if (err?.error?.code === 'ALREADY_REPORTED') {
          this.finishReport();
          return;
        }
        this.reportError = 'REPORT.ERROR';
        this.cdr.detectChanges();
      }
    });
  }

  private finishReport(): void {
    this.isReporting = false;
    this.isReportOpen = false;
    this.reportSent = true;
    this.cdr.detectChanges();
  }

  selectImage(url: string): void {
    this.selectedImage = url;
  }

  messageSeller(): void {
    if (!this.product?.vendorUserId) {
      return;
    }
    if (!this.authService.isAuthenticated) {
      this.authDialogService.openLogin().subscribe();
      return;
    }
    this.messagingService.createConversationWith(this.product.vendorUserId, this.product.id).subscribe({
      next: conversation => {
        this.router.navigate(['/messages'], { queryParams: { conversationId: conversation.id } });
      }
    });
  }

  addToCart(): void {
    if (!this.product) {
      return;
    }
    if (this.authService.isAuthenticated && !this.currentUserService.userId) {
      this.currentUserService.ensureLoaded();
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
    if (this.authService.isAuthenticated && !this.currentUserService.userId) {
      this.currentUserService.ensureLoaded();
      return;
    }
    if (this.isOwner()) {
      return;
    }
    if (!this.authService.isAuthenticated) {
      this.authDialogService.openLogin().subscribe();
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
