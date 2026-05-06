import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then((m) => m.LoginComponent)
  },
  {
    path: 'admin/audit',
    loadComponent: () =>
        import('./features/audit/audit-list.component')
            .then(m => m.AuditListComponent),
    canActivate: [roleGuard(['ADMIN'])]
  },
  {
    path: '',
    loadComponent: () =>
      import('./layout/main-layout/main-layout.component').then((m) => m.MainLayoutComponent),
    canActivate: [authGuard],
    children: [
      // Route par défaut : /caisses est accessible à tous les rôles authentifiés,
      // donc aucun risque de boucle "accès refusé" quel que soit le rôle.
      { path: '', redirectTo: 'caisses', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
        canActivate: [roleGuard(['ADMIN', 'SUPERVISEUR'])]
      },
      {
        path: 'utilisateurs',
        loadComponent: () =>
          import('./features/utilisateurs/utilisateurs.component').then(
            (m) => m.UtilisateursComponent
          ),
        canActivate: [roleGuard(['ADMIN'])]
      },
      {
        path: 'caisses',
        loadComponent: () =>
          import('./features/caisses/caisses.component').then((m) => m.CaissesComponent)
      },
      {
        path: 'categories',
        loadComponent: () =>
          import('./features/categories/categories.component').then((m) => m.CategoriesComponent),
        canActivate: [roleGuard(['ADMIN'])]
      },
      {
        path: 'clients',
        loadComponent: () =>
          import('./features/clients/clients.component').then((m) => m.ClientsComponent)
      },
      {
        path: 'operations',
        loadComponent: () =>
          import('./features/operations/operations.component').then((m) => m.OperationsComponent)
      },
      {
        path: 'journaux',
        loadComponent: () =>
          import('./features/journaux/journaux.component').then((m) => m.JournauxComponent),
        canActivate: [roleGuard(['ADMIN', 'SUPERVISEUR'])]
      },
      {
        path: 'audit',
        loadComponent: () =>
          import('./features/audit/audit-list.component').then((m) => m.AuditListComponent),
        canActivate: [roleGuard(['ADMIN'])]
      },
      // Si un guard refuse un accès, on renvoie sur /caisses (page sûre pour tous)
      // plutôt que sur /login qui ferait croire à tort à une session expirée.
      { path: 'unauthorized', redirectTo: 'caisses' }
    ]
  },
  { path: '**', redirectTo: '' }
];
