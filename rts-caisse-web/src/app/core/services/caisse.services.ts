import { HttpClient, HttpErrorResponse, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  ClotureCaisseRequest,
  DashboardResponse,
  JournalCaisse,
  OperationCaisse,
  OperationCaisseRequest,
  OuvertureCaisseRequest,
  Page
} from '../models/models';

// ======================================================
//  OPERATIONS DE CAISSE
// ======================================================
@Injectable({ providedIn: 'root' })
export class OperationService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/operations`;

  enregistrer(request: OperationCaisseRequest): Observable<OperationCaisse> {
    return this.http.post<OperationCaisse>(this.base, request);
  }

  annuler(id: number, motif: string): Observable<OperationCaisse> {
    return this.http.patch<OperationCaisse>(`${this.base}/${id}/annuler`, null, {
      params: new HttpParams().set('motif', motif)
    });
  }

  obtenir(id: number): Observable<OperationCaisse> {
    return this.http.get<OperationCaisse>(`${this.base}/${id}`);
  }

  historiqueParCaisse(
    caisseId: number,
    page = 0,
    size = 20
  ): Observable<Page<OperationCaisse>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', 'dateOperation,desc');
    return this.http.get<Page<OperationCaisse>>(`${this.base}/caisse/${caisseId}`, { params });
  }

  historiqueDuJour(caisseId: number): Observable<OperationCaisse[]> {
    return this.http.get<OperationCaisse[]>(`${this.base}/caisse/${caisseId}/jour`);
  }
}

// ======================================================
//  JOURNAL DE CAISSE
// ======================================================
@Injectable({ providedIn: 'root' })
export class JournalService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/journaux`;

  ouvrir(caisseId: number, request: OuvertureCaisseRequest): Observable<JournalCaisse> {
    return this.http.post<JournalCaisse>(`${this.base}/caisse/${caisseId}/ouvrir`, request);
  }

  cloturer(caisseId: number, request: ClotureCaisseRequest): Observable<JournalCaisse> {
    return this.http.post<JournalCaisse>(`${this.base}/caisse/${caisseId}/cloturer`, request);
  }

  valider(journalId: number): Observable<JournalCaisse> {
    return this.http.post<JournalCaisse>(`${this.base}/${journalId}/valider`, null);
  }

  obtenir(id: number): Observable<JournalCaisse> {
    return this.http.get<JournalCaisse>(`${this.base}/${id}`);
  }

  parCaisse(caisseId: number): Observable<JournalCaisse[]> {
    return this.http.get<JournalCaisse[]>(`${this.base}/caisse/${caisseId}`);
  }

  duJour(date?: string): Observable<JournalCaisse[]> {
    let params = new HttpParams();
    if (date) params = params.set('date', date);
    return this.http.get<JournalCaisse[]>(`${this.base}/jour`, { params });
  }

  /**
   * Exporte un journal au format Excel.
   *
   * Problème Angular : quand `responseType: 'blob'` est actif, les réponses
   * d'ERREUR arrivent elles aussi sous forme de Blob au lieu de JSON.
   * L'intercepteur global ne sait pas les lire et affiche "Erreur inattendue".
   *
   * Solution : on intercepte l'erreur ici, on lit le Blob comme du texte,
   * on reparse le JSON du serveur, puis on relance un HttpErrorResponse
   * correctement formé que l'intercepteur global sait traiter.
   */
  exporterExcel(journalId: number): Observable<HttpResponse<Blob>> {
    return this.http
      .get(`${this.base}/${journalId}/export/excel`, {
        responseType: 'blob',
        observe: 'response'
      })
      .pipe(catchError((error: HttpErrorResponse) => this.parseBlobError(error)));
  }

  /**
   * Convertit une erreur Blob en HttpErrorResponse lisible par l'intercepteur.
   * Si le corps n'est pas du JSON valide, on retransmet l'erreur d'origine.
   */
  private parseBlobError(error: HttpErrorResponse): Observable<never> {
    if (!(error.error instanceof Blob)) {
      return throwError(() => error);
    }

    return new Observable<never>((observer) => {
      const reader = new FileReader();

      reader.onload = () => {
        let parsedError: unknown;
        try {
          parsedError = JSON.parse(reader.result as string);
        } catch {
          parsedError = { message: reader.result };
        }

        observer.error(
          new HttpErrorResponse({
            error: parsedError,
            headers: error.headers,
            status: error.status,
            statusText: error.statusText,
            url: error.url ?? undefined
          })
        );
      };

      reader.onerror = () => observer.error(error);
      reader.readAsText(error.error);
    });
  }
}

// ======================================================
//  REPORTING
// ======================================================
@Injectable({ providedIn: 'root' })
export class ReportingService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/reporting`;

  dashboard(date?: string): Observable<DashboardResponse> {
    let params = new HttpParams();
    if (date) params = params.set('date', date);
    return this.http.get<DashboardResponse>(`${this.base}/dashboard`, { params });
  }
}
