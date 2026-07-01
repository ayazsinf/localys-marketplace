import { APP_INITIALIZER, ApplicationConfig, importProvidersFrom } from '@angular/core';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { AssetsTranslateLoader } from './translate-loader';
import { httpAuthInterceptor } from './auth.interceptor';
import { loadingInterceptor } from './loading.interceptor';
import { AuthService } from './service/auth.service';

export function createTranslateLoader(http: HttpClient) {
  return new AssetsTranslateLoader(http);
}

export function initializeAuth(authService: AuthService) {
  return () => firstValueFrom(authService.initialize());
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([loadingInterceptor, httpAuthInterceptor])
    ),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeAuth,
      deps: [AuthService],
      multi: true
    },
    importProvidersFrom(
      TranslateModule.forRoot({
        loader: {
          provide: TranslateLoader,
          useFactory: createTranslateLoader,
          deps: [HttpClient]
        },
        defaultLanguage: 'en'
      })
    )
  ]
};
