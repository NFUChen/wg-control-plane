// WireGuard 相關的 TypeScript 介面定義

export interface IPAddress {
  address: string; // e.g., "10.0.0.1/24"
}

export interface CreateServerRequest {
  name: string;
  interfaceName: string;
  networkAddress: string;
  listenPort: number;
  endpoint: string;
  dnsServers: string[];
}

export interface UpdateServerRequest {
  name?: string;
  interfaceName?: string;
  networkAddress?: string;
  listenPort?: number;
  endpoint?: string;
  dnsServers?: string[];
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
  enabled: boolean;
  totalClients: number;
  activeClients: number;
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
  enabled: boolean;
  clients: ClientResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface AddClientRequest {
  clientName: string;
  clientPublicKey?: string;
  presharedKey?: string;
  addresses: IPAddress[];
}

export interface ClientResponse {
  id: string;
  name: string;
  publicKey: string;
  allowedIPs: string[];
  persistentKeepalive: number;
  enabled: boolean;
  isOnline: boolean;
  lastHandshake?: string;
  dataReceived: number;
  dataSent: number;
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

// API 回應包裝器
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
  message?: string;
}

// 分頁介面
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

// UI 狀態介面
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

// 過濾器介面
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