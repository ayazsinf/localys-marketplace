import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from "../service/auth.service";
import { keycloak } from '../keycloak.service';

export const authGuard: CanActivateFn = () => {
    const router = inject(Router);
    const auth = inject(AuthService);

    if (!auth.isAuthenticated) {
        keycloak.login();
        return false;
    }

    return true;
};
