import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/servers',
    pathMatch: 'full'
  },
  {
    path: 'servers',
    loadComponent: () => import('./features/servers/server-list/server-list.component').then(c => c.ServerListComponent),
    title: 'Servers'
  },
  {
    path: 'servers/new',
    loadComponent: () => import('./features/servers/server-form/server-form.component').then(c => c.ServerFormComponent),
    title: 'Create Server'
  },
  {
    path: 'servers/:id',
    loadComponent: () => import('./features/servers/server-detail/server-detail.component').then(c => c.ServerDetailComponent),
    title: 'Server Details'
  },
  {
    path: 'servers/:id/edit',
    loadComponent: () => import('./features/servers/server-form/server-form.component').then(c => c.ServerFormComponent),
    title: 'Edit Server'
  },
  {
    path: 'servers/:serverId/clients',
    loadComponent: () => import('./features/clients/client-list/client-list.component').then(c => c.ClientListComponent),
    title: 'Clients'
  },
  {
    path: 'servers/:serverId/clients/new',
    loadComponent: () => import('./features/clients/client-form/client-form.component').then(c => c.ClientFormComponent),
    title: 'Add Client'
  },
  {
    path: '**',
    redirectTo: '/servers'
  }
];
