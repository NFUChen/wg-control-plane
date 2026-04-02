import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';

import { AnsibleService } from '../../../services/ansible.service';
import { DataTableComponent, TableAction } from '../../../shared/components/data-table/data-table.component';
import { AlertComponent } from '../../../shared/components/alert/alert.component';
import { StatusBadgeComponent, BadgeVariant } from '../../../shared/components/status-badge/status-badge.component';
import { PrivateKeySummary } from '../../../models/ansible.interface';
import { LoadingState, TableColumn } from '../../../models/wireguard.interface';

@Component({
  selector: 'app-private-key-list',
  standalone: true,
  imports: [CommonModule, RouterModule, DataTableComponent, AlertComponent, StatusBadgeComponent],
  template: `
    <div class="space-y-6">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <a
          routerLink="/ansible"
          class="text-sm font-medium text-blue-600 hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300"
        >
          ← Ansible
        </a>
      </div>

      @if (error) {
        <app-alert type="error" title="Error" [message]="error" (dismissed)="error = ''" />
      }
      @if (successMessage) {
        <app-alert type="success" [message]="successMessage" (dismissed)="successMessage = ''" />
      }

      <app-data-table
        title="Private key vault"
        subtitle="SSH keys referenced by Ansible hosts (key material is never shown in this table)"
        [columns]="columns"
        [data]="filtered"
        [loading]="loading.isLoading"
        [primaryAction]="primaryAction"
        [rowActions]="rowActions"
        [searchable]="true"
        [searchQuery]="searchQuery"
        emptyMessage="No keys stored"
        emptySubMessage="Add a PEM or OpenSSH private key for your hosts"
        (searchChange)="onSearchChange($event)"
        (primaryActionClick)="goNew()"
        (rowActionClick)="onRowAction($event)"
      >
        <ng-template #customTemplate let-item="$implicit" let-column="column">
          <ng-container [ngSwitch]="column.key">
            <span *ngSwitchCase="'enabled'">
              <app-status-badge [variant]="badge(item.enabled)" [label]="item.enabled ? 'Enabled' : 'Disabled'" />
            </span>
            <span *ngSwitchDefault class="text-sm text-gray-900 dark:text-gray-100">{{ item[column.key] }}</span>
          </ng-container>
        </ng-template>
      </app-data-table>
    </div>
  `
})
export class PrivateKeyListComponent implements OnInit, OnDestroy {
  filtered: PrivateKeySummary[] = [];
  private allKeys: PrivateKeySummary[] = [];

  loading: LoadingState = { isLoading: false };
  error = '';
  successMessage = '';
  searchQuery = '';

  columns: TableColumn[] = [
    { key: 'name', label: 'Name', sortable: true, type: 'text' },
    { key: 'description', label: 'Description', type: 'text' },
    { key: 'enabled', label: 'Status' },
    { key: 'updatedAt', label: 'Updated', sortable: true, type: 'date' },
    { key: 'actions', label: 'Actions', type: 'action', width: '120px' }
  ];

  primaryAction = { label: 'Add key', icon: 'M12 4v16m8-8H4' };

  rowActions: TableAction[] = [
    {
      label: 'Edit',
      icon: 'M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z',
      action: 'edit',
      variant: 'primary'
    },
    {
      label: 'Delete',
      icon: 'M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16',
      action: 'delete',
      variant: 'danger'
    }
  ];

  private destroy$ = new Subject<void>();

  constructor(
    private ansible: AnsibleService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.loading = { isLoading: true };
    this.ansible
      .listPrivateKeys()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: rows => {
          this.allKeys = rows;
          this.applyFilter();
          this.loading = { isLoading: false };
        },
        error: err => {
          this.error = this.ansible.getApiErrorMessage(err);
          this.loading = { isLoading: false };
        }
      });
  }

  applyFilter(): void {
    const q = this.searchQuery.trim().toLowerCase();
    if (!q) {
      this.filtered = [...this.allKeys];
      return;
    }
    this.filtered = this.allKeys.filter(
      k =>
        k.name.toLowerCase().includes(q) ||
        (k.description ?? '').toLowerCase().includes(q)
    );
  }

  onSearchChange(q: string): void {
    this.searchQuery = q;
    this.applyFilter();
  }

  badge(enabled: boolean): BadgeVariant {
    return enabled ? 'success' : 'gray';
  }

  goNew(): void {
    this.router.navigate(['/ansible/keys/new']);
  }

  onRowAction(ev: { action: string; item: PrivateKeySummary }): void {
    if (ev.action === 'edit') {
      this.router.navigate(['/ansible/keys', ev.item.id, 'edit']);
    }
    if (ev.action === 'delete') {
      if (
        confirm(
          `Delete private key "${ev.item.name}"? This will fail if any Ansible host still references it.`
        )
      ) {
        this.ansible.deletePrivateKey(ev.item.id).subscribe({
          next: () => {
            this.successMessage = 'Key deleted';
            this.load();
          },
          error: err => (this.error = this.ansible.getApiErrorMessage(err))
        });
      }
    }
  }
}
