import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { AuthService } from "../../service/auth.service";
import { CartNotice, CartService } from "../../service/cart.service";
import { SearchService } from "../../service/search.service";
import { TranslateService } from "@ngx-translate/core";
import { Subscription, timer } from "rxjs";
import { Router } from "@angular/router";
import { CurrentUserService } from "../../service/current-user.service";
import { NotificationDto, NotificationService } from "../../service/notification.service";

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
  isNotificationsOpen = false;
  searchTerm = '';
  cartCount$ = this.cartService.count$;
  supportedLangs = ['en', 'tr', 'fr'];
  currentLang = 'en';
  cartNotice: CartNotice | null = null;
  showCartNotice = false;
  notifications: NotificationDto[] = [];
  unreadCount = 0;
  private noticeTimer?: number;
  private subscriptions = new Subscription();

  constructor(
    public authService: AuthService,
    private cartService: CartService,
    private searchService: SearchService,
    private translateService: TranslateService,
    private router: Router,
    private currentUserService: CurrentUserService,
    private notificationService: NotificationService
  ) {}

  ngOnInit() {
    const storedLang = localStorage.getItem('lang');
    const initialLang = storedLang || this.translateService.currentLang || this.translateService.getDefaultLang() || 'en';
    this.currentLang = initialLang;
    this.translateService.use(initialLang);
    if (this.authService.isAuthenticated) {
      this.cartService.refresh().subscribe();
      this.currentUserService.load().subscribe();
      this.refreshUnreadCount();
    }
    const notificationPoll = timer(0, 30000).subscribe(() => {
      if (!this.authService.isAuthenticated) {
        return;
      }
      this.currentUserService.ensureLoaded();
      this.refreshUnreadCount();
      if (this.isNotificationsOpen) {
        this.loadNotifications();
      }
    });
    this.subscriptions.add(notificationPoll);
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

  toggleNotificationsMenu() {
    this.isNotificationsOpen = !this.isNotificationsOpen;
    if (this.isNotificationsOpen) {
      this.loadNotifications();
      this.refreshUnreadCount();
    }
  }

  closeLangMenu() {
    this.isLangMenuOpen = false;
  }

  closeNotificationsMenu() {
    this.isNotificationsOpen = false;
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
    this.closeNotificationsMenu();
    this.currentUserService.clear();
    this.unreadCount = 0;
    this.notifications = [];
  }

  openCart(): void {
    this.router.navigate(['/cart']);
  }

  onSearch(): void {
    const term = this.searchTerm.trim();
    this.searchService.setSearchTerm(term);
  }

  openNotification(notification: NotificationDto): void {
    if (!notification.read) {
      this.notificationService.markRead(notification.id).subscribe(() => {
        notification.read = true;
        if (this.unreadCount > 0) {
          this.unreadCount -= 1;
        }
      });
    }
    if (notification.link) {
      this.router.navigateByUrl(notification.link);
    } else {
      this.router.navigate(['/notifications']);
    }
    this.closeNotificationsMenu();
  }

  markAllNotificationsRead(): void {
    this.notificationService.markAllRead().subscribe(() => {
      this.notifications = this.notifications.map(notification => ({
        ...notification,
        read: true
      }));
      this.unreadCount = 0;
    });
  }

  private loadNotifications(): void {
    this.notificationService.list().subscribe(notifications => {
      this.notifications = notifications;
    });
  }

  private refreshUnreadCount(): void {
    this.notificationService.unreadCount().subscribe(count => {
      this.unreadCount = count;
    });
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
    if (target.closest('.notification-menu')) {
      return;
    }
    if (target.closest('.notification-trigger')) {
      return;
    }
    this.closeProfileMenu();
    this.closeLangMenu();
    this.closeNotificationsMenu();
  }
}
