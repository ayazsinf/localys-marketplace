import { ChangeDetectorRef, Component, DestroyRef, Input, OnInit, inject, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, catchError, debounceTime, of, switchMap } from 'rxjs';
import { CategoryNode, CategoryService } from '../../service/category.service';
import { ITEM_CONDITIONS, ItemCondition } from '../../service/listing.service';
import { DeliveryFilter } from '../../service/product.service';
import {
  DEFAULT_RADIUS_KM,
  LocationResult,
  LocationService,
  SEARCH_RADII_KM,
  SearchArea
} from '../../service/location.service';

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
  conditionsSelected = output<ItemCondition[]>();
  deliverySelected = output<DeliveryFilter | null>();
  areaSelected = output<SearchArea | null>();

  readonly conditions = ITEM_CONDITIONS;
  readonly deliveryOptions: Array<{ value: DeliveryFilter | null; labelKey: string }> = [
    { value: null, labelKey: 'FILTERS.DELIVERY_ANY' },
    { value: 'HAND', labelKey: 'DELIVERY.HAND' },
    { value: 'SHIPPING', labelKey: 'DELIVERY.SHIPPING' }
  ];
  selectedConditions = new Set<ItemCondition>();
  selectedDelivery: DeliveryFilter | null = null;

  readonly radii = SEARCH_RADII_KM;
  locationQuery = '';
  locationResults: LocationResult[] = [];
  selectedArea: SearchArea | null = null;
  radiusKm = DEFAULT_RADIUS_KM;
  isLocating = false;
  locationError: string | null = null;
  private readonly locationQuery$ = new Subject<string>();
  private readonly destroyRef = inject(DestroyRef);

  maxPrice = 10000;
  selectedMinPrice = 0;
  selectedMaxPrice = 10000;
  expandedCategoryIds = new Set<number>();

  /** Sets the slider from outside, e.g. when a saved search is opened. */
  @Input() set priceRange(range: { min: number; max: number } | null) {
    if (range) {
      this.selectedMinPrice = this.normalizePrice(range.min);
      this.selectedMaxPrice = Math.max(this.normalizePrice(range.max), this.selectedMinPrice);
    }
  }

  /** Sets the condition boxes from outside, e.g. when a saved search is opened. */
  @Input() set appliedConditions(conditions: ItemCondition[] | null) {
    if (conditions) {
      this.selectedConditions = new Set(conditions);
    }
  }

  /** Wrapped so that applying "any" again still reaches the setter. */
  @Input() set appliedDelivery(delivery: { value: DeliveryFilter | null } | null) {
    if (delivery) {
      this.selectedDelivery = delivery.value;
    }
  }

  /** Sets the area from outside, e.g. when a saved search is opened. */
  @Input() set area(area: SearchArea | null) {
    this.selectedArea = area;
    this.locationQuery = area?.label ?? '';
    if (area) {
      this.radiusKm = area.radiusKm;
    }
  }

  constructor(
    private categoryService: CategoryService,
    private locationService: LocationService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.locationQuery$
      .pipe(
        debounceTime(250),
        switchMap(query => this.locationService.search(query).pipe(catchError(() => of([])))),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(results => {
        this.locationResults = results;
        this.cdr.markForCheck();
      });

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

  toggleCondition(condition: ItemCondition): void {
    if (this.selectedConditions.has(condition)) {
      this.selectedConditions.delete(condition);
    } else {
      this.selectedConditions.add(condition);
    }
    this.conditionsSelected.emit([...this.selectedConditions]);
  }

  selectDelivery(delivery: DeliveryFilter | null): void {
    this.selectedDelivery = delivery;
    this.deliverySelected.emit(delivery);
  }

  onLocationInput(query: string): void {
    this.locationQuery = query;
    this.locationError = null;
    this.locationQuery$.next(query);
  }

  selectLocation(result: LocationResult): void {
    this.locationResults = [];
    this.locationQuery = result.label;
    this.setArea({ label: result.label, lat: result.latitude, lng: result.longitude, radiusKm: this.radiusKm });
  }

  async useMyLocation(): Promise<void> {
    this.isLocating = true;
    this.locationError = null;
    try {
      const position = await this.locationService.currentPosition();
      this.locationQuery = '';
      this.locationResults = [];
      this.setArea({ label: '', lat: position.lat, lng: position.lng, radiusKm: this.radiusKm });
    } catch {
      this.locationError = 'FILTERS.LOCATION_DENIED';
    } finally {
      this.isLocating = false;
      this.cdr.markForCheck();
    }
  }

  onRadiusChange(radiusKm: number): void {
    this.radiusKm = Number(radiusKm);
    if (this.selectedArea) {
      this.setArea({ ...this.selectedArea, radiusKm: this.radiusKm });
    }
  }

  clearArea(): void {
    this.locationQuery = '';
    this.locationResults = [];
    this.setArea(null);
  }

  private setArea(area: SearchArea | null): void {
    this.selectedArea = area;
    this.areaSelected.emit(area);
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
