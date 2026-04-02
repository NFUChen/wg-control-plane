import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { EMPTY, Subject, switchMap, takeUntil } from 'rxjs';

import { AnsibleExecutionJobDetail, AnsibleExecutionStatus } from '../../../models/ansible.interface';
import { ansiStringToHtml } from '../../../shared/utils/ansi-to-html';
import { AnsibleService } from '../../../services/ansible.service';
import { AlertComponent } from '../../../shared/components/alert/alert.component';
import { StatusBadgeComponent, BadgeVariant } from '../../../shared/components/status-badge/status-badge.component';

function filterNonEmptyExecutionErrors(raw: string[] | null | undefined): string[] {
  return (raw ?? []).map(e => (e ?? '').trim()).filter((e): e is string => e.length > 0);
}

@Component({
  selector: 'app-ansible-execution-job-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, AlertComponent, StatusBadgeComponent],
  template: `
    <div class="space-y-6">
      <a routerLink="/ansible/jobs" class="text-sm text-blue-600 dark:text-blue-400">← Execution jobs</a>

      @if (error) {
        <app-alert type="error" [message]="error" (dismissed)="error = ''" />
      }

      @if (loading) {
        <p class="text-sm text-gray-500 dark:text-gray-400">Loading…</p>
      } @else if (job) {
        <div class="space-y-6">
          <div class="flex flex-wrap items-start justify-between gap-4">
            <div>
              <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100">{{ job.playbook }}</h2>
              <p class="mt-1 font-mono text-xs text-gray-500 dark:text-gray-400">{{ job.id }}</p>
            </div>
            <app-status-badge [variant]="statusVariant(job.status)" [label]="job.status" />
          </div>

          <dl
            class="grid grid-cols-1 gap-4 border border-gray-200 rounded-lg bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-800/50 sm:grid-cols-2 lg:grid-cols-3"
          >
            <div>
              <dt class="text-xs font-medium uppercase text-gray-500 dark:text-gray-400">Created</dt>
              <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100">{{ job.createdAt | date: 'medium' }}</dd>
            </div>
            <div>
              <dt class="text-xs font-medium uppercase text-gray-500 dark:text-gray-400">Updated</dt>
              <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100">{{ job.updatedAt | date: 'medium' }}</dd>
            </div>
            <div>
              <dt class="text-xs font-medium uppercase text-gray-500 dark:text-gray-400">Started / completed</dt>
              <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100">
                {{ job.startedAt ? (job.startedAt | date: 'medium') : '—' }}
                <span class="text-gray-400"> → </span>
                {{ job.completedAt ? (job.completedAt | date: 'medium') : '—' }}
              </dd>
            </div>
            <div>
              <dt class="text-xs font-medium uppercase text-gray-500 dark:text-gray-400">Duration</dt>
              <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100">{{ formatDuration(job.durationSeconds) }}</dd>
            </div>
            <div>
              <dt class="text-xs font-medium uppercase text-gray-500 dark:text-gray-400">Exit code</dt>
              <dd class="mt-1 font-mono text-sm text-gray-900 dark:text-gray-100">{{ job.exitCode ?? '—' }}</dd>
            </div>
            <div>
              <dt class="text-xs font-medium uppercase text-gray-500 dark:text-gray-400">Check mode / verbosity</dt>
              <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100">
                {{ job.checkMode ? 'yes' : 'no' }} / {{ job.verbosity }}
              </dd>
            </div>
            <div class="sm:col-span-2">
              <dt class="text-xs font-medium uppercase text-gray-500 dark:text-gray-400">Triggered by</dt>
              <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100">{{ job.triggeredBy ?? '—' }}</dd>
            </div>
            <div class="sm:col-span-2 lg:col-span-3">
              <dt class="text-xs font-medium uppercase text-gray-500 dark:text-gray-400">Notes</dt>
              <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100">{{ job.notes ?? '—' }}</dd>
            </div>
          </dl>

          <section>
            <h3 class="mb-2 text-sm font-semibold text-gray-900 dark:text-gray-100">Inventory</h3>
            <pre
              class="max-h-64 overflow-auto rounded-lg border border-gray-200 bg-white p-4 font-mono text-xs text-gray-800 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-200"
              >{{ job.inventoryContent }}</pre
            >
          </section>

          <section>
            <h3 class="mb-2 text-sm font-semibold text-gray-900 dark:text-gray-100">Extra variables</h3>
            <pre
              class="max-h-64 overflow-auto rounded-lg border border-gray-200 bg-white p-4 font-mono text-xs text-gray-800 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-200"
              >{{ extraVarsJson }}</pre
            >
          </section>

          @if (executionErrorsFiltered.length > 0) {
            <section>
              <h3 class="mb-2 text-sm font-semibold text-red-700 dark:text-red-400">Execution errors</h3>
              <ul class="list-inside list-disc space-y-1 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-900 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-200">
                @for (e of executionErrorsFiltered; track $index) {
                  <li>{{ e }}</li>
                }
              </ul>
            </section>
          }

          <section>
            <h3 class="mb-2 text-sm font-semibold text-gray-900 dark:text-gray-100">Stdout</h3>
            <div
              class="max-h-96 overflow-auto whitespace-pre-wrap break-words rounded-lg border border-gray-200 bg-white p-4 font-mono text-xs leading-relaxed text-gray-800 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-200"
              [innerHTML]="stdoutHtml"
            ></div>
          </section>

          <section>
            <h3 class="mb-2 text-sm font-semibold text-gray-900 dark:text-gray-100">Stderr</h3>
            <div
              class="max-h-96 overflow-auto whitespace-pre-wrap break-words rounded-lg border border-gray-200 bg-white p-4 font-mono text-xs leading-relaxed text-gray-800 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-200"
              [innerHTML]="stderrHtml"
            ></div>
          </section>
        </div>
      }
    </div>
  `
})
export class AnsibleExecutionJobDetailComponent implements OnInit, OnDestroy {
  job: AnsibleExecutionJobDetail | null = null;
  /** Non-empty strings only — API may send [""] when there is no stderr message. */
  executionErrorsFiltered: string[] = [];
  loading = true;
  error = '';
  extraVarsJson = '';
  stdoutHtml: SafeHtml | null = null;
  stderrHtml: SafeHtml | null = null;

  private destroy$ = new Subject<void>();

  constructor(
    private route: ActivatedRoute,
    private ansible: AnsibleService,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        takeUntil(this.destroy$),
        switchMap(params => {
          const id = params.get('id');
          if (!id) {
            this.error = 'Missing job id';
            this.loading = false;
            return EMPTY;
          }
          this.loading = true;
          this.error = '';
          return this.ansible.getExecutionJob(id);
        })
      )
      .subscribe({
        next: j => {
          this.job = j;
          this.executionErrorsFiltered = filterNonEmptyExecutionErrors(j.executionErrors);
          this.stdoutHtml = this.toAnsiSafeHtml(j.stdout);
          this.stderrHtml = this.toAnsiSafeHtml(j.stderr);
          try {
            this.extraVarsJson = JSON.stringify(j.extraVars ?? {}, null, 2);
          } catch {
            this.extraVarsJson = String(j.extraVars);
          }
          this.loading = false;
        },
        error: err => {
          this.error = this.ansible.getApiErrorMessage(err);
          this.loading = false;
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
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

  private toAnsiSafeHtml(raw: string | null | undefined): SafeHtml {
    const text = raw ?? '';
    if (!text.trim()) {
      return this.sanitizer.bypassSecurityTrustHtml('<span class="text-gray-400 dark:text-gray-500">—</span>');
    }
    return this.sanitizer.bypassSecurityTrustHtml(ansiStringToHtml(text));
  }
}
