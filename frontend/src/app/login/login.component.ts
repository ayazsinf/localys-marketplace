import { Component } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { AuthService } from '../service/auth.service';
@Component({
    standalone: false,
    selector: 'app-login',
    templateUrl: './login.component.html'
})
export class LoginComponent {
    constructor(
        private authService: AuthService,
        private dialogRef: MatDialogRef<LoginComponent>
    ) {}

    submit() {
        this.authService.login().subscribe({
            next: () => this.dialogRef.close(true),
            error: () => this.dialogRef.close(false)
        });
    }

    close() {
        this.dialogRef.close(false);
    }
}
