import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, FormArray } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';

import { WireguardService } from '../../../services/wireguard.service';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { AlertComponent } from '../../../shared/components/alert/alert.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import {
  AddClientRequest,
  ServerDetailResponse,
  LoadingState
} from '../../../models/wireguard.interface';

@Component({
  selector: 'app-client-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    LoadingSpinnerComponent,
    AlertComponent,
    StatusBadgeComponent
  ],
  template: `
    <div class="max-w-2xl mx-auto space-y-6">
      <!-- Loading Spinner -->
      @if (loadingState.isLoading && !clientForm) {
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

      <!-- Server Info -->
      @if (server) {
      <div class="bg-white dark:bg-gray-900 shadow-sm rounded-lg border border-gray-200 dark:border-gray-700 p-6">
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ server.name }}</h2>
            <p class="text-sm text-gray-500 dark:text-gray-400">{{ server.endpoint }}:{{ server.listenPort }}</p>
          </div>
          <div class="flex items-center gap-3">
            <app-status-badge
              [variant]="getServerStatusVariant(server.enabled)"
              [label]="server.enabled ? 'Active' : 'Inactive'"
            />
            <span class="text-sm text-gray-500 dark:text-gray-400">{{ (server.clients.length || 0) }} clients</span>
          </div>
        </div>
      </div>
      }

      <!-- Form -->
      @if (clientForm) {
      <div class="bg-white dark:bg-gray-900 shadow-sm rounded-lg border border-gray-200 dark:border-gray-700">
        <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100">Add New Client</h2>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">Configure a new client for this WireGuard server</p>
        </div>

        <form [formGroup]="clientForm" (ngSubmit)="onSubmit()" class="p-6 space-y-6">
          <!-- Basic Information -->
          <div class="space-y-4">
            <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">Client Information</h3>

            <div>
              <label for="clientName" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Client Name *
              </label>
              <input
                type="text"
                id="clientName"
                formControlName="clientName"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="e.g., John's Laptop, Mobile Device"
              />
              @if (clientForm.get('clientName')?.invalid && clientForm.get('clientName')?.touched) {
                <div class="mt-1 text-sm text-red-600">
                  @if (clientForm.get('clientName')?.errors?.['required']) {
                    <div>Client name is required</div>
                  }
                  @if (clientForm.get('clientName')?.errors?.['minlength']) {
                    <div>Client name must be at least 2 characters</div>
                  }
                </div>
              }
            </div>
          </div>

          <!-- Key Configuration -->
          <div class="space-y-4">
            <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">Key Configuration</h3>
            <p class="text-sm text-gray-600 dark:text-gray-300">
              Leave the public key empty to auto-generate a new key pair for this client.
            </p>

            <div>
              <label for="clientPublicKey" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Client Public Key (Optional)
              </label>
              <textarea
                id="clientPublicKey"
                formControlName="clientPublicKey"
                rows="2"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono text-sm"
                placeholder="Leave empty to auto-generate..."
              ></textarea>
              @if (clientForm.get('clientPublicKey')?.invalid && clientForm.get('clientPublicKey')?.touched) {
                <div class="mt-1 text-sm text-red-600">
                  @if (clientForm.get('clientPublicKey')?.errors?.['pattern']) {
                    <div>Invalid public key format</div>
                  }
                </div>
              }
            </div>

            <div>
              <label for="presharedKey" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Pre-shared Key (Optional)
              </label>
              <textarea
                id="presharedKey"
                formControlName="presharedKey"
                rows="2"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono text-sm"
                placeholder="Optional for additional security..."
              ></textarea>
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                Optional pre-shared key for additional post-quantum security
              </p>
              @if (clientForm.get('presharedKey')?.invalid && clientForm.get('presharedKey')?.touched) {
                <div class="mt-1 text-sm text-red-600">
                  @if (clientForm.get('presharedKey')?.errors?.['pattern']) {
                    <div>Invalid pre-shared key format</div>
                  }
                </div>
              }
            </div>
          </div>

          <!-- IP Address Configuration -->
          <div class="space-y-4">
            <div class="flex items-center justify-between">
              <div>
                <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">IP Addresses</h3>
                <p class="text-sm text-gray-600 dark:text-gray-300">Configure allowed IP addresses for this client</p>
              </div>
              <button
                type="button"
                (click)="addIPAddress()"
                class="inline-flex items-center px-3 py-2 text-sm font-medium text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-950/50 border border-blue-200 dark:border-blue-800 rounded-md hover:bg-blue-100 dark:hover:bg-blue-900/40"
              >
                <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                </svg>
                Add IP Address
              </button>
            </div>

            <div formArrayName="addresses" class="space-y-3">
              @for (address of addresses.controls; track $index; let i = $index) {
                <div class="space-y-2">
                  <div [formGroupName]="i" class="flex items-start gap-3">
                    <div class="flex-1">
                      <input
                        type="text"
                        formControlName="address"
                        class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono"
                        placeholder="e.g., 10.0.0.2/32"
                      />
                      @if (address.get('address')?.invalid && address.get('address')?.touched) {
                        <div class="mt-1 text-sm text-red-600">
                          @if (address.get('address')?.errors?.['required']) {
                            <div>IP address is required</div>
                          }
                          @if (address.get('address')?.errors?.['pattern']) {
                            <div>Invalid IP address format (use CIDR notation)</div>
                          }
                        </div>
                      }
                    </div>
                    <button
                      type="button"
                      (click)="removeIPAddress(i)"
                      class="flex-shrink-0 mt-2 text-red-600 hover:text-red-800"
                      [disabled]="addresses.length === 1"
                    >
                      <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                      </svg>
                    </button>
                  </div>
                </div>
              }
            </div>

            <div class="bg-blue-50 dark:bg-blue-950/40 border border-blue-200 dark:border-blue-800 rounded-md p-4">
              <div class="flex items-start">
                <svg class="w-5 h-5 text-blue-400 dark:text-blue-500 mr-3 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <div class="text-sm">
                  <p class="text-blue-800 dark:text-blue-200 font-medium mb-1">IP Address Guidelines</p>
                  <ul class="text-blue-700 dark:text-blue-300 space-y-1">
                    <li>• Use CIDR notation (e.g., 10.0.0.2/32 for a single IP)</li>
                    <li>• Ensure IPs are within the server's network range: {{ getServerNetwork() }}</li>
                    <li>• Each client should have unique IP addresses</li>
                    <li>• /32 suffix is recommended for individual clients</li>
                  </ul>
                </div>
              </div>
            </div>
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
              [disabled]="clientForm.invalid || submitting"
              class="px-4 py-2 text-sm font-medium text-white bg-blue-600 border border-transparent rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              @if (submitting) {
                <span class="flex items-center">
                  <svg class="animate-spin -ml-1 mr-2 h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  Adding Client...
                </span>
              }
              @if (!submitting) {
                <span>Add Client</span>
              }
            </button>
          </div>
        </form>
      </div>
      }
    </div>
  `
})
export class ClientFormComponent implements OnInit, OnDestroy {
  clientForm!: FormGroup;
  server?: ServerDetailResponse;
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
      this.serverId = params['serverId'];
      if (this.serverId) {
        this.loadServer();
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
    this.clientForm = this.fb.group({
      clientName: ['', [Validators.required, Validators.minLength(2)]],
      clientPublicKey: [''], // Optional
      presharedKey: [''], // Optional
      addresses: this.fb.array([])
    });

    // Add a default IP address
    this.addIPAddress();
  }

  get addresses(): FormArray {
    return this.clientForm.get('addresses') as FormArray;
  }

  loadServer(): void {
    if (!this.serverId) return;

    this.wireguardService.getServerWithClients(this.serverId).subscribe({
      next: (server) => {
        this.server = server;
        this.suggestNextAvailableIP();
      },
      error: (error) => {
        console.error('Error loading server:', error);
      }
    });
  }

  addIPAddress(value: string = ''): void {
    const addressGroup = this.fb.group({
      address: [value, [Validators.required, Validators.pattern(/^(\d{1,3}\.){3}\d{1,3}\/\d{1,2}$/)]]
    });
    this.addresses.push(addressGroup);
  }

  removeIPAddress(index: number): void {
    if (this.addresses.length > 1) {
      this.addresses.removeAt(index);
    }
  }

  onSubmit(): void {
    if (this.clientForm.invalid) {
      // Mark all fields as touched to show validation errors
      this.markFormGroupTouched(this.clientForm);
      return;
    }

    if (!this.serverId) {
      console.error('Server ID is missing');
      return;
    }

    this.submitting = true;

    const formValue = this.clientForm.value;

    // Create the request object
    const addClientRequest: AddClientRequest = {
      clientName: formValue.clientName,
      addresses: formValue.addresses.map((addr: any) => ({ address: addr.address }))
    };

    // Add optional fields if provided
    if (formValue.clientPublicKey && formValue.clientPublicKey.trim()) {
      addClientRequest.clientPublicKey = formValue.clientPublicKey.trim();
    }

    if (formValue.presharedKey && formValue.presharedKey.trim()) {
      addClientRequest.presharedKey = formValue.presharedKey.trim();
    }

    this.wireguardService.addClientToServer(this.serverId, addClientRequest).subscribe({
      next: (client) => {
        // Navigate to the client list with success message
        this.router.navigate(['/servers', this.serverId, 'clients'], {
          queryParams: { success: `Client "${client.name}" added successfully` }
        });
      },
      error: (error) => {
        console.error('Error adding client:', error);
        this.submitting = false;
      }
    });
  }

  cancel(): void {
    if (this.serverId) {
      this.router.navigate(['/servers', this.serverId, 'clients']);
    } else {
      this.router.navigate(['/servers']);
    }
  }

  clearError(): void {
    this.loadingState = { isLoading: false };
  }

  getServerStatusVariant(enabled: boolean): 'success' | 'gray' {
    return enabled ? 'success' : 'gray';
  }

  getServerNetwork(): string {
    if (!this.server) return 'N/A';
    return typeof this.server.networkAddress === 'string'
      ? this.server.networkAddress
      : this.server.networkAddress?.address || 'N/A';
  }

  private markFormGroupTouched(formGroup: FormGroup): void {
    Object.keys(formGroup.controls).forEach(key => {
      const control = formGroup.get(key);
      control?.markAsTouched();

      if (control instanceof FormGroup) {
        this.markFormGroupTouched(control);
      } else if (control instanceof FormArray) {
        control.controls.forEach(arrayControl => {
          if (arrayControl instanceof FormGroup) {
            this.markFormGroupTouched(arrayControl);
          } else {
            arrayControl.markAsTouched();
          }
        });
      }
    });
  }

  private suggestNextAvailableIP(): void {
    if (!this.server || !this.server.networkAddress) return;

    // This is a simplified IP suggestion - in a real app, you'd want more sophisticated logic
    const networkAddr = typeof this.server.networkAddress === 'string'
      ? this.server.networkAddress
      : this.server.networkAddress.address;

    if (networkAddr && networkAddr.includes('/')) {
      const [network] = networkAddr.split('/');
      const networkParts = network.split('.');

      if (networkParts.length === 4) {
        // Simple logic: increment the last octet
        const baseNetwork = `${networkParts[0]}.${networkParts[1]}.${networkParts[2]}`;
        const usedIPs = this.server.clients?.map(client =>
          client.allowedIPs.map(ip => ip.split('/')[0])
        ).flat() || [];

        // Find next available IP starting from .2 (assuming .1 is the server)
        for (let i = 2; i <= 254; i++) {
          const candidateIP = `${baseNetwork}.${i}`;
          if (!usedIPs.includes(candidateIP)) {
            // Update the first address field if it's empty
            if (this.addresses.length > 0 && !this.addresses.at(0)?.get('address')?.value) {
              this.addresses.at(0)?.get('address')?.setValue(`${candidateIP}/32`);
            }
            break;
          }
        }
      }
    }
  }
}