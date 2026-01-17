import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { AuthService } from "../../service/auth.service";
import { CartNotice, CartService } from "../../service/cart.service";
import { SearchService } from "../../service/search.service";
import { TranslateService } from "@ngx-translate/core";
import { Subscription } from "rxjs";
import { Router } from "@angular/router";
import { CurrentUserService } from "../../service/current-user.service";

@Component({
  selector: 'app-navbar',
  standalone: false,
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.scss',
})
export class NavbarComponent implements OnInit, OnDestroy {
  isMenuOpen = false;
  isProfileMenuOpen = false;
  isLangMenuOpen = false;
  searchTerm = '';
  cartCount$ = this.cartService.count$;
  supportedLangs = ['en', 'tr', 'fr'];
  currentLang = 'en';
  cartNotice: CartNotice | null = null;
  showCartNotice = false;
  private noticeTimer?: number;
  private subscriptions = new Subscription();

  constructor(
    public authService: AuthService,
    private cartService: CartService,
    private searchService: SearchService,
    private translateService: TranslateService,
    private router: Router,
    private currentUserService: CurrentUserService
  ) {}

  ngOnInit() {
    const storedLang = localStorage.getItem('lang');
    const initialLang = storedLang || this.translateService.currentLang || this.translateService.getDefaultLang() || 'en';
    this.currentLang = initialLang;
    this.translateService.use(initialLang);
    if (this.authService.isAuthenticated) {
      this.cartService.refresh().subscribe();
      this.currentUserService.load().subscribe();
    }
    const noticeSub = this.cartService.notice$.subscribe(notice => {
      if (!notice) {
        return;
      }
      this.cartNotice = notice;
      this.showCartNotice = true;
      if (this.noticeTimer) {
        window.clearTimeout(this.noticeTimer);
      }
      this.noticeTimer = window.setTimeout(() => {
        this.showCartNotice = false;
        this.cartNotice = null;
        this.cartService.clearNotice();
      }, 4000);
    });
    this.subscriptions.add(noticeSub);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    if (this.noticeTimer) {
      window.clearTimeout(this.noticeTimer);
    }
  }

  login() {
    this.authService.login().subscribe();
  }

  register() {
    this.authService.register().subscribe();
  }

  toggleMenu() {
    this.isMenuOpen = !this.isMenuOpen;
  }

  toggleProfileMenu() {
    this.isProfileMenuOpen = !this.isProfileMenuOpen;
  }

  closeProfileMenu() {
    this.isProfileMenuOpen = false;
  }

  toggleLangMenu() {
    this.isLangMenuOpen = !this.isLangMenuOpen;
  }

  closeLangMenu() {
    this.isLangMenuOpen = false;
  }

  setLanguage(lang: string) {
    if (!this.supportedLangs.includes(lang)) {
      return;
    }
    this.currentLang = lang;
    this.translateService.use(lang);
    localStorage.setItem('lang', lang);
    this.closeLangMenu();
  }

  logout() {
    this.authService.logout();
    this.closeProfileMenu();
    this.currentUserService.clear();
  }

  openCart(): void {
    this.router.navigate(['/cart']);
  }

  onSearch(): void {
    const term = this.searchTerm.trim();
    this.searchService.setSearchTerm(term);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement | null;
    if (!target) {
      return;
    }
    if (target.closest('.profile-menu')) {
      return;
    }
    if (target.closest('.profile-trigger')) {
      return;
    }
    if (target.closest('.lang-menu')) {
      return;
    }
    if (target.closest('.lang-trigger')) {
      return;
    }
    this.closeProfileMenu();
    this.closeLangMenu();
  }
}
