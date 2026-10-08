import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, of, switchMap, tap } from 'rxjs';
import { Product } from '../../modules/product.model';
import { PageResponse, ProductSearchParams, ProductService, ProductSort } from '../../service/product.service';
import { SearchService } from '../../service/search.service';

type SortOption = 'newest' | 'priceLowHigh' | 'priceHighLow' | 'nameAZ';

const SORT_PARAM: Record<SortOption, ProductSort> = {
  newest: 'NEWEST',
  priceLowHigh: 'PRICE_ASC',
  priceHighLow: 'PRICE_DESC',
  nameAZ: 'NAME_ASC',
};
const PAGE_SIZE = 24;
/** Upper end of the sidebar slider; at this value there is no max-price filter. */
const PRICE_SLIDER_MAX = 10000;
const EMPTY_PAGE: PageResponse<Product> = { items: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };

@Component({
  selector: 'app-home',
  standalone: false,
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly productService = inject(ProductService);
  private readonly searchService = inject(SearchService);

  readonly products = signal<Product[]>([]);
  readonly totalElements = signal(0);
  readonly isLoading = signal(false);
  readonly isLoadingMore = signal(false);
  readonly loadError = signal(false);

  readonly sortOption = signal<SortOption>('newest');
  readonly selectedMinPrice = signal(0);
  readonly selectedMaxPrice = signal(PRICE_SLIDER_MAX);
  readonly selectedCategoryId = signal<number | null>(null);

  private readonly searchTerm = toSignal(this.searchService.searchTerm$, { initialValue: '' });
  private currentPage = 0;

  private readonly filters = computed<ProductSearchParams>(() => {
    const min = this.selectedMinPrice();
    const max = this.selectedMaxPrice();
    return {
      q: (this.searchTerm() ?? '').trim(),
      categoryId: this.selectedCategoryId(),
      minPrice: min > 0 ? min : null,
      maxPrice: max < PRICE_SLIDER_MAX ? max : null,
      sort: SORT_PARAM[this.sortOption()],
      size: PAGE_SIZE,
    };
  });
  private readonly filters$ = toObservable(this.filters);

  readonly hasMoreProducts = computed(() => this.products().length < this.totalElements());

  ngOnInit(): void {
    // Debounced so typing in the search box or dragging the price slider sends one request.
    this.filters$
      .pipe(
        debounceTime(300),
        tap(() => {
          this.isLoading.set(true);
          this.loadError.set(false);
        }),
        switchMap(filters => this.productService.search({ ...filters, page: 0 }).pipe(
          catchError(() => {
            this.loadError.set(true);
            return of(EMPTY_PAGE);
          })
        )),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(page => {
        this.currentPage = 0;
        this.products.set(page.items);
        this.totalElements.set(page.totalElements);
        this.isLoading.set(false);
      });
  }

  onCategorySelected(categoryId: number | null) {
    this.selectedCategoryId.set(categoryId ?? null);
  }

  onPriceRangeChange(range: { min: number; max: number }) {
    this.selectedMinPrice.set(range.min);
    this.selectedMaxPrice.set(range.max);
  }

  onSortChange(option: SortOption) {
    this.sortOption.set(option);
  }

  loadMoreProducts(): void {
    if (this.isLoadingMore()) {
      return;
    }
    this.isLoadingMore.set(true);
    const nextPage = this.currentPage + 1;
    this.productService.search({ ...this.filters(), page: nextPage })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => {
          this.currentPage = nextPage;
          // Skip duplicates in case new listings shifted the pages in between.
          const known = new Set(this.products().map(product => product.id));
          this.products.update(list => [...list, ...page.items.filter(product => !known.has(product.id))]);
          this.totalElements.set(page.totalElements);
          this.isLoadingMore.set(false);
        },
        error: () => {
          this.isLoadingMore.set(false);
        }
      });
  }

  trackById = (_: number, p: Product) => p.id;
}
