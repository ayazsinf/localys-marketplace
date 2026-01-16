import {Component, inject, Inject} from '@angular/core';
import {Product} from '../../modules/product.model';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {CartService} from "../../service/cart.service";
import {environment} from "../../../environments/environment";

@Component({
  selector: 'app-product-quick-view',
  standalone: false,
  templateUrl: './product-quick-view.component.html',
  styleUrls: ['./product-quick-view.component.scss']
})
export class ProductQuickViewComponent {

  product = inject<Product>(MAT_DIALOG_DATA);
  private dialogRef = inject(MatDialogRef<ProductQuickViewComponent>);
  private cart = inject(CartService);

  addToCart(): void {
    this.cart.add(this.product, 1);
    this.dialogRef.close();
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
}
