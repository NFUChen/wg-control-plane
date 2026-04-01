import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-user-profile',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="max-w-2xl">
      <h1 class="text-2xl font-semibold text-gray-900 dark:text-white mb-2">Profile</h1>
      <p class="text-sm text-gray-600 dark:text-gray-400 mb-8">
        Signed-in account and session. Use Sign out to end this session on this browser.
      </p>

      <div
        class="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 shadow-sm overflow-hidden"
      >
        <div class="px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/80">
          <h2 class="text-sm font-medium text-gray-900 dark:text-white">Account</h2>
        </div>
        <dl class="divide-y divide-gray-200 dark:divide-gray-700">
          @if (auth.user(); as u) {
            <div class="px-4 py-3 sm:grid sm:grid-cols-3 sm:gap-4">
              <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">Email</dt>
              <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100 sm:col-span-2 sm:mt-0 break-all">
                {{ u.email || '—' }}
              </dd>
            </div>
            <div class="px-4 py-3 sm:grid sm:grid-cols-3 sm:gap-4">
              <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">Username</dt>
              <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100 sm:col-span-2 sm:mt-0">
                {{ u.username || '—' }}
              </dd>
            </div>
            @if (u.id) {
              <div class="px-4 py-3 sm:grid sm:grid-cols-3 sm:gap-4">
                <dt class="text-sm font-medium text-gray-500 dark:text-gray-400">User ID</dt>
                <dd class="mt-1 text-sm font-mono text-gray-800 dark:text-gray-200 sm:col-span-2 sm:mt-0 break-all">
                  {{ u.id }}
                </dd>
              </div>
            }
          } @else {
            <div class="px-4 py-6 text-sm text-gray-600 dark:text-gray-400">Loading account…</div>
          }
        </dl>
      </div>

      <div class="mt-8 flex flex-wrap items-center gap-3">
        <button
          type="button"
          (click)="signOut()"
          class="inline-flex justify-center rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-red-500 focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2 dark:focus:ring-offset-gray-950"
        >
          Sign out
        </button>
        <a
          routerLink="/servers"
          class="text-sm font-medium text-blue-600 dark:text-blue-400 hover:text-blue-500"
        >
          Back to Servers
        </a>
      </div>
    </div>
  `
})
export class UserProfileComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  signOut(): void {
    this.auth.logout().subscribe({
      next: () => this.router.navigate(['/login'])
    });
  }
}
