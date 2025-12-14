import {Component, Input, output, Output} from '@angular/core';
import {Product} from "../../modules/product.model";
import {MatDialog} from "@angular/material/dialog";
import {ProductQuickViewComponent} from "../product-quick-view/product-quick-view.component";
import {CartService} from "../../service/cart.service";

@Component({
  selector: 'app-product-card',
  standalone: false,
  templateUrl: './product-card.component.html',
  styleUrl: './product-card.component.scss',
})
export class ProductCardComponent {
  @Input() product!: Product;
  add = output<Product>();

    constructor(private dialog: MatDialog,
                private cartService: CartService) {
    }
  generateStars(rating: number): string {
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


  addToCart() {
    this.cartService.add(this.product);
  }

  openQuickView(product: Product) {
    this.dialog.open(ProductQuickViewComponent, { data: product });
  }

}
