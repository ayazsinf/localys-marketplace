import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { AuthService } from '../../service/auth.service';
import { finalize } from 'rxjs';

export interface AuthDialogData {
  mode: 'login' | 'register';
}

export type AuthDialogResult = 'authenticated' | undefined;

@Component({
  selector: 'app-auth-dialog',
  standalone: false,
  templateUrl: './auth-dialog.component.html',
  styleUrl: './auth-dialog.component.scss'
})
export class AuthDialogComponent {
  private readonly data = inject<AuthDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<AuthDialogComponent, AuthDialogResult>>(MatDialogRef);
  private readonly authService = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  mode: AuthDialogData['mode'];
  username = '';
  email = '';
  password = '';
  error = '';
  isSubmitting = false;

  constructor() {
    this.mode = this.data.mode;
  }

  get isRegisterMode(): boolean {
    return this.mode === 'register';
  }

  get passwordRules() {
    return [
      { label: 'At least 8 characters', valid: this.password.length >= 8 },
      { label: 'One uppercase letter', valid: /[A-Z]/.test(this.password) },
      { label: 'One lowercase letter', valid: /[a-z]/.test(this.password) },
      { label: 'One number', valid: /\d/.test(this.password) },
      { label: 'One special character', valid: /[^A-Za-z0-9]/.test(this.password) }
    ];
  }

  get isPasswordValid(): boolean {
    return this.passwordRules.every(rule => rule.valid);
  }

  switchMode(mode: AuthDialogData['mode']): void {
    if (this.isSubmitting) {
      return;
    }
    this.mode = mode;
    this.error = '';
  }

  submit(): void {
    this.error = '';
    if (!this.username || !this.password || (this.isRegisterMode && !this.email)) {
      this.error = 'Please fill in all required fields.';
      return;
    }
    if (this.isRegisterMode && !this.isPasswordValid) {
      this.error = 'Password does not meet the requirements.';
      return;
    }

    this.isSubmitting = true;
    const request$ = this.isRegisterMode
      ? this.authService.register({
          username: this.username.trim(),
          email: this.email.trim(),
          password: this.password
        })
      : this.authService.login({
          username: this.username.trim(),
          password: this.password
        });

    request$.pipe(
      finalize(() => {
        this.isSubmitting = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: () => this.dialogRef.close('authenticated'),
      error: error => {
        this.error = this.resolveErrorMessage(error);
        this.cdr.detectChanges();
      }
    });
  }

  private resolveErrorMessage(error: any): string {
    const code = error?.error?.code;
    if (code === 'USER_ALREADY_EXISTS') {
      return 'Username or email already exists.';
    }
    if (code === 'BAD_CREDENTIALS') {
      return 'Invalid username or password.';
    }
    if (code === 'AUTH_PROVIDER_UNAVAILABLE') {
      return 'Authentication service is unavailable. Please try again.';
    }
    if (error?.name === 'TimeoutError') {
      return 'Authentication service did not respond. Please try again.';
    }
    return error?.error?.message || (this.isRegisterMode
      ? 'Account could not be created.'
      : 'Invalid username or password.');
  }
}
