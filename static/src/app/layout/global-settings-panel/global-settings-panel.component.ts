import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  inject
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormArray,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';

import { HttpErrorResponse } from '@angular/common/http';

import { GlobalConfigurationService } from '../../services/global-configuration.service';
import { GlobalConfig } from '../../models/global-config.interface';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';
import { AlertComponent } from '../../shared/components/alert/alert.component';

@Component({
  selector: 'app-global-settings-panel',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    LoadingSpinnerComponent,
    AlertComponent
  ],
  template: `
    <!-- Backdrop -->
    @if (open) {
      <div
        class="fixed inset-0 z-40 bg-black/40 backdrop-blur-[1px] transition-opacity"
        aria-hidden="true"
        (click)="close()"
      ></div>
    }

    <!-- Slide-over (right), WGDashboard-style grouped settings -->
    <aside
      class="fixed top-0 right-0 z-50 flex h-full w-full min-w-0 max-w-md flex-col border-l border-gray-200 bg-white shadow-2xl transition-transform duration-300 ease-out dark:border-gray-800 dark:bg-gray-950"
      [class.pointer-events-none]="!open"
      [class.translate-x-0]="open"
      [class.translate-x-full]="!open"
      [attr.aria-hidden]="!open"
      role="dialog"
      aria-labelledby="global-settings-title"
    >
      <header
        class="flex min-w-0 shrink-0 items-start justify-between gap-3 border-b border-gray-200 px-4 py-4 dark:border-gray-800"
      >
        <div class="min-w-0 flex-1 pr-1">
          <h2 id="global-settings-title" class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            Global settings
          </h2>
          <p class="mt-0.5 text-xs leading-snug text-gray-500 break-words [overflow-wrap:anywhere] dark:text-gray-400">
            Endpoint and defaults for new clients.
          </p>
        </div>
        <button
          type="button"
          class="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-gray-800 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100"
          (click)="close()"
          aria-label="Close global settings"
        >
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </header>

      <div class="min-h-0 min-w-0 flex-1 overflow-y-auto overflow-x-hidden px-4 py-4">
        @if (loadError) {
          <app-alert type="error" title="Could not load settings" [message]="loadError" />
        }

        @if (loading && !form) {
          <app-loading-spinner [showText]="true" loadingText="Loading global configuration..." containerClass="py-12" />
        }

        @if (form) {
          <form [formGroup]="form" (ngSubmit)="save()" class="space-y-6">
            @if (saveError) {
              <app-alert type="error" title="Save failed" [message]="saveError" (dismissed)="saveError = null" />
            }

            @if (versionLabel) {
              <p class="text-xs text-gray-500 dark:text-gray-400">Current version: {{ versionLabel }}</p>
            }

            <section class="rounded-lg border border-gray-200 bg-gray-50/80 p-4 dark:border-gray-700 dark:bg-gray-900/50">
              <h3 class="text-sm font-medium text-gray-900 dark:text-gray-100">Endpoint</h3>
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                Public host:port clients use to reach WireGuard (replaces per-server endpoint).
              </p>
              <label for="serverEndpoint" class="mt-3 block text-xs font-medium text-gray-700 dark:text-gray-300">
                Server endpoint *
              </label>
              <input
                id="serverEndpoint"
                type="text"
                formControlName="serverEndpoint"
                class="mt-1 w-full rounded-md border border-gray-300 bg-white px-3 py-2 font-mono text-sm text-gray-900 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
                placeholder="vpn.example.com:51820"
                autocomplete="off"
              />
            </section>

            <section class="rounded-lg border border-gray-200 bg-gray-50/80 p-4 dark:border-gray-700 dark:bg-gray-900/50">
              <h3 class="text-sm font-medium text-gray-900 dark:text-gray-100">Defaults (new clients)</h3>
              <div class="mt-3 grid grid-cols-2 gap-3">
                <div>
                  <label class="block text-xs font-medium text-gray-700 dark:text-gray-300" for="defaultMtu">MTU</label>
                  <input
                    id="defaultMtu"
                    type="number"
                    formControlName="defaultMtu"
                    class="mt-1 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
                    min="1280"
                    max="65536"
                  />
                </div>
                <div>
                  <label class="block text-xs font-medium text-gray-700 dark:text-gray-300" for="defaultPersistentKeepalive">
                    Persistent keepalive (s)
                  </label>
                  <input
                    id="defaultPersistentKeepalive"
                    type="number"
                    formControlName="defaultPersistentKeepalive"
                    class="mt-1 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
                    min="0"
                    max="3600"
                  />
                </div>
              </div>

              <div class="mt-4">
                <div class="flex items-center justify-between">
                  <span class="text-xs font-medium text-gray-700 dark:text-gray-300">Default DNS servers</span>
                  <button
                    type="button"
                    class="text-xs font-medium text-blue-600 hover:text-blue-800 dark:text-blue-400"
                    (click)="addDns()"
                  >
                    + Add
                  </button>
                </div>
                <div formArrayName="defaultDnsServers" class="mt-2 space-y-2">
                  @for (ctrl of dnsArray.controls; track $index; let i = $index) {
                    <div class="flex gap-2">
                      <input
                        type="text"
                        [formControlName]="i"
                        class="min-w-0 flex-1 rounded-md border border-gray-300 bg-white px-3 py-2 font-mono text-sm dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
                        placeholder="8.8.8.8"
                      />
                      <button
                        type="button"
                        class="shrink-0 rounded-md px-2 text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40"
                        (click)="removeDns(i)"
                        aria-label="Remove DNS server"
                      >
                        <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                        </svg>
                      </button>
                    </div>
                  }
                </div>
              </div>
            </section>

            <section class="rounded-lg border border-gray-200 bg-gray-50/80 p-4 dark:border-gray-700 dark:bg-gray-900/50">
              <h3 class="text-sm font-medium text-gray-900 dark:text-gray-100">Security</h3>
              <div class="mt-3 space-y-3">
                <label class="flex cursor-pointer items-center gap-3">
                  <input type="checkbox" formControlName="enablePresharedKeys" class="h-4 w-4 rounded border-gray-300" />
                  <span class="text-sm text-gray-800 dark:text-gray-200">Enable preshared keys</span>
                </label>
                <label class="flex cursor-pointer items-center gap-3">
                  <input type="checkbox" formControlName="autoGenerateKeys" class="h-4 w-4 rounded border-gray-300" />
                  <span class="text-sm text-gray-800 dark:text-gray-200">Auto-generate keys</span>
                </label>
              </div>
            </section>

            <div>
              <label for="changeDescription" class="block text-xs font-medium text-gray-700 dark:text-gray-300">
                Change note (optional)
              </label>
              <input
                id="changeDescription"
                type="text"
                formControlName="changeDescription"
                class="mt-1 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
                placeholder="e.g. Updated public IP"
              />
            </div>

            <div class="flex justify-end gap-2 border-t border-gray-200 pt-4 dark:border-gray-800">
              <button
                type="button"
                class="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700"
                (click)="close()"
              >
                Cancel
              </button>
              <button
                type="submit"
                [disabled]="form.invalid || saving"
                class="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
              >
                @if (saving) {
                  <span>Saving…</span>
                } @else {
                  <span>Save</span>
                }
              </button>
            </div>
          </form>
        }
      </div>
    </aside>
  `
})
export class GlobalSettingsPanelComponent implements OnChanges {
  @Input() open = false;
  @Output() openChange = new EventEmitter<boolean>();
  @Output() saved = new EventEmitter<void>();

  private readonly globalConfig = inject(GlobalConfigurationService);
  private readonly fb = inject(FormBuilder);

  form: FormGroup | null = null;
  loading = false;
  saving = false;
  loadError: string | null = null;
  saveError: string | null = null;
  versionLabel: string | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open) {
      this.loadError = null;
      this.saveError = null;
      this.load();
    }
  }

  get dnsArray(): FormArray {
    return this.form!.get('defaultDnsServers') as FormArray;
  }

  close(): void {
    this.openChange.emit(false);
  }

  addDns(value = ''): void {
    if (!this.form) return;
    this.dnsArray.push(this.fb.control(value, [Validators.pattern(/^(\d{1,3}\.){3}\d{1,3}$/)]));
  }

  removeDns(index: number): void {
    if (!this.form || this.dnsArray.length <= 1) return;
    this.dnsArray.removeAt(index);
  }

  private load(): void {
    this.loading = true;
    this.form = null;
    this.versionLabel = null;

    this.globalConfig.getCurrent().subscribe({
      next: row => {
        this.versionLabel = String(row.version);
        this.patchFromConfig(row.config);
        this.loading = false;
      },
      error: err => {
        this.loadError = this.messageFromHttp(err);
        this.loading = false;
      }
    });
  }

  private patchFromConfig(cfg: GlobalConfig): void {
    this.form = this.fb.group({
      serverEndpoint: [cfg.serverEndpoint ?? '', [Validators.required]],
      defaultMtu: [cfg.defaultMtu ?? 1420, [Validators.required, Validators.min(1280), Validators.max(65536)]],
      defaultPersistentKeepalive: [
        cfg.defaultPersistentKeepalive ?? 25,
        [Validators.required, Validators.min(0), Validators.max(3600)]
      ],
      enablePresharedKeys: [cfg.enablePresharedKeys ?? true],
      autoGenerateKeys: [cfg.autoGenerateKeys ?? true],
      changeDescription: ['']
    });
    const dns = (cfg.defaultDnsServers?.length ? cfg.defaultDnsServers : ['8.8.8.8', '1.1.1.1']).slice();
    const arr = this.fb.array(
      dns.map(ip =>
        this.fb.control(ip, [Validators.required, Validators.pattern(/^(\d{1,3}\.){3}\d{1,3}$/)])
      )
    );
    this.form.setControl('defaultDnsServers', arr);
  }

  save(): void {
    if (!this.form || this.form.invalid) {
      this.form?.markAllAsTouched();
      return;
    }
    this.saving = true;
    this.saveError = null;

    const v = this.form.getRawValue();
    const dnsList: string[] = v.defaultDnsServers.map((s: string) => s.trim()).filter(Boolean);
    const payload: GlobalConfig = {
      serverEndpoint: v.serverEndpoint.trim(),
      defaultDnsServers: dnsList.length ? dnsList : ['8.8.8.8'],
      defaultMtu: Number(v.defaultMtu),
      defaultPersistentKeepalive: Number(v.defaultPersistentKeepalive),
      enablePresharedKeys: !!v.enablePresharedKeys,
      autoGenerateKeys: !!v.autoGenerateKeys
    };
    const note = (v.changeDescription ?? '').trim();

    this.globalConfig
      .update(payload, { changeDescription: note || undefined })
      .subscribe({
        next: row => {
          this.versionLabel = String(row.version);
          this.saving = false;
          this.saved.emit();
          this.close();
        },
        error: err => {
          this.saveError = this.messageFromHttp(err);
          this.saving = false;
        }
      });
  }

  private messageFromHttp(err: unknown): string {
    const e = err as HttpErrorResponse;
    const body = e.error;
    if (typeof body === 'string' && body.trim()) return body;
    if (body && typeof body === 'object' && 'message' in body && (body as { message: unknown }).message != null) {
      return String((body as { message: unknown }).message);
    }
    if (e.message) return e.message;
    return 'Request failed';
  }
}
