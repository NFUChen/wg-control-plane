/** Mirrors backend GlobalConfig — used for GET /api/private/global-config/current/data and PUT body */
export interface GlobalConfig {
  serverEndpoint: string;
  defaultDnsServers: string[];
  defaultMtu: number;
  defaultPersistentKeepalive: number;
  enablePresharedKeys: boolean;
  autoGenerateKeys: boolean;
}

/** Full row from GET /api/private/global-config/current */
export interface GlobalConfiguration {
  id: string;
  version: number;
  config: GlobalConfig;
  createdBy?: string | null;
  changeDescription?: string | null;
  createdAt: string;
}
