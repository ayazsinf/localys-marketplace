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
import { DeliveryFilter, PageResponse, ProductSearchParams, ProductService, ProductSort } from '../../service/product.service';
import { ItemCondition } from '../../service/listing.service';
import { SearchService } from '../../service/search.service';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { AuthService } from '../../service/auth.service';
import { AuthDialogService } from '../../service/auth-dialog.service';
import { SavedSearchService } from '../../service/saved-search.service';
import { SearchArea } from '../../service/location.service';

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
/** The last search area is remembered on this device. */
const AREA_STORAGE_KEY = 'localys.searchArea';
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
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly authDialogService = inject(AuthDialogService);
  private readonly savedSearchService = inject(SavedSearchService);

  readonly products = signal<Product[]>([]);
  readonly totalElements = signal(0);
  readonly isLoading = signal(false);
  readonly isLoadingMore = signal(false);
  readonly loadError = signal(false);

  readonly sortOption = signal<SortOption>('newest');
  readonly selectedMinPrice = signal(0);
  readonly selectedMaxPrice = signal(PRICE_SLIDER_MAX);
  readonly selectedCategoryId = signal<number | null>(null);
  readonly selectedConditions = signal<ItemCondition[]>([]);
  readonly selectedDelivery = signal<DeliveryFilter | null>(null);
  readonly selectedArea = signal<SearchArea | null>(loadStoredArea());

  /** Pushed to the sidebar only when filters come from the URL (e.g. opening a saved search). */
  readonly appliedPriceRange = signal<{ min: number; max: number } | null>(null);

  readonly isSaveFormOpen = signal(false);
  readonly isSavingSearch = signal(false);
  readonly saveSearchResult = signal<'saved' | 'error' | 'limit' | null>(null);
  saveSearchName = '';

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
      condition: this.selectedConditions(),
      delivery: this.selectedDelivery(),
      lat: this.selectedArea()?.lat ?? null,
      lng: this.selectedArea()?.lng ?? null,
      radiusKm: this.selectedArea()?.radiusKm ?? null,
      sort: SORT_PARAM[this.sortOption()],
      size: PAGE_SIZE,
    };
  });
  private readonly filters$ = toObservable(this.filters);

  readonly hasMoreProducts = computed(() => this.products().length < this.totalElements());

  readonly hasActiveFilters = computed(() => {
    const filters = this.filters();
    return !!filters.q || filters.categoryId != null || filters.minPrice != null || filters.maxPrice != null
      || filters.lat != null;
  });

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(params => this.applyUrlFilters(params));

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

  onConditionsChange(conditions: ItemCondition[]) {
    this.selectedConditions.set(conditions);
  }

  onDeliveryChange(delivery: DeliveryFilter | null) {
    this.selectedDelivery.set(delivery);
  }

  onAreaChange(area: SearchArea | null) {
    this.selectedArea.set(area);
    storeArea(area);
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

  openSaveSearch(): void {
    if (!this.authService.isAuthenticated) {
      this.authDialogService.openLogin().subscribe();
      return;
    }
    this.saveSearchName = (this.filters().q ?? '').slice(0, 80);
    this.saveSearchResult.set(null);
    this.isSaveFormOpen.set(true);
  }

  cancelSaveSearch(): void {
    this.isSaveFormOpen.set(false);
  }

  saveSearch(): void {
    const name = this.saveSearchName.trim();
    if (!name || this.isSavingSearch()) {
      return;
    }
    const filters = this.filters();
    this.isSavingSearch.set(true);
    this.savedSearchService.create({
      name,
      query: filters.q || null,
      categoryId: filters.categoryId ?? null,
      minPrice: filters.minPrice ?? null,
      maxPrice: filters.maxPrice ?? null,
      alertsEnabled: true
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.isSavingSearch.set(false);
          this.isSaveFormOpen.set(false);
          this.saveSearchResult.set('saved');
        },
        error: err => {
          this.isSavingSearch.set(false);
          this.saveSearchResult.set(err?.error?.code === 'SAVED_SEARCH_LIMIT' ? 'limit' : 'error');
        }
      });
  }

  /**
   * Applies q, categoryId, minPrice and maxPrice from the URL (saved searches and their alert
   * notifications link here), then drops them so later filter changes do not leave a stale URL.
   */
  private applyUrlFilters(params: ParamMap): void {
    const keys = ['q', 'categoryId', 'minPrice', 'maxPrice'];
    if (!keys.some(key => params.has(key))) {
      return;
    }
    const categoryId = this.toNumber(params.get('categoryId'));
    const minPrice = this.toNumber(params.get('minPrice'));
    const maxPrice = this.toNumber(params.get('maxPrice'));
    const min = Math.min(Math.max(minPrice ?? 0, 0), PRICE_SLIDER_MAX);
    const max = Math.min(Math.max(maxPrice ?? PRICE_SLIDER_MAX, min), PRICE_SLIDER_MAX);

    this.searchService.setSearchTerm((params.get('q') ?? '').trim());
    this.selectedCategoryId.set(categoryId);
    this.selectedMinPrice.set(min);
    this.selectedMaxPrice.set(max);
    this.appliedPriceRange.set({ min, max });
    this.saveSearchResult.set(null);

    this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
  }

  private toNumber(value: string | null): number | null {
    if (value === null || value.trim() === '') {
      return null;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  trackById = (_: number, p: Product) => p.id;
}

function loadStoredArea(): SearchArea | null {
  try {
    const raw = localStorage.getItem(AREA_STORAGE_KEY);
    const area = raw ? JSON.parse(raw) as SearchArea : null;
    return area && Number.isFinite(area.lat) && Number.isFinite(area.lng) && Number.isFinite(area.radiusKm) ? area : null;
  } catch {
    return null;
  }
}

function storeArea(area: SearchArea | null): void {
  try {
    if (area) {
      localStorage.setItem(AREA_STORAGE_KEY, JSON.stringify(area));
    } else {
      localStorage.removeItem(AREA_STORAGE_KEY);
    }
  } catch {
    // Storage may be unavailable (private mode); the filter still works for this visit.
  }
}
