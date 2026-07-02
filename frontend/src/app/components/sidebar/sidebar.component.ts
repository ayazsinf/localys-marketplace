import { Component, OnInit, output } from '@angular/core';
import { CategoryNode, CategoryService } from '../../service/category.service';

@Component({
  selector: 'app-sidebar',
  standalone: false,
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent implements OnInit {
  categories: CategoryNode[] = [];
  categorySelected = output<number | null>();
  priceRangeSelected = output<{ min: number; max: number }>();

  maxPrice = 10000;
  selectedMinPrice = 0;
  selectedMaxPrice = 10000;
  expandedCategoryIds = new Set<number>();

  constructor(private categoryService: CategoryService) {}

  ngOnInit(): void {
    this.categoryService.loadTree().subscribe({
      next: tree => {
        this.categories = tree;
      },
      error: () => {
        this.categories = [];
      }
    });
  }

  selectCategory(categoryId: number) {
    this.categorySelected.emit(categoryId);
  }

  selectCategoryRow(category: CategoryNode): void {
    this.selectCategory(category.id);
    if (this.hasChildren(category)) {
      this.toggleCategory(category.id);
    }
  }

  selectAll() {
    this.categorySelected.emit(null);
  }

  toggleCategory(categoryId: number): void {
    if (this.expandedCategoryIds.has(categoryId)) {
      this.expandedCategoryIds.delete(categoryId);
      return;
    }
    this.expandedCategoryIds.add(categoryId);
  }

  isExpanded(categoryId: number): boolean {
    return this.expandedCategoryIds.has(categoryId);
  }

  hasChildren(category: CategoryNode): boolean {
    return (category.children ?? []).length > 0;
  }

  onMinPriceChange(value: number): void {
    const min = this.normalizePrice(value);
    this.selectedMinPrice = Math.min(min, this.selectedMaxPrice);
    this.emitPriceRange();
  }

  onMaxPriceChange(value: number): void {
    const max = this.normalizePrice(value);
    this.selectedMaxPrice = Math.max(max, this.selectedMinPrice);
    this.emitPriceRange();
  }

  minPercent(): number {
    return (this.selectedMinPrice / this.maxPrice) * 100;
  }

  maxPercent(): number {
    return (this.selectedMaxPrice / this.maxPrice) * 100;
  }

  private emitPriceRange(): void {
    this.priceRangeSelected.emit({
      min: this.selectedMinPrice,
      max: this.selectedMaxPrice
    });
  }

  private normalizePrice(value: number): number {
    const price = Number(value);
    if (!Number.isFinite(price)) {
      return 0;
    }
    return Math.min(Math.max(Math.round(price), 0), this.maxPrice);
  }
}
