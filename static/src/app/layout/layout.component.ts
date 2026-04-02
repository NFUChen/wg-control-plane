import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';

import { ThemeService } from '../services/theme.service';
import { AuthService } from '../services/auth.service';
import { WireguardService } from '../services/wireguard.service';
import { GlobalSettingsPanelComponent } from './global-settings-panel/global-settings-panel.component';
import { UserAccountMenuComponent } from './user-account-menu/user-account-menu.component';

/** Sidebar nav item for template iteration */
interface NavItem {
  path: string;
  label: string;
  /** SVG path d attribute (24x24 outline) */
  icon: string;
  /** Only match exact path (e.g. Ansible hub) */
  exact?: boolean;
}

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, GlobalSettingsPanelComponent, UserAccountMenuComponent],
  template: `
    <div class="min-h-screen bg-gray-50 dark:bg-gray-950 text-gray-900 dark:text-gray-100 flex">
      <!-- Mobile overlay -->
      @if (sidebarOpen) {
        <button
          type="button"
          class="fixed inset-0 z-40 bg-black/50 md:hidden"
          aria-label="Close menu"
          (click)="sidebarOpen = false"
        ></button>
      }

      <!-- Left sidebar: drawer on small screens, persistent from md up -->
      <aside
        id="app-sidebar"
        class="fixed inset-y-0 left-0 z-50 flex min-h-screen w-64 shrink-0 flex-col border-r border-gray-200 bg-white shadow-sm transition-transform duration-200 ease-out dark:border-gray-800 dark:bg-gray-900 md:relative md:z-0 md:min-h-screen md:shadow-none"
        [class.max-md:-translate-x-full]="!sidebarOpen"
        [class.max-md:translate-x-0]="sidebarOpen"
      >
        <!-- Brand -->
        <div class="flex h-14 shrink-0 items-center gap-2 border-b border-gray-200 px-4 dark:border-gray-800">
          <a
            routerLink="/servers"
            class="flex min-w-0 items-center gap-2 rounded-md font-semibold text-gray-900 outline-none ring-blue-500 focus-visible:ring-2 dark:text-white"
            (click)="closeSidebarOnNavigate()"
          >
            <span
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-blue-600 text-xs font-bold text-white"
              >WG</span
            >
            <span class="truncate text-sm leading-tight">Control Plane</span>
          </a>
        </div>

        <!-- Primary navigation -->
        <nav class="flex-1 overflow-y-auto px-3 py-4" aria-label="Main">
          <p class="mb-2 px-2 text-[11px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
            Operations
          </p>
          <ul class="space-y-0.5">
            @for (item of primaryNav; track item.path) {
              <li>
                <a
                  [routerLink]="item.path"
                  [routerLinkActiveOptions]="{ exact: item.exact ?? false }"
                  routerLinkActive="bg-blue-50 text-blue-700 dark:bg-blue-950/50 dark:text-blue-300"
                  class="group flex items-center gap-3 rounded-lg px-2 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-gray-800"
                  (click)="closeSidebarOnNavigate()"
                >
                  <svg
                    class="h-5 w-5 shrink-0 text-gray-400 group-hover:text-gray-600 dark:text-gray-500 dark:group-hover:text-gray-300"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                    stroke-width="1.5"
                    aria-hidden="true"
                  >
                    <path stroke-linecap="round" stroke-linejoin="round" [attr.d]="item.icon" />
                  </svg>
                  {{ item.label }}
                </a>
              </li>
            }
          </ul>

          <p class="mb-2 mt-6 px-2 text-[11px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
            Ansible
          </p>
          <ul class="space-y-0.5 border-l border-gray-200 ml-2 pl-2 dark:border-gray-700">
            @for (item of ansibleNav; track item.path) {
              <li>
                <a
                  [routerLink]="item.path"
                  [routerLinkActiveOptions]="{ exact: item.exact ?? false }"
                  routerLinkActive="bg-blue-50 text-blue-700 dark:bg-blue-950/50 dark:text-blue-300"
                  class="group flex items-center gap-3 rounded-lg px-2 py-1.5 text-sm text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-gray-800"
                  (click)="closeSidebarOnNavigate()"
                >
                  <svg
                    class="h-4 w-4 shrink-0 text-gray-400 group-hover:text-gray-600 dark:text-gray-500"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                    stroke-width="1.5"
                    aria-hidden="true"
                  >
                    <path stroke-linecap="round" stroke-linejoin="round" [attr.d]="item.icon" />
                  </svg>
                  {{ item.label }}
                </a>
              </li>
            }
          </ul>

          <p class="mb-2 mt-6 px-2 text-[11px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
            Account
          </p>
          <ul class="space-y-0.5">
            <li>
              <a
                routerLink="/profile"
                routerLinkActive="bg-blue-50 text-blue-700 dark:bg-blue-950/50 dark:text-blue-300"
                class="group flex items-center gap-3 rounded-lg px-2 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-gray-800"
                (click)="closeSidebarOnNavigate()"
              >
                <svg
                  class="h-5 w-5 shrink-0 text-gray-400 group-hover:text-gray-600 dark:text-gray-500"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                  stroke-width="1.5"
                  aria-hidden="true"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z"
                  />
                </svg>
                Profile
              </a>
            </li>
          </ul>
        </nav>
      </aside>

      <!-- Main column -->
      <div class="flex min-w-0 flex-1 flex-col md:pl-0">
        <!-- Top bar: context + tools -->
        <header
          class="sticky top-0 z-30 flex h-14 shrink-0 items-center gap-3 border-b border-gray-200 bg-white/95 px-4 backdrop-blur dark:border-gray-800 dark:bg-gray-900/95"
        >
          <button
            type="button"
            class="inline-flex rounded-md p-2 text-gray-600 hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500 md:hidden dark:text-gray-300 dark:hover:bg-gray-800"
            (click)="sidebarOpen = !sidebarOpen"
            [attr.aria-expanded]="sidebarOpen"
            aria-controls="app-sidebar"
            aria-label="Toggle navigation menu"
          >
            <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="1.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
            </svg>
          </button>

          <div class="min-w-0 flex-1">
            <h1 class="truncate text-base font-semibold text-gray-900 dark:text-gray-100">
              {{ getCurrentPageTitle() }}
            </h1>
          </div>

          <div class="flex shrink-0 items-center gap-1">
            @if (auth.user()) {
              <app-user-account-menu />
            }
            <button
              type="button"
              (click)="globalSettingsOpen = true"
              class="inline-flex items-center justify-center rounded-md p-2 text-gray-600 hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:text-gray-300 dark:hover:bg-gray-800"
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
              class="inline-flex items-center justify-center rounded-md p-2 text-gray-600 hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:text-gray-300 dark:hover:bg-gray-800"
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
          </div>
        </header>

        <!-- Page content -->
        <main class="flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <nav class="mb-6 text-sm" aria-label="Breadcrumb">
            <ol class="flex flex-wrap items-center gap-x-2 text-gray-500 dark:text-gray-400">
              <li>
                <a routerLink="/servers" class="hover:text-blue-600 dark:hover:text-blue-400">Home</a>
              </li>
              <li aria-hidden="true" class="text-gray-300 dark:text-gray-600">/</li>
              <li class="font-medium text-gray-700 dark:text-gray-300">{{ getCurrentPageTitle() }}</li>
            </ol>
          </nav>
          <router-outlet></router-outlet>
        </main>
      </div>

      <app-global-settings-panel
        [(open)]="globalSettingsOpen"
        (saved)="onGlobalSettingsSaved()"
      />
    </div>
  `
})
export class LayoutComponent {
  readonly theme = inject(ThemeService);
  readonly auth = inject(AuthService);
  private readonly wireguard = inject(WireguardService);
  globalSettingsOpen = false;
  /** Mobile / small screens: slide-out sidebar */
  sidebarOpen = false;

  readonly primaryNav: NavItem[] = [
    {
      path: '/servers',
      label: 'Servers',
      icon: 'M5.25 14.25h13.5m-13.5 0a3 3 0 01-3-3V4.875c0-.621.504-1.125 1.125-1.125h4.125c.621 0 1.125.504 1.125 1.125v.75c0 .621.504 1.125 1.125 1.125h4.125c.621 0 1.125-.504 1.125-1.125v-.75c0-.621.504-1.125 1.125-1.125h4.125c.621 0 1.125.504 1.125 1.125V11.25a3 3 0 01-3 3m-6.75-6.75h.008v.008h-.008V7.5zm3.75 0h.008v.008h-.008V7.5zm3.75 0h.008v.008h-.008V7.5z'
    }
  ];

  readonly ansibleNav: NavItem[] = [
    {
      path: '/ansible',
      label: 'Overview',
      exact: true,
      icon: 'M3.75 6A2.25 2.25 0 016 3.75h2.25A2.25 2.25 0 0110.5 6v2.25a2.25 2.25 0 01-2.25 2.25H6a2.25 2.25 0 01-2.25-2.25V6zM3.75 15.75A2.25 2.25 0 016 13.5h2.25a2.25 2.25 0 012.25 2.25V18a2.25 2.25 0 01-2.25 2.25H6A2.25 2.25 0 013.75 18v-2.25zM13.5 6a2.25 2.25 0 012.25-2.25H18A2.25 2.25 0 0120.25 6v2.25a2.25 2.25 0 01-2.25 2.25H15.75a2.25 2.25 0 01-2.25-2.25V6zM13.5 15.75a2.25 2.25 0 012.25-2.25H18a2.25 2.25 0 012.25 2.25V18A2.25 2.25 0 0118 20.25h-2.25A2.25 2.25 0 0113.5 18v-2.25z'
    },
    {
      path: '/ansible/keys',
      label: 'SSH keys',
      icon: 'M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v-2.25l6.864-6.864c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z'
    },
    {
      path: '/ansible/groups',
      label: 'Groups',
      icon: 'M18 18.72a9.094 9.094 0 003.741-.479 3 3 0 00-4.682-2.72m.94 3.198l.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0112 21c-2.17 0-4.207-.576-5.963-1.584A6 6 0 006 18.719m12 0a5.971 5.971 0 00-.941-3.197m0 0A5.995 5.995 0 0012 12.75a5.995 5.995 0 00-5.058 2.772m0 0a3 3 0 00-4.681 2.72 8.986 8.986 0 003.74.477m.94-3.197a5.971 5.971 0 00-.94 3.197M15 12a3 3 0 11-6 0 3 3 0 016 0z'
    },
    {
      path: '/ansible/hosts',
      label: 'Hosts',
      icon: 'M12 21a9.004 9.004 0 008.716-6.747M12 21a9.004 9.004 0 01-8.716-6.747M12 21c2.485 0 4.5-4.03 4.5-9S14.485 3 12 3m0 18c-2.485 0-4.5-4.03-4.5-9S9.515 3 12 3m0 0a8.997 8.997 0 017.843 4.582M12 3a8.997 8.997 0 00-7.843 4.582m15.686 0A11.953 11.953 0 0112 10.5c-2.998 0-5.74-1.1-7.843-2.918m15.686 0A8.959 8.959 0 0121 12c0 .778-.099 1.533-.284 2.253m0 0A17.919 17.919 0 0112 16.5c-3.162 0-6.133-.815-8.716-2.247m0 0A9.015 9.015 0 013 12c0-1.605.42-3.113 1.157-4.418'
    },
    {
      path: '/ansible/inventory',
      label: 'Inventory check',
      icon: 'M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z'
    }
  ];

  constructor(private router: Router) {
    this.router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => {
      this.sidebarOpen = false;
    });
  }

  closeSidebarOnNavigate(): void {
    if (typeof window !== 'undefined' && window.matchMedia('(max-width: 767px)').matches) {
      this.sidebarOpen = false;
    }
  }

  onGlobalSettingsSaved(): void {
    this.wireguard.refreshServers().subscribe({ error: () => {} });
  }

  getCurrentPageTitle(): string {
    const url = this.router.url.split('?')[0];
    if (url.includes('/ansible/inventory')) return 'Inventory validation';
    if (url.includes('/ansible/keys') && url.includes('/edit')) return 'Edit SSH key';
    if (url.includes('/ansible/keys/new')) return 'Add SSH key';
    if (url.includes('/ansible/keys')) return 'SSH keys';
    if (url.includes('/ansible/groups') && url.includes('/edit')) return 'Edit group';
    if (url.includes('/ansible/groups/new')) return 'New group';
    if (url.includes('/ansible/groups')) return 'Inventory groups';
    if (url.includes('/ansible/hosts') && url.includes('/edit')) return 'Edit host';
    if (url.includes('/ansible/hosts/new')) return 'Add host';
    if (url.includes('/ansible/hosts')) return 'Ansible hosts';
    if (url === '/ansible' || url === '/ansible/') return 'Ansible';
    if (url.includes('/ansible')) return 'Ansible';
    if (url.includes('/profile')) return 'Profile';
    if (url.includes('/servers/new')) return 'Create Server';
    if (url.includes('/edit')) return 'Edit Server';
    if (url.includes('/clients/new')) return 'Add Client';
    if (url.includes('/clients')) return 'Clients';
    if (url.includes('/servers/') && !url.includes('/new') && !url.includes('/edit')) return 'Server Details';
    if (url.includes('/servers')) return 'Servers';
    return 'Dashboard';
  }
}
