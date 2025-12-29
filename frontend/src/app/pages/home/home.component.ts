import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { Product } from '../../modules/product.model';
import { ProductService } from '../../service/product.service';
import { SearchService } from '../../service/search.service';

type SortOption = 'priceLowHigh' | 'priceHighLow' | 'ratingHighLow' | 'nameAZ';

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
  ) {
    // When products arrive first time, initialize max price slider once
    effect(() => {
      const list = this.products();
      if (list.length > 0 && this.selectedMaxPrice() == null) {
        const max = Math.ceil(Math.max(...list.map(p => p.price ?? 0)));
        this.selectedMaxPrice.set(max);
      }
    });
  }

  // Source of truth: signal from service
  readonly products = this.productService.products;

  // UI state as signals
  readonly sortOption = signal<SortOption>('priceLowHigh');
  readonly selectedRating = signal<number | null>(null);
  readonly selectedMaxPrice = signal<number | null>(null);
  readonly selectedCategory = signal<string>('all');

  // Search term from navbar (observable -> signal)
  private readonly searchTerm = toSignal(
      this.searchService.searchTerm$,
      { initialValue: '' }
  );

  // Derived state: categories list
  readonly categories = computed(() => {
    const list = this.products();
    const names = list
        .map(p => p.categoryName)
        .filter((x): x is string => !!x && x.trim().length > 0);

    return Array.from(new Set(names)).sort((a, b) => a.localeCompare(b));
  });

  // Derived state: filtered + sorted list
  readonly filteredProducts = computed(() => {
    let result = [...this.products()];

    // 1) category
    const cat = this.selectedCategory();
    if (cat && cat !== 'all') {
      result = result.filter(p => p.categoryName === cat);
    }

    // 2) search
    const term = (this.searchTerm() ?? '').trim().toLowerCase();
    if (term) {
      result = result.filter(p =>
          (p.name ?? '').toLowerCase().includes(term) ||
          (p.description ?? '').toLowerCase().includes(term)
      );
    }

    // 3) rating (optional)
    const rating = this.selectedRating();
    if (rating != null) {
      result = result.filter(p => (p.rating ?? 0) >= rating);
    }

    // 4) price cap
    const maxPrice = this.selectedMaxPrice();
    if (maxPrice != null) {
      result = result.filter(p => (p.price ?? 0) <= maxPrice);
    }

    // 5) sort
    switch (this.sortOption()) {
      case 'priceLowHigh':
        result.sort((a, b) => (a.price ?? 0) - (b.price ?? 0));
        break;
      case 'priceHighLow':
        result.sort((a, b) => (b.price ?? 0) - (a.price ?? 0));
        break;
      case 'ratingHighLow':
        result.sort((a, b) => (b.rating ?? 0) - (a.rating ?? 0));
        break;
      case 'nameAZ':
        result.sort((a, b) => (a.name ?? '').localeCompare(b.name ?? ''));
        break;
    }

    return result;
  });

  // Load products once
  ngOnInit() {
    this.productService
        .loadProducts()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          error: err => console.error('loadProducts error', err),
        });
  }

  // UI event handlers
  onCategorySelected(category: string) {
    this.selectedCategory.set(category ?? 'all');
  }

  onRatingSelected(rating: number) {
    // your old behavior: 5 means "all"
    this.selectedRating.set(rating === 5 ? null : rating);
  }

  onPriceChange(maxPrice: number) {
    this.selectedMaxPrice.set(maxPrice);
  }

  onSortChange(option: SortOption) {
    this.sortOption.set(option);
  }

  // Useful for *ngFor trackBy
  trackById = (_: number, p: Product) => p.id;
}
