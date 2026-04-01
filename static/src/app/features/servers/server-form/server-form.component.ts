import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, FormArray } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';

import { WireguardService } from '../../../services/wireguard.service';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { AlertComponent } from '../../../shared/components/alert/alert.component';
import {
  CreateServerRequest,
  UpdateServerRequest,
  ServerDetailResponse,
  LoadingState
} from '../../../models/wireguard.interface';

@Component({
  selector: 'app-server-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    LoadingSpinnerComponent,
    AlertComponent
  ],
  template: `
    <div class="max-w-2xl mx-auto space-y-6">
      <!-- Loading Spinner -->
      @if (loadingState.isLoading && !serverForm) {
        <app-loading-spinner
          [showText]="true"
          loadingText="Loading server details..."
          containerClass="py-8"
        />
      }

      <!-- Error Alert -->
      @if (loadingState.error) {
        <app-alert
          type="error"
          title="Error"
          [message]="loadingState.error"
          (dismissed)="clearError()"
        />
      }

      <!-- Form -->
      @if (serverForm) {
      <div class="bg-white dark:bg-gray-900 shadow-sm rounded-lg border border-gray-200 dark:border-gray-700">
        <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100">
            {{ isEditMode ? 'Edit Server' : 'Create New Server' }}
          </h2>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
            {{ isEditMode ? 'Update your WireGuard server configuration' : 'Configure a new WireGuard server' }}
          </p>
        </div>

        <form [formGroup]="serverForm" (ngSubmit)="onSubmit()" class="p-6 space-y-6">
          <!-- Basic Information -->
          <div class="space-y-4">
            <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">Basic Information</h3>

            <div>
              <label for="name" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Server Name *
              </label>
              <input
                type="text"
                id="name"
                formControlName="name"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="e.g., Main VPN Server"
              />
              @if (serverForm.get('name')?.invalid && serverForm.get('name')?.touched) {
                <div class="mt-1 text-sm text-red-600">
                  @if (serverForm.get('name')?.errors?.['required']) {
                    <div>Server name is required</div>
                  }
                  @if (serverForm.get('name')?.errors?.['minlength']) {
                    <div>Server name must be at least 3 characters</div>
                  }
                </div>
              }
            </div>

            <div>
              <label for="interfaceName" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Interface Name *
              </label>
              <input
                type="text"
                id="interfaceName"
                formControlName="interfaceName"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="e.g., wg0"
              />
              @if (serverForm.get('interfaceName')?.invalid && serverForm.get('interfaceName')?.touched) {
                <div class="mt-1 text-sm text-red-600">
                  @if (serverForm.get('interfaceName')?.errors?.['required']) {
                    <div>Interface name is required</div>
                  }
                </div>
              }
            </div>
          </div>

          <!-- Network Configuration -->
          <div class="space-y-4">
            <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">Network Configuration</h3>

            <div>
              <label for="networkAddress" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Network Address *
              </label>
              <input
                type="text"
                id="networkAddress"
                formControlName="networkAddress"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="e.g., 10.0.0.0/24"
              />
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                Specify the network range for this VPN (CIDR notation)
              </p>
              @if (serverForm.get('networkAddress')?.invalid && serverForm.get('networkAddress')?.touched) {
                <div class="mt-1 text-sm text-red-600">
                  @if (serverForm.get('networkAddress')?.errors?.['required']) {
                    <div>Network address is required</div>
                  }
                  @if (serverForm.get('networkAddress')?.errors?.['pattern']) {
                    <div>Invalid network address format</div>
                  }
                </div>
              }
            </div>

            <p class="text-xs text-gray-500 dark:text-gray-400 -mt-2">
              Public endpoint (host:port for clients) is configured in <strong class="font-medium text-gray-700 dark:text-gray-300">Global settings</strong> (gear icon in the header).
            </p>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label for="listenPort" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  Listen Port *
                </label>
                <input
                  type="number"
                  id="listenPort"
                  formControlName="listenPort"
                  class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="51820"
                  min="1"
                  max="65535"
                />
                @if (serverForm.get('listenPort')?.invalid && serverForm.get('listenPort')?.touched) {
                  <div class="mt-1 text-sm text-red-600">
                    @if (serverForm.get('listenPort')?.errors?.['required']) {
                      <div>Listen port is required</div>
                    }
                    @if (serverForm.get('listenPort')?.errors?.['min'] || serverForm.get('listenPort')?.errors?.['max']) {
                      <div>
                        Port must be between 1 and 65535
                      </div>
                    }
                  </div>
                }
              </div>
            </div>
          </div>

          <!-- PostUp / PostDown (wg-quick hooks) -->
          <div class="space-y-4">
            <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">Interface scripts</h3>
            <p class="text-xs text-gray-500 dark:text-gray-400 -mt-2">
              Optional shell commands run after the interface comes up (PostUp) or before it goes down (PostDown), e.g. iptables NAT.
            </p>

            <div>
              <label for="postUp" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                PostUp
              </label>
              <textarea
                id="postUp"
                formControlName="postUp"
                rows="3"
                maxlength="8192"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono text-sm"
                placeholder="e.g. iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE"
              ></textarea>
            </div>

            <div>
              <label for="postDown" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                PostDown
              </label>
              <textarea
                id="postDown"
                formControlName="postDown"
                rows="3"
                maxlength="8192"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono text-sm"
                placeholder="e.g. iptables -t nat -D POSTROUTING -o eth0 -j MASQUERADE"
              ></textarea>
            </div>
          </div>

          <!-- DNS Configuration -->
          <div class="space-y-4">
            <div class="flex items-center justify-between">
              <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">DNS Servers</h3>
              <button
                type="button"
                (click)="addDnsServer()"
                class="inline-flex items-center px-3 py-2 text-sm font-medium text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-950/50 border border-blue-200 dark:border-blue-800 rounded-md hover:bg-blue-100 dark:hover:bg-blue-900/40"
              >
                <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                </svg>
                Add DNS Server
              </button>
            </div>

            <div formArrayName="dnsServers" class="space-y-3">
              @for (dns of dnsServers.controls; track $index; let i = $index) {
                <div class="flex items-center gap-3">
                  <div class="flex-1">
                    <input
                      type="text"
                      [formControlName]="i"
                      class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      placeholder="e.g., 1.1.1.1"
                    />
                    @if (dns.invalid && dns.touched) {
                      <div class="mt-1 text-sm text-red-600">
                        @if (dns.errors?.['pattern']) {
                          <div>Invalid IP address format</div>
                        }
                      </div>
                    }
                  </div>
                  <button
                    type="button"
                    (click)="removeDnsServer(i)"
                    class="flex-shrink-0 text-red-600 hover:text-red-800"
                  >
                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                  </button>
                </div>
              }
            </div>

            <p class="text-xs text-gray-500 dark:text-gray-400">
              Configure DNS servers for clients connecting to this VPN. Leave empty to use system defaults.
            </p>
          </div>

          <!-- Form Actions -->
          <div class="flex items-center justify-end gap-3 pt-6 border-t border-gray-200 dark:border-gray-700">
            <button
              type="button"
              (click)="cancel()"
              class="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-md hover:bg-gray-50 dark:hover:bg-gray-700"
            >
              Cancel
            </button>
            <button
              type="submit"
              [disabled]="serverForm.invalid || submitting"
              class="px-4 py-2 text-sm font-medium text-white bg-blue-600 border border-transparent rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              @if (submitting) {
                <span class="flex items-center">
                  <svg class="animate-spin -ml-1 mr-2 h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  {{ isEditMode ? 'Updating...' : 'Creating...' }}
                </span>
              }
              @if (!submitting) {
                <span>
                  {{ isEditMode ? 'Update Server' : 'Create Server' }}
                </span>
              }
            </button>
          </div>
        </form>
      </div>
      }
    </div>
  `
})
export class ServerFormComponent implements OnInit, OnDestroy {
  serverForm!: FormGroup;
  isEditMode = false;
  serverId?: string;
  loadingState: LoadingState = { isLoading: false };
  submitting = false;

  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private wireguardService: WireguardService
  ) {
    this.createForm();
  }

  ngOnInit(): void {
    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      this.serverId = params['id'];
      this.isEditMode = !!this.serverId && this.route.snapshot.url.some(segment => segment.path === 'edit');

      if (this.isEditMode && this.serverId) {
        this.loadServerForEdit();
      }
    });

    // Subscribe to loading state
    this.wireguardService.serversLoading$
      .pipe(takeUntil(this.destroy$))
      .subscribe(loading => {
        this.loadingState = loading;
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  createForm(): void {
    this.serverForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(3)]],
      interfaceName: ['', [Validators.required]],
      networkAddress: ['', [Validators.required, Validators.pattern(/^(\d{1,3}\.){3}\d{1,3}\/\d{1,2}$/)]],
      listenPort: [51820, [Validators.required, Validators.min(1), Validators.max(65535)]],
      postUp: ['', [Validators.maxLength(8192)]],
      postDown: ['', [Validators.maxLength(8192)]],
      dnsServers: this.fb.array([])
    });

    // Add default DNS servers
    this.addDnsServer('1.1.1.1');
  }

  get dnsServers(): FormArray {
    return this.serverForm.get('dnsServers') as FormArray;
  }

  loadServerForEdit(): void {
    if (!this.serverId) return;

    this.wireguardService.getServerWithClients(this.serverId).subscribe({
      next: (server) => {
        this.populateForm(server);
      },
      error: (error) => {
        console.error('Error loading server for edit:', error);
      }
    });
  }

  populateForm(server: ServerDetailResponse): void {
    this.serverForm.patchValue({
      name: server.name,
      interfaceName: server.interfaceName,
      networkAddress: this.getNetworkAddress(server),
      listenPort: server.listenPort,
      postUp: server.postUp ?? '',
      postDown: server.postDown ?? ''
    });

    // Clear existing DNS servers and add the ones from the server
    this.dnsServers.clear();
    if (server.dnsServers && server.dnsServers.length > 0) {
      server.dnsServers.forEach(dns => {
        this.addDnsServer(this.getDnsAddress(dns));
      });
    }
  }

  addDnsServer(value: string = ''): void {
    const dnsControl = this.fb.control(value, [
      Validators.pattern(/^(\d{1,3}\.){3}\d{1,3}$/)
    ]);
    this.dnsServers.push(dnsControl);
  }

  removeDnsServer(index: number): void {
    this.dnsServers.removeAt(index);
  }

  onSubmit(): void {
    if (this.serverForm.invalid) {
      // Mark all fields as touched to show validation errors
      Object.keys(this.serverForm.controls).forEach(key => {
        const control = this.serverForm.get(key);
        control?.markAsTouched();
      });

      // Mark DNS server controls as touched
      this.dnsServers.controls.forEach(control => {
        control.markAsTouched();
      });

      return;
    }

    this.submitting = true;

    const formValue = this.serverForm.value;
    const dnsServers = formValue.dnsServers.filter((dns: string) => dns.trim() !== '');

    if (this.isEditMode && this.serverId) {
      this.updateServer(formValue, dnsServers);
    } else {
      this.createServer(formValue, dnsServers);
    }
  }

  createServer(formValue: any, dnsServers: string[]): void {
    const createRequest: CreateServerRequest = {
      name: formValue.name,
      interfaceName: formValue.interfaceName,
      networkAddress: formValue.networkAddress,
      listenPort: formValue.listenPort,
      dnsServers: dnsServers,
      postUp: (() => {
        const t = (formValue.postUp ?? '').trim();
        return t.length > 0 ? t : undefined;
      })(),
      postDown: (() => {
        const t = (formValue.postDown ?? '').trim();
        return t.length > 0 ? t : undefined;
      })()
    };

    this.wireguardService.createServer(createRequest).subscribe({
      next: (server) => {
        this.router.navigate(['/servers', server.id]);
      },
      error: (error) => {
        console.error('Error creating server:', error);
        this.submitting = false;
      }
    });
  }

  updateServer(formValue: any, dnsServers: string[]): void {
    if (!this.serverId) return;

    const updateRequest: UpdateServerRequest = {
      name: formValue.name,
      interfaceName: formValue.interfaceName,
      networkAddress: formValue.networkAddress,
      listenPort: formValue.listenPort,
      dnsServers: dnsServers,
      postUp: (formValue.postUp ?? '').trim(),
      postDown: (formValue.postDown ?? '').trim()
    };

    this.wireguardService.updateServer(this.serverId, updateRequest).subscribe({
      next: (server) => {
        this.router.navigate(['/servers', server.id]);
      },
      error: (error) => {
        console.error('Error updating server:', error);
        this.submitting = false;
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/servers']);
  }

  clearError(): void {
    this.loadingState = { isLoading: false };
  }

  private getNetworkAddress(server: ServerDetailResponse): string {
    return typeof server.networkAddress === 'string'
      ? server.networkAddress
      : server.networkAddress?.address || '';
  }

  private getDnsAddress(dns: any): string {
    return typeof dns === 'string' ? dns : dns?.address || '';
  }
}