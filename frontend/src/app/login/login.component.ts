import { Component } from '@angular/core';
import {FormBuilder, Validators} from '@angular/forms';
import { MatDialogRef} from "@angular/material/dialog";
import {AuthService} from "../service/auth.service";


@Component({
    standalone: false,
    selector: 'app-login',
    templateUrl: './login.component.html'
})
export class LoginComponent {
    form = this.fb.group({
        username: ['', [Validators.required]],
        password: ['', [Validators.required, Validators.minLength(6)]],
    });

    error: string | null = null;

    constructor(
        private fb: FormBuilder,
        private authService: AuthService,
        private dialogRef: MatDialogRef<LoginComponent>
    ) {}

    submit() {
        if (this.form.invalid) return;

        const { username, password } = this.form.value;

        this.authService.login(this.form.value.username!, this.form.value.password!)
            .subscribe({
                next: () => this.dialogRef.close(true),
                error: (err) => this.error = err.error?.message ?? 'Login failed'
            });

    }

    close() {
        this.dialogRef.close(false);
    }
}