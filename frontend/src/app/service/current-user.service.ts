import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { UserProfile, UserService } from './user.service';

@Injectable({ providedIn: 'root' })
export class CurrentUserService {
  private readonly profileSubject = new BehaviorSubject<UserProfile | null>(null);
  readonly profile$ = this.profileSubject.asObservable();

  constructor(private userService: UserService) {}

  load(): Observable<UserProfile> {
    return this.userService.getMe().pipe(
      tap(profile => this.profileSubject.next(profile))
    );
  }

  clear(): void {
    this.profileSubject.next(null);
  }

  get userId(): number | null {
    return this.profileSubject.value?.id ?? null;
  }
}
