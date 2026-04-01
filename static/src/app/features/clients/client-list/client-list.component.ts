import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';

import { WireguardService } from '../../../services/wireguard.service';
import { DataTableComponent, TableAction } from '../../../shared/components/data-table/data-table.component';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { AlertComponent } from '../../../shared/components/alert/alert.component';
import { StatusBadgeComponent, BadgeVariant } from '../../../shared/components/status-badge/status-badge.component';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import {
  ClientResponse,
  ServerDetailResponse,
  TableColumn,
  LoadingState,
  ConfigurationPreview
} from '../../../models/wireguard.interface';

@Component({
  selector: 'app-client-list',
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
          loadingText="Loading server and clients..."
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

      <!-- Success Alert -->
      @if (successMessage) {
        <app-alert
          type="success"
          [message]="successMessage"
          (dismissed)="clearSuccessMessage()"
        />
      }

      <!-- Server Info -->
      @if (server) {
      <div class="bg-white dark:bg-gray-900 shadow-sm rounded-lg border border-gray-200 dark:border-gray-700 p-6">
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100">{{ server.name }}</h2>
            <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">{{ server.endpoint }}:{{ server.listenPort }}</p>
          </div>
          <div class="flex items-center gap-3">
            <app-status-badge
              [variant]="getServerStatusVariant(server.enabled)"
              [label]="server.enabled ? 'Active' : 'Inactive'"
            />
            <button
              (click)="viewServer()"
              class="text-sm text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300"
            >
              View Server Details →
            </button>
          </div>
        </div>
      </div>
      }

      <!-- Clients Table -->
      <app-data-table
        title="Connected Clients"
        [subtitle]="getClientsSubtitle()"
        [columns]="tableColumns"
        [data]="clients"
        [loading]="clientsLoading"
        [primaryAction]="primaryAction"
        [rowActions]="rowActions"
        [searchable]="true"
        [searchQuery]="searchQuery"
        emptyMessage="No clients connected"
        emptySubMessage="Add clients to this server to get started"
        (searchChange)="onSearchChange($event)"
        (primaryActionClick)="addClient()"
        (rowActionClick)="onRowAction($event)"
      >
        <!-- Custom templates for special columns -->
        <ng-template #customTemplate let-item="$implicit" let-column="column">
          <ng-container [ngSwitch]="column.key">
            <!-- Client Status -->
            <span *ngSwitchCase="'status'">
              <app-status-badge
                [variant]="getClientStatusVariant(item.enabled, item.isOnline)"
                [label]="getClientStatusLabel(item.enabled, item.isOnline)"
              />
            </span>

            <!-- Public Key -->
            <span *ngSwitchCase="'publicKey'">
              <div class="font-mono text-xs bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100 px-2 py-1 rounded max-w-xs truncate"
                   [title]="item.publicKey">
                {{ item.publicKey }}
              </div>
            </span>

            <!-- Allowed IPs -->
            <span *ngSwitchCase="'allowedIPs'">
              <div class="space-y-1">
                @for (ip of item.allowedIPs; track $index) {
                  <div class="text-sm font-mono bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100 px-2 py-1 rounded">
                    {{ ip }}
                  </div>
                }
                @if (item.allowedIPs.length === 0) {
                  <div class="text-sm text-gray-500 dark:text-gray-400 italic">
                    No IPs configured
                  </div>
                }
              </div>
            </span>

            <!-- Data Usage -->
            <span *ngSwitchCase="'dataUsage'">
              <div class="text-sm space-y-1 text-gray-900 dark:text-gray-100">
                <div class="flex items-center gap-1">
                  <span class="text-green-600 dark:text-green-400">↑</span>
                  <span>{{ formatBytes(item.dataSent) }}</span>
                </div>
                <div class="flex items-center gap-1">
                  <span class="text-blue-600 dark:text-blue-400">↓</span>
                  <span>{{ formatBytes(item.dataReceived) }}</span>
                </div>
              </div>
            </span>

            <!-- Last Handshake -->
            <span *ngSwitchCase="'lastHandshake'">
              @if (item.lastHandshake) {
                <div class="text-sm text-gray-900 dark:text-gray-100">
                  <div>{{ item.lastHandshake | date:'short' }}</div>
                  <div class="text-xs text-gray-500 dark:text-gray-400">{{ getTimeAgo(item.lastHandshake) }}</div>
                </div>
              }
              @if (!item.lastHandshake) {
                <span class="text-sm text-gray-500 dark:text-gray-400 italic">
                  Never connected
                </span>
              }
            </span>

            <!-- Keepalive -->
            <span *ngSwitchCase="'keepalive'">
              @if (item.persistentKeepalive > 0) {
                <span class="text-sm text-gray-900 dark:text-gray-100">
                  {{ item.persistentKeepalive }}s
                </span>
              }
              @if (item.persistentKeepalive === 0) {
                <span class="text-sm text-gray-500 dark:text-gray-400">
                  Disabled
                </span>
              }
            </span>
          </ng-container>
        </ng-template>
      </app-data-table>

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

        <div slot="footer" class="flex flex-wrap items-center justify-end gap-2 w-full">
          <button
            type="button"
            (click)="copyConfigPreviewToClipboard()"
            [disabled]="!configPreview || configPreviewLoading"
            class="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-md hover:bg-gray-50 dark:hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
          >
            Copy preview
          </button>
          <button
            type="button"
            (click)="downloadFullConfigFromPreview()"
            [disabled]="!previewClientId || configPreviewLoading"
            class="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
          >
            Download full config
          </button>
          <button
            type="button"
            (click)="closeConfigPreviewModal()"
            class="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-md hover:bg-gray-50 dark:hover:bg-gray-700"
          >
            Close
          </button>
        </div>
      </app-modal>
    </div>
  `
})
export class ClientListComponent implements OnInit, OnDestroy {
  server?: ServerDetailResponse;
  clients: ClientResponse[] = [];
  serverId?: string;
  loadingState: LoadingState = { isLoading: false };
  clientsLoading = false;
  searchQuery = '';
  successMessage = '';

  /** WireGuard config preview modal (GET …/preview) */
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

  // Table configuration
  tableColumns: TableColumn[] = [
    { key: 'name', label: 'Client Name', sortable: true, type: 'text' },
    { key: 'status', label: 'Status', type: 'boolean' },
    { key: 'publicKey', label: 'Public Key', type: 'text' },
    { key: 'allowedIPs', label: 'Allowed IPs', type: 'text' },
    { key: 'dataUsage', label: 'Data Usage', type: 'text' },
    { key: 'lastHandshake', label: 'Last Handshake', sortable: true, type: 'date' },
    { key: 'keepalive', label: 'Keepalive', type: 'text' },
    { key: 'actions', label: 'Actions', type: 'action', width: '260px' }
  ];

  primaryAction = {
    label: 'Add Client',
    icon: 'M12 6v6m0 0v6m0-6h6m-6 0H6'
  };

  rowActions: TableAction[] = [
    {
      label: 'Edit',
      icon: 'M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z',
      action: 'edit',
      variant: 'secondary'
    },
    {
      label: 'Preview Config',
      icon: 'M15 12a3 3 0 11-6 0 3 3 0 016 0z M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z',
      action: 'preview',
      variant: 'secondary'
    },
    {
      label: 'Download Config',
      icon: 'M12 10v6m0 0l-3-3m3 3l3-3M3 17V7a2 2 0 012-2h6l2 2h6a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2z',
      action: 'download',
      variant: 'primary'
    },
    {
      label: 'Download (All Traffic)',
      icon: 'M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10',
      action: 'download-all',
      variant: 'secondary'
    },
    {
      label: 'View Info',
      icon: 'M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
      action: 'info',
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
    private wireguardService: WireguardService
  ) {}

  ngOnInit(): void {
    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      this.serverId = params['serverId'];
      if (this.serverId) {
        this.loadServerAndClients();
      }
    });

    // Subscribe to loading states
    this.wireguardService.serversLoading$
      .pipe(takeUntil(this.destroy$))
      .subscribe(loading => {
        this.loadingState = loading;
      });

    this.wireguardService.clientsLoading$
      .pipe(takeUntil(this.destroy$))
      .subscribe(loading => {
        this.clientsLoading = loading.isLoading;
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadServerAndClients(): void {
    if (!this.serverId) return;

    this.wireguardService.getServerWithClients(this.serverId).subscribe({
      next: (server) => {
        this.server = server;
        this.clients = this.filterClients(server.clients || []);
      },
      error: (error) => {
        console.error('Error loading server and clients:', error);
      }
    });
  }

  onSearchChange(query: string): void {
    this.searchQuery = query;
    if (this.server) {
      this.clients = this.filterClients(this.server.clients || []);
    }
  }

  onRowAction(event: { action: string; item: ClientResponse }): void {
    const { action, item } = event;

    switch (action) {
      case 'edit':
        if (this.serverId) {
          this.router.navigate(['/servers', this.serverId, 'clients', item.id, 'edit']);
        }
        break;
      case 'preview':
        this.openConfigPreview(item.id);
        break;
      case 'download':
        this.downloadClientConfig(item.id, false);
        break;
      case 'download-all':
        this.downloadClientConfig(item.id, true);
        break;
      case 'info':
        this.viewClientInfo(item.id);
        break;
      case 'remove':
        this.removeClient(item);
        break;
    }
  }

  addClient(): void {
    if (this.serverId) {
      this.router.navigate(['/servers', this.serverId, 'clients', 'new']);
    }
  }

  viewServer(): void {
    if (this.serverId) {
      this.router.navigate(['/servers', this.serverId]);
    }
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
        next: (preview) => {
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
        this.successMessage =
          'Preview copied to clipboard.';
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

  downloadClientConfig(clientId: string, allowAllTraffic: boolean): void {
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

  viewClientInfo(clientId: string): void {
    this.wireguardService.getClientInfo(clientId).subscribe({
      next: (clientInfo) => {
        // Create a nice info display - in a real app this could be a modal
        const info = `
Client Information:
━━━━━━━━━━━━━━━━━━
Name: ${clientInfo.name}
Public Key: ${clientInfo.publicKey}
Allowed IPs: ${clientInfo.allowedIPs}
Status: ${clientInfo.enabled ? 'Enabled' : 'Disabled'}
Connection: ${clientInfo.isOnline ? 'Online' : 'Offline'}

Server: ${clientInfo.server.name}
Endpoint: ${clientInfo.server.endpoint}
        `.trim();

        alert(info);
      },
      error: (error) => {
        console.error('Error getting client info:', error);
      }
    });
  }

  removeClient(client: ClientResponse): void {
    if (!this.serverId) return;

    const confirmMessage = `Are you sure you want to remove client "${client.name}"?\n\nThis will:\n• Disconnect the client immediately\n• Remove all configuration\n• Cannot be undone\n\nType the client name to confirm: ${client.name}`;
    const userInput = prompt(confirmMessage);

    if (userInput === client.name) {
      this.wireguardService.removeClientFromServer(this.serverId, client.id).subscribe({
        next: () => {
          this.successMessage = `Client "${client.name}" removed successfully`;
          this.loadServerAndClients(); // Refresh the data
        },
        error: (error) => {
          console.error('Error removing client:', error);
        }
      });
    } else if (userInput !== null) {
      alert('Client name did not match. Removal cancelled.');
    }
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

  getClientsSubtitle(): string {
    if (!this.server) return '';
    const total = this.clients.length;
    const online = this.clients.filter(c => c.enabled && c.isOnline).length;
    return `${total} clients, ${online} online`;
  }

  formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  getTimeAgo(dateString: string): string {
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;

    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}h ago`;

    const diffDays = Math.floor(diffHours / 24);
    return `${diffDays}d ago`;
  }

  private filterClients(clients: ClientResponse[]): ClientResponse[] {
    if (!this.searchQuery.trim()) {
      return clients;
    }

    const query = this.searchQuery.toLowerCase();
    return clients.filter(client =>
      client.name.toLowerCase().includes(query) ||
      client.publicKey.toLowerCase().includes(query) ||
      client.allowedIPs.some(ip => ip.toLowerCase().includes(query))
    );
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
}