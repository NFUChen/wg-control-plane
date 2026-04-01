import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, map, of, throwError } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { CurrentUser, LoginRequest } from '../models/auth.interface';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = '/api';

  /** Present when session has been validated at least once in this tab */
  readonly sessionLoaded = signal(false);
  readonly user = signal<CurrentUser | null>(null);

  /**
   * Resolves current session via GET /private/auth/me (cookie).
   * Safe to call multiple times; repeats use cache after first completion.
   */
  ensureSession(): Observable<boolean> {
    if (this.sessionLoaded()) {
      return of(this.user() !== null);
    }
    return this.http.get<CurrentUser>(`${this.apiBase}/private/auth/me`).pipe(
      map(u => {
        this.user.set(u);
        this.sessionLoaded.set(true);
        return true;
      }),
      catchError(() => {
        this.user.set(null);
        this.sessionLoaded.set(true);
        return of(false);
      })
    );
  }

  login(body: LoginRequest): Observable<CurrentUser> {
    return this.http
      .post<{ message: string }>(`${this.apiBase}/public/auth/login`, body)
      .pipe(
        switchMap(() => this.fetchCurrentUser()),
        catchError(err => throwError(() => this.mapLoginError(err)))
      );
  }

  /** Load user after login cookie is set */
  fetchCurrentUser(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>(`${this.apiBase}/private/auth/me`).pipe(
      map(u => {
        this.user.set(u);
        this.sessionLoaded.set(true);
        return u;
      })
    );
  }

  logout(): Observable<void> {
    return this.http.get<{ message: string }>(`${this.apiBase}/private/auth/logout`).pipe(
      map(() => {
        this.user.set(null);
        this.sessionLoaded.set(true);
      }),
      catchError(() => {
        this.user.set(null);
        this.sessionLoaded.set(true);
        return of(undefined);
      })
    );
  }

  private mapLoginError(err: unknown): Error {
    if (err instanceof HttpErrorResponse) {
      const body = err.error;
      if (body && typeof body === 'object') {
        const msg = (body as { message?: unknown }).message;
        if (typeof msg === 'string' && msg.trim().length > 0) {
          return new Error(msg);
        }
      }
      if (err.status === 401 || err.status === 403) {
        return new Error('Invalid email or password.');
      }
    }
    return new Error('Sign-in failed. Please try again.');
  }
}
