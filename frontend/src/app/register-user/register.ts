// src/app/auth/register/register.component.ts
import { Component } from '@angular/core';
import {MatDialogRef} from "@angular/material/dialog";
import {AuthService} from "../service/auth.service";

@Component({
    selector: 'app-register',
    standalone: false,
    templateUrl: './register.html',
    styleUrl: './register.scss'
})
export class RegisterComponent {
    constructor(
        private authService: AuthService,
        private dialogRef: MatDialogRef<RegisterComponent>
    ) {}

    submit(): void {
        this.authService.register().subscribe({
            next: () => this.dialogRef.close(true),
            error: () => this.dialogRef.close(false)
        });
    }
    close(): void {
        this.dialogRef.close(false);
    }
}
