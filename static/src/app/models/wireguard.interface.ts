// WireGuard-related TypeScript interfaces

export type ClientDeploymentStatus = 'NONE' | 'DEPLOYED' | 'DEPLOY_FAILED' | 'PENDING_REMOVAL';

export interface IPAddress {
  address: string; // e.g., "10.0.0.1/24"
}

export interface CreateServerRequest {
  name: string;
  interfaceName: string;
  networkAddress: string;
  listenPort: number;
  dnsServers: string[];
  postUp?: string | null;
  postDown?: string | null;
  /** When set, server is deployed via Ansible to this host (immutable after create). Omit or null = local WG on control plane. */
  hostId?: string | null;
}

export interface UpdateServerRequest {
  name?: string;
  interfaceName?: string;
  networkAddress?: string;
  listenPort?: number;
  dnsServers?: string[];
  postUp?: string | null;
  postDown?: string | null;
}

export interface ServerResponse {
  id: string;
  name: string;
  interfaceName: string;
  publicKey: string;
  networkAddress: IPAddress;
  listenPort: number;
  endpoint: string;
  dnsServers: IPAddress[];
  postUp?: string | null;
  postDown?: string | null;
  enabled: boolean;
  /** WireGuard interface is up (same meaning as client isOnline — runtime connectivity). */
  isOnline: boolean;
  totalClients: number;
  activeClients: number;
  /** Set when this server is Ansible-managed (not local WG on the control plane). */
  hostId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ServerDetailResponse {
  id: string;
  name: string;
  interfaceName: string;
  publicKey: string;
  networkAddress: IPAddress;
  listenPort: number;
  endpoint: string;
  dnsServers: IPAddress[];
  postUp?: string | null;
  postDown?: string | null;
  enabled: boolean;
  clients: ClientResponse[];
  /** Set when this server is Ansible-managed (not local WG on the control plane). */
  hostId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AddClientRequest {
  clientName: string;
  /** Local WG interface on the client host (Ansible deploy); wg0–wg99, same rules as server. */
  interfaceName: string;
  clientPublicKey?: string;
  presharedKey?: string;
  /** This client's address(es) on the WireGuard tunnel (`Address` in client .conf); multiple = comma-separated in generated config). */
  peerIPs: IPAddress[];
  /**
   * Extra prefixes routed to this peer on the server (in addition to [peerIP]);
   * distinct from the client-side “which traffic uses the tunnel” setting in generated .conf.
   */
  allowedIPs: IPAddress[];
  /** Optional: deploy client config to this Ansible host (only for Ansible-managed servers). Immutable after create. */
  hostId?: string | null;
}

/** PUT /api/private/wireguard/servers/{serverId}/clients/{clientId} — omitted fields unchanged; presharedKey: '' clears PSK */
export interface UpdateClientRequest {
  clientName?: string;
  interfaceName?: string;
  peerIPs?: IPAddress[];
  presharedKey?: string;
  persistentKeepalive?: number;
  enabled?: boolean;
}

/** GET /api/private/wireguard/clients/{id} */
export interface ClientDetailResponse {
  id: string;
  name: string;
  interfaceName: string;
  publicKey: string;
  /** VPN/tunnel address(es); immutable after create. */
  peerIPs: string[];
  allowedIPs: string[];
  enabled: boolean;
  isOnline: boolean;
  lastHandshake?: string | null;
  persistentKeepalive: number;
  /** When set, client config is deployed to this Ansible host. */
  hostId: string | null;
  server: {
    id: string;
    name: string;
    endpoint: string;
    publicKey: string;
    dnsServers: string[];
    mtu: number | null;
  };
}

export interface ClientResponse {
  id: string;
  name: string;
  interfaceName: string;
  publicKey: string;
  peerIPs: string[];
  allowedIPs: string[];
  persistentKeepalive: number;
  enabled: boolean;
  isOnline: boolean;
  lastHandshake?: string;
  dataReceived: number;
  dataSent: number;
  /** When set, client config is deployed to this Ansible host. */
  hostId: string | null;
  deploymentStatus: ClientDeploymentStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ClientCreationResponse {
  client: ClientResponse;
  privateKey: string;
}

export interface UpdateClientStatsRequest {
  lastHandshake: string;
  dataReceived: number;
  dataSent: number;
}

export interface ServerStatisticsResponse {
  serverId: string;
  serverName: string;
  endpoint: string;
  listenPort: number;
  totalClients: number;
  onlineClients: number;
  offlineClients: number;
  totalDataReceived: number;
  totalDataSent: number;
  networkAddress: string;
}

export interface ClientInfo {
  id: string;
  name: string;
  publicKey: string;
  allowedIPs: string;
  enabled: boolean;
  isOnline: boolean;
  server: {
    id: string;
    name: string;
    endpoint: string;
  };
}

/** GET /api/private/wireguard/clients/{id}/preview — WireGuard config text (includes PrivateKey; same generation as download) */
export interface ConfigurationMetadata {
  clientId: string;
  serverName: string;
  createdAt: string;
  configHash: string;
  validationErrors: string[];
}

export interface ConfigurationPreview {
  fileName: string;
  content: string;
  metadata: ConfigurationMetadata;
}

// API response wrapper
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
  message?: string;
}

// Pagination
export interface PaginationParams {
  page: number;
  size: number;
  sortBy?: string;
  sortDirection?: 'asc' | 'desc';
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

// UI loading state
export interface LoadingState {
  isLoading: boolean;
  error?: string;
}

export interface TableColumn {
  key: string;
  label: string;
  sortable?: boolean;
  width?: string;
  type?: 'text' | 'number' | 'boolean' | 'date' | 'action';
}

// List filters
export interface ServerFilter {
  searchQuery?: string;
  enabled?: boolean;
  hasClients?: boolean;
}

export interface ClientFilter {
  searchQuery?: string;
  enabled?: boolean;
  isOnline?: boolean;
  serverId?: string;
}