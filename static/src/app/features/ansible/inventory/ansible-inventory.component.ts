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
    <div class="space-y-8">
      <a routerLink="/ansible" class="text-sm text-blue-600 dark:text-blue-400">← Ansible</a>

      @if (error) {
        <app-alert type="error" [message]="error" (dismissed)="error = ''" />
      }
      @if (successMessage) {
        <app-alert type="success" [message]="successMessage" (dismissed)="successMessage = ''" />
      }

      <div class="grid gap-6 lg:grid-cols-3">
        <div class="lg:col-span-2 space-y-4">
          <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-6 shadow-sm">
            <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">Actions</h3>
            <div class="mt-4 flex flex-wrap gap-2">
              <button
                type="button"
                (click)="runValidate()"
                [disabled]="busy"
                class="px-3 py-2 bg-blue-600 text-white text-sm rounded-md disabled:opacity-50"
              >
                Validate inventory
              </button>
              <button
                type="button"
                (click)="runPreview()"
                [disabled]="busy"
                class="px-3 py-2 border border-gray-300 dark:border-gray-600 text-sm rounded-md disabled:opacity-50"
              >
                Preview generated INI
              </button>
              <button
                type="button"
                (click)="runGenerate()"
                [disabled]="busy"
                class="px-3 py-2 border border-gray-300 dark:border-gray-600 text-sm rounded-md disabled:opacity-50"
              >
                Generate &amp; save (full)
              </button>
            </div>
            <div class="mt-4 flex flex-wrap items-end gap-3">
              <div>
                <label class="block text-xs text-gray-500 mb-1">Group inventory</label>
                <select
                  [(ngModel)]="selectedGroupId"
                  class="px-3 py-2 border rounded-md bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 text-sm min-w-[200px]"
                >
                  <option value="">Select group…</option>
                  @for (g of groups; track g.id) {
                    <option [value]="g.id">{{ g.name }}</option>
                  }
                </select>
              </div>
              <button
                type="button"
                (click)="runGenerateGroup()"
                [disabled]="busy || !selectedGroupId"
                class="px-3 py-2 border border-gray-300 dark:border-gray-600 text-sm rounded-md disabled:opacity-50"
              >
                Generate for group
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
              <p class="font-medium">{{ validation.valid ? 'Valid' : 'Invalid' }} — hosts: {{ validation.hostCount }}, groups: {{ validation.groupCount }}</p>
              @if (validation.errors.length > 0) {
                <ul class="mt-2 text-sm list-disc list-inside text-red-800 dark:text-red-200">
                  @for (e of validation.errors; track e) {
                    <li>{{ e }}</li>
                  }
                </ul>
              }
              @if (validation.warnings.length > 0) {
                <ul class="mt-2 text-sm list-disc list-inside text-amber-800 dark:text-amber-200">
                  @for (w of validation.warnings; track w) {
                    <li>{{ w }}</li>
                  }
                </ul>
              }
            </div>
          }

          @if (previewText !== null) {
            <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden shadow-sm">
              <div class="px-4 py-2 border-b border-gray-200 dark:border-gray-700 flex justify-between items-center">
                <span class="text-sm font-medium">Preview</span>
                <button type="button" (click)="previewText = null" class="text-xs text-gray-500 hover:text-gray-800">Close</button>
              </div>
              <pre class="p-4 text-xs font-mono whitespace-pre-wrap max-h-[480px] overflow-auto text-gray-800 dark:text-gray-200">{{ previewText }}</pre>
            </div>
          }

          <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 shadow-sm">
            <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex justify-between items-center">
              <h3 class="text-lg font-semibold">Saved inventory files</h3>
              <button type="button" (click)="loadFiles()" [disabled]="busy" class="text-sm text-blue-600 dark:text-blue-400">Refresh</button>
            </div>
            @if (filesLoading) {
              <app-loading-spinner containerClass="py-12" />
            } @else {
              <div class="overflow-x-auto">
                <table class="w-full text-sm">
                  <thead class="bg-gray-50 dark:bg-gray-800/80 text-left text-xs uppercase text-gray-500">
                    <tr>
                      <th class="px-6 py-3">File</th>
                      <th class="px-6 py-3">Size</th>
                      <th class="px-6 py-3">Created</th>
                      <th class="px-6 py-3 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-gray-200 dark:divide-gray-700">
                    @for (f of files; track f.filename) {
                      <tr>
                        <td class="px-6 py-3 font-mono text-xs">{{ f.filename }}</td>
                        <td class="px-6 py-3">{{ f.size | number }} B</td>
                        <td class="px-6 py-3">{{ f.createdAt | date: 'medium' }}</td>
                        <td class="px-6 py-3 text-right space-x-2">
                          <button type="button" class="text-blue-600 text-xs" (click)="viewFile(f)">View</button>
                          <a
                            class="text-blue-600 text-xs"
                            [href]="downloadUrl(f.filename)"
                            target="_blank"
                            rel="noopener noreferrer"
                            >Download</a>
                          <button type="button" class="text-red-600 text-xs" (click)="deleteFile(f)">Delete</button>
                        </td>
                      </tr>
                    }
                    @if (files.length === 0) {
                      <tr>
                        <td colspan="4" class="px-6 py-8 text-center text-gray-500">No files yet. Generate an inventory to create one.</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>

          @if (viewContent !== null) {
            <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 shadow-sm">
              <div class="px-4 py-2 border-b flex justify-between">
                <span class="text-sm font-medium">{{ viewTitle }}</span>
                <button type="button" (click)="viewContent = null" class="text-xs text-gray-500">Close</button>
              </div>
              <pre class="p-4 text-xs font-mono max-h-[400px] overflow-auto whitespace-pre-wrap">{{ viewContent }}</pre>
            </div>
          }
        </div>

        <div class="space-y-4">
          @if (statsLoading) {
            <app-loading-spinner containerClass="py-8" />
          } @else if (stats) {
            <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-6 shadow-sm text-sm space-y-3">
              <h3 class="text-lg font-semibold">Statistics</h3>
              <div>
                <p class="text-gray-500 dark:text-gray-400">Hosts</p>
                <p>Total {{ stats.hostStatistics.totalHosts }}, enabled {{ stats.hostStatistics.enabledHosts }}</p>
                <p>Ungrouped {{ stats.hostStatistics.ungroupedHosts }}</p>
              </div>
              <div>
                <p class="text-gray-500 dark:text-gray-400">Groups</p>
                <p>Total {{ stats.hostStatistics.totalGroups }}, enabled {{ stats.hostStatistics.enabledGroups }}</p>
              </div>
              <div>
                <p class="text-gray-500 dark:text-gray-400">Inventory files</p>
                <p>Count {{ stats.inventoryFileStatistics.fileCount }}, size {{ stats.inventoryFileStatistics.totalSize | number }} B</p>
              </div>
            </div>
          }
        </div>
      </div>
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

  private destroy$ = new Subject<void>();

  constructor(private ansible: AnsibleService) {}

  ngOnInit(): void {
    this.loadFiles();
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

  downloadUrl(filename: string): string {
    const enc = encodeURIComponent(filename);
    return `/api/ansible/inventory/files/${enc}/download`;
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
        this.successMessage = 'Inventory file generated';
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
        this.successMessage = 'Group inventory file generated';
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
    if (!confirm(`Delete file "${f.filename}"?`)) return;
    this.ansible.deleteInventoryFile(f.filename).subscribe({
      next: () => {
        this.successMessage = 'File deleted';
        this.loadFiles();
        this.loadStats();
      },
      error: err => (this.error = this.ansible.getApiErrorMessage(err))
    });
  }
}
