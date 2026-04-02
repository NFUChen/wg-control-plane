import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';

import { AnsibleService } from '../../../services/ansible.service';
import { AlertComponent } from '../../../shared/components/alert/alert.component';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import {
  AnsibleStatisticsResponse,
  InventoryFileInfo,
  InventoryValidationResponse
} from '../../../models/ansible.interface';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-ansible-inventory',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, AlertComponent, LoadingSpinnerComponent],
  template: `
    <div class="space-y-6">
      <a routerLink="/ansible" class="text-sm text-blue-600 dark:text-blue-400">← Ansible</a>

      @if (error) {
        <app-alert type="error" [message]="error" (dismissed)="error = ''" />
      }
      @if (successMessage) {
        <app-alert type="success" [message]="successMessage" (dismissed)="successMessage = ''" />
      }

      <div>
        <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100">Inventory validation</h2>
        <p class="mt-2 max-w-3xl text-sm leading-relaxed text-gray-600 dark:text-gray-400">
          Confirm that hosts and groups in the control plane form a coherent Ansible inventory. Use
          <strong class="font-medium text-gray-800 dark:text-gray-200">Validate</strong> for checks,
          <strong class="font-medium text-gray-800 dark:text-gray-200">Preview</strong> to see the effective INI text Ansible would use
          (lines such as <code class="rounded bg-gray-100 px-1 text-xs dark:bg-gray-800">ansible_ssh_private_key_file=…</code> refer to
          <span class="whitespace-nowrap">runtime paths on the app server</span> — not PEM contents).
        </p>
      </div>

      <div class="grid gap-6 lg:grid-cols-3 lg:items-start">
        <!-- Main: validation + preview -->
        <div class="space-y-4 lg:col-span-2">
          <div class="rounded-lg border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-900">
            <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">Checks &amp; preview</h3>
            <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
              These run against the current database state — no dependency on files written to disk.
            </p>
            <div class="mt-5 flex flex-wrap gap-2">
              <button
                type="button"
                (click)="runValidate()"
                [disabled]="busy"
                class="inline-flex items-center rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700 disabled:opacity-50"
              >
                Run validation
              </button>
              <button
                type="button"
                (click)="runPreview()"
                [disabled]="busy"
                class="inline-flex items-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700"
              >
                Preview effective inventory
              </button>
            </div>
          </div>

          @if (validation) {
            <div
              class="rounded-lg border p-4"
              [ngClass]="
                validation.valid
                  ? 'border-green-300 bg-green-50 dark:border-green-800 dark:bg-green-950/30'
                  : 'border-red-300 bg-red-50 dark:border-red-800 dark:bg-red-950/30'
              "
            >
              <p class="font-medium text-gray-900 dark:text-gray-100">
                {{ validation.valid ? 'Validation passed' : 'Validation failed' }} — hosts
                {{ validation.hostCount }}, groups {{ validation.groupCount }}
              </p>
              @if (validation.errors.length > 0) {
                <ul class="mt-2 list-inside list-disc text-sm text-red-800 dark:text-red-200">
                  @for (e of validation.errors; track e) {
                    <li>{{ e }}</li>
                  }
                </ul>
              }
              @if (validation.warnings.length > 0) {
                <ul class="mt-2 list-inside list-disc text-sm text-amber-800 dark:text-amber-200">
                  @for (w of validation.warnings; track w) {
                    <li>{{ w }}</li>
                  }
                </ul>
              }
            </div>
          }

          @if (previewText !== null) {
            <div class="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900">
              <div class="flex items-center justify-between border-b border-gray-200 px-4 py-3 dark:border-gray-700">
                <div>
                  <span class="text-sm font-medium text-gray-900 dark:text-gray-100">Effective inventory (preview)</span>
                  <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">Read-only; same shape Ansible would consume at runtime.</p>
                </div>
                <button type="button" (click)="previewText = null" class="text-xs text-gray-500 hover:text-gray-800 dark:hover:text-gray-300">
                  Close
                </button>
              </div>
              <pre
                class="max-h-[min(28rem,60vh)] overflow-auto whitespace-pre-wrap p-4 font-mono text-xs text-gray-800 dark:text-gray-200"
                >{{ previewText }}</pre
              >
            </div>
          }
        </div>

        <!-- Summary: stats -->
        <aside class="lg:col-span-1">
          <div
            class="rounded-lg border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900 lg:sticky lg:top-20"
          >
            <div class="flex items-start justify-between gap-2">
              <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">Summary</h3>
              <button
                type="button"
                (click)="refreshSummary()"
                [disabled]="statsLoading"
                class="shrink-0 text-xs font-medium text-blue-600 hover:text-blue-800 disabled:opacity-50 dark:text-blue-400"
              >
                Refresh
              </button>
            </div>
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">Counts from the control plane (not from on-disk files).</p>

            @if (statsLoading) {
              <app-loading-spinner containerClass="py-8" />
            } @else if (stats) {
              <dl class="mt-4 space-y-4 text-sm">
                <div>
                  <dt class="text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">Hosts</dt>
                  <dd class="mt-1 text-gray-900 dark:text-gray-100">
                    <span class="text-lg font-semibold">{{ stats.hostStatistics.totalHosts }}</span>
                    <span class="text-gray-500"> total</span>
                    <span class="ml-2 text-gray-600 dark:text-gray-300">· {{ stats.hostStatistics.enabledHosts }} enabled</span>
                  </dd>
                  <dd class="mt-1 text-xs text-gray-500">Ungrouped: {{ stats.hostStatistics.ungroupedHosts }}</dd>
                </div>
                <div>
                  <dt class="text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">Groups</dt>
                  <dd class="mt-1 text-gray-900 dark:text-gray-100">
                    <span class="text-lg font-semibold">{{ stats.hostStatistics.totalGroups }}</span>
                    <span class="text-gray-500"> total</span>
                    <span class="ml-2 text-gray-600 dark:text-gray-300">· {{ stats.hostStatistics.enabledGroups }} enabled</span>
                  </dd>
                </div>
                <div class="border-t border-gray-100 pt-3 dark:border-gray-800">
                  <dt class="text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">Server file snapshots</dt>
                  <dd class="mt-1 text-xs text-gray-600 dark:text-gray-400">
                    {{ stats.inventoryFileStatistics.fileCount }} file(s), {{ stats.inventoryFileStatistics.totalSize | number }} B
                    <span class="block text-[11px] text-gray-400 dark:text-gray-500">Optional persisted copies; may be empty in ephemeral deploys.</span>
                  </dd>
                </div>
              </dl>
            } @else {
              <p class="mt-4 text-sm text-gray-500">Summary unavailable.</p>
            }
          </div>
        </aside>
      </div>
  `
})
export class AnsibleInventoryComponent implements OnInit, OnDestroy {
  files: InventoryFileInfo[] = [];
  filesLoading = false;
  stats: AnsibleStatisticsResponse | null = null;
  statsLoading = false;
  groups: { id: string; name: string }[] = [];
  selectedGroupId = '';

  validation: InventoryValidationResponse | null = null;
  previewText: string | null = null;
  viewContent: string | null = null;
  viewTitle = '';

  busy = false;
  error = '';
  successMessage = '';

  private optionalFilesOpened = false;

  private destroy$ = new Subject<void>();

  constructor(private ansible: AnsibleService) {}

  ngOnInit(): void {
    this.loadStats();
    this.ansible
      .listGroups(true)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: g => (this.groups = g.map(x => ({ id: x.id, name: x.name }))),
        error: () => {}
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onOptionalFilesToggle(ev: Event): void {
    const el = ev.target as HTMLDetailsElement;
    if (el.open && !this.optionalFilesOpened) {
      this.optionalFilesOpened = true;
      this.loadFiles();
    }
  }

  refreshSummary(): void {
    this.loadStats();
  }

  downloadUrl(filename: string): string {
    const enc = encodeURIComponent(filename);
    return `/api/private/ansible/inventory/files/${enc}/download`;
  }

  loadFiles(): void {
    this.filesLoading = true;
    this.ansible.listInventoryFiles().subscribe({
      next: list => {
        this.files = list;
        this.filesLoading = false;
      },
      error: err => {
        this.error = this.ansible.getApiErrorMessage(err);
        this.filesLoading = false;
      }
    });
  }

  loadStats(): void {
    this.statsLoading = true;
    this.ansible.getStatistics().subscribe({
      next: s => {
        this.stats = s;
        this.statsLoading = false;
      },
      error: () => {
        this.statsLoading = false;
      }
    });
  }

  runValidate(): void {
    this.busy = true;
    this.validation = null;
    this.ansible.validateInventory().subscribe({
      next: v => {
        this.validation = v;
        this.busy = false;
        this.loadStats();
      },
      error: err => {
        this.error = this.ansible.getApiErrorMessage(err);
        this.busy = false;
      }
    });
  }

  runPreview(): void {
    this.busy = true;
    this.previewText = null;
    this.ansible.previewInventory().subscribe({
      next: text => {
        this.previewText = text;
        this.busy = false;
      },
      error: (err: HttpErrorResponse) => {
        this.previewText = typeof err.error === 'string' ? err.error : this.ansible.getApiErrorMessage(err);
        this.busy = false;
      }
    });
  }

  runGenerate(): void {
    const name = window.prompt('Optional filename (leave empty for auto):', '');
    if (name === null) return;
    this.busy = true;
    this.ansible.generateInventory(name || undefined).subscribe({
      next: () => {
        this.successMessage = 'Inventory written on server (optional snapshot).';
        this.busy = false;
        this.loadFiles();
        this.loadStats();
      },
      error: err => {
        this.error = this.ansible.getApiErrorMessage(err);
        this.busy = false;
      }
    });
  }

  runGenerateGroup(): void {
    if (!this.selectedGroupId) return;
    const name = window.prompt('Optional filename (leave empty for auto):', '');
    if (name === null) return;
    this.busy = true;
    this.ansible.generateGroupInventory(this.selectedGroupId, name || undefined).subscribe({
      next: () => {
        this.successMessage = 'Group inventory written on server (optional snapshot).';
        this.busy = false;
        this.loadFiles();
        this.loadStats();
      },
      error: err => {
        this.error = this.ansible.getApiErrorMessage(err);
        this.busy = false;
      }
    });
  }

  viewFile(f: InventoryFileInfo): void {
    this.viewTitle = f.filename;
    this.viewContent = '';
    this.ansible.getInventoryFileContent(f.filename).subscribe({
      next: text => (this.viewContent = text),
      error: err => {
        this.error = this.ansible.getApiErrorMessage(err);
        this.viewContent = null;
      }
    });
  }

  deleteFile(f: InventoryFileInfo): void {
    if (!confirm(`Delete file "${f.filename}" on the server?`)) return;
    this.ansible.deleteInventoryFile(f.filename).subscribe({
      next: () => {
        this.successMessage = 'Server file removed.';
        this.loadFiles();
        this.loadStats();
      },
      error: err => (this.error = this.ansible.getApiErrorMessage(err))
    });
  }
}
