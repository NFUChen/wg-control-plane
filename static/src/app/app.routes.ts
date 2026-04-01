import { Routes } from '@angular/router';

import { authChildGuard, authGuard, loginPageGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then(c => c.LoginComponent),
    canActivate: [loginPageGuard],
    title: 'Sign in'
  },
  {
    path: '',
    loadComponent: () => import('./layout/layout.component').then(c => c.LayoutComponent),
    canActivate: [authGuard],
    canActivateChild: [authChildGuard],
    children: [
      {
        path: '',
        redirectTo: 'servers',
        pathMatch: 'full'
      },
      {
        path: 'servers',
        loadComponent: () =>
          import('./features/servers/server-list/server-list.component').then(c => c.ServerListComponent),
        title: 'Servers'
      },
      {
        path: 'servers/new',
        loadComponent: () =>
          import('./features/servers/server-form/server-form.component').then(c => c.ServerFormComponent),
        title: 'Create Server'
      },
      {
        path: 'servers/:id',
        loadComponent: () =>
          import('./features/servers/server-detail/server-detail.component').then(c => c.ServerDetailComponent),
        title: 'Server Details'
      },
      {
        path: 'servers/:id/edit',
        loadComponent: () =>
          import('./features/servers/server-form/server-form.component').then(c => c.ServerFormComponent),
        title: 'Edit Server'
      },
      {
        path: 'servers/:serverId/clients',
        loadComponent: () =>
          import('./features/clients/client-list/client-list.component').then(c => c.ClientListComponent),
        title: 'Clients'
      },
      {
        path: 'servers/:serverId/clients/new',
        loadComponent: () =>
          import('./features/clients/client-form/client-form.component').then(c => c.ClientFormComponent),
        title: 'Add Client'
      },
      {
        path: 'servers/:serverId/clients/:clientId/edit',
        loadComponent: () =>
          import('./features/clients/client-form/client-form.component').then(c => c.ClientFormComponent),
        title: 'Edit Client'
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./features/profile/user-profile.component').then(c => c.UserProfileComponent),
        title: 'Profile'
      }
    ]
  },
  {
    path: '**',
    redirectTo: '/servers'
  }
];
