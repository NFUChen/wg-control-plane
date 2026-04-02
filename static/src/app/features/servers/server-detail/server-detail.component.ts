import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';

import { WireguardService } from '../../../services/wireguard.service';
import { AnsibleService } from '../../../services/ansible.service';
import { DataTableComponent, TableAction } from '../../../shared/components/data-table/data-table.component';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { AlertComponent } from '../../../shared/components/alert/alert.component';
import { StatusBadgeComponent, BadgeVariant } from '../../../shared/components/status-badge/status-badge.component';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import {
  ServerDetailResponse,
  ClientResponse,
  TableColumn,
  LoadingState,
  ConfigurationPreview
} from '../../../models/wireguard.interface';

@Component({
  selector: 'app-server-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    DataTableComponent,
    LoadingSpinnerComponent,
    AlertComponent,
    StatusBadgeComponent,
    ModalComponent
  ],
  template: `
    <div class="space-y-6">
      <!-- Loading Spinner -->
      @if (loadingState.isLoading && !server) {
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
          title="Error loading server"
          [message]="loadingState.error"
          (dismissed)="clearError()"
        />
      }

      <!-- Success Alert -->
      @if (successMessage) {
        <app-alert
          type="success"
          [message]="successMessage"
          (dismissed)="clearSuccessMessage()"
        />
      }

      <!-- Server Details -->
      @if (server && !loadingState.isLoading) {
      <div class="bg-white dark:bg-gray-900 shadow-sm rounded-lg border border-gray-200 dark:border-gray-700">
        <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <div class="flex items-center justify-between">
            <div>
              <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100">{{ server.name }}</h2>
              <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">Server Configuration Details</p>
            </div>
            <div class="flex items-center gap-3">
              <app-status-badge
                [variant]="getServerStatusVariant(server.enabled)"
                [label]="server.enabled ? 'Active' : 'Inactive'"
              />
              <button
                (click)="editServer()"
                class="inline-flex items-center px-3 py-2 border border-gray-300 dark:border-gray-600 text-sm font-medium rounded-md text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700"
              >
                <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                </svg>
                Edit Server
              </button>
            </div>
          </div>
        </div>

        <div class="p-6">
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            <!-- Network Information -->
            <div>
              <h3 class="text-sm font-medium text-gray-900 dark:text-gray-100 mb-3">Network Configuration</h3>
              <dl class="space-y-2">
                <div>
                  <dt class="text-xs text-gray-500 dark:text-gray-400">Network Address</dt>
                  <dd class="text-sm font-mono bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded text-gray-900 dark:text-gray-100">{{ getNetworkAddress(server) }}</dd>
                </div>
                <div>
                  <dt class="text-xs text-gray-500 dark:text-gray-400">Listen Port</dt>
                  <dd class="text-sm text-gray-900 dark:text-gray-100">{{ server.listenPort }}</dd>
                </div>
                <div>
                  <dt class="text-xs text-gray-500 dark:text-gray-400">Public endpoint (global)</dt>
                  <dd class="text-sm font-mono text-gray-900 dark:text-gray-100">{{ server.endpoint }}</dd>
                </div>
              </dl>
            </div>

            <!-- Server Information -->
            <div>
              <h3 class="text-sm font-medium text-gray-900 dark:text-gray-100 mb-3">Server Details</h3>
              <dl class="space-y-2">
                <div>
                  <dt class="text-xs text-gray-500 dark:text-gray-400">Public Key</dt>
                  <dd class="text-xs font-mono bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded break-all text-gray-900 dark:text-gray-100">{{ server.publicKey }}</dd>
                </div>
                <div>
                  <dt class="text-xs text-gray-500 dark:text-gray-400">Created</dt>
                  <dd class="text-sm text-gray-900 dark:text-gray-100">{{ server.createdAt | date:'medium' }}</dd>
                </div>
                <div>
                  <dt class="text-xs text-gray-500 dark:text-gray-400">Last Updated</dt>
                  <dd class="text-sm text-gray-900 dark:text-gray-100">{{ server.updatedAt | date:'medium' }}</dd>
                </div>
                <div class="sm:col-span-2">
                  <dt class="text-xs text-gray-500 dark:text-gray-400">Deployment</dt>
                  <dd class="text-sm text-gray-900 dark:text-gray-100">{{ deploymentLabel }}</dd>
                </div>
              </dl>
            </div>

            <!-- DNS Configuration -->
            <div>
              <h3 class="text-sm font-medium text-gray-900 dark:text-gray-100 mb-3">DNS Servers</h3>
              <div class="space-y-1">
                @for (dns of server.dnsServers; track $index) {
                  <div class="text-sm font-mono bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded text-gray-900 dark:text-gray-100">
                    {{ getDnsAddress(dns) }}
                  </div>
                }
                @if (server.dnsServers.length === 0) {
                  <div class="text-sm text-gray-500 dark:text-gray-400 italic">
                    No DNS servers configured
                  </div>
                }
              </div>
            </div>
          </div>
        </div>
      </div>
      }

      <!-- Clients Table -->
      @if (server) {
        <app-data-table
          title="Connected Clients"
          [subtitle]="getClientsSubtitle()"
          [columns]="clientColumns"
          [data]="server.clients || []"
          [loading]="clientsLoading"
          [primaryAction]="clientsPrimaryAction"
          [rowActions]="clientRowActions"
          [searchable]="true"
          emptyMessage="No clients connected"
          emptySubMessage="Add clients to this server to get started"
          (primaryActionClick)="addClient()"
          (rowActionClick)="onClientAction($event)"
        >
          <!-- Custom templates for client columns -->
          <ng-template #customTemplate let-item="$implicit" let-column="column">
            <ng-container [ngSwitch]="column.key">
              <!-- Client Status (WG + Ansible deploy; avoid stacked loud pills) -->
              <span *ngSwitchCase="'status'">
                <div class="flex flex-col gap-1 whitespace-nowrap">
                  <app-status-badge
                    [variant]="getClientStatusVariant(item.enabled, item.isOnline)"
                    [label]="getClientStatusLabel(item.enabled, item.isOnline)"
                  />
                  @if (item.deploymentStatus === 'DEPLOY_FAILED' || item.deploymentStatus === 'PENDING_REMOVAL') {
                    <div
                      class="flex flex-nowrap items-center justify-between gap-2 rounded-md border border-gray-200/90 bg-gray-50/90 px-2 py-1.5 dark:border-gray-600/80 dark:bg-gray-800/50"
                      [title]="item.deploymentStatus === 'DEPLOY_FAILED' ? 'Retry deploying client config to remote host' : 'Retry cleaning up client config on remote host'"
                    >
                      <span class="text-[11px] leading-snug text-red-600 dark:text-red-400 whitespace-nowrap">
                        {{ item.deploymentStatus === 'DEPLOY_FAILED' ? 'Remote deploy failed' : 'Removal pending on host' }}
                      </span>
                      <button
                        type="button"
                        (click)="retryClientDeployment(item)"
                        [disabled]="retryingClientIds.has(item.id)"
                        class="inline-flex shrink-0 items-center gap-0.5 whitespace-nowrap rounded px-1.5 py-0.5 text-[11px] font-medium text-gray-700 hover:bg-gray-200/90 disabled:cursor-wait disabled:opacity-50 dark:text-gray-300 dark:hover:bg-gray-700/80"
                      >
                        <svg class="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"
                             [class.animate-spin]="retryingClientIds.has(item.id)">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                        </svg>
                        {{ retryingClientIds.has(item.id) ? 'Retrying' : 'Retry' }}
                      </button>
                    </div>
                  }
                  @if (item.deploymentStatus === 'DEPLOYED') {
                    <span class="text-[11px] text-gray-500 dark:text-gray-400 whitespace-nowrap">Remote config deployed</span>
                  }
                </div>
              </span>

              <!-- Allowed IPs -->
              <span *ngSwitchCase="'allowedIPs'">
                <div class="space-y-1">
                  @for (ip of item.allowedIPs; track $index) {
                    <div class="text-sm font-mono bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded text-gray-900 dark:text-gray-100">
                      {{ ip }}
                    </div>
                  }
                </div>
              </span>

              <!-- Data Usage -->
              <span *ngSwitchCase="'dataUsage'">
                <div class="text-sm text-gray-900 dark:text-gray-100">
                  <div>↑ {{ formatBytes(item.dataSent) }}</div>
                  <div>↓ {{ formatBytes(item.dataReceived) }}</div>
                </div>
              </span>

              <!-- Last Handshake -->
              <span *ngSwitchCase="'lastHandshake'">
                @if (item.lastHandshake) {
                  <span class="text-sm text-gray-900 dark:text-gray-100">
                    {{ item.lastHandshake | date:'short' }}
                  </span>
                }
                @if (!item.lastHandshake) {
                  <span class="text-sm text-gray-500 dark:text-gray-400 italic">
                    Never
                  </span>
                }
              </span>
            </ng-container>
          </ng-template>
        </app-data-table>
      }

      <!-- Config preview: same body as download; full .conf including PrivateKey -->
      <app-modal
        [isOpen]="configPreviewModalOpen"
        [title]="configPreviewTitle"
        size="xl"
        [hasFooter]="true"
        [hasCustomFooter]="true"
        [showCloseButton]="true"
        cancelLabel="Close"
        [showCancelButton]="false"
        [showConfirmButton]="false"
        (closeModal)="closeConfigPreviewModal()"
      >
        @if (configPreviewLoading) {
          <div class="flex justify-center py-12">
            <app-loading-spinner [showText]="true" loadingText="Loading preview..." />
          </div>
        } @else if (configPreviewError) {
          <app-alert type="error" title="Preview failed" [message]="configPreviewError" />
        } @else if (configPreview) {
          <div class="space-y-4">
            <pre
              class="max-h-[min(28rem,60vh)] overflow-auto whitespace-pre-wrap break-words rounded-md border border-gray-200 bg-gray-50 p-4 font-mono text-xs text-gray-900 dark:border-gray-700 dark:bg-gray-950 dark:text-gray-100 sm:text-sm"
            >{{ configPreview.content }}</pre>

            <label class="flex cursor-pointer items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
              <input
                type="checkbox"
                [checked]="configPreviewAllowAllTraffic"
                (change)="onConfigPreviewAllowAllChange($any($event.target).checked)"
                class="rounded border-gray-300 text-blue-600 focus:ring-blue-500 dark:border-gray-600"
              />
              Route all traffic through VPN (AllowAllTraffic)
            </label>

            <div class="space-y-1 text-xs text-gray-500 dark:text-gray-400">
              <div>Server: {{ configPreview.metadata.serverName }}</div>
              <div class="truncate font-mono" title="{{ configPreview.metadata.configHash }}">
                Hash: {{ configPreview.metadata.configHash }}
              </div>
            </div>
          </div>
        }

        <div slot="footer" class="flex w-full flex-wrap items-center justify-end gap-2">
          <button
            type="button"
            (click)="copyConfigPreviewToClipboard()"
            [disabled]="!configPreview || configPreviewLoading"
            class="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700"
          >
            Copy preview
          </button>
          <button
            type="button"
            (click)="downloadFullConfigFromPreview()"
            [disabled]="!previewClientId || configPreviewLoading"
            class="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
          >
            Download full config
          </button>
          <button
            type="button"
            (click)="closeConfigPreviewModal()"
            class="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700"
          >
            Close
          </button>
        </div>
      </app-modal>

      <!-- Remove client confirmation -->
      <app-modal
        [isOpen]="removeClientModalOpen"
        title="Remove client"
        size="md"
        [hasFooter]="true"
        [hasCustomFooter]="false"
        [showCloseButton]="true"
        cancelLabel="Cancel"
        confirmLabel="Remove"
        [showCancelButton]="true"
        [showConfirmButton]="true"
        [confirmVariant]="'danger'"
        (closeModal)="closeRemoveClientModal()"
        (cancelAction)="closeRemoveClientModal()"
        (confirmAction)="confirmRemoveClient()"
      >
        <p class="text-sm text-gray-600 dark:text-gray-300">
          Are you sure you want to remove client
          <strong class="font-medium text-gray-900 dark:text-gray-100">{{ clientPendingRemoval?.name }}</strong>?
          This action cannot be undone.
        </p>
      </app-modal>
    </div>
  `
})
export class ServerDetailComponent implements OnInit, OnDestroy {
  server?: ServerDetailResponse;
  serverId?: string;
  deploymentLabel = '—';
  loadingState: LoadingState = { isLoading: false };
  clientsLoading = false;
  successMessage = '';

  removeClientModalOpen = false;
  clientPendingRemoval: ClientResponse | null = null;

  /** In-flight retry-deploy requests (Ansible client deploy / removal cleanup). */
  retryingClientIds = new Set<string>();

  /** WireGuard config preview modal (same behavior as client-list) */
  configPreviewModalOpen = false;
  configPreviewLoading = false;
  configPreviewError: string | null = null;
  configPreview: ConfigurationPreview | null = null;
  previewClientId: string | null = null;
  configPreviewAllowAllTraffic = false;

  private destroy$ = new Subject<void>();

  get configPreviewTitle(): string {
    return this.configPreview?.fileName
      ? `Config preview — ${this.configPreview.fileName}`
      : 'Config preview';
  }

  // Client table configuration
  clientColumns: TableColumn[] = [
    { key: 'name', label: 'Client Name', sortable: true, type: 'text' },
    { key: 'status', label: 'Status', type: 'boolean' },
    { key: 'allowedIPs', label: 'Allowed IPs', type: 'text' },
    { key: 'dataUsage', label: 'Data Usage', type: 'text' },
    { key: 'lastHandshake', label: 'Last Handshake', sortable: true, type: 'date' },
    { key: 'actions', label: 'Actions', type: 'action', width: '200px' }
  ];

  clientsPrimaryAction = {
    label: 'Add Client',
    icon: 'M12 6v6m0 0v6m0-6h6m-6 0H6'
  };

  clientRowActions: TableAction[] = [
    {
      label: 'Edit',
      icon: 'M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z',
      action: 'edit',
      variant: 'secondary'
    },
    {
      label: 'Download Config',
      icon: 'M12 10v6m0 0l-3-3m3 3l3-3M3 17V7a2 2 0 012-2h6l2 2h6a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2z',
      action: 'download',
      variant: 'primary'
    },
    {
      label: 'Preview Config',
      icon: 'M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
      action: 'preview',
      variant: 'secondary'
    },
    {
      label: 'Remove',
      icon: 'M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16',
      action: 'remove',
      variant: 'danger'
    }
  ];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private wireguardService: WireguardService,
    private ansible: AnsibleService
  ) {}

  ngOnInit(): void {
    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      this.serverId = params['id'];
      if (this.serverId) {
        this.loadServerDetails();
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

  loadServerDetails(): void {
    if (!this.serverId) return;

    this.wireguardService.getServerWithClients(this.serverId).subscribe({
      next: (server) => {
        this.server = server;
        this.refreshDeploymentLabel(server);
      },
      error: (error) => {
        console.error('Error loading server details:', error);
      }
    });
  }

  private refreshDeploymentLabel(server: ServerDetailResponse): void {
    if (!server.hostId) {
      this.deploymentLabel = 'This control plane — local WireGuard';
      return;
    }
    this.deploymentLabel = 'Loading…';
    this.ansible.listHosts(false).subscribe({
      next: hosts => {
        const h = hosts.find(x => x.id === server.hostId);
        this.deploymentLabel = h
          ? `Ansible — ${h.hostname} (${h.ipAddress})`
          : `Ansible host (${server.hostId})`;
      },
      error: () => {
        this.deploymentLabel = `Ansible host (${server.hostId})`;
      }
    });
  }

  editServer(): void {
    if (this.serverId) {
      this.router.navigate(['/servers', this.serverId, 'edit']);
    }
  }

  addClient(): void {
    if (this.serverId) {
      this.router.navigate(['/servers', this.serverId, 'clients', 'new']);
    }
  }

  onClientAction(event: { action: string; item: ClientResponse }): void {
    const { action, item } = event;

    switch (action) {
      case 'edit':
        if (this.serverId) {
          this.router.navigate(['/servers', this.serverId, 'clients', item.id, 'edit']);
        }
        break;
      case 'download':
        this.downloadClientConfig(item.id);
        break;
      case 'preview':
        this.openConfigPreview(item.id);
        break;
      case 'remove':
        this.removeClient(item);
        break;
    }
  }

  downloadClientConfig(clientId: string, allowAllTraffic = false): void {
    this.wireguardService.downloadClientConfig(clientId, allowAllTraffic).subscribe({
      next: ({ content, fileName }) => {
        const blob = new Blob([content], { type: 'text/plain' });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        link.click();
        window.URL.revokeObjectURL(url);

        const trafficType = allowAllTraffic ? ' (all traffic)' : '';
        this.successMessage = `Configuration downloaded successfully${trafficType}`;
      },
      error: (error) => {
        console.error('Error downloading client config:', error);
      }
    });
  }

  openConfigPreview(clientId: string): void {
    this.previewClientId = clientId;
    this.configPreviewAllowAllTraffic = false;
    this.configPreviewModalOpen = true;
    this.configPreviewError = null;
    this.configPreview = null;
    this.loadConfigPreview();
  }

  closeConfigPreviewModal(): void {
    this.configPreviewModalOpen = false;
    this.previewClientId = null;
    this.configPreview = null;
    this.configPreviewError = null;
    this.configPreviewLoading = false;
  }

  loadConfigPreview(): void {
    if (!this.previewClientId) return;
    this.configPreviewLoading = true;
    this.configPreviewError = null;
    this.wireguardService
      .getClientConfigurationPreview(this.previewClientId, this.configPreviewAllowAllTraffic)
      .subscribe({
        next: preview => {
          this.configPreview = preview;
          this.configPreviewLoading = false;
        },
        error: (error: HttpErrorResponse) => {
          this.configPreviewLoading = false;
          this.configPreviewError = this.previewHttpErrorMessage(error);
        }
      });
  }

  onConfigPreviewAllowAllChange(checked: boolean): void {
    this.configPreviewAllowAllTraffic = checked;
    if (this.configPreviewModalOpen && this.previewClientId) {
      this.loadConfigPreview();
    }
  }

  copyConfigPreviewToClipboard(): void {
    if (!this.configPreview) return;
    void navigator.clipboard.writeText(this.configPreview.content).then(
      () => {
        this.successMessage = 'Preview copied to clipboard.';
      },
      () => {
        this.successMessage = '';
      }
    );
  }

  downloadFullConfigFromPreview(): void {
    if (!this.previewClientId) return;
    this.downloadClientConfig(this.previewClientId, this.configPreviewAllowAllTraffic);
  }

  private previewHttpErrorMessage(error: HttpErrorResponse): string {
    const body = error.error;
    if (typeof body === 'string' && body.trim()) {
      return body.length > 200 ? `${body.slice(0, 200)}…` : body;
    }
    if (body && typeof body === 'object' && 'message' in body) {
      const m = (body as { message?: string }).message;
      if (typeof m === 'string' && m.trim()) return m;
    }
    return error.message || 'Failed to load configuration preview';
  }

  removeClient(client: ClientResponse): void {
    if (!this.serverId) return;
    this.clientPendingRemoval = client;
    this.removeClientModalOpen = true;
  }

  closeRemoveClientModal(): void {
    this.removeClientModalOpen = false;
    this.clientPendingRemoval = null;
  }

  retryClientDeployment(client: ClientResponse): void {
    if (!this.serverId || this.retryingClientIds.has(client.id)) return;

    this.retryingClientIds.add(client.id);
    this.clearError();

    this.wireguardService.retryClientDeployment(this.serverId, client.id).subscribe({
      next: (updatedClient) => {
        this.retryingClientIds.delete(client.id);
        if (updatedClient) {
          this.successMessage = `Deployment retry succeeded for "${client.name}"`;
        } else {
          this.successMessage = `Cleanup completed — client "${client.name}" removed`;
        }
        this.loadServerDetails();
      },
      error: (error) => {
        this.retryingClientIds.delete(client.id);
        this.loadingState = {
          isLoading: false,
          error: `Retry failed for "${client.name}": ${this.wireguardService.getApiErrorMessage(error)}`
        };
      }
    });
  }

  confirmRemoveClient(): void {
    const client = this.clientPendingRemoval;
    if (!client || !this.serverId) {
      this.closeRemoveClientModal();
      return;
    }

    this.clientsLoading = true;
    this.wireguardService.removeClientFromServer(this.serverId, client.id).subscribe({
      next: () => {
        this.successMessage = `Client "${client.name}" removed successfully`;
        this.closeRemoveClientModal();
        this.loadServerDetails();
        this.clientsLoading = false;
      },
      error: (error) => {
        console.error('Error removing client:', error);
        this.clientsLoading = false;
        this.closeRemoveClientModal();
      }
    });
  }

  clearError(): void {
    this.loadingState = { isLoading: false };
  }

  clearSuccessMessage(): void {
    this.successMessage = '';
  }

  getServerStatusVariant(enabled: boolean): BadgeVariant {
    return enabled ? 'success' : 'gray';
  }

  getClientStatusVariant(enabled: boolean, isOnline: boolean): BadgeVariant {
    if (!enabled) return 'gray';
    return isOnline ? 'success' : 'warning';
  }

  getClientStatusLabel(enabled: boolean, isOnline: boolean): string {
    if (!enabled) return 'Disabled';
    return isOnline ? 'Online' : 'Offline';
  }

  getNetworkAddress(server: ServerDetailResponse): string {
    return typeof server.networkAddress === 'string'
      ? server.networkAddress
      : server.networkAddress?.address || 'N/A';
  }

  getDnsAddress(dns: any): string {
    return typeof dns === 'string' ? dns : dns?.address || 'N/A';
  }

  getClientsSubtitle(): string {
    if (!this.server) return '';
    const total = this.server.clients?.length || 0;
    const online = this.server.clients?.filter(c => c.enabled && c.isOnline).length || 0;
    return `${total} total clients, ${online} online`;
  }

  formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }
}