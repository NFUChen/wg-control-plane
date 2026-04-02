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
      },
      {
        path: 'ansible',
        loadComponent: () =>
          import('./features/ansible/ansible-hub/ansible-hub.component').then(c => c.AnsibleHubComponent),
        title: 'Ansible'
      },
      {
        path: 'ansible/keys',
        loadComponent: () =>
          import('./features/ansible/private-keys/private-key-list.component').then(c => c.PrivateKeyListComponent),
        title: 'SSH keys'
      },
      {
        path: 'ansible/keys/new',
        loadComponent: () =>
          import('./features/ansible/private-keys/private-key-form.component').then(c => c.PrivateKeyFormComponent),
        title: 'Add SSH key'
      },
      {
        path: 'ansible/keys/:id/edit',
        loadComponent: () =>
          import('./features/ansible/private-keys/private-key-form.component').then(c => c.PrivateKeyFormComponent),
        title: 'Edit SSH key'
      },
      {
        path: 'ansible/groups',
        loadComponent: () =>
          import('./features/ansible/groups/ansible-group-list.component').then(c => c.AnsibleGroupListComponent),
        title: 'Inventory groups'
      },
      {
        path: 'ansible/groups/new',
        loadComponent: () =>
          import('./features/ansible/groups/ansible-group-form.component').then(c => c.AnsibleGroupFormComponent),
        title: 'New group'
      },
      {
        path: 'ansible/groups/:id/edit',
        loadComponent: () =>
          import('./features/ansible/groups/ansible-group-form.component').then(c => c.AnsibleGroupFormComponent),
        title: 'Edit group'
      },
      {
        path: 'ansible/hosts',
        loadComponent: () =>
          import('./features/ansible/hosts/ansible-host-list.component').then(c => c.AnsibleHostListComponent),
        title: 'Ansible hosts'
      },
      {
        path: 'ansible/hosts/new',
        loadComponent: () =>
          import('./features/ansible/hosts/ansible-host-form.component').then(c => c.AnsibleHostFormComponent),
        title: 'Add host'
      },
      {
        path: 'ansible/hosts/:id/edit',
        loadComponent: () =>
          import('./features/ansible/hosts/ansible-host-form.component').then(c => c.AnsibleHostFormComponent),
        title: 'Edit host'
      },
      {
        path: 'ansible/inventory',
        loadComponent: () =>
          import('./features/ansible/inventory/ansible-inventory.component').then(c => c.AnsibleInventoryComponent),
        title: 'Inventory validation'
      }
    ]
  },
  {
    path: '**',
    redirectTo: '/servers'
  }
];
