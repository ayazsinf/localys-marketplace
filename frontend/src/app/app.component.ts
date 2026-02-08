import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AppModule } from './app.module';
import { LoadingService } from './service/loading.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, AppModule],
  template: `
    <app-navbar></app-navbar>
    <div class="global-loader" *ngIf="loading$ | async" aria-live="polite" aria-busy="true">
      <div class="spinner" role="status" aria-label="Loading"></div>
    </div>
    <router-outlet></router-outlet>
  `,
  styles: [`
    .global-loader {
      position: fixed;
      inset: 0;
      background: rgba(255, 255, 255, 0.6);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 9999;
      backdrop-filter: blur(1px);
    }
    .spinner {
      width: 48px;
      height: 48px;
      border: 4px solid #e0e0e0;
      border-top-color: #111;
      border-radius: 50%;
      animation: spin 0.9s linear infinite;
    }
    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }
  `]
})
export class AppComponent {
  readonly loading$ = this.loadingService.loading$;

  constructor(private readonly loadingService: LoadingService) {}
}
