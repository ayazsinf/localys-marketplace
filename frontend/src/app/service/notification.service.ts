import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { TranslateService } from '@ngx-translate/core';

export interface NotificationDto {
  id: number;
  type: string;
  title: string;
  message: string;
  link?: string | null;
  read: boolean;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  constructor(
    private http: HttpClient,
    private translateService: TranslateService
  ) {}

  list(): Observable<NotificationDto[]> {
    return this.http.get<NotificationDto[]>(`${environment.apiUrl}/notifications`);
  }

  unreadCount(): Observable<number> {
    return this.http.get<number>(`${environment.apiUrl}/notifications/unread-count`);
  }

  markRead(id: number): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/notifications/${id}/read`, {});
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/notifications/read-all`, {});
  }

  getTitle(notification: NotificationDto): string {
    const key = `NOTIFICATION_CONTENT.${notification.type}.TITLE`;
    const translated = this.translateService.instant(key);
    return translated === key ? notification.title : translated;
  }

  getMessage(notification: NotificationDto): string {
    const quotedValues = Array.from(notification.message.matchAll(/"([^"]+)"/g), match => match[1]);
    if (notification.type === 'PRODUCT_CREATED' && quotedValues[0]) {
      return this.translateService.instant('NOTIFICATION_CONTENT.PRODUCT_CREATED.MESSAGE', {
        product: quotedValues[0]
      });
    }
    if (notification.type === 'FAVORITE_ADDED' && quotedValues[0]) {
      const actor = notification.message.split('"')[0].trim() || this.translateService.instant('COMMON.SOMEONE');
      return this.translateService.instant('NOTIFICATION_CONTENT.FAVORITE_ADDED.MESSAGE', {
        actor,
        product: quotedValues[0]
      });
    }
    return notification.message;
  }
}
