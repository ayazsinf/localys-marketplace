import {Component, Input, OnInit, output, Output} from '@angular/core';
import {Product} from "../../modules/product.model";
import {MatDialog} from "@angular/material/dialog";
import {ProductQuickViewComponent} from "../product-quick-view/product-quick-view.component";
import {CartService} from "../../service/cart.service";
import {FavoritesService} from "../../service/favorites.service";
import {Router} from "@angular/router";
import {CurrentUserService} from "../../service/current-user.service";
import { AuthService } from "../../service/auth.service";
import { resolveImageUrl } from "../../utils/image-url";

@Component({
  selector: 'app-product-card',
  standalone: false,
  templateUrl: './product-card.component.html',
  styleUrl: './product-card.component.scss',
})
export class ProductCardComponent implements OnInit {
  @Input() product!: Product;



  add = output<Product>();

    constructor(private dialog: MatDialog,
                private cartService: CartService,
                private favoritesService: FavoritesService,
                private router: Router,
                private currentUserService: CurrentUserService,
                private authService: AuthService) {
    }
  ngOnInit() {
    this.product.rating=Math.floor(Math.random() * 5) + 1;
    this.favoritesService.ensureFavoriteIds();
    if (this.authService.isAuthenticated) {
      this.currentUserService.ensureLoaded();
    }
  }
  generateStars(rating: number ): string {
    const maxStars = 5;
    const fullStars = Math.floor(rating);
    const hasHalfStar = rating % 1 >= 0.5;

    let result = '';

    // Dolu yıldızlar
    for (let i = 0; i < fullStars; i++) {
      result += '★';
    }

    // Yarım yıldız
    if (hasHalfStar) {
      result += '☆'; // istersen burada özel bir ikon da kullanabilirsin
    }

    // Boş yıldızlar
    const emptyStars = maxStars - fullStars - (hasHalfStar ? 1 : 0);
    for (let i = 0; i < emptyStars; i++) {
      result += '☆';
    }

    return result;
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
    return resolveImageUrl(url);
  }

  isOwner(): boolean {
    const currentUserId = this.currentUserService.userId;
    return !!currentUserId && this.product.vendorUserId === currentUserId;
  }

}
