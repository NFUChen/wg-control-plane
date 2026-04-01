import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthService } from '../../../services/auth.service';
import { ThemeService } from '../../../services/theme.service';
import { AlertComponent } from '../../../shared/components/alert/alert.component';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    AlertComponent,
    LoadingSpinnerComponent
  ],
  template: `
    <div class="min-h-screen bg-gray-50 dark:bg-gray-950 flex flex-col">
      <header
        class="flex justify-end items-center px-4 py-3 border-b border-gray-200 dark:border-gray-800 bg-white/80 dark:bg-gray-900/80 backdrop-blur"
      >
        <button
          type="button"
          (click)="theme.toggle()"
          class="inline-flex items-center justify-center rounded-md p-2 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
          [attr.aria-label]="theme.isDark() ? 'Switch to light theme' : 'Switch to dark theme'"
        >
          @if (theme.isDark()) {
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z"
              />
            </svg>
          } @else {
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z"
              />
            </svg>
          }
        </button>
      </header>

      <div class="flex-1 flex items-center justify-center px-4 py-12">
        <div class="w-full max-w-md space-y-8">
          <div class="text-center">
            <h1 class="text-2xl font-bold text-gray-900 dark:text-white">WireGuard Control Plane</h1>
            <p class="mt-2 text-sm text-gray-600 dark:text-gray-400">Sign in with your account email and password</p>
          </div>

          @if (errorMessage) {
            <app-alert type="error" title="Sign-in error" [message]="errorMessage" (dismissed)="errorMessage = ''" />
          }

          <div class="bg-white dark:bg-gray-900 shadow-sm rounded-lg border border-gray-200 dark:border-gray-700 p-8">
            @if (submitting) {
              <app-loading-spinner [showText]="true" loadingText="Signing in..." containerClass="py-6" />
            } @else {
              <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-6">
                <div>
                  <label for="email" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    Email
                  </label>
                  <input
                    id="email"
                    type="email"
                    formControlName="email"
                    autocomplete="username"
                    class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="you@example.com"
                  />
                  @if (form.get('email')?.invalid && form.get('email')?.touched) {
                    <p class="mt-1 text-sm text-red-600 dark:text-red-400">Enter a valid email address</p>
                  }
                </div>

                <div>
                  <label for="password" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    Password
                  </label>
                  <input
                    id="password"
                    type="password"
                    formControlName="password"
                    autocomplete="current-password"
                    class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  />
                  @if (form.get('password')?.invalid && form.get('password')?.touched) {
                    <p class="mt-1 text-sm text-red-600 dark:text-red-400">Password is required</p>
                  }
                </div>

                <button
                  type="submit"
                  [disabled]="form.invalid"
                  class="w-full flex justify-center py-2.5 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed dark:focus:ring-offset-gray-900"
                >
                  Sign in
                </button>
              </form>
            }
          </div>
        </div>
      </div>
    </div>
  `
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  readonly theme = inject(ThemeService);

  submitting = false;
  errorMessage = '';

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, password } = this.form.getRawValue();
    this.errorMessage = '';
    this.submitting = true;
    this.auth.login({ email: email.trim(), password }).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/servers';
        this.router.navigateByUrl(returnUrl);
      },
      error: (err: Error) => {
        this.submitting = false;
        this.errorMessage = err.message || 'Sign-in failed.';
      }
    });
  }
}
