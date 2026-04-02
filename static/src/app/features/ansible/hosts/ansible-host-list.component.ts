import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';

import { AnsibleService } from '../../../services/ansible.service';
import { DataTableComponent, TableAction } from '../../../shared/components/data-table/data-table.component';
import { AlertComponent } from '../../../shared/components/alert/alert.component';
import { StatusBadgeComponent, BadgeVariant } from '../../../shared/components/status-badge/status-badge.component';
import { AnsibleHost } from '../../../models/ansible.interface';
import { LoadingState, TableColumn } from '../../../models/wireguard.interface';

@Component({
  selector: 'app-ansible-host-list',
  standalone: true,
  imports: [CommonModule, RouterModule, DataTableComponent, AlertComponent, StatusBadgeComponent],
  template: `
    <div class="space-y-6">
      <a routerLink="/ansible" class="text-sm text-blue-600 dark:text-blue-400">← Ansible</a>

      @if (error) {
        <app-alert type="error" [message]="error" (dismissed)="error = ''" />
      }
      @if (successMessage) {
        <app-alert type="success" [message]="successMessage" (dismissed)="successMessage = ''" />
      }

      <app-data-table
        title="Ansible hosts"
        subtitle="SSH targets for inventory generation"
        [columns]="columns"
        [data]="filtered"
        [loading]="loading.isLoading"
        [primaryAction]="primaryAction"
        [rowActions]="rowActions"
        [searchable]="true"
        [searchQuery]="searchQuery"
        (searchChange)="onSearchChange($event)"
        (primaryActionClick)="goNew()"
        (rowActionClick)="onRowAction($event)"
      >
        <ng-template #customTemplate let-item="$implicit" let-column="column">
          <ng-container [ngSwitch]="column.key">
            <span *ngSwitchCase="'enabled'">
              <app-status-badge [variant]="badge(item.enabled)" [label]="item.enabled ? 'Enabled' : 'Disabled'" />
            </span>
            <span *ngSwitchCase="'group'">
              {{ item.ansibleInventoryGroup?.name ?? '—' }}
            </span>
            <span *ngSwitchCase="'reachability'" class="inline-flex items-center justify-center w-10">
              <ng-container [ngSwitch]="pingState(item.id)">
                <svg
                  *ngSwitchCase="'checking'"
                  class="w-5 h-5 animate-spin text-blue-600 dark:text-blue-400"
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                  <path
                    class="opacity-75"
                    fill="currentColor"
                    d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                  />
                </svg>
                <svg
                  *ngSwitchCase="'ok'"
                  class="w-5 h-5 text-green-600 dark:text-green-400"
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                  aria-hidden="true"
                  title="Last ping: OK"
                >
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <svg
                  *ngSwitchCase="'fail'"
                  class="w-5 h-5 text-red-600 dark:text-red-400"
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                  aria-hidden="true"
                  title="Last ping: failed"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
                <span
                  *ngSwitchDefault
                  class="text-gray-400 dark:text-gray-500 text-sm select-none"
                  title="Use Actions → Health check"
                  >—</span>
              </ng-container>
            </span>
            <span *ngSwitchDefault class="text-sm text-gray-900 dark:text-gray-100">{{ item[column.key] }}</span>
          </ng-container>
        </ng-template>
      </app-data-table>
    </div>
  `
})
export class AnsibleHostListComponent implements OnInit, OnDestroy {
  filtered: AnsibleHost[] = [];
  private all: AnsibleHost[] = [];

  loading: LoadingState = { isLoading: false };
  error = '';
  successMessage = '';
  searchQuery = '';

  private pingUi: Record<string, 'idle' | 'checking' | 'ok' | 'fail'> = {};

  columns: TableColumn[] = [
    { key: 'hostname', label: 'Hostname', sortable: true, type: 'text' },
    { key: 'ipAddress', label: 'IP', type: 'text' },
    { key: 'sshUsername', label: 'SSH user', type: 'text' },
    { key: 'group', label: 'Group' },
    { key: 'enabled', label: 'Status' },
    { key: 'reachability', label: 'Last ping' },
    { key: 'actions', label: 'Actions', type: 'action' }
  ];

  primaryAction = { label: 'Add host', icon: 'M12 4v16m8-8H4' };

  rowActions: TableAction[] = [
    {
      label: 'Edit',
      icon: 'M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z',
      action: 'edit',
      variant: 'primary'
    },
    {
      label: 'Health check',
      icon: 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z',
      action: 'health',
      variant: 'secondary'
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
      .listHosts()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: rows => {
          this.all = rows;
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
      this.filtered = [...this.all];
      return;
    }
    this.filtered = this.all.filter(
      h =>
        h.ipAddress.toLowerCase().includes(q) ||
        h.hostname.toLowerCase().includes(q) ||
        h.sshUsername.toLowerCase().includes(q)
    );
  }

  onSearchChange(q: string): void {
    this.searchQuery = q;
    this.applyFilter();
  }

  badge(enabled: boolean): BadgeVariant {
    return enabled ? 'success' : 'gray';
  }

  pingState(hostId: string): 'idle' | 'checking' | 'ok' | 'fail' {
    return this.pingUi[hostId] ?? 'idle';
  }

  goNew(): void {
    this.router.navigate(['/ansible/hosts/new']);
  }

  onRowAction(ev: { action: string; item: AnsibleHost }): void {
    if (ev.action === 'edit') {
      this.router.navigate(['/ansible/hosts', ev.item.id, 'edit']);
    }
    if (ev.action === 'health') {
      this.error = '';
      this.successMessage = '';
      const id = ev.item.id;
      this.pingUi = { ...this.pingUi, [id]: 'checking' };
      this.ansible.runHostHealthCheck(id).subscribe({
        next: job => {
          const ok = job.successful && job.exitCode === 0;
          this.pingUi = { ...this.pingUi, [id]: ok ? 'ok' : 'fail' };
          this.successMessage = ok
            ? `Ping OK — ${ev.item.hostname} (job ${job.id})`
            : `Ping failed — ${ev.item.hostname} (exit ${job.exitCode ?? '—'}, job ${job.id})`;
        },
        error: err => {
          this.pingUi = { ...this.pingUi, [id]: 'fail' };
          this.error = this.ansible.getApiErrorMessage(err);
        }
      });
    }
    if (ev.action === 'delete') {
      if (confirm(`Delete host "${ev.item.hostname}"?`)) {
        this.ansible.deleteHost(ev.item.id).subscribe({
          next: () => {
            this.successMessage = 'Host deleted';
            this.load();
          },
          error: err => (this.error = this.ansible.getApiErrorMessage(err))
        });
      }
    }
  }
}
