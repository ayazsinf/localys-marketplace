import { ApplicationConfig, APP_INITIALIZER, importProvidersFrom } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { provideHttpClient, HttpClient, withInterceptors } from '@angular/common/http';

import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';

import { AssetsTranslateLoader } from './translate-loader';
import { httpAuthInterceptor } from './auth.interceptor';
import { initKeycloak } from './keycloak.service';
export function createTranslateLoader(http: HttpClient) {
    return new AssetsTranslateLoader(http);
}

export const appConfig: ApplicationConfig = {
    providers: [

        provideRouter(routes),

        // 🔥 HttpClient provider — zorunlu
        provideHttpClient(
            withInterceptors([httpAuthInterceptor])
        ),
        {
            provide: APP_INITIALIZER,
            useFactory: () => initKeycloak,
            multi: true
        },

        // 🔥 ngx-translate provider
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
    ],
};
