import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';

import {
  AnsibleExecutionJobSummary,
  AnsibleExecutionStatus,
  SpringPage
} from '../../../models/ansible.interface';
import { AnsibleService } from '../../../services/ansible.service';
import { DataTableComponent, TableAction } from '../../../shared/components/data-table/data-table.component';
import { AlertComponent } from '../../../shared/components/alert/alert.component';
import { StatusBadgeComponent, BadgeVariant } from '../../../shared/components/status-badge/status-badge.component';
import { LoadingState, TableColumn } from '../../../models/wireguard.interface';

@Component({
  selector: 'app-ansible-execution-job-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, DataTableComponent, AlertComponent, StatusBadgeComponent],
  template: `
    <div class="space-y-6">
      <a routerLink="/ansible" class="text-sm text-blue-600 dark:text-blue-400">← Ansible</a>

      @if (error) {
        <app-alert type="error" [message]="error" (dismissed)="error = ''" />
      }

      <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-end">
        <div class="flex flex-wrap items-center gap-2">
          <label class="text-sm text-gray-600 dark:text-gray-400" for="job-status">Status</label>
          <select
            id="job-status"
            [(ngModel)]="statusFilter"
            (ngModelChange)="onStatusChange()"
            class="rounded-md border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
          >
            <option value="">All</option>
            <option value="PENDING">PENDING</option>
            <option value="RUNNING">RUNNING</option>
            <option value="SUCCESS">SUCCESS</option>
            <option value="FAILED">FAILED</option>
            <option value="CANCELLED">CANCELLED</option>
          </select>
        </div>
      </div>

      <app-data-table
        title="Execution jobs"
        subtitle="Ansible playbook runs triggered from the control plane (newest first)."
        [columns]="columns"
        [data]="filtered"
        [loading]="loading.isLoading"
        [rowActions]="rowActions"
        [searchable]="true"
        [searchQuery]="searchQuery"
        emptyMessage="No execution jobs"
        emptySubMessage="Run a WireGuard or Ansible action to create a job."
        (searchChange)="onSearchChange($event)"
        (rowActionClick)="onRowAction($event)"
      >
        <ng-template #customTemplate let-item="$implicit" let-column="column">
          <ng-container [ngSwitch]="column.key">
            <span *ngSwitchCase="'status'">
              <app-status-badge [variant]="statusVariant(item.status)" [label]="item.status" />
            </span>
            <span *ngSwitchCase="'createdAt'" class="text-sm text-gray-900 dark:text-gray-100 whitespace-nowrap">{{
              item.createdAt | date: 'medium'
            }}</span>
            <span *ngSwitchCase="'durationSeconds'" class="text-sm text-gray-700 dark:text-gray-300">{{
              formatDuration(item.durationSeconds)
            }}</span>
            <span *ngSwitchCase="'exitCode'" class="text-sm font-mono text-gray-900 dark:text-gray-100">{{
              item.exitCode ?? '—'
            }}</span>
            <span *ngSwitchCase="'notes'" class="text-sm text-gray-600 dark:text-gray-400 max-w-xs truncate" [title]="item.notes ?? ''">{{
              item.notes || '—'
            }}</span>
            <span *ngSwitchDefault class="text-sm text-gray-900 dark:text-gray-100">{{ item[column.key] }}</span>
          </ng-container>
        </ng-template>
      </app-data-table>

      @if (page && page.totalPages > 1) {
        <div class="flex flex-wrap items-center justify-between gap-3 border-t border-gray-200 pt-4 dark:border-gray-700">
          <p class="text-sm text-gray-500 dark:text-gray-400">
            Page {{ page.number + 1 }} of {{ page.totalPages }} · {{ page.totalElements }} jobs
          </p>
          <div class="flex gap-2">
            <button
              type="button"
              [disabled]="page.first"
              (click)="goPage(page.number - 1)"
              class="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700"
            >
              Previous
            </button>
            <button
              type="button"
              [disabled]="page.last"
              (click)="goPage(page.number + 1)"
              class="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700"
            >
              Next
            </button>
          </div>
        </div>
      }
    </div>
  `
})
export class AnsibleExecutionJobListComponent implements OnInit, OnDestroy {
  filtered: AnsibleExecutionJobSummary[] = [];
  private rows: AnsibleExecutionJobSummary[] = [];
  page: SpringPage<AnsibleExecutionJobSummary> | null = null;

  loading: LoadingState = { isLoading: false };
  error = '';
  searchQuery = '';
  statusFilter = '';

  columns: TableColumn[] = [
    { key: 'createdAt', label: 'Created', sortable: true },
    { key: 'playbook', label: 'Playbook', sortable: true, type: 'text' },
    { key: 'status', label: 'Status' },
    { key: 'exitCode', label: 'Exit code' },
    { key: 'durationSeconds', label: 'Duration' },
    { key: 'triggeredBy', label: 'Triggered by', type: 'text' },
    { key: 'notes', label: 'Notes' },
    { key: 'actions', label: 'Actions', type: 'action' }
  ];

  rowActions: TableAction[] = [
    {
      label: 'View',
      icon: 'M15 12a3 3 0 11-6 0 3 3 0 016 0z M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z',
      action: 'view',
      variant: 'primary'
    }
  ];

  private destroy$ = new Subject<void>();
  private pageIndex = 0;

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
    const st = this.statusFilter.trim();
    this.ansible
      .listExecutionJobs(this.pageIndex, 50, st || undefined)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: p => {
          this.page = p;
          this.rows = p.content ?? [];
          this.applyFilter();
          this.loading = { isLoading: false };
        },
        error: err => {
          this.error = this.ansible.getApiErrorMessage(err);
          this.loading = { isLoading: false };
        }
      });
  }

  onStatusChange(): void {
    this.pageIndex = 0;
    this.load();
  }

  goPage(n: number): void {
    this.pageIndex = n;
    this.load();
  }

  applyFilter(): void {
    const q = this.searchQuery.trim().toLowerCase();
    if (!q) {
      this.filtered = [...this.rows];
      return;
    }
    this.filtered = this.rows.filter(
      j =>
        j.playbook.toLowerCase().includes(q) ||
        j.status.toLowerCase().includes(q) ||
        (j.triggeredBy?.toLowerCase().includes(q) ?? false) ||
        (j.notes?.toLowerCase().includes(q) ?? false)
    );
  }

  onSearchChange(q: string): void {
    this.searchQuery = q;
    this.applyFilter();
  }

  formatDuration(s: number | null): string {
    if (s == null) return '—';
    if (s < 60) return `${s}s`;
    const m = Math.floor(s / 60);
    const sec = s % 60;
    return `${m}m ${sec}s`;
  }

  statusVariant(s: AnsibleExecutionStatus): BadgeVariant {
    switch (s) {
      case 'SUCCESS':
        return 'success';
      case 'FAILED':
        return 'danger';
      case 'RUNNING':
        return 'warning';
      case 'PENDING':
        return 'info';
      case 'CANCELLED':
        return 'gray';
      default:
        return 'gray';
    }
  }

  onRowAction(ev: { action: string; item: AnsibleExecutionJobSummary }): void {
    if (ev.action === 'view') {
      this.router.navigate(['/ansible/jobs', ev.item.id]);
    }
  }
}
