import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';

import { WireguardService } from '../../../services/wireguard.service';
import { DataTableComponent, TableAction } from '../../../shared/components/data-table/data-table.component';
import { AlertComponent } from '../../../shared/components/alert/alert.component';
import { StatusBadgeComponent, BadgeVariant } from '../../../shared/components/status-badge/status-badge.component';
import {
  ServerResponse,
  TableColumn,
  LoadingState
} from '../../../models/wireguard.interface';

@Component({
  selector: 'app-server-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    DataTableComponent,
    AlertComponent,
    StatusBadgeComponent
  ],
  template: `
    <div class="space-y-6">
      <!-- Error Alert -->
      @if (loadingState.error) {
        <app-alert
          type="error"
          title="Error loading servers"
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

      <!-- Server Table -->
      <app-data-table
        title="WireGuard Servers"
        subtitle="Manage your WireGuard server configurations"
        [columns]="tableColumns"
        [data]="servers"
        [loading]="loadingState.isLoading"
        [primaryAction]="primaryAction"
        [rowActions]="rowActions"
        [searchable]="true"
        [searchQuery]="searchQuery"
        emptyMessage="No servers found"
        emptySubMessage="Create your first server to get started"
        (searchChange)="onSearchChange($event)"
        (primaryActionClick)="createServer()"
        (rowActionClick)="onRowAction($event)"
      >
        <!-- Custom templates for special columns -->
        <ng-template #customTemplate let-item="$implicit" let-column="column">
          <ng-container [ngSwitch]="column.key">
            <!-- Server Status -->
            <span *ngSwitchCase="'enabled'">
              <app-status-badge
                [variant]="getServerStatusVariant(item.enabled)"
                [label]="item.enabled ? 'Active' : 'Inactive'"
              />
            </span>

            <!-- Client Count -->
            <span *ngSwitchCase="'clientCount'">
              <div class="flex items-center gap-2">
                <span class="font-medium text-gray-900 dark:text-gray-100">{{ item.totalClients }}</span>
                <span class="text-gray-500 dark:text-gray-400">total</span>
                @if (item.activeClients !== undefined) {
                  <span class="text-green-600 dark:text-green-400 text-sm">
                    ({{ item.activeClients }} online)
                  </span>
                }
              </div>
            </span>

            <!-- Network Address -->
            <span *ngSwitchCase="'networkAddress'">
              <code class="text-sm bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100 px-2 py-1 rounded">
                {{ getNetworkAddress(item) }}
              </code>
            </span>

            <!-- Endpoint -->
            <span *ngSwitchCase="'endpoint'">
              <div class="font-mono text-sm text-gray-900 dark:text-gray-100">
                {{ item.endpoint }}:{{ item.listenPort }}
              </div>
            </span>

            <!-- Interface up/down: Stop — toggle — Start (uses API isOnline) -->
            <span *ngSwitchCase="'interfaceUp'" class="inline-flex items-center gap-2">
              <span class="text-[10px] font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">Stop</span>
              <button
                type="button"
                role="switch"
                [attr.aria-checked]="item.isOnline"
                [disabled]="isServerPowerBusy(item.id) || (!item.enabled && !item.isOnline)"
                [title]="getInterfaceToggleTitle(item)"
                (click)="onServerInterfaceToggle(item)"
                class="relative inline-flex h-7 w-12 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900 disabled:cursor-not-allowed disabled:opacity-50"
                [class.bg-blue-600]="item.isOnline"
                [class.bg-gray-300]="!item.isOnline"
                [class.dark:bg-gray-600]="!item.isOnline"
              >
                <span
                  class="pointer-events-none inline-block h-6 w-6 rounded-full bg-white shadow transition-transform"
                  [class.translate-x-0.5]="!item.isOnline"
                  [class.translate-x-5]="item.isOnline"
                ></span>
              </button>
              <span class="text-[10px] font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">Start</span>
            </span>
          </ng-container>
        </ng-template>
      </app-data-table>
    </div>
  `
})
export class ServerListComponent implements OnInit, OnDestroy {
  servers: ServerResponse[] = [];
  loadingState: LoadingState = { isLoading: false };
  searchQuery = '';
  successMessage = '';
  /** Server IDs with interface start/stop request in flight */
  private readonly serverPowerLoadingIds = new Set<string>();

  private destroy$ = new Subject<void>();

  // Table configuration
  tableColumns: TableColumn[] = [
    { key: 'name', label: 'Name', sortable: true, type: 'text' },
    { key: 'enabled', label: 'Status', sortable: true, type: 'boolean' },
    // Omit type so cells use the custom template (type 'text' bypasses customTemplate and stringifies objects).
    { key: 'endpoint', label: 'Endpoint' },
    { key: 'networkAddress', label: 'Network' },
    { key: 'clientCount', label: 'Clients', sortable: true, type: 'number' },
    { key: 'createdAt', label: 'Created', sortable: true, type: 'date' },
    { key: 'interfaceUp', label: 'Interface' },
    { key: 'actions', label: 'Actions', type: 'action', width: '120px' }
  ];

  primaryAction = {
    label: 'Create Server',
    icon: 'M12 6v6m0 0v6m0-6h6m-6 0H6'
  };

  rowActions: TableAction[] = [
    {
      label: 'View',
      icon: 'M15 12a3 3 0 11-6 0 3 3 0 016 0z M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z',
      action: 'view',
      variant: 'primary'
    },
    {
      label: 'Edit',
      icon: 'M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z',
      action: 'edit',
      variant: 'secondary'
    },
    {
      label: 'Delete',
      icon: 'M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16',
      action: 'delete',
      variant: 'danger'
    }
  ];

  constructor(
    private wireguardService: WireguardService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadServers();

    // Subscribe to loading state
    this.wireguardService.serversLoading$
      .pipe(takeUntil(this.destroy$))
      .subscribe(loading => {
        this.loadingState = loading;
      });

    // Subscribe to servers data
    this.wireguardService.servers$
      .pipe(takeUntil(this.destroy$))
      .subscribe(servers => {
        this.servers = this.filterServers(servers);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadServers(): void {
    this.wireguardService.getAllServers().subscribe({
      next: () => {
        // Data is handled by the service's BehaviorSubject
      },
      error: (error) => {
        console.error('Error loading servers:', error);
      }
    });
  }

  onSearchChange(query: string): void {
    this.searchQuery = query;
    this.servers = this.filterServers(this.servers);
  }

  onRowAction(event: { action: string; item: ServerResponse }): void {
    const { action, item } = event;

    switch (action) {
      case 'view':
        this.viewServer(item.id);
        break;
      case 'edit':
        this.editServer(item.id);
        break;
      case 'delete':
        this.deleteServer(item);
        break;
    }
  }

  createServer(): void {
    this.router.navigate(['/servers/new']);
  }

  viewServer(serverId: string): void {
    this.router.navigate(['/servers', serverId]);
  }

  editServer(serverId: string): void {
    this.router.navigate(['/servers', serverId, 'edit']);
  }

  isServerPowerBusy(serverId: string): boolean {
    return this.serverPowerLoadingIds.has(serverId);
  }

  getInterfaceToggleTitle(server: ServerResponse): string {
    if (!server.enabled && !server.isOnline) {
      return 'Enable the server before starting the interface';
    }
    return server.isOnline ? 'Interface running — click to stop' : 'Interface down — click to start';
  }

  onServerInterfaceToggle(server: ServerResponse): void {
    if (this.isServerPowerBusy(server.id)) return;
    const wantOn = !server.isOnline;
    if (wantOn && !server.enabled) {
      return;
    }

    this.serverPowerLoadingIds.add(server.id);
    const req$ = wantOn
      ? this.wireguardService.launchServer(server.id)
      : this.wireguardService.stopServer(server.id);

    req$.subscribe({
      next: () => {
        this.serverPowerLoadingIds.delete(server.id);
        this.successMessage = wantOn ? 'Interface started' : 'Interface stopped';
        this.loadServers();
      },
      error: (error) => {
        console.error('Error changing interface state:', error);
        this.serverPowerLoadingIds.delete(server.id);
        this.loadServers();
      }
    });
  }

  deleteServer(server: ServerResponse): void {
    if (confirm(`Are you sure you want to delete server "${server.name}"? This action cannot be undone.`)) {
      // Note: The backend doesn't seem to have a delete server endpoint
      // This would need to be implemented on the backend
      console.log('Delete server not implemented in backend');
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

  getNetworkAddress(server: ServerResponse): string {
    return typeof server.networkAddress === 'string'
      ? server.networkAddress
      : server.networkAddress?.address || 'N/A';
  }

  private filterServers(servers: ServerResponse[]): ServerResponse[] {
    if (!this.searchQuery.trim()) {
      return servers;
    }

    const query = this.searchQuery.toLowerCase();
    return servers.filter(server =>
      server.name.toLowerCase().includes(query) ||
      server.endpoint.toLowerCase().includes(query) ||
      this.getNetworkAddress(server).toLowerCase().includes(query)
    );
  }
}