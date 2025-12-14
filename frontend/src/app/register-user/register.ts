// src/app/auth/register/register.component.ts
import { Component } from '@angular/core';
import {FormBuilder, NonNullableFormBuilder, Validators} from '@angular/forms';
import { Router } from '@angular/router';
import {MatDialogRef} from "@angular/material/dialog";
import {RegisterRequest} from "../modules/RegisterRequest";
import {HttpErrorResponse} from "@angular/common/http";
import {AuthService} from "../service/auth.service";

@Component({
    selector: 'app-register',
    standalone: false,
    templateUrl: './register.html',
    styleUrl: './register.scss'
})
export class RegisterComponent {

    form = this.fb.group({
        username: ['', [Validators.required]],
        email: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required, Validators.minLength(6)]],
    });

    error: string | null = null;
    loading = false;

    constructor(
        private fb: NonNullableFormBuilder,
        private authService: AuthService,
        private dialogRef: MatDialogRef<RegisterComponent>
    ) {}

    submit(): void {
        if (this.form.invalid || this.loading) {
            this.form.markAllAsTouched();
            return;
        }

        this.loading = true;
        this.error = null;

        const req: RegisterRequest = this.form.getRawValue();
        this.authService.register(req)
            .subscribe({
                next: () => this.dialogRef.close(true),
                error: (err) => this.error = err.error?.message ?? 'Register failed'
            });

    }
    close(): void {
        this.dialogRef.close(false);
    }
}
