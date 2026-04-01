import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';

import { WireguardService } from '../../../services/wireguard.service';
import { DataTableComponent, TableAction } from '../../../shared/components/data-table/data-table.component';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { AlertComponent } from '../../../shared/components/alert/alert.component';
import { StatusBadgeComponent, BadgeVariant } from '../../../shared/components/status-badge/status-badge.component';
import {
  ServerDetailResponse,
  ClientResponse,
  TableColumn,
  LoadingState
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
    StatusBadgeComponent
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
                  <dt class="text-xs text-gray-500 dark:text-gray-400">Endpoint</dt>
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
              <!-- Client Status -->
              <span *ngSwitchCase="'status'">
                <div class="flex items-center gap-2">
                  <app-status-badge
                    [variant]="getClientStatusVariant(item.enabled, item.isOnline)"
                    [label]="getClientStatusLabel(item.enabled, item.isOnline)"
                  />
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
    </div>
  `
})
export class ServerDetailComponent implements OnInit, OnDestroy {
  server?: ServerDetailResponse;
  serverId?: string;
  loadingState: LoadingState = { isLoading: false };
  clientsLoading = false;
  successMessage = '';

  private destroy$ = new Subject<void>();

  // Client table configuration
  clientColumns: TableColumn[] = [
    { key: 'name', label: 'Client Name', sortable: true, type: 'text' },
    { key: 'status', label: 'Status', type: 'boolean' },
    { key: 'allowedIPs', label: 'Allowed IPs', type: 'text' },
    { key: 'dataUsage', label: 'Data Usage', type: 'text' },
    { key: 'lastHandshake', label: 'Last Handshake', sortable: true, type: 'date' },
    { key: 'actions', label: 'Actions', type: 'action', width: '120px' }
  ];

  clientsPrimaryAction = {
    label: 'Add Client',
    icon: 'M12 6v6m0 0v6m0-6h6m-6 0H6'
  };

  clientRowActions: TableAction[] = [
    {
      label: 'Download Config',
      icon: 'M12 10v6m0 0l-3-3m3 3l3-3M3 17V7a2 2 0 012-2h6l2 2h6a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2z',
      action: 'download',
      variant: 'primary'
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
      },
      error: (error) => {
        console.error('Error loading server details:', error);
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
      case 'download':
        this.downloadClientConfig(item.id);
        break;
      case 'info':
        this.viewClientInfo(item.id);
        break;
      case 'remove':
        this.removeClient(item);
        break;
    }
  }

  downloadClientConfig(clientId: string): void {
    this.wireguardService.downloadClientConfig(clientId, false).subscribe({
      next: ({ content, fileName }) => {
        const blob = new Blob([content], { type: 'text/plain' });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        link.click();
        window.URL.revokeObjectURL(url);

        this.successMessage = 'Configuration downloaded successfully';
      },
      error: (error) => {
        console.error('Error downloading client config:', error);
      }
    });
  }

  viewClientInfo(clientId: string): void {
    this.wireguardService.getClientInfo(clientId).subscribe({
      next: (clientInfo) => {
        // For now, just log the info - could open a modal in a full implementation
        console.log('Client Info:', clientInfo);
        alert(`Client Info:\nName: ${clientInfo.name}\nPublic Key: ${clientInfo.publicKey}\nAllowed IPs: ${clientInfo.allowedIPs}\nEnabled: ${clientInfo.enabled}\nOnline: ${clientInfo.isOnline}`);
      },
      error: (error) => {
        console.error('Error getting client info:', error);
      }
    });
  }

  removeClient(client: ClientResponse): void {
    if (!this.serverId) return;

    if (confirm(`Are you sure you want to remove client "${client.name}"? This action cannot be undone.`)) {
      this.clientsLoading = true;
      this.wireguardService.removeClientFromServer(this.serverId, client.id).subscribe({
        next: () => {
          this.successMessage = `Client "${client.name}" removed successfully`;
          this.loadServerDetails(); // Refresh the data
          this.clientsLoading = false;
        },
        error: (error) => {
          console.error('Error removing client:', error);
          this.clientsLoading = false;
        }
      });
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