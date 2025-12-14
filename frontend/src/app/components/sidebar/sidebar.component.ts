import {Component, output} from '@angular/core';
import {ProductService} from "../../service/product.service";

@Component({
  selector: 'app-sidebar',
  standalone: false,
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent {
  categories: string[] = [];
  ratingSelected = output<number | null>();
  categorySelected = output<string>();
  maxPriceSelected = output<number>()

  selectedRating: number | null = null;

  maxPrice = 500;
  selectedMaxPrice = 500;

  constructor(private productService: ProductService) {
    this.categories = this.productService.getCategories();
  }

  selectCategory(category: string) {
    this.categorySelected.emit(category);
  }
  onRatingChange(rating: number) {
    this.ratingSelected.emit(rating);
  }


  selectAll() {
    this.categorySelected.emit('all');
  }

  generateStars(rating: number): string {
    return '★'.repeat(Math.floor(rating));
  }

  // Price slider değişince
  onPriceChange(value: number): void {
    this.selectedMaxPrice = +value;
    this.maxPriceSelected.emit(this.selectedMaxPrice);
  }
}
