import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CartService } from '../../service/cart.service';
import { Offer, OfferService } from '../../service/offer.service';
import { environment } from '../../../environments/environment';

type OfferTab = 'received' | 'sent';

/** Offers the user received on their listings and offers they made to other sellers. */
@Component({
  selector: 'app-offers',
  templateUrl: './offers.component.html',
  styleUrl: './offers.component.scss',
  standalone: false
})
export class OffersComponent implements OnInit {
  activeTab: OfferTab = 'received';
  offers: Offer[] = [];
  isLoading = false;
  errorMessage = '';
  processingId: number | null = null;
  counterAmounts: Record<number, number> = {};
  counterOpenId: number | null = null;

  constructor(
    private offerService: OfferService,
    private cartService: CartService,
    private route: ActivatedRoute,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      this.activeTab = params.get('tab') === 'sent' ? 'sent' : 'received';
      this.load();
    });
  }

  selectTab(tab: OfferTab): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: { tab }, replaceUrl: true });
  }

  accept(offer: Offer): void {
    this.run(offer, this.offerService.accept(offer.id));
  }

  decline(offer: Offer): void {
    this.run(offer, this.offerService.decline(offer.id));
  }

  withdraw(offer: Offer): void {
    this.run(offer, this.offerService.withdraw(offer.id));
  }

  openCounter(offer: Offer): void {
    this.counterOpenId = offer.id;
    // Start halfway between the offer and the asking price.
    this.counterAmounts[offer.id] = Math.round((offer.amount + offer.listingPrice) / 2 * 100) / 100;
  }

  sendCounter(offer: Offer): void {
    const amount = Number(this.counterAmounts[offer.id]);
    if (!(amount > offer.amount && amount < offer.listingPrice)) {
      this.errorMessage = 'OFFERS.ERROR_COUNTER_RANGE';
      return;
    }
    this.counterOpenId = null;
    this.run(offer, this.offerService.counter(offer.id, amount));
  }

  buy(offer: Offer): void {
    this.processingId = offer.id;
    this.offerService.addToCart(offer.id).subscribe({
      next: () => {
        this.cartService.refresh().subscribe();
        this.router.navigate(['/checkout'], { queryParams: { items: offer.productId } });
      },
      error: () => this.failed()
    });
  }

  canBuy(offer: Offer): boolean {
    return offer.status === 'ACCEPTED' && (!offer.expiresAt || new Date(offer.expiresAt).getTime() > Date.now());
  }

  imageUrl(url: string | null): string {
    if (!url) {
      return '';
    }
    if (url.startsWith('http://') || url.startsWith('https://')) {
      return url;
    }
    return `${environment.apiUrl}${url.startsWith('/') ? url : `/${url}`}`;
  }

  private run(offer: Offer, action$: ReturnType<OfferService['accept']>): void {
    this.processingId = offer.id;
    this.errorMessage = '';
    action$.subscribe({
      next: updated => {
        this.offers = this.offers.map(item => item.id === updated.id ? updated : item);
        this.processingId = null;
        this.cdr.detectChanges();
      },
      error: () => this.failed()
    });
  }

  private failed(): void {
    this.processingId = null;
    this.errorMessage = 'OFFERS.ERROR_ACTION';
    this.load();
  }

  private load(): void {
    this.isLoading = true;
    this.offerService.list(this.activeTab === 'received' ? 'seller' : 'buyer').subscribe({
      next: offers => {
        this.offers = offers;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = 'OFFERS.ERROR_LOAD';
        this.cdr.detectChanges();
      }
    });
  }
}
