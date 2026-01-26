import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, finalize, tap } from 'rxjs';
import { UserProfile, UserService } from './user.service';

@Injectable({ providedIn: 'root' })
export class CurrentUserService {
  private readonly profileSubject = new BehaviorSubject<UserProfile | null>(null);
  readonly profile$ = this.profileSubject.asObservable();
  private loading = false;
  private loaded = false;

  constructor(private userService: UserService) {}

  load(): Observable<UserProfile> {
    return this.userService.getMe().pipe(
      tap(profile => this.profileSubject.next(profile))
    );
  }

  ensureLoaded(): void {
    if (this.loading || this.loaded) {
      return;
    }
    this.loading = true;
    this.load().pipe(
      finalize(() => {
        this.loading = false;
      })
    ).subscribe({
      next: () => {
        this.loaded = true;
      },
      error: () => {
        this.loaded = false;
      }
    });
  }

  clear(): void {
    this.profileSubject.next(null);
    this.loading = false;
    this.loaded = false;
  }

  get userId(): number | null {
    return this.profileSubject.value?.id ?? null;
  }
}
