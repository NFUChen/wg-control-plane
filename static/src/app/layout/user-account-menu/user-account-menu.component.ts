import { Component, HostListener, inject, signal, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-user-account-menu',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="relative">
      <button
        type="button"
        (click)="toggleMenu($event)"
        class="inline-flex max-w-full items-center gap-2 rounded-md border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-800 px-2 py-1.5 text-left text-sm text-gray-800 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
        [attr.aria-expanded]="open()"
        aria-haspopup="true"
      >
        <span
          class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-blue-100 dark:bg-blue-900/50 text-sm font-medium text-blue-800 dark:text-blue-200"
        >
          {{ initial() }}
        </span>
        <span class="hidden min-w-0 sm:block max-w-[12rem] truncate" [title]="label()">
          {{ label() }}
        </span>
        <svg class="h-4 w-4 shrink-0 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      @if (open()) {
        <div
          class="absolute right-0 z-50 mt-1 w-56 origin-top-right rounded-md bg-white dark:bg-gray-800 py-1 shadow-lg ring-1 ring-black/5 dark:ring-white/10"
          role="menu"
        >
          <a
            routerLink="/profile"
            (click)="close()"
            class="block px-4 py-2 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-700"
            role="menuitem"
          >
            Profile
          </a>
          <button
            type="button"
            (click)="signOut()"
            class="w-full text-left px-4 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-gray-100 dark:hover:bg-gray-700"
            role="menuitem"
          >
            Sign out
          </button>
        </div>
      }
    </div>
  `
})
export class UserAccountMenuComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly open = signal(false);

  initial(): string {
    const u = this.auth.user();
    const s = (u?.email || u?.username || '?').trim();
    return (s[0] || '?').toUpperCase();
  }

  label(): string {
    const u = this.auth.user();
    return (u?.email || u?.username || 'Account').trim();
  }

  toggleMenu(event: Event): void {
    event.stopPropagation();
    this.open.update(v => !v);
  }

  close(): void {
    this.open.set(false);
  }

  signOut(): void {
    this.close();
    this.auth.logout().subscribe({
      next: () => this.router.navigate(['/login'])
    });
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(ev: MouseEvent): void {
    if (!this.open()) return;
    const t = ev.target;
    if (t instanceof Node && this.host.nativeElement.contains(t)) return;
    this.close();
  }
}
