import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { Role } from '../models/models';

/**
 * Garde de base : bloque les routes si l'utilisateur n'est pas authentifié.
 * Redirige vers /login en conservant l'URL demandée.
 */
export const authGuard: CanActivateFn = (_, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login'], {
    queryParams: { redirect: state.url }
  });
};

/**
 * Garde avec restriction par rôle. À utiliser via la factory ci-dessous :
 *   canActivate: [roleGuard(['ADMIN', 'SUPERVISEUR'])]
 */
export function roleGuard(allowedRoles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    if (!auth.isAuthenticated()) {
      return router.createUrlTree(['/login']);
    }
    if (auth.hasRole(...allowedRoles)) {
      return true;
    }
    return router.createUrlTree(['/unauthorized']);
  };
}
