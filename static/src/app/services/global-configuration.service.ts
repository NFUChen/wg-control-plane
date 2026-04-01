import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { GlobalConfig, GlobalConfiguration } from '../models/global-config.interface';

@Injectable({
  providedIn: 'root'
})
export class GlobalConfigurationService {
  private readonly baseUrl = '/api/global-config';

  constructor(private http: HttpClient) {}

  getCurrentData(): Observable<GlobalConfig> {
    return this.http
      .get<GlobalConfig>(`${this.baseUrl}/current/data`)
      .pipe(catchError(err => this.handleError(err)));
  }

  getCurrent(): Observable<GlobalConfiguration> {
    return this.http
      .get<GlobalConfiguration>(`${this.baseUrl}/current`)
      .pipe(catchError(err => this.handleError(err)));
  }

  update(
    config: GlobalConfig,
    options?: { updatedBy?: string; changeDescription?: string }
  ): Observable<GlobalConfiguration> {
    let params = new HttpParams();
    if (options?.updatedBy) {
      params = params.set('updatedBy', options.updatedBy);
    }
    if (options?.changeDescription) {
      params = params.set('changeDescription', options.changeDescription);
    }
    return this.http
      .put<GlobalConfiguration>(`${this.baseUrl}/update`, config, { params })
      .pipe(catchError(err => this.handleError(err)));
  }

  private handleError(error: HttpErrorResponse): Observable<never> {
    return throwError(() => error);
  }
}
