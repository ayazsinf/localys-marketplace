import { Component, Input, OnInit, output } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { Product } from '../../modules/product.model';
import { ProductQuickViewComponent } from '../product-quick-view/product-quick-view.component';
import { CartService } from '../../service/cart.service';
import { environment } from '../../../environments/environment';
import { FavoritesService } from '../../service/favorites.service';
import { CurrentUserService } from '../../service/current-user.service';
import { AuthService } from '../../service/auth.service';
import { AuthDialogService } from '../../service/auth-dialog.service';

@Component({
  selector: 'app-product-card',
  standalone: false,
  templateUrl: './product-card.component.html',
  styleUrl: './product-card.component.scss',
})
export class ProductCardComponent implements OnInit {
  @Input() product!: Product;

  add = output<Product>();

  constructor(
    private dialog: MatDialog,
    private cartService: CartService,
    private favoritesService: FavoritesService,
    private router: Router,
    private currentUserService: CurrentUserService,
    private authService: AuthService,
    private authDialogService: AuthDialogService
  ) {}

  ngOnInit() {
    if (this.authService.isAuthenticated) {
      this.favoritesService.ensureFavoriteIds();
      this.currentUserService.ensureLoaded();
    }
  }

  addToCart(event?: Event) {
    event?.stopPropagation();
    if (this.authService.isAuthenticated && !this.currentUserService.userId) {
      this.currentUserService.ensureLoaded();
      return;
    }
    if (this.isOwner()) {
      return;
    }
    this.cartService.add(this.product);
  }

  openQuickView(product: Product, event?: Event) {
    event?.stopPropagation();
    this.dialog.open(ProductQuickViewComponent, { data: product });
  }

  isFavorite(): boolean {
    return this.favoritesService.isFavorite(this.product.id);
  }

  toggleFavorite(event: Event) {
    event.stopPropagation();
    if (!this.authService.isAuthenticated) {
      this.authDialogService.openLogin().subscribe(authenticated => {
        if (authenticated) {
          this.favoritesService.ensureFavoriteIds();
        }
      });
      return;
    }
    if (this.authService.isAuthenticated && !this.currentUserService.userId) {
      this.currentUserService.ensureLoaded();
      return;
    }
    if (this.isOwner()) {
      return;
    }
    this.favoritesService.toggleFavorite(this.product.id).subscribe({
      error: () => {}
    });
  }

  openDetails() {
    this.router.navigate(['/products', this.product.id]);
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

  isOwner(): boolean {
    const currentUserId = this.currentUserService.userId;
    return !!currentUserId && this.product.vendorUserId === currentUserId;
  }
}
