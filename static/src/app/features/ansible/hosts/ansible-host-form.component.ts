import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { AnsibleService } from '../../../services/ansible.service';
import { AlertComponent } from '../../../shared/components/alert/alert.component';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import {
  AnsibleExecutionJobDetail,
  AnsibleHost,
  AnsibleInventoryGroup,
  PrivateKeySummary
} from '../../../models/ansible.interface';

@Component({
  selector: 'app-ansible-host-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, AlertComponent, LoadingSpinnerComponent],
  template: `
    <div class="max-w-2xl mx-auto space-y-6">
      <a routerLink="/ansible/hosts" class="text-sm text-blue-600 dark:text-blue-400">← Hosts</a>

      @if (error) {
        <app-alert type="error" [message]="error" (dismissed)="error = ''" />
      }
      @if (healthCheckMessage) {
        <app-alert
          [type]="healthCheckOk ? 'success' : 'error'"
          [message]="healthCheckMessage"
          (dismissed)="clearHealthCheck()"
        />
      }

      @if (metaLoading) {
        <app-loading-spinner [showText]="true" loadingText="Loading..." containerClass="py-12" />
      }

      @if (!metaLoading) {
        <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 shadow-sm">
          <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
            <div>
              <h2 class="text-xl font-semibold">{{ isEdit ? 'Edit host' : 'Add Ansible host' }}</h2>
              <p class="text-sm text-gray-500 mt-1">
                When sudo is required, you must provide the sudo password on every save (it is not shown back from the server).
              </p>
            </div>
            @if (isEdit && id) {
              <div class="flex items-center gap-3 shrink-0">
                @switch (healthCheckVisual) {
                  @case ('checking') {
                    <svg
                      class="w-6 h-6 shrink-0 animate-spin text-blue-600 dark:text-blue-400"
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
                  }
                  @case ('ok') {
                    <svg
                      class="w-6 h-6 shrink-0 text-green-600 dark:text-green-400"
                      xmlns="http://www.w3.org/2000/svg"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                      aria-hidden="true"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                  }
                  @case ('fail') {
                    <svg
                      class="w-6 h-6 shrink-0 text-red-600 dark:text-red-400"
                      xmlns="http://www.w3.org/2000/svg"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                      aria-hidden="true"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                  }
                }
                <button
                  type="button"
                  (click)="runHealthCheck()"
                  [disabled]="healthCheckRunning"
                  class="px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 disabled:opacity-50"
                >
                  {{ healthCheckRunning ? 'Checking…' : 'Health check (ping)' }}
                </button>
              </div>
            }
          </div>
          <form [formGroup]="form" (ngSubmit)="submit()" class="p-6 space-y-4">
            <div class="grid sm:grid-cols-2 gap-4">
              <div>
                <label class="block text-sm font-medium mb-1">Hostname *</label>
                <input formControlName="hostname" class="w-full px-3 py-2 border rounded-md bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600" />
              </div>
              <div>
                <label class="block text-sm font-medium mb-1">IP address *</label>
                <input formControlName="ipAddress" class="w-full px-3 py-2 border rounded-md bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600" />
              </div>
              <div>
                <label class="block text-sm font-medium mb-1">SSH port</label>
                <input type="number" formControlName="sshPort" class="w-full px-3 py-2 border rounded-md bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600" />
              </div>
              <div>
                <label class="block text-sm font-medium mb-1">SSH user *</label>
                <input formControlName="sshUsername" class="w-full px-3 py-2 border rounded-md bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600" />
              </div>
              <div class="sm:col-span-2">
                <label class="block text-sm font-medium mb-1">SSH private key *</label>
                <select
                  formControlName="sshPrivateKeyId"
                  class="w-full px-3 py-2 border rounded-md bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600"
                >
                  <option value="">Select a key</option>
                  @for (k of keys; track k.id) {
                    <option [value]="k.id">{{ k.name }}{{ k.enabled ? '' : ' (disabled)' }}</option>
                  }
                </select>
              </div>
              <!-- FEATURE: INVENTORY_GROUPS - Hidden for future release -->
              <!-- <div class="sm:col-span-2">
                <label class="block text-sm font-medium mb-1">Inventory group</label>
                <select
                  formControlName="ansibleInventoryGroupId"
                  class="w-full px-3 py-2 border rounded-md bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600"
                >
                  <option value="">(none)</option>
                  @for (g of groups; track g.id) {
                    <option [value]="g.id">{{ g.name }}</option>
                  }
                </select>
              </div> -->
              <div class="flex items-center gap-2 sm:col-span-2">
                <input type="checkbox" formControlName="sudoRequired" id="sudo" />
                <label for="sudo" class="text-sm">Sudo required</label>
              </div>
              <div class="sm:col-span-2">
                <label class="block text-sm font-medium mb-1">Sudo password</label>
                <input
                  type="password"
                  formControlName="sudoPassword"
                  autocomplete="new-password"
                  class="w-full px-3 py-2 border rounded-md bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600"
                />
              </div>
              <div class="sm:col-span-2">
                <label class="block text-sm font-medium mb-1">Python interpreter</label>
                <input formControlName="pythonInterpreter" class="w-full px-3 py-2 border rounded-md bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600" />
              </div>
              <div class="flex items-center gap-2 sm:col-span-2">
                <input type="checkbox" formControlName="enabled" id="en" />
                <label for="en" class="text-sm">Enabled</label>
              </div>
              <div class="sm:col-span-2">
                <label class="block text-sm font-medium mb-1">Tags (comma-separated)</label>
                <input formControlName="tagsText" class="w-full px-3 py-2 border rounded-md bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600" />
              </div>
              <div class="sm:col-span-2">
                <label class="block text-sm font-medium mb-1">Description</label>
                <input formControlName="description" class="w-full px-3 py-2 border rounded-md bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600" />
              </div>
              <div class="sm:col-span-2">
                <label class="block text-sm font-medium mb-1">Custom variables (JSON object; keys become ansible_* host vars)</label>
                <textarea
                  formControlName="customVariablesJson"
                  rows="6"
                  class="w-full font-mono text-sm px-3 py-2 border rounded-md bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600"
                  placeholder='{ "port": "2222" }'
                ></textarea>
              </div>
            </div>
            <div class="flex gap-3 pt-2">
              <button
                type="submit"
                [disabled]="submitting || form.invalid"
                class="px-4 py-2 bg-blue-600 text-white rounded-md disabled:opacity-50"
              >
                {{ submitting ? 'Saving…' : 'Save' }}
              </button>
              <a routerLink="/ansible/hosts" class="px-4 py-2 border rounded-md">Cancel</a>
            </div>
          </form>
        </div>
      }
    </div>
  `
})
export class AnsibleHostFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  form = this.fb.group({
    hostname: ['', Validators.required],
    ipAddress: ['', Validators.required],
    sshPort: [22, [Validators.required, Validators.min(1), Validators.max(65535)]],
    sshUsername: ['', Validators.required],
    sshPrivateKeyId: ['', Validators.required],
    ansibleInventoryGroupId: [''],
    sudoRequired: [true],
    sudoPassword: [''],
    pythonInterpreter: ['/usr/bin/python3'],
    enabled: [true],
    tagsText: [''],
    description: [''],
    customVariablesJson: ['{}']
  });

  keys: PrivateKeySummary[] = [];
  groups: AnsibleInventoryGroup[] = [];

  id: string | null = null;
  isEdit = false;
  metaLoading = false;
  submitting = false;
  error = '';

  healthCheckRunning = false;
  healthCheckMessage = '';
  healthCheckOk = false;
  lastHealthJob: AnsibleExecutionJobDetail | null = null;
  /** Inline icon beside the health button (row-action icons in the list are static). */
  healthCheckVisual: 'none' | 'checking' | 'ok' | 'fail' = 'none';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private ansible: AnsibleService
  ) {}

  clearHealthCheck(): void {
    this.healthCheckMessage = '';
    this.lastHealthJob = null;
    this.healthCheckVisual = 'none';
  }

  runHealthCheck(): void {
    if (!this.id || this.healthCheckRunning) return;
    this.healthCheckRunning = true;
    this.healthCheckMessage = '';
    this.lastHealthJob = null;
    this.healthCheckVisual = 'checking';
    this.ansible.runHostHealthCheck(this.id).subscribe({
      next: job => {
        this.lastHealthJob = job;
        this.healthCheckOk = job.successful && job.exitCode === 0;
        this.healthCheckVisual = this.healthCheckOk ? 'ok' : 'fail';
        const summary = this.healthCheckOk
          ? `Ping succeeded (exit ${job.exitCode ?? '—'}). Job ${job.id}.`
          : `Ping failed (exit ${job.exitCode ?? '—'}). Job ${job.id}.`;
        const tail =
          job.stdout?.trim() || job.stderr?.trim()
            ? `\n\n${(job.stdout || '').trim()}\n${(job.stderr || '').trim()}`.trim()
            : '';
        this.healthCheckMessage = summary + (tail ? `\n\n${tail}` : '');
        this.healthCheckRunning = false;
      },
      error: (err: HttpErrorResponse) => {
        this.error = this.ansible.getApiErrorMessage(err);
        this.healthCheckVisual = 'fail';
        this.healthCheckRunning = false;
      }
    });
  }

  ngOnInit(): void {
    this.id = this.route.snapshot.paramMap.get('id');
    this.isEdit = !!this.id && this.router.url.includes('/edit');

    this.metaLoading = true;
    if (this.isEdit && this.id) {
      forkJoin({
        keys: this.ansible.listPrivateKeys(),
        groups: this.ansible.listGroups(true),
        host: this.ansible.getHost(this.id)
      }).subscribe({
        next: ({ keys, groups, host }) => {
          this.keys = keys;
          this.groups = groups;
          this.patchHost(host);
          this.metaLoading = false;
        },
        error: (err: HttpErrorResponse) => {
          this.error = this.ansible.getApiErrorMessage(err);
          this.metaLoading = false;
        }
      });
    } else {
      forkJoin({
        keys: this.ansible.listPrivateKeys(),
        groups: this.ansible.listGroups(true)
      }).subscribe({
        next: ({ keys, groups }) => {
          this.keys = keys;
          this.groups = groups;
          this.metaLoading = false;
        },
        error: (err: HttpErrorResponse) => {
          this.error = this.ansible.getApiErrorMessage(err);
          this.metaLoading = false;
        }
      });
    }
  }

  private patchHost(h: AnsibleHost): void {
    this.form.patchValue({
      hostname: h.hostname,
      ipAddress: h.ipAddress,
      sshPort: h.sshPort,
      sshUsername: h.sshUsername,
      sshPrivateKeyId: h.sshPrivateKey.id,
      ansibleInventoryGroupId: h.ansibleInventoryGroup?.id ?? '',
      sudoRequired: h.sudoRequired,
      sudoPassword: '',
      pythonInterpreter: h.pythonInterpreter ?? '/usr/bin/python3',
      enabled: h.enabled,
      tagsText: this.tagsToText(h.tags),
      description: h.description ?? '',
      customVariablesJson: this.annotationToJson(h.annotation)
    });
  }

  private tagsToText(tags: string | null): string {
    if (!tags?.trim()) return '';
    try {
      const a = JSON.parse(tags) as unknown;
      return Array.isArray(a) ? (a as string[]).join(', ') : '';
    } catch {
      return '';
    }
  }

  private annotationToJson(ann: Record<string, unknown>): string {
    const o: Record<string, string> = {};
    for (const [k, v] of Object.entries(ann || {})) {
      const key = k.startsWith('ansible_') ? k.slice(8) : k;
      o[key] = String(v);
    }
    return Object.keys(o).length ? JSON.stringify(o, null, 2) : '{}';
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    let customVariables: Record<string, string> | null = null;
    const raw = this.form.value.customVariablesJson?.trim();
    if (raw && raw !== '{}') {
      try {
        const parsed = JSON.parse(raw) as unknown;
        if (parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)) {
          customVariables = {};
          for (const [k, v] of Object.entries(parsed as Record<string, unknown>)) {
            customVariables[k] = String(v);
          }
        } else {
          this.error = 'Custom variables must be a JSON object';
          return;
        }
      } catch {
        this.error = 'Invalid JSON in custom variables';
        return;
      }
    }

    const tags = this.form.value.tagsText
      ? this.form.value.tagsText
          .split(',')
          .map(t => t.trim())
          .filter(Boolean)
      : [];

    const groupId = this.form.value.ansibleInventoryGroupId?.trim();
    const body = {
      hostname: this.form.value.hostname!.trim(),
      ipAddress: this.form.value.ipAddress!.trim(),
      sshPort: Number(this.form.value.sshPort),
      sshUsername: this.form.value.sshUsername!.trim(),
      sshPrivateKeyId: this.form.value.sshPrivateKeyId!,
      ansibleInventoryGroupId: groupId ? groupId : null,
      sudoRequired: !!this.form.value.sudoRequired,
      sudoPassword: this.form.value.sudoPassword?.trim() || null,
      pythonInterpreter: this.form.value.pythonInterpreter?.trim() || null,
      enabled: !!this.form.value.enabled,
      tags: tags.length ? tags : null,
      description: this.form.value.description?.trim() || null,
      customVariables
    };

    this.submitting = true;
    this.error = '';

    if (this.isEdit && this.id) {
      this.ansible.updateHost(this.id, body).subscribe({
        next: () => this.router.navigate(['/ansible/hosts']),
        error: err => {
          this.error = this.ansible.getApiErrorMessage(err);
          this.submitting = false;
        }
      });
    } else {
      this.ansible.createHost(body).subscribe({
        next: () => this.router.navigate(['/ansible/hosts']),
        error: err => {
          this.error = this.ansible.getApiErrorMessage(err);
          this.submitting = false;
        }
      });
    }
  }
}
