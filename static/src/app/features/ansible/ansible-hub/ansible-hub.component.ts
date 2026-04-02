import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-ansible-hub',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="space-y-8">
      <div>
        <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100">Ansible</h2>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          Manage SSH keys, inventory groups, hosts, and validate the effective Ansible inventory.
        </p>
      </div>

      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        @for (card of cards; track card.path) {
          <a
            [routerLink]="card.path"
            class="block rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-6 shadow-sm hover:border-blue-400 dark:hover:border-blue-500 transition-colors"
          >
            <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">{{ card.title }}</h3>
            <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">{{ card.description }}</p>
          </a>
        }
      </div>
    </div>
  `
})
export class AnsibleHubComponent {
  readonly cards = [
    {
      path: '/ansible/keys',
      title: 'Private key vault',
      description: 'Store SSH private keys used by Ansible hosts.'
    },
    {
      path: '/ansible/groups',
      title: 'Inventory groups',
      description: 'Define Ansible groups and group variables.'
    },
    {
      path: '/ansible/hosts',
      title: 'Hosts',
      description: 'Register Linux targets and SSH settings.'
    },
    {
      path: '/ansible/inventory',
      title: 'Inventory check',
      description: 'Validate configuration and preview the effective inventory; optional on-server file snapshots.'
    }
  ];
}
