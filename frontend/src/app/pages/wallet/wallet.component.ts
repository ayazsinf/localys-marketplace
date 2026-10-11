import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Wallet, WalletService } from '../../service/wallet.service';
import { getLocaleForLanguage } from '../../shared/market';

/** A seller's earnings: held, available and paid out, and the Stripe payout setup. */
@Component({
  selector: 'app-wallet',
  templateUrl: './wallet.component.html',
  styleUrl: './wallet.component.scss',
  standalone: false
})
export class WalletComponent implements OnInit {
  wallet: Wallet | null = null;
  isLoading = true;
  isRedirecting = false;
  errorMessage = '';
  /** Back from Stripe: "done" or "refresh" (the link expired). */
  onboardingResult: string | null = null;

  constructor(
    private walletService: WalletService,
    private route: ActivatedRoute,
    private translateService: TranslateService
  ) {}

  ngOnInit(): void {
    this.onboardingResult = this.route.snapshot.queryParamMap.get('onboarding');
    this.walletService.get().subscribe({
      next: wallet => {
        this.wallet = wallet;
        this.isLoading = false;
      },
      error: () => {
        this.errorMessage = 'WALLET.ERROR_LOAD';
        this.isLoading = false;
      }
    });
  }

  get commissionPercent(): string {
    return this.wallet ? (this.wallet.commissionRate * 100).toLocaleString(this.locale, { maximumFractionDigits: 2 }) : '';
  }

  setUpPayouts(): void {
    if (this.isRedirecting) {
      return;
    }
    this.isRedirecting = true;
    this.errorMessage = '';
    this.walletService.onboardingUrl().subscribe({
      next: url => window.location.assign(url),
      error: () => {
        this.isRedirecting = false;
        this.errorMessage = 'WALLET.ERROR_ONBOARDING';
      }
    });
  }

  formatDate(value: string | null): string {
    if (!value) {
      return '';
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '' : date.toLocaleDateString(this.locale);
  }

  private get locale(): string {
    return getLocaleForLanguage(this.translateService.currentLang || this.translateService.getDefaultLang());
  }
}
