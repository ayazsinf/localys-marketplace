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
  ) {
    effect(() => {
      const list = this.products();
      if (list.length > 0 && this.selectedMaxPrice() == null) {
        const max = Math.ceil(Math.max(...list.map(p => p.price ?? 0)));
        this.selectedMaxPrice.set(max);
      }
    });
  }

  readonly products = this.productService.products;

  readonly sortOption = signal<SortOption>('priceLowHigh');
  readonly selectedMaxPrice = signal<number | null>(null);
  readonly selectedCategory = signal<string>('all');

  private readonly searchTerm = toSignal(
      this.searchService.searchTerm$,
      { initialValue: '' }
  );

  readonly categories = computed(() => {
    const list = this.products();
    const names = list
        .map(p => p.categoryName)
        .filter((x): x is string => !!x && x.trim().length > 0);

    return Array.from(new Set(names)).sort((a, b) => a.localeCompare(b));
  });

  readonly filteredProducts = computed(() => {
    let result = [...this.products()];

    const cat = this.selectedCategory();
    if (cat && cat !== 'all') {
      result = result.filter(p => p.categoryName === cat);
    }

    const term = (this.searchTerm() ?? '').trim().toLowerCase();
    if (term) {
      result = result.filter(p =>
          (p.name ?? '').toLowerCase().includes(term) ||
          (p.description ?? '').toLowerCase().includes(term)
      );
    }

    const maxPrice = this.selectedMaxPrice();
    if (maxPrice != null) {
      result = result.filter(p => (p.price ?? 0) <= maxPrice);
    }

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

  ngOnInit() {
    this.productService
        .loadProducts()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          error: err => console.error('loadProducts error', err),
        });
  }

  onCategorySelected(category: string) {
    this.selectedCategory.set(category ?? 'all');
  }

  onPriceChange(maxPrice: number) {
    this.selectedMaxPrice.set(maxPrice);
  }

  onSortChange(option: SortOption) {
    this.sortOption.set(option);
  }

  trackById = (_: number, p: Product) => p.id;
}
