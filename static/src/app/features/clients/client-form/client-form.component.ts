import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';

import { WireguardService } from '../../../services/wireguard.service';
import { AnsibleService } from '../../../services/ansible.service';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { AlertComponent } from '../../../shared/components/alert/alert.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import {
  AddClientRequest,
  UpdateClientRequest,
  ServerDetailResponse,
  LoadingState
} from '../../../models/wireguard.interface';
import { AnsibleHost } from '../../../models/ansible.interface';

/** IPv4 + CIDR prefix; used for peer IP and optional extra routes. */
const IPV4_CIDR = /^(\d{1,3}\.){3}\d{1,3}\/\d{1,2}$/;

/** Required peer tunnel IP; trims whitespace so trailing spaces do not fail validation. */
function peerIpCidrValidator(control: AbstractControl): ValidationErrors | null {
  const v = (control.value ?? '').toString().trim();
  if (!v) return { required: true };
  if (!IPV4_CIDR.test(v)) return { pattern: true };
  return null;
}

/**
 * Optional row in “additional allowed IPs”: empty is valid; non-empty must be IPv4 CIDR.
 * (Rows with `required` would keep the whole form invalid when the user adds a blank row.)
 */
function optionalExtraAllowedCidrValidator(control: AbstractControl): ValidationErrors | null {
  const v = (control.value ?? '').toString().trim();
  if (!v) return null;
  if (!IPV4_CIDR.test(v)) return { pattern: true };
  return null;
}

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
      @if (loadingState.isLoading && !server) {
        <app-loading-spinner
          [showText]="true"
          loadingText="Loading server details..."
          containerClass="py-8"
        />
      }

      @if (server && isEditMode && !editDataReady) {
        <app-loading-spinner
          [showText]="true"
          loadingText="Loading client..."
          containerClass="py-8"
        />
      }

      <!-- Error Alert (server load or save validation/API errors) -->
      @if (alertErrorMessage) {
        <app-alert
          type="error"
          title="Error"
          [message]="alertErrorMessage"
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
      @if (clientForm && server && editDataReady) {
      <div class="bg-white dark:bg-gray-900 shadow-sm rounded-lg border border-gray-200 dark:border-gray-700">
        <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100">
            {{ isEditMode ? 'Edit Client' : 'Add New Client' }}
          </h2>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
            {{ isEditMode ? 'Update tunnel settings for this client (public key cannot be changed).' : 'Configure a new client for this WireGuard server' }}
          </p>
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

            <div>
              <label for="interfaceName" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Interface name (wg0–wg99) *
              </label>
              <input
                type="text"
                id="interfaceName"
                formControlName="interfaceName"
                class="w-full max-w-xs px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 font-mono focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="e.g., wg0"
              />
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                Used for the <code class="text-xs">wg-quick</code> systemd unit on the client host (Ansible deploy). Independent of the display name above.
              </p>
              @if (clientForm.get('interfaceName')?.invalid && clientForm.get('interfaceName')?.touched) {
                <div class="mt-1 text-sm text-red-600">
                  @if (clientForm.get('interfaceName')?.errors?.['required']) {
                    <div>Interface name is required</div>
                  }
                  @if (clientForm.get('interfaceName')?.errors?.['pattern']) {
                    <div>Must be wg0 through wg99 (e.g. wg0, wg12)</div>
                  }
                </div>
              }
            </div>

            @if (isEditMode) {
              <div class="flex flex-col gap-4 sm:flex-row sm:items-center">
                <label class="inline-flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
                  <input
                    type="checkbox"
                    formControlName="enabled"
                    class="rounded border-gray-300 text-blue-600 focus:ring-blue-500 dark:border-gray-600"
                  />
                  Client enabled (peer appears in server config when checked)
                </label>
              </div>
              <div>
                <label for="persistentKeepalive" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  Persistent keepalive (seconds)
                </label>
                <input
                  type="number"
                  id="persistentKeepalive"
                  formControlName="persistentKeepalive"
                  min="0"
                  max="65535"
                  class="w-full max-w-xs px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
                <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">Use 0 to disable. Typical values: 0 or 25.</p>
              </div>
            }
          </div>

          <!-- Key Configuration -->
          @if (!isEditMode) {
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
          }

          @if (isEditMode) {
          <div class="space-y-4">
            <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">Keys</h3>
            <div>
              <span class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Public key</span>
              <div
                class="w-full break-all rounded-md border border-gray-200 bg-gray-50 px-3 py-2 font-mono text-xs text-gray-900 dark:border-gray-700 dark:bg-gray-950 dark:text-gray-100 sm:text-sm"
              >{{ publicKeyDisplay }}</div>
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">To change keys, remove this client and add a new one.</p>
            </div>
            <div>
              <label for="presharedKeyEdit" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Pre-shared Key
              </label>
              <textarea
                id="presharedKeyEdit"
                formControlName="presharedKey"
                rows="2"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono text-sm"
                placeholder="Leave empty to keep the current key. Enter a new key to replace it."
              ></textarea>
              <label class="mt-2 flex cursor-pointer items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
                <input
                  type="checkbox"
                  formControlName="removePresharedKey"
                  class="rounded border-gray-300 text-blue-600 focus:ring-blue-500 dark:border-gray-600"
                />
                Remove pre-shared key
              </label>
              @if (clientForm.get('presharedKey')?.invalid && clientForm.get('presharedKey')?.touched) {
                <div class="mt-1 text-sm text-red-600">
                  @if (clientForm.get('presharedKey')?.errors?.['pattern']) {
                    <div>Invalid pre-shared key format</div>
                  }
                </div>
              }
            </div>
          </div>
          }

          @if (server.hostId && !isEditMode) {
            <div class="space-y-3 rounded-lg border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-800/40">
              <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">Client deployment (optional)</h3>
              <p class="text-sm text-gray-600 dark:text-gray-400">
                Optionally push this client&apos;s WireGuard config to an Ansible host. Leave as &quot;Config only&quot; to keep credentials on the control plane only.
              </p>
              <div>
                <label for="clientDeployHostId" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  Remote host
                </label>
                <select
                  id="clientDeployHostId"
                  formControlName="clientDeployHostId"
                  class="w-full max-w-xl px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                >
                  <option value="">Configuration only (no remote deploy)</option>
                  @for (h of ansibleHosts; track h.id) {
                    <option [value]="h.id">{{ h.hostname }} — {{ h.ipAddress }}</option>
                  }
                </select>
              </div>
            </div>
          }

          @if (isEditMode && editDataReady) {
            <div class="rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 text-sm text-gray-800 dark:border-gray-700 dark:bg-gray-800/40 dark:text-gray-200">
              <span class="font-medium text-gray-900 dark:text-gray-100">Client deploy host:</span>
              {{ clientDeployHostSummary || '—' }}
            </div>
          }

          <!-- Peer IP + additional allowed IPs (server-side routes) -->
          <div class="space-y-6">
            <div class="space-y-3">
              <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">Peer IP (VPN address)</h3>
              <p class="text-sm text-gray-600 dark:text-gray-300">
                The address this client uses on the WireGuard tunnel (<code class="text-xs font-mono">Address</code> in the client config).
                This is not the same as additional allowed IPs below, which add extra prefixes routed to this peer on the server.
              </p>
              @if (!isEditMode) {
                <div class="space-y-3">
                  <div class="flex flex-wrap items-end justify-between gap-3">
                    <span class="block text-sm font-medium text-gray-700 dark:text-gray-300">Peer IP(s) *</span>
                    <button
                      type="button"
                      (click)="addPeerIP()"
                      class="inline-flex shrink-0 items-center px-3 py-2 text-sm font-medium text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-950/50 border border-blue-200 dark:border-blue-800 rounded-md hover:bg-blue-100 dark:hover:bg-blue-900/40"
                    >
                      <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                      </svg>
                      Add address
                    </button>
                  </div>
                  <div formArrayName="peerIPs" class="space-y-3 max-w-lg">
                    @for (row of peerIPs.controls; track $index; let i = $index) {
                      <div [formGroupName]="i" class="flex items-start gap-3">
                        <div class="flex-1">
                          <input
                            type="text"
                            formControlName="address"
                            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono"
                            [attr.id]="i === 0 ? 'peerIP0' : null"
                            placeholder="e.g., 10.8.0.2/32"
                          />
                          @if (row.get('address')?.invalid && row.get('address')?.touched) {
                            <div class="mt-1 text-sm text-red-600">
                              @if (row.get('address')?.errors?.['required']) {
                                <div>Address is required</div>
                              }
                              @if (row.get('address')?.errors?.['pattern']) {
                                <div>Invalid format (use CIDR, e.g. 10.8.0.2/32)</div>
                              }
                            </div>
                          }
                        </div>
                        <button
                          type="button"
                          (click)="removePeerIP(i)"
                          class="flex-shrink-0 mt-2 text-red-600 hover:text-red-800 disabled:opacity-30 disabled:cursor-not-allowed"
                          [disabled]="peerIPs.length === 1"
                          title="Remove address"
                        >
                          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                          </svg>
                        </button>
                      </div>
                    }
                  </div>
                </div>
              }
              @if (isEditMode) {
                <div
                  class="w-full max-w-lg break-all rounded-md border border-gray-200 bg-gray-50 px-3 py-2 font-mono text-sm text-gray-900 dark:border-gray-700 dark:bg-gray-950 dark:text-gray-100"
                >{{ peerIPDisplay }}</div>
                <p class="text-xs text-gray-500 dark:text-gray-400">Peer IP is fixed after the client is created.</p>
              }
            </div>

            <div class="space-y-4">
              <div class="flex items-center justify-between gap-4">
                <div>
                  <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100">Additional allowed IPs</h3>
                  <p class="text-sm text-gray-600 dark:text-gray-300">
                    Extra prefixes routed to this peer on the <span class="font-medium">server</span> (e.g. remote site LANs for site-to-site VPN). May be outside the server tunnel subnet. Leave empty if not needed.
                  </p>
                </div>
                <button
                  type="button"
                  (click)="addAllowedIP()"
                  class="inline-flex shrink-0 items-center px-3 py-2 text-sm font-medium text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-950/50 border border-blue-200 dark:border-blue-800 rounded-md hover:bg-blue-100 dark:hover:bg-blue-900/40"
                >
                  <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                  </svg>
                  Add CIDR
                </button>
              </div>

              @if (allowedIPs.length === 0) {
                <p class="text-sm text-gray-500 dark:text-gray-400 italic">No extra routes — server will use the peer IP only.</p>
              }

              <div formArrayName="allowedIPs" class="space-y-3">
                @for (address of allowedIPs.controls; track $index; let i = $index) {
                  <div class="space-y-2">
                    <div [formGroupName]="i" class="flex items-start gap-3">
                      <div class="flex-1">
                        <input
                          type="text"
                          formControlName="address"
                          class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono"
                          placeholder="e.g., 10.0.0.10/32"
                        />
                        @if (address.get('address')?.invalid && address.get('address')?.touched) {
                          <div class="mt-1 text-sm text-red-600">
                            @if (address.get('address')?.errors?.['pattern']) {
                              <div>Invalid format (use CIDR, e.g. 10.0.0.10/32), or clear the field</div>
                            }
                          </div>
                        }
                      </div>
                      <button
                        type="button"
                        (click)="removeAllowedIP(i)"
                        class="flex-shrink-0 mt-2 text-red-600 hover:text-red-800"
                        title="Remove this CIDR"
                      >
                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                        </svg>
                      </button>
                    </div>
                  </div>
                }
              </div>
            </div>

            <div class="bg-blue-50 dark:bg-blue-950/40 border border-blue-200 dark:border-blue-800 rounded-md p-4">
              <div class="flex items-start">
                <svg class="w-5 h-5 text-blue-400 dark:text-blue-500 mr-3 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <div class="text-sm">
                  <p class="text-blue-800 dark:text-blue-200 font-medium mb-1">IP guidelines</p>
                  <ul class="text-blue-700 dark:text-blue-300 space-y-1">
                    <li>• Use CIDR notation (e.g., 10.8.0.2/32)</li>
                    <li>
                      • Peer IP(s) are usually taken from the server tunnel network ({{ getServerNetwork() }}). Additional CIDRs may be other subnets you route through this peer (site-to-site).
                    </li>
                    <li>• Each client must use unique addresses across all peer IPs and extra routes</li>
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
                  {{ isEditMode ? 'Saving...' : 'Adding Client...' }}
                </span>
              }
              @if (!submitting) {
                <span>{{ isEditMode ? 'Save changes' : 'Add Client' }}</span>
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
  /** Set on route `.../clients/:clientId/edit` */
  clientId?: string;
  /** Shown in edit mode (public key is immutable in the API). */
  publicKeyDisplay = '';
  /** For edit flow: wait for GET /api/private/wireguard/clients/:id before showing the form. */
  editDataReady = true;
  /** Edit mode: read-only VPN/tunnel IPs from API (peer IP is immutable after create). */
  peerIPDisplay = '';
  ansibleHosts: AnsibleHost[] = [];
  /** Resolved label for edit mode when client has hostId. */
  clientDeployHostSummary = '';
  loadingState: LoadingState = { isLoading: false };
  /** Shown when add/update client fails (e.g. duplicate IP from API). */
  formSubmitError: string | null = null;
  submitting = false;

  get isEditMode(): boolean {
    return !!this.clientId;
  }

  /** Combined load + submit error for `app-alert` ([message] must be `string`). */
  get alertErrorMessage(): string {
    return this.loadingState.error ?? this.formSubmitError ?? '';
  }

  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private wireguardService: WireguardService,
    private ansible: AnsibleService
  ) {
    this.createForm();
  }

  ngOnInit(): void {
    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      this.serverId = params['serverId'];
      this.clientId = params['clientId'];
      this.editDataReady = !this.isEditMode;
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
      interfaceName: [
        'wg0',
        [Validators.required, Validators.pattern(/^wg[0-9]{1,2}$/)]
      ],
      clientPublicKey: [''], // Optional (add client only)
      presharedKey: [''], // Optional
      removePresharedKey: [false],
      persistentKeepalive: [25, [Validators.min(0), Validators.max(65535)]],
      enabled: [true],
      clientDeployHostId: [''],
      peerIPs: this.fb.array([this.createPeerIpGroup('')]),
      allowedIPs: this.fb.array([])
    });
  }

  private createPeerIpGroup(value: string): FormGroup {
    return this.fb.group({
      address: [value, [peerIpCidrValidator]]
    });
  }

  get peerIPs(): FormArray {
    return this.clientForm.get('peerIPs') as FormArray;
  }

  get allowedIPs(): FormArray {
    return this.clientForm.get('allowedIPs') as FormArray;
  }

  addPeerIP(value: string = ''): void {
    this.peerIPs.push(this.createPeerIpGroup(value));
  }

  removePeerIP(index: number): void {
    if (this.peerIPs.length > 1) {
      this.peerIPs.removeAt(index);
    }
  }

  loadServer(): void {
    if (!this.serverId) return;

    this.wireguardService.getServerWithClients(this.serverId).subscribe({
      next: (server) => {
        this.server = server;
        if (server.hostId) {
          this.loadAnsibleHosts();
        }
        if (this.isEditMode && this.clientId) {
          this.loadClientForEdit();
        } else {
          this.prepareAddClientForm();
          this.suggestNextAvailableIP();
        }
      },
      error: (error) => {
        console.error('Error loading server:', error);
      }
    });
  }

  private loadClientForEdit(): void {
    if (!this.clientId) return;
    this.editDataReady = false;
    this.wireguardService.getClientDetails(this.clientId).subscribe({
      next: (details) => {
        if (this.server && details.server.id !== this.server.id) {
          console.warn('Client server id does not match route server id');
        }
        this.publicKeyDisplay = details.publicKey;
        this.peerIPDisplay = details.peerIPs?.length ? details.peerIPs.join(', ') : '—';
        while (this.allowedIPs.length) {
          this.allowedIPs.removeAt(0);
        }
        details.allowedIPs.forEach(ip => this.addAllowedIP(ip));
        this.clientForm.patchValue({
          clientName: details.name,
          interfaceName: details.interfaceName,
          persistentKeepalive: details.persistentKeepalive,
          enabled: details.enabled,
          clientPublicKey: '',
          presharedKey: '',
          removePresharedKey: false
        });
        while (this.peerIPs.length) {
          this.peerIPs.removeAt(0);
        }
        if (!details.hostId) {
          this.clientDeployHostSummary = 'Configuration only (not deployed to a remote host)';
        } else {
          this.clientDeployHostSummary = '';
          this.ansible.listHosts(false).subscribe({
            next: hosts => {
              const h = hosts.find(x => x.id === details.hostId);
              this.clientDeployHostSummary = h
                ? `${h.hostname} (${h.ipAddress})`
                : details.hostId ?? '';
            },
            error: () => {
              this.clientDeployHostSummary = details.hostId ?? '';
            }
          });
        }
        this.editDataReady = true;
      },
      error: (error) => {
        console.error('Error loading client:', error);
        this.editDataReady = true;
      }
    });
  }

  addAllowedIP(value: string = ''): void {
    const addressGroup = this.fb.group({
      address: [value, [optionalExtraAllowedCidrValidator]]
    });
    this.allowedIPs.push(addressGroup);
  }

  removeAllowedIP(index: number): void {
    this.allowedIPs.removeAt(index);
  }

  /** Reset controls for “add client” when the route is create (or returning from edit). */
  private prepareAddClientForm(): void {
    while (this.peerIPs.length) {
      this.peerIPs.removeAt(0);
    }
    this.addPeerIP('');
    while (this.allowedIPs.length) {
      this.allowedIPs.removeAt(0);
    }
    this.peerIPDisplay = '';
  }

  private loadAnsibleHosts(): void {
    this.ansible.listHosts(false).subscribe({
      next: rows => {
        this.ansibleHosts = rows.filter(h => h.enabled);
      },
      error: () => {
        this.ansibleHosts = [];
      }
    });
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
    this.formSubmitError = null;

    const formValue = this.clientForm.value;

    if (this.isEditMode && this.clientId) {
      const body: UpdateClientRequest = {
        clientName: formValue.clientName.trim(),
        interfaceName: (formValue.interfaceName as string).trim(),
        addresses: (formValue.allowedIPs as { address: string }[])
          .map(a => a.address?.trim())
          .filter((a): a is string => !!a)
          .map(address => ({ address })),
        persistentKeepalive: formValue.persistentKeepalive,
        enabled: formValue.enabled
      };
      if (formValue.removePresharedKey) {
        body.presharedKey = '';
      } else if (formValue.presharedKey?.trim()) {
        body.presharedKey = formValue.presharedKey.trim();
      }

      this.wireguardService.updateClient(this.serverId, this.clientId, body).subscribe({
        next: (client) => {
          this.router.navigate(['/servers', this.serverId, 'clients'], {
            queryParams: { success: `Client "${client.name}" updated` }
          });
        },
        error: (error) => {
          console.error('Error updating client:', error);
          this.submitting = false;
          this.formSubmitError = this.wireguardService.getApiErrorMessage(error);
        }
      });
      return;
    }

    const raw = this.clientForm.getRawValue();
    const peerList = (raw.peerIPs as { address: string }[])
      .map(p => p.address?.trim())
      .filter((a): a is string => !!a);
    if (!peerList.length) {
      this.submitting = false;
      this.formSubmitError = 'At least one peer IP is required.';
      return;
    }

    const extraAllowed = (formValue.allowedIPs as { address: string }[])
      .map(a => a.address?.trim())
      .filter((a): a is string => !!a);

    const addClientRequest: AddClientRequest = {
      clientName: formValue.clientName,
      interfaceName: (formValue.interfaceName as string).trim(),
      peerIPs: peerList.map(address => ({ address })),
      allowedIPs: extraAllowed.map(address => ({ address }))
    };

    // Add optional fields if provided
    if (formValue.clientPublicKey && formValue.clientPublicKey.trim()) {
      addClientRequest.clientPublicKey = formValue.clientPublicKey.trim();
    }

    if (formValue.presharedKey && formValue.presharedKey.trim()) {
      addClientRequest.presharedKey = formValue.presharedKey.trim();
    }

    const deployHost = (formValue.clientDeployHostId as string)?.trim();
    if (deployHost) {
      addClientRequest.hostId = deployHost;
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
        this.formSubmitError = this.wireguardService.getApiErrorMessage(error);
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
    this.formSubmitError = null;
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
        const usedIPs =
          this.server.clients?.flatMap(client => [
            ...(client.peerIPs ?? []).map(ip => ip.split('/')[0]),
            ...client.allowedIPs.map(ip => ip.split('/')[0])
          ]) ?? [];

        // Find next available IP starting from .2 (assuming .1 is the server)
        for (let i = 2; i <= 254; i++) {
          const candidateIP = `${baseNetwork}.${i}`;
          if (!usedIPs.includes(candidateIP)) {
            const firstPeer = this.peerIPs.at(0)?.get('address');
            if (firstPeer && !firstPeer.value?.toString().trim()) {
              firstPeer.setValue(`${candidateIP}/32`);
            }
            break;
          }
        }
      }
    }
  }
}