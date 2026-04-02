/** Ansible / SSH private key — API types (camelCase matches Jackson). */

export interface PrivateKeySummary {
  id: string;
  name: string;
  enabled: boolean;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePrivateKeyRequest {
  name: string;
  content: string;
  description?: string | null;
  enabled: boolean;
}

export interface UpdatePrivateKeyRequest {
  name: string;
  content?: string | null;
  description?: string | null;
  enabled: boolean;
}

export interface AnsibleInventoryGroup {
  id: string;
  name: string;
  description: string | null;
  enabled: boolean;
  /** JSON string from backend */
  variables: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAnsibleInventoryGroupRequest {
  name: string;
  description?: string | null;
  enabled: boolean;
  variables?: Record<string, unknown> | null;
}

export interface UpdateAnsibleInventoryGroupRequest {
  name: string;
  description?: string | null;
  enabled: boolean;
  variables?: Record<string, unknown> | null;
}

export interface PrivateKeyRef {
  id: string;
  name: string;
  enabled: boolean;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AnsibleHost {
  id: string;
  name: string;
  hostname: string;
  ipAddress: string;
  sshPort: number;
  sshUsername: string;
  sshPrivateKey: PrivateKeyRef;
  ansibleInventoryGroup: AnsibleInventoryGroup | null;
  sudoRequired: boolean;
  sudoPassword: string | null;
  pythonInterpreter: string | null;
  enabled: boolean;
  tags: string | null;
  description: string | null;
  annotation: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAnsibleHostRequest {
  name: string;
  hostname: string;
  ipAddress: string;
  sshPort: number;
  sshUsername: string;
  sshPrivateKeyId: string;
  ansibleInventoryGroupId?: string | null;
  sudoRequired: boolean;
  sudoPassword?: string | null;
  pythonInterpreter?: string | null;
  enabled: boolean;
  tags?: string[] | null;
  description?: string | null;
  customVariables?: Record<string, string> | null;
}

export interface UpdateAnsibleHostRequest {
  name: string;
  hostname: string;
  ipAddress: string;
  sshPort: number;
  sshUsername: string;
  sshPrivateKeyId: string;
  ansibleInventoryGroupId?: string | null;
  sudoRequired: boolean;
  sudoPassword?: string | null;
  pythonInterpreter?: string | null;
  enabled: boolean;
  tags?: string[] | null;
  description?: string | null;
  customVariables?: Record<string, string> | null;
}

export interface InventoryFileMetadata {
  description?: string | null;
  groupName?: string | null;
  hostCount: number;
  generatedAt: string;
  tags?: string[];
}

export interface InventoryFileInfo {
  filename: string;
  path: string;
  size: number;
  createdAt: string;
  metadata?: InventoryFileMetadata | null;
}

export interface InventoryValidationResponse {
  valid: boolean;
  hostCount: number;
  groupCount: number;
  message: string | null;
  errors: string[];
  warnings: string[];
}

export interface AnsibleStatisticsResponse {
  hostStatistics: {
    totalHosts: number;
    enabledHosts: number;
    disabledHosts: number;
    ungroupedHosts: number;
    totalGroups: number;
    enabledGroups: number;
    disabledGroups: number;
  };
  inventoryFileStatistics: {
    fileCount: number;
    totalSize: number;
    oldestFile: string | null;
    newestFile: string | null;
  };
}
