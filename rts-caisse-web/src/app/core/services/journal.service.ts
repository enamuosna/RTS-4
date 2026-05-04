import {
  HttpClient,
  HttpErrorResponse,
  HttpParams,
  HttpResponse
} from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import {
  ClotureCaisseRequest,
  JournalCaisse,
  OuvertureCaisseRequest
} from '../models/models';

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
   * Télécharge le journal au format Excel.
   * URL DOIT correspondre EXACTEMENT au backend :
   *   JournalExcelController : @GetMapping("/{id}/export.xlsx")
   *   → /api/journaux/{id}/export.xlsx
   */
  exporterExcel(journalId: number): Observable<HttpResponse<Blob>> {
    return this.http
      .get(`${this.base}/${journalId}/export.xlsx`, {
        responseType: 'blob',
        observe: 'response'
      })
      .pipe(catchError((error: HttpErrorResponse) => this.parseBlobError(error)));
  }

  /**
   * Quand responseType='blob', le body d'erreur arrive aussi en Blob.
   * On le relit en texte pour récupérer le JSON d'erreur de Spring.
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
