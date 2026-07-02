import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import * as L from 'leaflet';
import { Listing, ListingRequest, ListingService } from '../../service/listing.service';
import { environment } from '../../../environments/environment';
import { CategoryNode, CategoryService } from '../../service/category.service';
import { TranslateService } from '@ngx-translate/core';
import { getCurrencyForCountry, getDefaultCountryForLanguage, MARKET_OPTIONS } from '../../shared/market';

interface CategoryOption {
  id: number;
  label: string;
  selectable: boolean;
}

interface LocationResult {
  label: string;
  latitude: number;
  longitude: number;
  postalCode: string | null;
  city: string | null;
}

@Component({
  selector: 'app-listings',
  templateUrl: './listings.component.html',
  styleUrl: './listings.component.scss',
  standalone: false
})
export class ListingsComponent implements OnInit {
  listings: Listing[] = [];
  isLoading = false;
  isSaving = false;
  errorMessage = '';
  isFormOpen = false;
  editingId: number | null = null;
  selectedImages: File[] = [];
  imagePreviews: string[] = [];
  marketOptions = MARKET_OPTIONS;
  categories: CategoryOption[] = [];
  locationQuery = '';
  locationResults: LocationResult[] = [];
  isSearchingLocation = false;
  private map: L.Map | null = null;
  private marker: L.Marker | null = null;
  private readonly maxImages = 6;
  private readonly maxImageSizeBytes = 10 * 1024 * 1024;
  private readonly allowedImageTypes = new Set(['image/jpeg', 'image/png', 'image/webp']);
  private readonly fallbackLat = 48.8566;
  private readonly fallbackLng = 2.3522;

  form: ListingRequest = {
    name: '',
    description: '',
    price: 0,
    country: 'FR',
    currency: 'EUR',
    stockQty: 0,
    active: true,
    sku: '',
    brand: '',
    categoryId: null,
    locationText: '',
    latitude: null,
    longitude: null
  };

  constructor(
    private listingService: ListingService,
    private cdr: ChangeDetectorRef,
    private categoryService: CategoryService,
    private http: HttpClient,
    private translateService: TranslateService
  ) {}

  ngOnInit(): void {
    this.loadListings();
    this.loadCategories();
  }

  loadListings(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.listingService.loadListings().subscribe({
      next: listings => {
        this.listings = listings;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.errorMessage = 'LISTINGS.ERROR_LOAD';
        this.isLoading = false;
      }
    });
  }

  openNewListing(): void {
    this.editingId = null;
    this.isFormOpen = true;
    this.clearImages();
    this.locationQuery = '';
    this.locationResults = [];
    const defaultCountry = this.getDefaultCountry();
    this.form = {
      name: '',
      description: '',
      price: 0,
      country: defaultCountry,
      currency: getCurrencyForCountry(defaultCountry),
      stockQty: 0,
      active: true,
      sku: '',
      brand: '',
      categoryId: null,
      locationText: '',
      latitude: null,
      longitude: null
    };
    setTimeout(() => this.initMap(), 0);
  }

  openEditListing(listing: Listing): void {
    const country = listing.country || this.getDefaultCountry();
    this.editingId = listing.id;
    this.isFormOpen = true;
    this.clearImages();
    this.form = {
      name: listing.name,
      description: listing.description,
      price: listing.price,
      country,
      currency: getCurrencyForCountry(country),
      stockQty: listing.stockQty,
      active: listing.active,
      sku: listing.sku,
      brand: listing.brand,
      categoryId: listing.categoryId ?? null,
      locationText: listing.locationText ?? '',
      latitude: listing.latitude ?? null,
      longitude: listing.longitude ?? null
    };
    this.locationQuery = listing.locationText ?? '';
    this.locationResults = [];
    setTimeout(() => this.initMap(), 0);
    setTimeout(() => this.updateMapLocation(this.form.latitude, this.form.longitude), 0);
  }

  onCountryChange(value: string): void {
    const country = value ? value.toUpperCase() : this.getDefaultCountry();
    this.form.country = country;
    this.form.currency = getCurrencyForCountry(country);
  }

  cancelForm(): void {
    this.isFormOpen = false;
    this.editingId = null;
    this.clearImages();
    this.locationQuery = '';
    this.locationResults = [];
    this.destroyMap();
  }

  loadCategories(): void {
    this.categoryService.loadTree().subscribe({
      next: tree => {
        this.categories = this.flattenCategoryOptions(tree);
      },
      error: () => {
        this.categories = [];
      }
    });
  }

  onImagesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = input.files ? Array.from(input.files) : [];
    this.errorMessage = '';
    const validFiles: File[] = [];

    for (const file of files) {
      if (!this.allowedImageTypes.has(file.type)) {
        this.errorMessage = 'LISTINGS.ERROR_IMAGE_TYPE';
        continue;
      }
      if (file.size > this.maxImageSizeBytes) {
        this.errorMessage = 'LISTINGS.ERROR_IMAGE_SIZE';
        continue;
      }
      validFiles.push(file);
    }

    if (validFiles.length > this.maxImages) {
      this.errorMessage = 'LISTINGS.ERROR_IMAGE_LIMIT';
      validFiles.splice(this.maxImages);
    }

    this.selectedImages = validFiles;
    this.imagePreviews = validFiles.map(file => URL.createObjectURL(file));
  }

  searchLocation(query: string): void {
    this.locationQuery = query;
    this.form.locationText = query.trim() || null;
    this.form.latitude = null;
    this.form.longitude = null;
    if (!query || query.trim().length < 3) {
      this.locationResults = [];
      return;
    }

    this.isSearchingLocation = true;
    this.http.get<{ results?: LocationResult[] }>(`${environment.apiUrl}/locations/search`, {
      params: { q: query.trim() }
    })
      .subscribe({
        next: response => {
          this.locationResults = response.results ?? [];
          this.isSearchingLocation = false;
        },
        error: () => {
          this.locationResults = [];
          this.isSearchingLocation = false;
        }
      });
  }

  selectLocation(result: LocationResult): void {
    this.form.locationText = result.label;
    this.form.latitude = result.latitude;
    this.form.longitude = result.longitude;
    this.locationQuery = result.label;
    this.locationResults = [];
    this.updateMapLocation(result.latitude, result.longitude);
  }

  onLatitudeChange(value: string | number): void {
    const lat = typeof value === 'number' ? value : Number(value);
    this.form.latitude = Number.isFinite(lat) ? lat : null;
    this.updateMapLocation(this.form.latitude, this.form.longitude);
  }

  onLongitudeChange(value: string | number): void {
    const lng = typeof value === 'number' ? value : Number(value);
    this.form.longitude = Number.isFinite(lng) ? lng : null;
    this.updateMapLocation(this.form.latitude, this.form.longitude);
  }

  removeImage(index: number): void {
    this.selectedImages.splice(index, 1);
    const [preview] = this.imagePreviews.splice(index, 1);
    if (preview) {
      URL.revokeObjectURL(preview);
    }
  }

  saveListing(): void {
    const resolvedCategoryId = this.form.categoryId ?? null;
    if (!this.form.name?.trim() || !resolvedCategoryId) {
      this.errorMessage = 'LISTINGS.ERROR_REQUIRED';
      return;
    }

    this.isSaving = true;
    this.errorMessage = '';
    const country = this.form.country?.trim().toUpperCase() || this.getDefaultCountry();
    const payload: ListingRequest = {
      ...this.form,
      name: this.form.name.trim(),
      country,
      currency: getCurrencyForCountry(country),
      sku: this.form.sku?.trim() || null,
      categoryId: resolvedCategoryId
    };

    const request$ = this.editingId
      ? this.listingService.updateListing(this.editingId, payload)
      : this.listingService.createListing(payload);

    request$.subscribe({
      next: listing => {
        const listingId = listing.id;
        if (this.selectedImages.length > 0) {
          this.listingService.uploadListingImages(listingId, this.selectedImages).subscribe({
            next: () => {
              this.finishSave();
            },
            error: () => {
              this.errorMessage = 'LISTINGS.ERROR_UPLOAD';
              this.isSaving = false;
            }
          });
          return;
        }
        this.finishSave();
      },
      error: err => {
        if (err?.status === 409 && err?.error?.code === 'SKU_ALREADY_EXISTS') {
          this.errorMessage = 'LISTINGS.ERROR_SKU_DUPLICATE';
        } else {
          this.errorMessage = 'LISTINGS.ERROR_SAVE';
        }
        this.isSaving = false;
      }
    });
  }

  deleteListing(listing: Listing): void {
    this.isSaving = true;
    this.errorMessage = '';
    this.listingService.deleteListing(listing.id).subscribe({
      next: () => {
        this.isSaving = false;
        this.listings = this.listings.filter(item => item.id !== listing.id);
        this.cdr.detectChanges();
      },
      error: () => {
        this.errorMessage = 'LISTINGS.ERROR_DELETE';
        this.isSaving = false;
      }
    });
  }

  getImageUrl(url?: string): string {
    if (!url) {
      return '';
    }
    const origin = window.location.origin;
    if (url.startsWith(origin) && environment.apiUrl.startsWith('http')) {
      return `${environment.apiUrl}${url.substring(origin.length)}`;
    }
    if (url.startsWith('http://') || url.startsWith('https://')) {
      return url;
    }
    const normalized = url.startsWith('/') ? url : `/${url}`;
    return `${environment.apiUrl}${normalized}`;
  }

  private clearImages(): void {
    this.imagePreviews.forEach(preview => URL.revokeObjectURL(preview));
    this.imagePreviews = [];
    this.selectedImages = [];
  }

  private finishSave(): void {
    this.isSaving = false;
    this.isFormOpen = false;
    this.editingId = null;
    this.clearImages();
    this.loadListings();
    this.cdr.detectChanges();
  }

  private getDefaultCountry(): string {
    return getDefaultCountryForLanguage(this.translateService.currentLang || this.translateService.getDefaultLang());
  }

  private flattenCategoryOptions(nodes: CategoryNode[], depth = 0): CategoryOption[] {
    return nodes.flatMap(node => {
      const children = node.children ?? [];
      const label = `${'  '.repeat(depth)}${node.pathNames.join(' / ')}`;
      return [
        { id: node.id, label, selectable: children.length === 0 },
        ...this.flattenCategoryOptions(children, depth + 1)
      ];
    });
  }

  private createMarkerIcon(): L.Icon {
    const iconUrl = new URL('/assets/leaflet/marker-icon.png', window.location.origin).toString();
    const iconRetinaUrl = new URL('/assets/leaflet/marker-icon-2x.png', window.location.origin).toString();
    const shadowUrl = new URL('/assets/leaflet/marker-shadow.png', window.location.origin).toString();
    return L.icon({
      iconUrl,
      iconRetinaUrl,
      shadowUrl,
      iconSize: [25, 41],
      iconAnchor: [12, 41],
      shadowSize: [41, 41]
    });
  }

  private initMap(): void {
    this.destroyMap();

    const container = document.getElementById('listing-location-map');
    if (!container) {
      return;
    }

    this.map = L.map(container, {
      zoomControl: true,
      attributionControl: true
    }).setView([this.fallbackLat, this.fallbackLng], 11);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors'
    }).addTo(this.map);
  }

  private updateMapLocation(lat: number | null | undefined, lng: number | null | undefined): void {
    if (!this.map) {
      return;
    }
    if (typeof lat !== 'number' || typeof lng !== 'number') {
      this.map.setView([this.fallbackLat, this.fallbackLng], 11);
      if (this.marker) {
        this.marker.remove();
        this.marker = null;
      }
      return;
    }

    this.map.setView([lat, lng], 13);
    if (this.marker) {
      this.marker.setLatLng([lat, lng]);
      return;
    }
    this.marker = L.marker([lat, lng], { icon: this.createMarkerIcon() }).addTo(this.map);
  }

  private destroyMap(): void {
    if (this.marker) {
      this.marker.remove();
      this.marker = null;
    }
    if (this.map) {
      this.map.remove();
      this.map = null;
    }
  }
}
