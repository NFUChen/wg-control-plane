import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, BehaviorSubject, throwError } from 'rxjs';
import { catchError, tap, map } from 'rxjs/operators';
import {
  ServerResponse,
  ServerDetailResponse,
  CreateServerRequest,
  UpdateServerRequest,
  ClientResponse,
  AddClientRequest,
  UpdateClientStatsRequest,
  ServerStatisticsResponse,
  ClientInfo,
  LoadingState
} from '../models/wireguard.interface';

@Injectable({
  providedIn: 'root'
})
export class WireguardService {
  private readonly baseUrl = '/api/wireguard'; // Change this if your backend uses different path
  private readonly clientBaseUrl = '/api/clients';

  // Loading states for UI feedback
  private serversLoadingSubject = new BehaviorSubject<LoadingState>({ isLoading: false });
  private clientsLoadingSubject = new BehaviorSubject<LoadingState>({ isLoading: false });

  public serversLoading$ = this.serversLoadingSubject.asObservable();
  public clientsLoading$ = this.clientsLoadingSubject.asObservable();

  // Data caches for optimistic updates
  private serversCache = new BehaviorSubject<ServerResponse[]>([]);
  public servers$ = this.serversCache.asObservable();

  constructor(private http: HttpClient) {}

  // ==================== Server Operations ====================

  /**
   * Get all servers
   */
  getAllServers(): Observable<ServerResponse[]> {
    this.setServersLoading(true);
    const url = `${this.baseUrl}/servers`;
    console.log('Making API request to:', url);

    return this.http.get<ServerResponse[]>(url, {
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      }
    }).pipe(
      tap(servers => {
        console.log('✅ Successfully received servers:', servers);
        this.serversCache.next(servers);
        this.setServersLoading(false);
      }),
      catchError(error => {
        console.error('❌ Error in getAllServers:', {
          url: url,
          status: error.status,
          statusText: error.statusText,
          error: error.error
        });
        this.setServersLoading(false, this.getErrorMessage(error));
        return throwError(() => error);
      })
    );
  }

  /**
   * Get active servers only
   */
  getActiveServers(): Observable<ServerResponse[]> {
    this.setServersLoading(true);
    return this.http.get<ServerResponse[]>(`${this.baseUrl}/servers/active`).pipe(
      tap(() => this.setServersLoading(false)),
      catchError(error => {
        this.setServersLoading(false, this.getErrorMessage(error));
        return throwError(() => error);
      })
    );
  }

  /**
   * Get server by ID with clients
   */
  getServerWithClients(serverId: string): Observable<ServerDetailResponse> {
    this.setServersLoading(true);
    return this.http.get<ServerDetailResponse>(`${this.baseUrl}/servers/${serverId}`).pipe(
      tap(() => this.setServersLoading(false)),
      catchError(error => {
        this.setServersLoading(false, this.getErrorMessage(error));
        return throwError(() => error);
      })
    );
  }

  /**
   * Get server statistics
   */
  getServerStats(serverId: string): Observable<ServerStatisticsResponse> {
    return this.http.get<ServerStatisticsResponse>(`${this.baseUrl}/servers/${serverId}/stats`).pipe(
      catchError(error => this.handleError(error))
    );
  }

  /**
   * Create a new server
   */
  createServer(request: CreateServerRequest): Observable<ServerResponse> {
    this.setServersLoading(true);
    return this.http.post<ServerResponse>(`${this.baseUrl}/servers`, request).pipe(
      tap(newServer => {
        // Optimistically update cache
        const currentServers = this.serversCache.getValue();
        this.serversCache.next([...currentServers, newServer]);
        this.setServersLoading(false);
      }),
      catchError(error => {
        this.setServersLoading(false, this.getErrorMessage(error));
        return throwError(() => error);
      })
    );
  }

  /**
   * Update server
   */
  updateServer(serverId: string, request: UpdateServerRequest): Observable<ServerResponse> {
    this.setServersLoading(true);
    return this.http.put<ServerResponse>(`${this.baseUrl}/servers/${serverId}`, request).pipe(
      tap(updatedServer => {
        // Optimistically update cache
        const currentServers = this.serversCache.getValue();
        const updatedServers = currentServers.map(server =>
          server.id === serverId ? updatedServer : server
        );
        this.serversCache.next(updatedServers);
        this.setServersLoading(false);
      }),
      catchError(error => {
        this.setServersLoading(false, this.getErrorMessage(error));
        return throwError(() => error);
      })
    );
  }

  /**
   * Launch server
   */
  launchServer(serverId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/server-up/${serverId}`, {}).pipe(
      catchError(error => this.handleError(error))
    );
  }

  // ==================== Client Operations ====================

  /**
   * Get clients for a server
   */
  getServerClients(serverId: string): Observable<ClientResponse[]> {
    this.setClientsLoading(true);
    return this.http.get<ClientResponse[]>(`${this.baseUrl}/servers/${serverId}/clients`).pipe(
      tap(() => this.setClientsLoading(false)),
      catchError(error => {
        this.setClientsLoading(false, this.getErrorMessage(error));
        return throwError(() => error);
      })
    );
  }

  /**
   * Get active clients for a server
   */
  getActiveServerClients(serverId: string): Observable<ClientResponse[]> {
    this.setClientsLoading(true);
    return this.http.get<ClientResponse[]>(`${this.baseUrl}/servers/${serverId}/clients/active`).pipe(
      tap(() => this.setClientsLoading(false)),
      catchError(error => {
        this.setClientsLoading(false, this.getErrorMessage(error));
        return throwError(() => error);
      })
    );
  }

  /**
   * Add client to server
   */
  addClientToServer(serverId: string, request: AddClientRequest): Observable<ClientResponse> {
    this.setClientsLoading(true);
    return this.http.post<ClientResponse>(`${this.baseUrl}/servers/${serverId}/clients/add`, request).pipe(
      tap(() => this.setClientsLoading(false)),
      catchError(error => {
        this.setClientsLoading(false, this.getErrorMessage(error));
        return throwError(() => error);
      })
    );
  }

  /**
   * Remove client from server
   */
  removeClientFromServer(serverId: string, clientId: string): Observable<void> {
    this.setClientsLoading(true);
    return this.http.delete<void>(`${this.baseUrl}/servers/${serverId}/clients/${clientId}`).pipe(
      tap(() => this.setClientsLoading(false)),
      catchError(error => {
        this.setClientsLoading(false, this.getErrorMessage(error));
        return throwError(() => error);
      })
    );
  }

  /**
   * Update client statistics
   */
  updateClientStats(clientId: string, request: UpdateClientStatsRequest): Observable<ClientResponse> {
    return this.http.put<ClientResponse>(`${this.baseUrl}/clients/${clientId}/stats`, request).pipe(
      catchError(error => this.handleError(error))
    );
  }

  /**
   * Get client info
   */
  getClientInfo(clientId: string): Observable<ClientInfo> {
    return this.http.get<ClientInfo>(`${this.clientBaseUrl}/${clientId}/info`).pipe(
      catchError(error => this.handleError(error))
    );
  }

  /**
   * Download client configuration
   */
  downloadClientConfig(clientId: string, allowAllTraffic: boolean = false): Observable<string> {
    const params = allowAllTraffic ? '?allowAllTraffic=true' : '';
    return this.http.get(`${this.clientBaseUrl}/${clientId}/config${params}`, {
      responseType: 'text'
    }).pipe(
      catchError(error => this.handleError(error))
    );
  }

  // ==================== Utility Methods ====================

  /**
   * Refresh servers cache
   */
  refreshServers(): Observable<ServerResponse[]> {
    return this.getAllServers();
  }

  /**
   * Clear all caches
   */
  clearCache(): void {
    this.serversCache.next([]);
  }

  // ==================== Private Helper Methods ====================

  private setServersLoading(loading: boolean, error?: string): void {
    this.serversLoadingSubject.next({ isLoading: loading, error });
  }

  private setClientsLoading(loading: boolean, error?: string): void {
    this.clientsLoadingSubject.next({ isLoading: loading, error });
  }

  private handleError(error: HttpErrorResponse): Observable<never> {
    return throwError(() => error);
  }

  private getErrorMessage(error: HttpErrorResponse): string {
    if (error.error instanceof ErrorEvent) {
      // Client-side error
      return `Error: ${error.error.message}`;
    } else {
      // Server-side error
      switch (error.status) {
        case 404:
          return 'Resource not found';
        case 400:
          return error.error?.message || 'Bad request';
        case 401:
          return 'Unauthorized access';
        case 403:
          return 'Access forbidden';
        case 500:
          return 'Internal server error';
        default:
          return `Error ${error.status}: ${error.error?.message || error.message}`;
      }
    }
  }
}