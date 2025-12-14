import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import {AuthService} from "../service/auth.service";

export const authGuard: CanActivateFn = () => {
    const router = inject(Router);
    const auth = inject(AuthService);

    const token = auth.token;
    if (!token) {
        router.navigate(['/']);
        return false;
    }

    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        if (Date.now() > payload.exp * 1000) {
            auth.logout();
            router.navigate(['/']);
            return false;
        }
    } catch {
        auth.logout();
        router.navigate(['/']);
        return false;
    }

    return true;
};
