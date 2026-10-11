import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BoostOffer, Listing, ListingRemovalReason, ListingService } from '../../service/listing.service';
import { environment } from '../../../environments/environment';

interface RemovalOption {
  value: ListingRemovalReason;
  labelKey: string;
}

/** The seller's own listings. Creating and editing happen in the listing wizard. */
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
  boostOffer: BoostOffer | null = null;
  boostingId: number | null = null;
  /** Back from Stripe after paying (or not) for a boost. */
  boostNotice: 'success' | 'canceled' | null = null;
  /** Set after the wizard published or updated a listing. */
  publishedNotice: 'created' | 'updated' | null = null;
  pendingRemovalListing: Listing | null = null;
  removalReason: ListingRemovalReason = 'SOLD_ON_LOCALYS';
  removalNote = '';
  removalOptions: RemovalOption[] = [
    { value: 'SOLD_ON_LOCALYS', labelKey: 'LISTINGS.REMOVAL_SOLD_ON_LOCALYS' },
    { value: 'SOLD_ELSEWHERE', labelKey: 'LISTINGS.REMOVAL_SOLD_ELSEWHERE' },
    { value: 'NO_LONGER_AVAILABLE', labelKey: 'LISTINGS.REMOVAL_NO_LONGER_AVAILABLE' },
    { value: 'OTHER', labelKey: 'LISTINGS.REMOVAL_OTHER' }
  ];

  constructor(
    private listingService: ListingService,
    private route: ActivatedRoute,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const published = this.route.snapshot.queryParamMap.get('published');
    if (published === 'created' || published === 'updated') {
      this.publishedNotice = published;
    }
    const boost = this.route.snapshot.queryParamMap.get('boost');
    if (boost === 'success' || boost === 'canceled') {
      this.boostNotice = boost;
    }
    this.listingService.boostOffer().subscribe({
      next: offer => {
        this.boostOffer = offer;
        this.cdr.detectChanges();
      },
      error: () => {
        this.boostOffer = null;
      }
    });
    this.loadListings();
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
    this.router.navigate(['/listings/new']);
  }

  openEditListing(listing: Listing): void {
    this.router.navigate(['/listings', listing.id, 'edit']);
  }

  isOnline(listing: Listing): boolean {
    return listing.active && listing.moderationStatus === 'APPROVED' && !listing.removedAt;
  }

  canRenew(listing: Listing): boolean {
    return !listing.renewableAt || new Date(listing.renewableAt).getTime() <= Date.now();
  }

  /** Moves the listing back to the top of search results. */
  isBoosted(listing: Listing): boolean {
    return !!listing.boostedUntil && new Date(listing.boostedUntil).getTime() > Date.now();
  }

  boost(listing: Listing): void {
    if (this.boostingId) {
      return;
    }
    this.boostingId = listing.id;
    this.errorMessage = '';
    this.listingService.boostListing(listing.id).subscribe({
      next: url => window.location.assign(url),
      error: () => {
        this.boostingId = null;
        this.errorMessage = 'BOOST.ERROR';
        this.cdr.detectChanges();
      }
    });
  }

  renew(listing: Listing): void {
    this.isSaving = true;
    this.errorMessage = '';
    this.listingService.renewListing(listing.id).subscribe({
      next: renewed => {
        this.isSaving = false;
        // The response has no counters; keep the ones already shown.
        this.listings = this.listings.map(item => item.id === renewed.id
          ? { ...item, listedAt: renewed.listedAt, renewableAt: renewed.renewableAt }
          : item);
        this.cdr.detectChanges();
      },
      error: () => {
        this.isSaving = false;
        this.errorMessage = 'STATS.ERROR_RENEW';
        this.cdr.detectChanges();
      }
    });
  }

  openRemoveListing(listing: Listing): void {
    this.pendingRemovalListing = listing;
    this.removalReason = 'SOLD_ON_LOCALYS';
    this.removalNote = '';
    this.errorMessage = '';
  }

  cancelRemoveListing(): void {
    this.pendingRemovalListing = null;
    this.removalNote = '';
  }

  deleteListing(): void {
    if (!this.pendingRemovalListing) {
      return;
    }
    this.isSaving = true;
    this.errorMessage = '';
    const listing = this.pendingRemovalListing;
    this.listingService.deleteListing(listing.id, {
      reason: this.removalReason,
      note: this.removalNote.trim() || null
    }).subscribe({
      next: removed => {
        this.isSaving = false;
        this.listings = this.listings.map(item => item.id === removed.id ? removed : item);
        this.pendingRemovalListing = null;
        this.removalNote = '';
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
}
