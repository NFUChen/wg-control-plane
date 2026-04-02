import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';

import { AnsibleService } from '../../../services/ansible.service';
import { AlertComponent } from '../../../shared/components/alert/alert.component';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-ansible-group-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, AlertComponent, LoadingSpinnerComponent],
  template: `
    <div class="max-w-2xl mx-auto space-y-6">
      <a routerLink="/ansible/groups" class="text-sm text-blue-600 dark:text-blue-400">← Groups</a>

      @if (error) {
        <app-alert type="error" [message]="error" (dismissed)="error = ''" />
      }

      @if (metaLoading) {
        <app-loading-spinner [showText]="true" loadingText="Loading..." containerClass="py-12" />
      }

      @if (!metaLoading) {
        <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 shadow-sm">
          <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
            <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100">
              {{ isEdit ? 'Edit group' : 'New inventory group' }}
            </h2>
          </div>
          <form [formGroup]="form" (ngSubmit)="submit()" class="p-6 space-y-4">
            <div>
              <label class="block text-sm font-medium mb-1">Name *</label>
              <input
                formControlName="name"
                class="w-full px-3 py-2 border rounded-md bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600"
              />
              <p class="text-xs text-gray-500 mt-1">Letters, numbers, underscore and hyphen; must not start with a digit.</p>
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">Description</label>
              <input formControlName="description" class="w-full px-3 py-2 border rounded-md bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600" />
            </div>
            <div class="flex items-center gap-2">
              <input type="checkbox" formControlName="enabled" id="g-en" />
              <label for="g-en" class="text-sm">Enabled</label>
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">Group variables (JSON object)</label>
              <textarea
                formControlName="variablesJson"
                rows="8"
                class="w-full font-mono text-sm px-3 py-2 border rounded-md bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600"
                placeholder='{ "ansible_user": "ubuntu" }'
              ></textarea>
            </div>
            <div class="flex gap-3">
              <button
                type="submit"
                [disabled]="submitting || form.invalid"
                class="px-4 py-2 bg-blue-600 text-white rounded-md disabled:opacity-50"
              >
                {{ submitting ? 'Saving…' : 'Save' }}
              </button>
              <a routerLink="/ansible/groups" class="px-4 py-2 border rounded-md">Cancel</a>
            </div>
          </form>
        </div>
      }
    </div>
  `
})
export class AnsibleGroupFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  form = this.fb.group({
    name: ['', Validators.required],
    description: [''],
    enabled: [true],
    variablesJson: ['{}']
  });

  id: string | null = null;
  isEdit = false;
  metaLoading = false;
  submitting = false;
  error = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private ansible: AnsibleService
  ) {}

  ngOnInit(): void {
    this.id = this.route.snapshot.paramMap.get('id');
    this.isEdit = !!this.id && this.router.url.includes('/edit');

    if (this.isEdit && this.id) {
      this.metaLoading = true;
      this.ansible.getGroup(this.id).subscribe({
        next: g => {
          let vars = '{}';
          if (g.variables?.trim()) {
            try {
              const parsed = JSON.parse(g.variables);
              vars = JSON.stringify(parsed, null, 2);
            } catch {
              vars = g.variables;
            }
          }
          this.form.patchValue({
            name: g.name,
            description: g.description ?? '',
            enabled: g.enabled,
            variablesJson: vars
          });
          this.metaLoading = false;
        },
        error: err => {
          this.error = this.ansible.getApiErrorMessage(err);
          this.metaLoading = false;
        }
      });
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    let variables: Record<string, unknown> | null = null;
    const raw = this.form.value.variablesJson?.trim();
    if (raw) {
      try {
        const parsed = JSON.parse(raw) as unknown;
        if (parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)) {
          variables = parsed as Record<string, unknown>;
        } else {
          this.error = 'Variables must be a JSON object';
          return;
        }
      } catch {
        this.error = 'Invalid JSON in group variables';
        return;
      }
    }

    this.submitting = true;
    this.error = '';
    const body = {
      name: this.form.value.name!.trim(),
      description: this.form.value.description?.trim() || null,
      enabled: !!this.form.value.enabled,
      variables
    };

    if (this.isEdit && this.id) {
      this.ansible.updateGroup(this.id, body).subscribe({
        next: () => this.router.navigate(['/ansible/groups']),
        error: err => {
          this.error = this.ansible.getApiErrorMessage(err);
          this.submitting = false;
        }
      });
    } else {
      this.ansible.createGroup(body).subscribe({
        next: () => this.router.navigate(['/ansible/groups']),
        error: err => {
          this.error = this.ansible.getApiErrorMessage(err);
          this.submitting = false;
        }
      });
    }
  }
}
