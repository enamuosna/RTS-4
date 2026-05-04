import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Caisse,
  CategorieOperation,
  Client,
  RegisterRequest,
  TypeOperation,
  Utilisateur
} from '../models/models';

// ======================================================
//  UTILISATEURS
// ======================================================
@Injectable({ providedIn: 'root' })
export class UtilisateurService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/utilisateurs`;

  lister(): Observable<Utilisateur[]> {
    return this.http.get<Utilisateur[]>(this.base);
  }

  obtenir(id: number): Observable<Utilisateur> {
    return this.http.get<Utilisateur>(`${this.base}/${id}`);
  }

  creer(request: RegisterRequest): Observable<Utilisateur> {
    return this.http.post<Utilisateur>(this.base, request);
  }

  activer(id: number, actif: boolean): Observable<Utilisateur> {
    return this.http.patch<Utilisateur>(`${this.base}/${id}/activer`, null, {
      params: new HttpParams().set('actif', actif)
    });
  }

  changerMotDePasse(id: number, nouveau: string): Observable<void> {
    return this.http.patch<void>(`${this.base}/${id}/mot-de-passe`, null, {
      params: new HttpParams().set('nouveau', nouveau)
    });
  }

  supprimer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}

// ======================================================
//  CAISSES
// ======================================================
@Injectable({ providedIn: 'root' })
export class CaisseService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/caisses`;

  lister(): Observable<Caisse[]> {
    return this.http.get<Caisse[]>(this.base);
  }

  obtenir(id: number): Observable<Caisse> {
    return this.http.get<Caisse>(`${this.base}/${id}`);
  }

  creer(dto: Partial<Caisse>): Observable<Caisse> {
    return this.http.post<Caisse>(this.base, dto);
  }

  modifier(id: number, dto: Partial<Caisse>): Observable<Caisse> {
    return this.http.put<Caisse>(`${this.base}/${id}`, dto);
  }

  affecterCaissier(id: number, caissierId: number): Observable<Caisse> {
    return this.http.patch<Caisse>(`${this.base}/${id}/caissier`, null, {
      params: new HttpParams().set('caissierId', caissierId)
    });
  }

  suspendre(id: number, suspendre: boolean): Observable<Caisse> {
    return this.http.patch<Caisse>(`${this.base}/${id}/suspendre`, null, {
      params: new HttpParams().set('suspendre', suspendre)
    });
  }
}

// ======================================================
//  CATEGORIES
// ======================================================
@Injectable({ providedIn: 'root' })
export class CategorieService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/categories`;

  lister(type?: TypeOperation): Observable<CategorieOperation[]> {
    let params = new HttpParams();
    if (type) params = params.set('type', type);
    return this.http.get<CategorieOperation[]>(this.base, { params });
  }

  creer(dto: Partial<CategorieOperation>): Observable<CategorieOperation> {
    return this.http.post<CategorieOperation>(this.base, dto);
  }

  modifier(id: number, dto: Partial<CategorieOperation>): Observable<CategorieOperation> {
    return this.http.put<CategorieOperation>(`${this.base}/${id}`, dto);
  }
}

// ======================================================
//  CLIENTS
// ======================================================
@Injectable({ providedIn: 'root' })
export class ClientService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/clients`;

  lister(q?: string): Observable<Client[]> {
    let params = new HttpParams();
    if (q) params = params.set('q', q);
    return this.http.get<Client[]>(this.base, { params });
  }

  obtenir(id: number): Observable<Client> {
    return this.http.get<Client>(`${this.base}/${id}`);
  }

  creer(dto: Partial<Client>): Observable<Client> {
    return this.http.post<Client>(this.base, dto);
  }

  modifier(id: number, dto: Partial<Client>): Observable<Client> {
    return this.http.put<Client>(`${this.base}/${id}`, dto);
  }
}
