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
  ClientResponse,
  ServerDetailResponse,
  TableColumn,
  LoadingState
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
    StatusBadgeComponent
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

  private destroy$ = new Subject<void>();

  // Table configuration
  tableColumns: TableColumn[] = [
    { key: 'name', label: 'Client Name', sortable: true, type: 'text' },
    { key: 'status', label: 'Status', type: 'boolean' },
    { key: 'publicKey', label: 'Public Key', type: 'text' },
    { key: 'allowedIPs', label: 'Allowed IPs', type: 'text' },
    { key: 'dataUsage', label: 'Data Usage', type: 'text' },
    { key: 'lastHandshake', label: 'Last Handshake', sortable: true, type: 'date' },
    { key: 'keepalive', label: 'Keepalive', type: 'text' },
    { key: 'actions', label: 'Actions', type: 'action', width: '140px' }
  ];

  primaryAction = {
    label: 'Add Client',
    icon: 'M12 6v6m0 0v6m0-6h6m-6 0H6'
  };

  rowActions: TableAction[] = [
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

  downloadClientConfig(clientId: string, allowAllTraffic: boolean): void {
    this.wireguardService.downloadClientConfig(clientId, allowAllTraffic).subscribe({
      next: (configContent) => {
        // Create download link
        const blob = new Blob([configContent], { type: 'text/plain' });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `client-${clientId}${allowAllTraffic ? '-all-traffic' : ''}.conf`;
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
}