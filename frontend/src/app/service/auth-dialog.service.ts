import { Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable, map } from 'rxjs';
import { AuthDialogComponent, AuthDialogData, AuthDialogResult } from '../components/auth-dialog/auth-dialog.component';

@Injectable({
  providedIn: 'root'
})
export class AuthDialogService {
  constructor(private readonly dialog: MatDialog) {}

  openLogin(): Observable<boolean> {
    return this.open('login');
  }

  openRegister(): Observable<boolean> {
    return this.open('register');
  }

  private open(mode: AuthDialogData['mode']): Observable<boolean> {
    const ref = this.dialog.open<AuthDialogComponent, AuthDialogData, AuthDialogResult>(AuthDialogComponent, {
      width: '420px',
      maxWidth: 'calc(100vw - 32px)',
      autoFocus: 'first-tabbable',
      panelClass: 'auth-dialog-panel',
      data: { mode }
    });

    return ref.afterClosed().pipe(map(result => result === 'authenticated'));
  }
}
