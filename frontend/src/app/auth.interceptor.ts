import { HttpInterceptorFn } from '@angular/common/http';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { keycloak } from './keycloak.service';

let sessionExpiredNotified = false;

export const httpAuthInterceptor: HttpInterceptorFn = (req, next) => {
  if (!keycloak.token) {
    return next(req);
  }

  return from(keycloak.updateToken(30)).pipe(
    switchMap(() => {
      const token = keycloak.token;
      if (token) {
        req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
      }
      return next(req);
    }),
    catchError(err => {
      const isExpired = typeof keycloak.isTokenExpired === 'function' && keycloak.isTokenExpired(5);
      if (keycloak.authenticated && isExpired && !sessionExpiredNotified) {
        sessionExpiredNotified = true;
        window.alert('Session expired. Please sign in again.');
        keycloak.login();
      }
      return throwError(() => err);
    })
  );
};
