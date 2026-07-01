import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from "../service/auth.service";
import { AuthDialogService } from '../service/auth-dialog.service';

export const authGuard: CanActivateFn = (_route, state) => {
    const router = inject(Router);
    const auth = inject(AuthService);
    const authDialog = inject(AuthDialogService);

    if (!auth.isAuthenticated) {
        authDialog.openLogin().subscribe(authenticated => {
            if (authenticated) {
                router.navigateByUrl(state.url);
            }
        });
        return false;
    }

    return true;
};
