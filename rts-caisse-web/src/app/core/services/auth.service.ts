import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, Role } from '../models/models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _currentUser = signal<AuthResponse | null>(this.loadFromStorage());

  readonly currentUser = this._currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this._currentUser() !== null);
  readonly currentRole = computed<Role | null>(() => this._currentUser()?.role ?? null);

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/auth/login`, request)
      .pipe(tap((response) => this.persistSession(response)));
  }

  logout(): void {
    localStorage.removeItem(environment.tokenKey);
    localStorage.removeItem(environment.userKey);
    this._currentUser.set(null);
    void this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(environment.tokenKey);
  }

  hasRole(...roles: Role[]): boolean {
    const current = this.currentRole();
    return current !== null && roles.includes(current);
  }

  // ------------------------------------------------------------------

  private persistSession(response: AuthResponse): void {
    localStorage.setItem(environment.tokenKey, response.token);
    localStorage.setItem(environment.userKey, JSON.stringify(response));
    this._currentUser.set(response);
  }

  private loadFromStorage(): AuthResponse | null {
    const raw = localStorage.getItem(environment.userKey);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as AuthResponse;
    } catch {
      return null;
    }
  }
}
