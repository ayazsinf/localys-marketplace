import {Component, Input, OnInit, output, Output} from '@angular/core';
import {Product} from "../../modules/product.model";
import {MatDialog} from "@angular/material/dialog";
import {ProductQuickViewComponent} from "../product-quick-view/product-quick-view.component";
import {CartService} from "../../service/cart.service";
import {environment} from "../../../environments/environment";
import {FavoritesService} from "../../service/favorites.service";
import {Router} from "@angular/router";
import {CurrentUserService} from "../../service/current-user.service";

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
                private currentUserService: CurrentUserService) {
    }
  ngOnInit() {
    this.product.rating=Math.floor(Math.random() * 5) + 1;
    this.favoritesService.ensureFavoriteIds();
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
