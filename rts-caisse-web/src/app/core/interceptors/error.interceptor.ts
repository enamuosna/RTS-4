import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Capture les erreurs HTTP globales :
 *   - 401 : session expirée → déconnexion
 *   - 403 : privilèges insuffisants
 *   - autres : affiche un snackbar avec le message renvoyé par le backend
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);
  const auth = inject(AuthService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      let message = 'Erreur inattendue.';

      if (error.status === 0) {
        message = 'Serveur injoignable. Vérifiez votre connexion.';
      } else if (error.status === 401) {
        message = 'Session expirée, veuillez vous reconnecter.';
        auth.logout();
      } else if (error.status === 403) {
        message = 'Accès refusé : vous n\'avez pas les privilèges nécessaires.';
      } else if (error.error?.message) {
        message = error.error.message;
      } else if (error.error?.erreurs) {
        // Erreurs de validation par champ
        const champs = Object.entries(error.error.erreurs as Record<string, string>)
          .map(([c, m]) => `${c} : ${m}`)
          .join(' · ');
        message = `Validation : ${champs}`;
      }

      // Évite le double-snackbar lors du 401 qui déclenche déjà un redirect
      if (error.status !== 401) {
        snackBar.open(message, 'Fermer', {
          duration: 5000,
          panelClass: ['snackbar-error'],
          horizontalPosition: 'end',
          verticalPosition: 'top'
        });
      }

      return throwError(() => error);
    })
  );
};
