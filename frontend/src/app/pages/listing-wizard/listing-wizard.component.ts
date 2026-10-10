import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import * as L from 'leaflet';
import { Observable, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CategoryNode, CategoryService } from '../../service/category.service';
import { ITEM_CONDITIONS, ItemCondition, Listing, ListingRequest, ListingService } from '../../service/listing.service';
import { getCurrencyForCountry, getDefaultCountryForLanguage, MARKET_OPTIONS } from '../../shared/market';

interface LocationResult {
  label: string;
  latitude: number;
  longitude: number;
}

type StepId = 'category' | 'details' | 'photos' | 'price' | 'location' | 'preview';

const STEPS: StepId[] = ['category', 'details', 'photos', 'price', 'location', 'preview'];
const MAX_IMAGES = 10;
const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const ALLOWED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif']);
/** Same minimum the moderation policy uses before a listing can be auto-approved. */
const MIN_DESCRIPTION_LENGTH = 20;
const FALLBACK_LAT = 48.8566;
const FALLBACK_LNG = 2.3522;

/**
 * Step-by-step listing creation and editing, like "Déposer une annonce" on classifieds sites:
 * category and title, details and condition, photos, price, location and delivery, preview.
 */
@Component({
  selector: 'app-listing-wizard',
  templateUrl: './listing-wizard.component.html',
  styleUrl: './listing-wizard.component.scss',
  standalone: false
})
export class ListingWizardComponent implements OnInit, OnDestroy {
  readonly steps = STEPS;
  readonly conditions = ITEM_CONDITIONS;
  readonly marketOptions = MARKET_OPTIONS;
  readonly minDescriptionLength = MIN_DESCRIPTION_LENGTH;
  readonly maxImages = MAX_IMAGES;

  stepIndex = 0;
  editingId: number | null = null;
  isLoading = false;
  isSaving = false;
  errorMessage = '';
  /** Validation message of the current step, shown when "Next" is refused. */
  stepError = '';

  categoryTree: CategoryNode[] = [];
  selectedRootId: number | null = null;

  existingImages: string[] = [];
  newImages: File[] = [];
  newImagePreviews: string[] = [];

  locationQuery = '';
  locationResults: LocationResult[] = [];

  form: ListingRequest = this.emptyForm();

  private map: L.Map | null = null;
  private marker: L.Marker | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private listingService: ListingService,
    private categoryService: CategoryService,
    private http: HttpClient,
    private translateService: TranslateService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.editingId = Number.isFinite(id) && id > 0 ? id : null;
    this.categoryService.loadTree().subscribe({
      next: tree => {
        this.categoryTree = tree;
        this.syncRootWithCategory();
        this.cdr.detectChanges();
      },
      error: () => this.categoryTree = []
    });
    if (this.editingId) {
      this.loadListing(this.editingId);
    }
  }

  ngOnDestroy(): void {
    this.destroyMap();
    this.newImagePreviews.forEach(preview => URL.revokeObjectURL(preview));
  }

  get step(): StepId {
    return this.steps[this.stepIndex];
  }

  get rootCategories(): CategoryNode[] {
    return this.categoryTree;
  }

  /** Selectable (leaf) categories under the chosen top-level category. */
  get leafCategories(): CategoryNode[] {
    const root = this.categoryTree.find(node => node.id === this.selectedRootId);
    if (!root) {
      return [];
    }
    return root.children?.length ? this.leaves(root.children) : [root];
  }

  get selectedCategory(): CategoryNode | null {
    return this.findCategory(this.categoryTree, this.form.categoryId ?? null);
  }

  get imageCount(): number {
    return this.existingImages.length + this.newImages.length;
  }

  get previewImage(): string | null {
    return this.existingImages[0] ? this.imageUrl(this.existingImages[0]) : this.newImagePreviews[0] ?? null;
  }

  selectRoot(node: CategoryNode): void {
    this.selectedRootId = node.id;
    if (!node.children?.length) {
      this.form.categoryId = node.id;
    } else if (!this.leaves(node.children).some(leaf => leaf.id === this.form.categoryId)) {
      this.form.categoryId = null;
    }
  }

  selectCategory(node: CategoryNode): void {
    this.form.categoryId = node.id;
  }

  goTo(index: number): void {
    // Steps can be revisited freely; jumping ahead still validates each step in between.
    while (this.stepIndex < index) {
      if (!this.validateStep(this.step)) {
        return;
      }
      this.stepIndex++;
    }
    this.stepIndex = index;
    this.stepError = '';
    this.afterStepChange();
  }

  next(): void {
    if (!this.validateStep(this.step)) {
      return;
    }
    this.stepError = '';
    this.stepIndex = Math.min(this.stepIndex + 1, this.steps.length - 1);
    this.afterStepChange();
  }

  back(): void {
    this.stepError = '';
    this.stepIndex = Math.max(this.stepIndex - 1, 0);
    this.afterStepChange();
  }

  onCountryChange(country: string): void {
    this.form.country = country;
    this.form.currency = getCurrencyForCountry(country);
  }

  onImagesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = input.files ? Array.from(input.files) : [];
    input.value = '';
    this.stepError = '';
    for (const file of files) {
      if (!ALLOWED_IMAGE_TYPES.has(file.type)) {
        this.stepError = 'LISTINGS.ERROR_IMAGE_TYPE';
        continue;
      }
      if (file.size > MAX_IMAGE_BYTES) {
        this.stepError = 'LISTINGS.ERROR_IMAGE_SIZE';
        continue;
      }
      if (this.imageCount >= MAX_IMAGES) {
        this.stepError = 'LISTINGS.ERROR_IMAGE_LIMIT';
        break;
      }
      this.newImages.push(file);
      this.newImagePreviews.push(URL.createObjectURL(file));
    }
  }

  removeNewImage(index: number): void {
    this.newImages.splice(index, 1);
    const [preview] = this.newImagePreviews.splice(index, 1);
    if (preview) {
      URL.revokeObjectURL(preview);
    }
  }

  removeExistingImage(url: string): void {
    if (!this.editingId) {
      return;
    }
    this.listingService.removeListingImage(this.editingId, url).subscribe({
      next: remaining => {
        this.existingImages = remaining;
        this.cdr.detectChanges();
      },
      error: () => {
        this.stepError = 'WIZARD.ERROR_REMOVE_IMAGE';
        this.cdr.detectChanges();
      }
    });
  }

  searchLocation(query: string): void {
    this.locationQuery = query;
    this.form.locationText = query.trim() || null;
    this.form.latitude = null;
    this.form.longitude = null;
    if (query.trim().length < 3) {
      this.locationResults = [];
      return;
    }
    this.http.get<{ results?: LocationResult[] }>(`${environment.apiUrl}/locations/search`, {
      params: { q: query.trim() }
    }).subscribe({
      next: response => {
        this.locationResults = response.results ?? [];
        this.cdr.detectChanges();
      },
      error: () => this.locationResults = []
    });
  }

  selectLocation(result: LocationResult): void {
    this.form.locationText = result.label;
    this.form.latitude = result.latitude;
    this.form.longitude = result.longitude;
    this.locationQuery = result.label;
    this.locationResults = [];
    this.updateMap();
  }

  publish(): void {
    for (const step of this.steps) {
      if (!this.validateStep(step)) {
        this.stepIndex = this.steps.indexOf(step);
        this.afterStepChange();
        return;
      }
    }
    this.isSaving = true;
    this.errorMessage = '';
    const country = this.form.country || this.defaultCountry();
    const payload: ListingRequest = {
      ...this.form,
      name: this.form.name.trim(),
      description: this.form.description?.trim() ?? '',
      brand: this.form.brand?.trim() || null,
      country,
      currency: getCurrencyForCountry(country)
    };
    const save$ = this.editingId
      ? this.listingService.updateListing(this.editingId, payload)
      : this.listingService.createListing(payload);

    save$.subscribe({
      next: listing => {
        this.uploadNewImages(listing.id).subscribe({
          next: () => this.router.navigate(['/listings'], { queryParams: { published: this.editingId ? 'updated' : 'created' } }),
          error: err => {
            // The listing is saved; send the seller to edit it so the photos can be retried.
            this.isSaving = false;
            this.editingId = listing.id;
            this.errorMessage = this.uploadError(err?.error?.code);
            this.cdr.detectChanges();
          }
        });
      },
      error: err => {
        this.isSaving = false;
        this.errorMessage = err?.error?.code === 'LISTING_NOT_REACTIVATABLE'
          ? 'LISTINGS.ERROR_NOT_REACTIVATABLE'
          : 'LISTINGS.ERROR_SAVE';
        this.cdr.detectChanges();
      }
    });
  }

  imageUrl(url: string): string {
    if (url.startsWith('http://') || url.startsWith('https://')) {
      return url;
    }
    return `${environment.apiUrl}${url.startsWith('/') ? url : `/${url}`}`;
  }

  private validateStep(step: StepId): boolean {
    let error = '';
    switch (step) {
      case 'category':
        if (!this.form.name?.trim()) {
          error = 'WIZARD.ERROR_TITLE';
        } else if (!this.form.categoryId) {
          error = 'WIZARD.ERROR_CATEGORY';
        }
        break;
      case 'details':
        if (!this.form.condition) {
          error = 'WIZARD.ERROR_CONDITION';
        } else if ((this.form.description?.trim().length ?? 0) < MIN_DESCRIPTION_LENGTH) {
          error = 'WIZARD.ERROR_DESCRIPTION';
        }
        break;
      case 'photos':
        if (this.imageCount === 0) {
          error = 'WIZARD.ERROR_PHOTOS';
        }
        break;
      case 'price':
        if (!(Number(this.form.price) > 0)) {
          error = 'WIZARD.ERROR_PRICE';
        } else if (!(Number(this.form.stockQty) >= 1)) {
          error = 'WIZARD.ERROR_QUANTITY';
        }
        break;
      case 'location':
        if (!this.form.locationText?.trim()) {
          error = 'WIZARD.ERROR_LOCATION';
        } else if (!this.form.handDelivery && !this.form.shippingAvailable) {
          error = 'WIZARD.ERROR_DELIVERY';
        }
        break;
    }
    this.stepError = error;
    return !error;
  }

  private afterStepChange(): void {
    this.destroyMap();
    if (this.step === 'location') {
      setTimeout(() => this.initMap(), 0);
    }
    window.scrollTo({ top: 0 });
  }

  private loadListing(id: number): void {
    this.isLoading = true;
    this.listingService.loadListings().subscribe({
      next: listings => {
        const listing = listings.find(item => item.id === id);
        this.isLoading = false;
        if (!listing) {
          this.errorMessage = 'WIZARD.ERROR_NOT_FOUND';
        } else {
          this.fillForm(listing);
        }
        this.cdr.detectChanges();
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = 'LISTINGS.ERROR_LOAD';
        this.cdr.detectChanges();
      }
    });
  }

  private fillForm(listing: Listing): void {
    const country = listing.country || this.defaultCountry();
    this.form = {
      name: listing.name,
      description: listing.description ?? '',
      price: listing.price,
      country,
      currency: getCurrencyForCountry(country),
      stockQty: listing.stockQty,
      active: listing.active,
      sku: listing.sku,
      brand: listing.brand ?? '',
      categoryId: listing.categoryId ?? null,
      locationText: listing.locationText ?? '',
      latitude: listing.latitude ?? null,
      longitude: listing.longitude ?? null,
      condition: listing.condition ?? null,
      handDelivery: listing.handDelivery ?? false,
      shippingAvailable: listing.shippingAvailable ?? true
    };
    this.existingImages = listing.imageUrls ?? [];
    this.locationQuery = listing.locationText ?? '';
    this.syncRootWithCategory();
  }

  private uploadNewImages(listingId: number): Observable<unknown> {
    if (this.newImages.length === 0) {
      return of(null);
    }
    return this.listingService.uploadListingImages(listingId, this.newImages);
  }

  private uploadError(code: string | undefined): string {
    switch (code) {
      case 'TOO_MANY_IMAGES':
        return 'LISTINGS.ERROR_UPLOAD_TOO_MANY';
      case 'INVALID_IMAGE':
        return 'LISTINGS.ERROR_IMAGE_TYPE';
      case 'IMAGE_TOO_LARGE':
        return 'LISTINGS.ERROR_IMAGE_SIZE';
      default:
        return 'LISTINGS.ERROR_UPLOAD';
    }
  }

  private syncRootWithCategory(): void {
    const category = this.selectedCategory;
    if (category) {
      this.selectedRootId = category.pathIds?.[0] ?? category.id;
    }
  }

  private leaves(nodes: CategoryNode[]): CategoryNode[] {
    return nodes.flatMap(node => node.children?.length ? this.leaves(node.children) : [node]);
  }

  private findCategory(nodes: CategoryNode[], id: number | null): CategoryNode | null {
    if (id === null) {
      return null;
    }
    for (const node of nodes) {
      if (node.id === id) {
        return node;
      }
      const found = this.findCategory(node.children ?? [], id);
      if (found) {
        return found;
      }
    }
    return null;
  }

  private emptyForm(): ListingRequest {
    const country = this.defaultCountry();
    return {
      name: '',
      description: '',
      price: 0,
      country,
      currency: getCurrencyForCountry(country),
      stockQty: 1,
      active: true,
      sku: null,
      brand: '',
      categoryId: null,
      locationText: '',
      latitude: null,
      longitude: null,
      condition: null,
      handDelivery: true,
      shippingAvailable: true
    };
  }

  private defaultCountry(): string {
    const lang = this.translateService?.currentLang || this.translateService?.getDefaultLang();
    return getDefaultCountryForLanguage(lang);
  }

  private initMap(): void {
    const container = document.getElementById('wizard-location-map');
    if (!container) {
      return;
    }
    this.map = L.map(container).setView([FALLBACK_LAT, FALLBACK_LNG], 11);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors'
    }).addTo(this.map);
    this.updateMap();
  }

  private updateMap(): void {
    if (!this.map) {
      return;
    }
    const lat = this.form.latitude;
    const lng = this.form.longitude;
    if (typeof lat !== 'number' || typeof lng !== 'number') {
      this.marker?.remove();
      this.marker = null;
      return;
    }
    this.map.setView([lat, lng], 13);
    if (this.marker) {
      this.marker.setLatLng([lat, lng]);
      return;
    }
    this.marker = L.marker([lat, lng], {
      icon: L.icon({
        iconUrl: '/assets/leaflet/marker-icon.png',
        iconRetinaUrl: '/assets/leaflet/marker-icon-2x.png',
        shadowUrl: '/assets/leaflet/marker-shadow.png',
        iconSize: [25, 41],
        iconAnchor: [12, 41],
        shadowSize: [41, 41]
      })
    }).addTo(this.map);
  }

  private destroyMap(): void {
    this.marker?.remove();
    this.marker = null;
    this.map?.remove();
    this.map = null;
  }
}
