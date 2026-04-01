import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';

import { ThemeService } from '../services/theme.service';
import { WireguardService } from '../services/wireguard.service';
import { GlobalSettingsPanelComponent } from './global-settings-panel/global-settings-panel.component';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, GlobalSettingsPanelComponent],
  template: `
    <div class="min-h-screen bg-gray-50 dark:bg-gray-950 text-gray-900 dark:text-gray-100">
      <!-- Navigation Header -->
      <nav class="bg-white dark:bg-gray-900 shadow-sm border-b border-gray-200 dark:border-gray-800">
        <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div class="flex justify-between items-center h-16">
            <!-- Logo and main nav -->
            <div class="flex items-center">
              <div class="flex-shrink-0">
                <h1 class="text-xl font-bold text-gray-900 dark:text-white">WireGuard Control Plane</h1>
              </div>
              <div class="hidden md:block ml-10">
                <div class="flex items-baseline space-x-4">
                  <a
                    routerLink="/servers"
                    routerLinkActive="bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300"
                    class="text-gray-900 dark:text-gray-100 hover:bg-gray-100 dark:hover:bg-gray-800 hover:text-gray-900 dark:hover:text-white px-3 py-2 rounded-md text-sm font-medium transition-colors duration-200"
                  >
                    Servers
                  </a>
                </div>
              </div>
            </div>

            <div class="flex items-center gap-1">
            <button
              type="button"
              (click)="globalSettingsOpen = true"
              class="inline-flex items-center justify-center rounded-md p-2 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
              aria-label="Open global settings"
              title="Global settings"
            >
              <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="1.5">
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.324.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 011.37.49l1.296 2.247a1.125 1.125 0 01-.26 1.431l-1.003.827c-.293.24-.438.613-.431.992a6.759 6.759 0 010 .255c-.007.378.138.75.43.99l1.005.828c.424.35.534.954.26 1.43l-1.298 2.247a1.125 1.125 0 01-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.57 6.57 0 01-.22.128c-.331.183-.581.495-.644.869l-.213 1.28c-.09.543-.56.941-1.11.941h-2.594c-.55 0-1.02-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 01-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 01-1.369-.49l-1.297-2.247a1.125 1.125 0 01.26-1.431l1.004-.827c.292-.24.437-.613.43-.992a6.932 6.932 0 010-.255c.007-.378-.138-.75-.43-.99l-1.004-.828a1.125 1.125 0 01-.26-1.43l1.297-2.247a1.125 1.125 0 011.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.087.22-.128.332-.183.582-.495.644-.869l.214-1.281z"
                />
                <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
            </button>

            <button
              type="button"
              (click)="theme.toggle()"
              class="inline-flex items-center justify-center rounded-md p-2 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
              [attr.aria-label]="theme.isDark() ? 'Switch to light theme' : 'Switch to dark theme'"
            >
              <!-- Sun: show in dark mode (switch to light) -->
              @if (theme.isDark()) {
                <svg
                  class="h-5 w-5"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z"
                  />
                </svg>
              }
              @if (!theme.isDark()) {
                <svg
                  class="h-5 w-5"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z"
                  />
                </svg>
              }
            </button>
            </div>
          </div>
        </div>

        <!-- Mobile menu -->
        <div class="md:hidden border-t border-gray-200 dark:border-gray-800">
          <div class="px-2 pt-2 pb-3 space-y-1 sm:px-3">
            <a
              routerLink="/servers"
              routerLinkActive="bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300"
              class="text-gray-900 dark:text-gray-100 hover:bg-gray-100 dark:hover:bg-gray-800 hover:text-gray-900 dark:hover:text-white block px-3 py-2 rounded-md text-base font-medium"
            >
              Servers
            </a>
          </div>
        </div>
      </nav>

      <!-- Main content -->
      <main class="max-w-8xl mx-auto py-6 sm:px-6 lg:px-8">
        <div class="px-4 py-6 sm:px-0">
          <!-- Breadcrumb navigation -->
          <nav class="flex mb-6" aria-label="Breadcrumb">
            <ol class="inline-flex items-center space-x-1 md:space-x-3">
              <li class="inline-flex items-center">
                <a
                  routerLink="/servers"
                  class="inline-flex items-center text-sm font-medium text-gray-700 dark:text-gray-300 hover:text-blue-600 dark:hover:text-blue-400"
                >
                  <svg class="mr-2 w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                    <path d="M10.707 2.293a1 1 0 00-1.414 0l-7 7a1 1 0 001.414 1.414L4 10.414V17a1 1 0 001 1h2a1 1 0 001-1v-2a1 1 0 011-1h2a1 1 0 011 1v2a1 1 0 001 1h2a1 1 0 001-1v-6.586l.293.293a1 1 0 001.414-1.414l-7-7z"></path>
                  </svg>
                  Home
                </a>
              </li>
              <li>
                <div class="flex items-center">
                  <svg class="w-6 h-6 text-gray-400 dark:text-gray-500" fill="currentColor" viewBox="0 0 20 20">
                    <path fill-rule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clip-rule="evenodd"></path>
                  </svg>
                  <span class="ml-1 text-sm font-medium text-gray-500 dark:text-gray-400 md:ml-2">{{ getCurrentPageTitle() }}</span>
                </div>
              </li>
            </ol>
          </nav>

          <!-- Page content -->
          <router-outlet></router-outlet>
        </div>
      </main>

      <app-global-settings-panel
        [(open)]="globalSettingsOpen"
        (saved)="onGlobalSettingsSaved()"
      />
    </div>
  `
})
export class LayoutComponent {
  readonly theme = inject(ThemeService);
  private readonly wireguard = inject(WireguardService);
  globalSettingsOpen = false;

  constructor(private router: Router) {}

  onGlobalSettingsSaved(): void {
    this.wireguard.refreshServers().subscribe({ error: () => {} });
  }

  getCurrentPageTitle(): string {
    const url = this.router.url;
    if (url.includes('/servers/new')) return 'Create Server';
    if (url.includes('/edit')) return 'Edit Server';
    if (url.includes('/clients/new')) return 'Add Client';
    if (url.includes('/clients')) return 'Clients';
    if (url.includes('/servers/') && !url.includes('/new') && !url.includes('/edit')) return 'Server Details';
    if (url.includes('/servers')) return 'Servers';
    return 'Dashboard';
  }
}
