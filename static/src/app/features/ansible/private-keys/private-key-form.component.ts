import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';

import { AnsibleService } from '../../../services/ansible.service';
import { AlertComponent } from '../../../shared/components/alert/alert.component';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-private-key-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, AlertComponent, LoadingSpinnerComponent],
  template: `
    <div class="max-w-2xl mx-auto space-y-6">
      <a routerLink="/ansible/keys" class="text-sm font-medium text-blue-600 hover:text-blue-800 dark:text-blue-400">
        ← Keys
      </a>

      @if (error) {
        <app-alert type="error" title="Error" [message]="error" (dismissed)="error = ''" />
      }

      @if (metaLoading) {
        <app-loading-spinner [showText]="true" loadingText="Loading..." containerClass="py-12" />
      }

      @if (!metaLoading) {
        <div class="bg-white dark:bg-gray-900 shadow-sm rounded-lg border border-gray-200 dark:border-gray-700">
          <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
            <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100">
              {{ isEdit ? 'Edit private key' : 'Add private key' }}
            </h2>
            <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
              Paste PEM or OpenSSH private key text. {{ isEdit ? 'Leave key material empty to keep the existing secret.' : '' }}
            </p>
          </div>

          <form [formGroup]="form" (ngSubmit)="submit()" class="p-6 space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Name *</label>
              <input
                formControlName="name"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Description</label>
              <input
                formControlName="description"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100"
              />
            </div>
            <div class="flex items-center gap-2">
              <input type="checkbox" formControlName="enabled" id="pk-enabled" class="rounded" />
              <label for="pk-enabled" class="text-sm text-gray-700 dark:text-gray-300">Enabled</label>
            </div>

            <div>
              <div class="flex items-center justify-between gap-2 mb-1">
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">Key material</label>
                @if (isEdit) {
                  <button
                    type="button"
                    (click)="loadContent()"
                    [disabled]="contentLoading"
                    class="text-sm text-blue-600 hover:text-blue-800 dark:text-blue-400 disabled:opacity-50"
                  >
                    {{ contentLoading ? 'Loading…' : 'Load current key from server' }}
                  </button>
                }
              </div>
              <textarea
                formControlName="content"
                rows="12"
                class="w-full font-mono text-sm px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100"
                [placeholder]="isEdit ? '(optional — leave empty to keep existing)' : '-----BEGIN ... PRIVATE KEY-----'"
              ></textarea>
            </div>

            <div class="flex gap-3 pt-2">
              <button
                type="submit"
                [disabled]="submitting || form.invalid"
                class="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
              >
                {{ submitting ? 'Saving…' : 'Save' }}
              </button>
              <a routerLink="/ansible/keys" class="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-gray-700 dark:text-gray-300">
                Cancel
              </a>
            </div>
          </form>
        </div>
      }
    </div>
  `
})
export class PrivateKeyFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  form = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(1)]],
    description: [''],
    enabled: [true],
    content: ['']
  });

  id: string | null = null;
  isEdit = false;
  metaLoading = false;
  submitting = false;
  contentLoading = false;
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
      this.ansible.getPrivateKey(this.id).subscribe({
        next: meta => {
          this.form.patchValue({
            name: meta.name,
            description: meta.description ?? '',
            enabled: meta.enabled,
            content: ''
          });
          this.form.get('content')?.clearValidators();
          this.form.get('content')?.updateValueAndValidity();
          this.metaLoading = false;
        },
        error: err => {
          this.error = this.ansible.getApiErrorMessage(err);
          this.metaLoading = false;
        }
      });
    } else {
      this.form.get('content')?.setValidators([Validators.required]);
      this.form.get('content')?.updateValueAndValidity();
    }
  }

  loadContent(): void {
    if (!this.id) return;
    this.contentLoading = true;
    this.ansible.getPrivateKeyContent(this.id).subscribe({
      next: text => {
        this.form.patchValue({ content: text });
        this.contentLoading = false;
      },
      error: err => {
        this.error = this.ansible.getApiErrorMessage(err);
        this.contentLoading = false;
      }
    });
  }

  submit(): void {
    if (this.form.invalid || !this.id && !this.form.value.content?.trim()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting = true;
    this.error = '';

    const v = this.form.getRawValue();
    if (this.isEdit && this.id) {
      const body = {
        name: v.name!.trim(),
        description: v.description?.trim() || null,
        enabled: !!v.enabled,
        content: v.content?.trim() ? v.content.trim() : null
      };
      this.ansible.updatePrivateKey(this.id, body).subscribe({
        next: () => this.router.navigate(['/ansible/keys']),
        error: err => {
          this.error = this.ansible.getApiErrorMessage(err);
          this.submitting = false;
        }
      });
    } else {
      this.ansible
        .createPrivateKey({
          name: v.name!.trim(),
          content: v.content!.trim(),
          description: v.description?.trim() || null,
          enabled: !!v.enabled
        })
        .subscribe({
          next: () => this.router.navigate(['/ansible/keys']),
          error: err => {
            this.error = this.ansible.getApiErrorMessage(err);
            this.submitting = false;
          }
        });
    }
  }
}
