import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import {
  AnsibleHost,
  AnsibleInventoryGroup,
  AnsibleStatisticsResponse,
  CreateAnsibleHostRequest,
  CreateAnsibleInventoryGroupRequest,
  CreatePrivateKeyRequest,
  InventoryFileInfo,
  InventoryValidationResponse,
  PrivateKeySummary,
  UpdateAnsibleHostRequest,
  UpdateAnsibleInventoryGroupRequest,
  UpdatePrivateKeyRequest
} from '../models/ansible.interface';

@Injectable({ providedIn: 'root' })
export class AnsibleService {
  private readonly ansibleUrl = '/api/ansible';
  private readonly privateKeysUrl = '/api/private/ansible/private-keys';

  constructor(private http: HttpClient) {}

  getApiErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.error instanceof ErrorEvent) {
        return `Error: ${error.error.message}`;
      }
      const body = error.error;
      if (typeof body === 'string' && body.trim()) return body;
      if (body && typeof body === 'object') {
        const msg = (body as { message?: string; error?: string }).message;
        if (msg) return msg;
        const err = (body as { error?: string }).error;
        if (typeof err === 'string') return err;
      }
      switch (error.status) {
        case 400:
          return 'Bad request';
        case 401:
          return 'Unauthorized';
        case 403:
          return 'Forbidden';
        case 404:
          return 'Not found';
        case 409:
          return 'Conflict';
        case 500:
          return 'Server error';
        default:
          return `Error ${error.status}: ${error.message}`;
      }
    }
    return 'An unexpected error occurred';
  }

  // --- Private keys (vault) ---
  listPrivateKeys(): Observable<PrivateKeySummary[]> {
    return this.http.get<PrivateKeySummary[]>(this.privateKeysUrl).pipe(catchError(e => this.handle(e)));
  }

  getPrivateKey(id: string): Observable<PrivateKeySummary> {
    return this.http.get<PrivateKeySummary>(`${this.privateKeysUrl}/${id}`).pipe(catchError(e => this.handle(e)));
  }

  getPrivateKeyContent(id: string): Observable<string> {
    return this.http
      .get(`${this.privateKeysUrl}/${id}/content`, { responseType: 'text' })
      .pipe(catchError(e => this.handle(e)));
  }

  createPrivateKey(body: CreatePrivateKeyRequest): Observable<PrivateKeySummary> {
    return this.http.post<PrivateKeySummary>(this.privateKeysUrl, body).pipe(catchError(e => this.handle(e)));
  }

  updatePrivateKey(id: string, body: UpdatePrivateKeyRequest): Observable<PrivateKeySummary> {
    return this.http.put<PrivateKeySummary>(`${this.privateKeysUrl}/${id}`, body).pipe(catchError(e => this.handle(e)));
  }

  deletePrivateKey(id: string): Observable<void> {
    return this.http.delete<void>(`${this.privateKeysUrl}/${id}`).pipe(catchError(e => this.handle(e)));
  }

  // --- Groups ---
  listGroups(enabledOnly = false): Observable<AnsibleInventoryGroup[]> {
    const params = new HttpParams().set('enabledOnly', String(enabledOnly));
    return this.http.get<AnsibleInventoryGroup[]>(`${this.ansibleUrl}/groups`, { params }).pipe(catchError(e => this.handle(e)));
  }

  getGroup(id: string): Observable<AnsibleInventoryGroup> {
    return this.http.get<AnsibleInventoryGroup>(`${this.ansibleUrl}/groups/${id}`).pipe(catchError(e => this.handle(e)));
  }

  createGroup(body: CreateAnsibleInventoryGroupRequest): Observable<AnsibleInventoryGroup> {
    return this.http.post<AnsibleInventoryGroup>(`${this.ansibleUrl}/groups`, body).pipe(catchError(e => this.handle(e)));
  }

  updateGroup(id: string, body: UpdateAnsibleInventoryGroupRequest): Observable<AnsibleInventoryGroup> {
    return this.http.put<AnsibleInventoryGroup>(`${this.ansibleUrl}/groups/${id}`, body).pipe(catchError(e => this.handle(e)));
  }

  deleteGroup(id: string): Observable<void> {
    return this.http.delete<void>(`${this.ansibleUrl}/groups/${id}`).pipe(catchError(e => this.handle(e)));
  }

  // --- Hosts ---
  listHosts(enabledOnly = false): Observable<AnsibleHost[]> {
    const params = new HttpParams().set('enabledOnly', String(enabledOnly));
    return this.http.get<AnsibleHost[]>(`${this.ansibleUrl}/hosts`, { params }).pipe(catchError(e => this.handle(e)));
  }

  getHost(id: string): Observable<AnsibleHost> {
    return this.http.get<AnsibleHost>(`${this.ansibleUrl}/hosts/${id}`).pipe(catchError(e => this.handle(e)));
  }

  createHost(body: CreateAnsibleHostRequest): Observable<AnsibleHost> {
    return this.http.post<AnsibleHost>(`${this.ansibleUrl}/hosts`, body).pipe(catchError(e => this.handle(e)));
  }

  updateHost(id: string, body: UpdateAnsibleHostRequest): Observable<AnsibleHost> {
    return this.http.put<AnsibleHost>(`${this.ansibleUrl}/hosts/${id}`, body).pipe(catchError(e => this.handle(e)));
  }

  deleteHost(id: string): Observable<void> {
    return this.http.delete<void>(`${this.ansibleUrl}/hosts/${id}`).pipe(catchError(e => this.handle(e)));
  }

  // --- Inventory files ---
  listInventoryFiles(): Observable<InventoryFileInfo[]> {
    return this.http.get<InventoryFileInfo[]>(`${this.ansibleUrl}/inventory/files`).pipe(catchError(e => this.handle(e)));
  }

  previewInventory(): Observable<string> {
    return this.http
      .get(`${this.ansibleUrl}/inventory/preview`, { responseType: 'text' })
      .pipe(catchError(e => this.handle(e)));
  }

  getInventoryFileContent(filename: string): Observable<string> {
    const enc = encodeURIComponent(filename);
    return this.http
      .get(`${this.ansibleUrl}/inventory/files/${enc}/content`, { responseType: 'text' })
      .pipe(catchError(e => this.handle(e)));
  }

  generateInventory(filename?: string | null, includeMetadata = true): Observable<InventoryFileInfo> {
    let params = new HttpParams().set('includeMetadata', String(includeMetadata));
    if (filename?.trim()) params = params.set('filename', filename.trim());
    return this.http
      .post<InventoryFileInfo>(`${this.ansibleUrl}/inventory/generate`, null, { params })
      .pipe(catchError(e => this.handle(e)));
  }

  generateGroupInventory(
    groupId: string,
    filename?: string | null,
    includeMetadata = true
  ): Observable<InventoryFileInfo> {
    let params = new HttpParams().set('includeMetadata', String(includeMetadata));
    if (filename?.trim()) params = params.set('filename', filename.trim());
    return this.http
      .post<InventoryFileInfo>(`${this.ansibleUrl}/inventory/generate/group/${groupId}`, null, { params })
      .pipe(catchError(e => this.handle(e)));
  }

  deleteInventoryFile(filename: string): Observable<void> {
    const enc = encodeURIComponent(filename);
    return this.http.delete<void>(`${this.ansibleUrl}/inventory/files/${enc}`).pipe(catchError(e => this.handle(e)));
  }

  /**
   * Backend returns 200 when valid, 400 with the same JSON shape when invalid.
   */
  validateInventory(): Observable<InventoryValidationResponse> {
    return this.http
      .get<InventoryValidationResponse>(`${this.ansibleUrl}/validation`, { observe: 'response' })
      .pipe(
        map(res => res.body as InventoryValidationResponse),
        catchError((err: HttpErrorResponse) => {
          if (err.status === 400 && err.error && typeof err.error === 'object') {
            return of(err.error as InventoryValidationResponse);
          }
          return this.handle(err);
        })
      );
  }

  getStatistics(): Observable<AnsibleStatisticsResponse> {
    return this.http.get<AnsibleStatisticsResponse>(`${this.ansibleUrl}/statistics`).pipe(catchError(e => this.handle(e)));
  }

  private handle(error: HttpErrorResponse): Observable<never> {
    return throwError(() => error);
  }
}
