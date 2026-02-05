import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import * as L from 'leaflet';
import { Listing, ListingRequest, ListingService } from '../../service/listing.service';
import { environment } from '../../../environments/environment';
import { Category, CategoryService } from '../../service/category.service';

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
  categories: Category[] = [];
  subcategories: Category[] = [];
  selectedParentId: number | null = null;
  locationQuery = '';
  locationResults: Array<{
    label: string;
    latitude: number;
    longitude: number;
  }> = [];
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
    currency: 'EUR',
    stockQty: 0,
    active: true,
    sku: '',
    brand: '',
    categoryId: null,
    subcategoryId: null,
    locationText: '',
    latitude: null,
    longitude: null
  };

  constructor(
    private listingService: ListingService,
    private cdr: ChangeDetectorRef,
    private categoryService: CategoryService,
    private http: HttpClient
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
    this.selectedParentId = null;
    this.subcategories = [];
    this.locationQuery = '';
    this.locationResults = [];
    this.form = {
      name: '',
      description: '',
      price: 0,
      currency: 'EUR',
      stockQty: 0,
      active: true,
      sku: '',
      brand: '',
      categoryId: null,
      subcategoryId: null as number | null,
      locationText: '',
      latitude: null,
      longitude: null
    };
    setTimeout(() => this.initMap(), 0);
  }

  openEditListing(listing: Listing): void {
    this.editingId = listing.id;
    this.isFormOpen = true;
    this.clearImages();
    this.selectedParentId = listing.parentCategoryId ?? null;
    if (this.selectedParentId) {
      this.loadSubcategories(this.selectedParentId, listing.categoryId ?? null);
    } else {
      this.subcategories = [];
    }
    this.form = {
      name: listing.name,
      description: listing.description,
      price: listing.price,
      currency: listing.currency,
      stockQty: listing.stockQty,
      active: listing.active,
      sku: listing.sku,
      brand: listing.brand,
      categoryId: listing.categoryId ?? null,
      subcategoryId: null as number | null,
      locationText: listing.locationText ?? '',
      latitude: listing.latitude ?? null,
      longitude: listing.longitude ?? null
    };
    this.locationQuery = listing.locationText ?? '';
    this.locationResults = [];
    setTimeout(() => this.initMap(), 0);
    setTimeout(() => this.updateMapLocation(this.form.latitude, this.form.longitude), 0);
  }

  cancelForm(): void {
    this.isFormOpen = false;
    this.editingId = null;
    this.clearImages();
    this.selectedParentId = null;
    this.subcategories = [];
    this.locationQuery = '';
    this.locationResults = [];
    this.destroyMap();
  }

  loadCategories(): void {
    this.categoryService.loadRootCategories().subscribe({
      next: categories => {
        this.categories = categories;
      },
      error: () => {
        this.categories = [];
      }
    });
  }

  onParentCategoryChange(value: string): void {
    const parentId = value ? Number(value) : null;
    this.selectedParentId = Number.isFinite(parentId) ? parentId : null;
    this.form.categoryId = null;
    if (this.selectedParentId) {
      this.loadSubcategories(this.selectedParentId, null);
    } else {
      this.subcategories = [];
    }
  }

  private loadSubcategories(parentId: number, selectedId: number | null): void {
    this.categoryService.loadChildren(parentId).subscribe({
      next: categories => {
        this.subcategories = categories;
        if (selectedId) {
          this.form.categoryId = selectedId;
        }
      },
      error: () => {
        this.subcategories = [];
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

    const encoded = encodeURIComponent(query.trim());
    const url = `https://api-adresse.data.gouv.fr/search/?q=${encoded}&limit=5`;
    this.isSearchingLocation = true;
    this.http.get<{ features?: Array<{ properties?: { label?: string }; geometry?: { coordinates?: number[] } }> }>(url)
      .subscribe({
        next: response => {
          const features = response.features ?? [];
          this.locationResults = features
            .map(feature => {
              const label = feature.properties?.label ?? '';
              const coords = feature.geometry?.coordinates ?? [];
              const longitude = coords[0];
              const latitude = coords[1];
              if (!label || typeof latitude !== 'number' || typeof longitude !== 'number') {
                return null;
              }
              return { label, latitude, longitude };
            })
            .filter((item): item is { label: string; latitude: number; longitude: number } => !!item);
          this.isSearchingLocation = false;
        },
        error: () => {
          this.locationResults = [];
          this.isSearchingLocation = false;
        }
      });
  }

  selectLocation(result: { label: string; latitude: number; longitude: number }): void {
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
    if (!this.form.name?.trim() || !this.form.categoryId) {
      this.errorMessage = 'LISTINGS.ERROR_REQUIRED';
      return;
    }

    this.isSaving = true;
    this.errorMessage = '';
    const payload: ListingRequest = {
      ...this.form,
      name: this.form.name.trim(),
      sku: this.form.sku?.trim() || null
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
