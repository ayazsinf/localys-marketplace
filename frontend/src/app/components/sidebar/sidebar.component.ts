import { Component, output } from '@angular/core';
import { ProductService } from '../../service/product.service';

@Component({
  selector: 'app-sidebar',
  standalone: false,
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent {
  categories: string[] = [];
  categorySelected = output<string>();
  maxPriceSelected = output<number>();

  maxPrice = 500;
  selectedMaxPrice = 500;

  constructor(private productService: ProductService) {
    this.categories = this.productService.getCategories();
  }

  selectCategory(category: string) {
    this.categorySelected.emit(category);
  }

  selectAll() {
    this.categorySelected.emit('all');
  }

  onPriceChange(value: number): void {
    this.selectedMaxPrice = +value;
    this.maxPriceSelected.emit(this.selectedMaxPrice);
  }
}
