import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { Product } from '../../modules/product.model';
import { ProductService } from '../../service/product.service';
import { PublicProfile, PublicProfileService } from '../../service/public-profile.service';
import { Review, ReviewRole, ReviewService } from '../../service/review.service';
import { getLocaleForLanguage } from '../../shared/market';
import { AuthService } from '../../service/auth.service';
import { BlockService } from '../../service/block.service';
import { CurrentUserService } from '../../service/current-user.service';

const LISTINGS_PAGE_SIZE = 12;
const REVIEWS_PAGE_SIZE = 10;

type ProfileTab = 'listings' | 'reviews';

/**
 * Public user page, like a classifieds seller page: who they are, how they are rated as a
 * seller and as a buyer, their listings and the reviews they received.
 */
@Component({
  selector: 'app-public-profile',
  templateUrl: './public-profile.component.html',
  styleUrl: './public-profile.component.scss',
  standalone: false
})
export class PublicProfileComponent implements OnInit, OnDestroy {
  profile: PublicProfile | null = null;
  isLoading = true;
  notFound = false;
  activeTab: ProfileTab = 'listings';

  listings: Product[] = [];
  hasMoreListings = false;
  isLoadingListings = false;
  private listingsPage = 0;

  reviews: Review[] = [];
  reviewRole: ReviewRole | null = null;
  hasMoreReviews = false;
  isLoadingReviews = false;
  private reviewsPage = 0;

  /** Null until known, and always null when signed out or on one's own profile. */
  blockedByMe: boolean | null = null;
  isBlockBusy = false;

  private userId = 0;
  private subscriptions = new Subscription();

  constructor(
    private route: ActivatedRoute,
    private publicProfileService: PublicProfileService,
    private productService: ProductService,
    private reviewService: ReviewService,
    private translateService: TranslateService,
    private cdr: ChangeDetectorRef,
    private authService: AuthService,
    private blockService: BlockService,
    private currentUserService: CurrentUserService
  ) {}

  ngOnInit(): void {
    // The same component is reused when navigating from one profile to another.
    this.subscriptions.add(this.route.paramMap.subscribe(params => {
      this.userId = Number(params.get('id'));
      this.reset();
      this.load();
    }));
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  get initial(): string {
    return (this.profile?.displayName?.trim()[0] ?? '?').toUpperCase();
  }

  selectTab(tab: ProfileTab): void {
    this.activeTab = tab;
    if (tab === 'reviews' && this.reviews.length === 0 && !this.isLoadingReviews) {
      this.loadReviews();
    }
  }

  filterReviews(role: ReviewRole | null): void {
    if (this.reviewRole === role) {
      return;
    }
    this.reviewRole = role;
    this.reviews = [];
    this.reviewsPage = 0;
    this.loadReviews();
  }

  toggleBlock(): void {
    if (this.blockedByMe === null || this.isBlockBusy) {
      return;
    }
    const blocking = !this.blockedByMe;
    const name = this.profile?.displayName || this.translateService.instant('PROFILE_PUBLIC.MEMBER');
    if (blocking && !confirm(this.translateService.instant('BLOCK.CONFIRM', { name }))) {
      return;
    }
    this.isBlockBusy = true;
    const request = blocking ? this.blockService.block(this.userId) : this.blockService.unblock(this.userId);
    this.subscriptions.add(request.subscribe({
      next: () => {
        this.blockedByMe = blocking;
        this.isBlockBusy = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.isBlockBusy = false;
        this.cdr.detectChanges();
      }
    }));
  }

  private loadBlockState(): void {
    if (!this.authService.isAuthenticated) {
      return;
    }
    this.subscriptions.add(this.currentUserService.load().subscribe({
      next: me => {
        if (me.id === this.userId) {
          return;
        }
        this.subscriptions.add(this.blockService.isBlocked(this.userId).subscribe({
          next: blocked => {
            this.blockedByMe = blocked;
            this.cdr.detectChanges();
          },
          error: () => {}
        }));
      },
      error: () => {}
    }));
  }

  loadMoreListings(): void {
    this.listingsPage++;
    this.loadListings();
  }

  loadMoreReviews(): void {
    this.reviewsPage++;
    this.loadReviews();
  }

  stars(rating: number): string {
    const rounded = Math.round(rating);
    return '★'.repeat(rounded) + '☆'.repeat(5 - rounded);
  }

  formatDate(value: string, withDay = true): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    const locale = getLocaleForLanguage(this.translateService.currentLang || this.translateService.getDefaultLang());
    return withDay
      ? date.toLocaleDateString(locale)
      : date.toLocaleDateString(locale, { month: 'long', year: 'numeric' });
  }

  private reset(): void {
    this.profile = null;
    this.isLoading = true;
    this.notFound = false;
    this.activeTab = 'listings';
    this.listings = [];
    this.listingsPage = 0;
    this.reviews = [];
    this.reviewsPage = 0;
    this.reviewRole = null;
    this.blockedByMe = null;
  }

  private load(): void {
    if (!Number.isFinite(this.userId) || this.userId <= 0) {
      this.isLoading = false;
      this.notFound = true;
      return;
    }
    this.subscriptions.add(this.publicProfileService.get(this.userId).subscribe({
      next: profile => {
        this.profile = profile;
        this.isLoading = false;
        this.loadListings();
        this.loadBlockState();
        this.cdr.detectChanges();
      },
      error: () => {
        this.isLoading = false;
        this.notFound = true;
        this.cdr.detectChanges();
      }
    }));
  }

  private loadListings(): void {
    this.isLoadingListings = true;
    this.subscriptions.add(this.productService.search({
      sellerId: this.userId,
      sort: 'NEWEST',
      page: this.listingsPage,
      size: LISTINGS_PAGE_SIZE
    }).subscribe({
      next: page => {
        this.listings = [...this.listings, ...page.items];
        this.hasMoreListings = page.page + 1 < page.totalPages;
        this.isLoadingListings = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.isLoadingListings = false;
        this.cdr.detectChanges();
      }
    }));
  }

  private loadReviews(): void {
    this.isLoadingReviews = true;
    this.subscriptions.add(this.reviewService
      .getUserReviews(this.userId, this.reviewRole, this.reviewsPage, REVIEWS_PAGE_SIZE)
      .subscribe({
        next: result => {
          this.reviews = [...this.reviews, ...result.reviews.items];
          this.hasMoreReviews = result.reviews.page + 1 < result.reviews.totalPages;
          this.isLoadingReviews = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.isLoadingReviews = false;
          this.cdr.detectChanges();
        }
      }));
  }
}
