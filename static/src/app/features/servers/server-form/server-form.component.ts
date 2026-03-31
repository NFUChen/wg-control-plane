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
      <app-loading-spinner
        *ngIf="loadingState.isLoading && !serverForm"
        [showText]="true"
        loadingText="Loading server details..."
        containerClass="py-8"
      />

      <!-- Error Alert -->
      <app-alert
        *ngIf="loadingState.error"
        type="error"
        title="Error"
        [message]="loadingState.error"
        (dismissed)="clearError()"
      />

      <!-- Form -->
      <div *ngIf="serverForm" class="bg-white dark:bg-gray-900 shadow-sm rounded-lg border border-gray-200 dark:border-gray-700">
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
              <div *ngIf="serverForm.get('name')?.invalid && serverForm.get('name')?.touched"
                   class="mt-1 text-sm text-red-600">
                <div *ngIf="serverForm.get('name')?.errors?.['required']">Server name is required</div>
                <div *ngIf="serverForm.get('name')?.errors?.['minlength']">Server name must be at least 3 characters</div>
              </div>
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
              <div *ngIf="serverForm.get('interfaceName')?.invalid && serverForm.get('interfaceName')?.touched"
                   class="mt-1 text-sm text-red-600">
                <div *ngIf="serverForm.get('interfaceName')?.errors?.['required']">Interface name is required</div>
              </div>
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
              <div *ngIf="serverForm.get('networkAddress')?.invalid && serverForm.get('networkAddress')?.touched"
                   class="mt-1 text-sm text-red-600">
                <div *ngIf="serverForm.get('networkAddress')?.errors?.['required']">Network address is required</div>
                <div *ngIf="serverForm.get('networkAddress')?.errors?.['pattern']">Invalid network address format</div>
              </div>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label for="endpoint" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  Public Endpoint *
                </label>
                <input
                  type="text"
                  id="endpoint"
                  formControlName="endpoint"
                  class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="e.g., vpn.example.com"
                />
                <div *ngIf="serverForm.get('endpoint')?.invalid && serverForm.get('endpoint')?.touched"
                     class="mt-1 text-sm text-red-600">
                  <div *ngIf="serverForm.get('endpoint')?.errors?.['required']">Endpoint is required</div>
                </div>
              </div>

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
                <div *ngIf="serverForm.get('listenPort')?.invalid && serverForm.get('listenPort')?.touched"
                     class="mt-1 text-sm text-red-600">
                  <div *ngIf="serverForm.get('listenPort')?.errors?.['required']">Listen port is required</div>
                  <div *ngIf="serverForm.get('listenPort')?.errors?.['min'] || serverForm.get('listenPort')?.errors?.['max']">
                    Port must be between 1 and 65535
                  </div>
                </div>
              </div>
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
              <div *ngFor="let dns of dnsServers.controls; let i = index" class="flex items-center gap-3">
                <div class="flex-1">
                  <input
                    type="text"
                    [formControlName]="i"
                    class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="e.g., 1.1.1.1"
                  />
                  <div *ngIf="dns.invalid && dns.touched" class="mt-1 text-sm text-red-600">
                    <div *ngIf="dns.errors?.['pattern']">Invalid IP address format</div>
                  </div>
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
              <span *ngIf="submitting" class="flex items-center">
                <svg class="animate-spin -ml-1 mr-2 h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                {{ isEditMode ? 'Updating...' : 'Creating...' }}
              </span>
              <span *ngIf="!submitting">
                {{ isEditMode ? 'Update Server' : 'Create Server' }}
              </span>
            </button>
          </div>
        </form>
      </div>
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
      endpoint: ['', [Validators.required]],
      listenPort: [51820, [Validators.required, Validators.min(1), Validators.max(65535)]],
      dnsServers: this.fb.array([])
    });

    // Add default DNS servers
    this.addDnsServer('1.1.1.1');
    this.addDnsServer('1.0.0.1');
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
      interfaceName: server.id, // This might need to be mapped differently
      networkAddress: this.getNetworkAddress(server),
      endpoint: server.endpoint,
      listenPort: server.listenPort
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
      endpoint: formValue.endpoint,
      dnsServers: dnsServers
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
      endpoint: formValue.endpoint,
      dnsServers: dnsServers
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