import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { NotificationDto, NotificationService } from '../../service/notification.service';
import { Subscription, timer } from 'rxjs';

type NotificationFilter = 'all' | 'unread' | 'read';

@Component({
  selector: 'app-notifications',
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss',
  standalone: false
})
export class NotificationsComponent implements OnInit, OnDestroy {
  notifications: NotificationDto[] = [];
  activeFilter: NotificationFilter = 'all';
  isLoading = true;
  errorMessage = '';
  private subscriptions = new Subscription();

  constructor(
    private notificationService: NotificationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadNotifications();
    const pollSub = timer(30000, 30000).subscribe(() => {
      this.loadNotifications();
    });
    this.subscriptions.add(pollSub);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  get filteredNotifications(): NotificationDto[] {
    if (this.activeFilter === 'all') {
      return this.notifications;
    }
    const isRead = this.activeFilter === 'read';
    return this.notifications.filter(item => item.read === isRead);
  }

  setFilter(filter: NotificationFilter): void {
    this.activeFilter = filter;
  }

  markAllRead(): void {
    if (this.notifications.length === 0) {
      return;
    }
    this.notificationService.markAllRead().subscribe({
      next: () => {
        this.notifications = this.notifications.map(item => ({
          ...item,
          read: true
        }));
      }
    });
  }

  openNotification(notification: NotificationDto): void {
    if (!notification.read) {
      this.notificationService.markRead(notification.id).subscribe(() => {
        notification.read = true;
      });
    }

    if (notification.link) {
      this.router.navigateByUrl(notification.link);
    }
  }

  formatTime(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    return date.toLocaleString();
  }

  trackByNotification(_: number, notification: NotificationDto): number {
    return notification.id;
  }

  private loadNotifications(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.notificationService.list().subscribe({
      next: notifications => {
        this.notifications = notifications;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = 'NOTIFICATIONS.LOAD_ERROR';
      }
    });
  }
}
