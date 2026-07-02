import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { Product } from '../../modules/product.model';
import { ProductService } from '../../service/product.service';
import { SearchService } from '../../service/search.service';

type SortOption = 'priceLowHigh' | 'priceHighLow' | 'nameAZ';

@Component({
  selector: 'app-home',
  standalone: false,
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  private readonly destroyRef = inject(DestroyRef);

  constructor(
      private readonly productService: ProductService,
      private readonly searchService: SearchService
  ) {}

  readonly products = this.productService.products;

  readonly sortOption = signal<SortOption>('priceLowHigh');
  readonly selectedMinPrice = signal(0);
  readonly selectedMaxPrice = signal(10000);
  readonly selectedCategoryId = signal<number | null>(null);
  readonly visibleProductCount = signal(9);

  private readonly searchTerm = toSignal(
      this.searchService.searchTerm$,
      { initialValue: '' }
  );

  readonly filteredProducts = computed(() => {
    let result = [...this.products()];

    const categoryId = this.selectedCategoryId();
    if (categoryId) {
      result = result.filter(p => (p.categoryPathIds ?? []).includes(categoryId));
    }

    const term = (this.searchTerm() ?? '').trim().toLowerCase();
    if (term) {
      result = result.filter(p =>
          (p.name ?? '').toLowerCase().includes(term) ||
          (p.description ?? '').toLowerCase().includes(term)
      );
    }

    const minPrice = this.selectedMinPrice();
    const maxPrice = this.selectedMaxPrice();
    result = result.filter(p => {
      const price = p.price ?? 0;
      return price >= minPrice && price <= maxPrice;
    });

    switch (this.sortOption()) {
      case 'priceLowHigh':
        result.sort((a, b) => (a.price ?? 0) - (b.price ?? 0));
        break;
      case 'priceHighLow':
        result.sort((a, b) => (b.price ?? 0) - (a.price ?? 0));
        break;
      case 'nameAZ':
        result.sort((a, b) => (a.name ?? '').localeCompare(b.name ?? ''));
        break;
    }

    return result;
  });

  readonly visibleProducts = computed(() =>
      this.filteredProducts().slice(0, this.visibleProductCount())
  );

  readonly hasMoreProducts = computed(() =>
      this.visibleProductCount() < this.filteredProducts().length
  );

  ngOnInit() {
    this.productService
        .loadProducts()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          error: err => console.error('loadProducts error', err),
        });

    this.searchService.searchTerm$
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.resetVisibleProducts());
  }

  onCategorySelected(categoryId: number | null) {
    this.selectedCategoryId.set(categoryId ?? null);
    this.resetVisibleProducts();
  }

  onPriceRangeChange(range: { min: number; max: number }) {
    this.selectedMinPrice.set(range.min);
    this.selectedMaxPrice.set(range.max);
    this.resetVisibleProducts();
  }

  onSortChange(option: SortOption) {
    this.sortOption.set(option);
    this.resetVisibleProducts();
  }

  loadMoreProducts(): void {
    this.visibleProductCount.update(count => count + 9);
  }

  private resetVisibleProducts(): void {
    this.visibleProductCount.set(9);
  }

  trackById = (_: number, p: Product) => p.id;
}
