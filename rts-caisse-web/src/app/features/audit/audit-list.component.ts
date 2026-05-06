// =====================================================================
//  Composant de consultation du journal d'audit RTS Caisse (admin)
//
//  À placer dans :
//    rts-caisse-web/src/app/pages/audit/audit-list.component.ts
//    rts-caisse-web/src/app/pages/audit/audit-list.component.html
//    rts-caisse-web/src/app/pages/audit/audit-list.component.css
//
//  Route à ajouter dans app.routes.ts :
//
//    {
//      path: 'admin/audit',
//      loadComponent: () =>
//        import('./pages/audit/audit-list.component')
//          .then(m => m.AuditListComponent),
//      canActivate: [adminGuard]
//    }
// =====================================================================

import { CommonModule, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';

import {
  AUDIT_ACTIONS_LIST,
  AuditAction,
  AuditFiltres,
  AuditLog,
  AuditService
} from '../../core/services/audit.service';
import { AuditDetailDialogComponent } from './audit-detail-dialog.component';

type FiltreSucces = 'all' | 'success' | 'failure';

@Component({
  selector: 'rts-audit-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [provideNativeDateAdapter()],
  imports: [
    CommonModule,
    FormsModule,
    DatePipe,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatChipsModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
    MatDatepickerModule,
    MatDialogModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './audit-list.component.html',
  styleUrls: ['./audit-list.component.css']
})
export class AuditListComponent implements OnInit {

  private readonly auditService = inject(AuditService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  // ----- État affichage -----
  readonly logs = signal<AuditLog[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);

  readonly colonnes = [
    'date', 'action', 'utilisateur', 'entite', 'ip', 'statut', 'actions'
  ];

  readonly actionsList = AUDIT_ACTIONS_LIST;

  // ----- Filtres (modèle 2-way) -----
  filtreAction: AuditAction | '' = '';
  filtreUserId: number | null = null;
  filtreEntityType: string = '';
  filtreEntityId: number | null = null;
  filtreSucces: FiltreSucces = 'all';
  filtreDateFrom: Date | null = null;
  filtreDateTo: Date | null = null;

  // ----- Pagination -----
  pageIndex = 0;
  pageSize = 25;
  readonly pageSizes = [10, 25, 50, 100, 200];

  ngOnInit(): void {
    this.charger();
  }

  // ------------------------------------------------------------------
  //  Chargement
  // ------------------------------------------------------------------

  charger(): void {
    this.loading.set(true);

    const filtres: AuditFiltres = {
      action: this.filtreAction || undefined,
      userId: this.filtreUserId ?? undefined,
      entityType: this.filtreEntityType?.trim() || undefined,
      entityId: this.filtreEntityId ?? undefined,
      success: this.filtreSucces === 'all'
        ? undefined
        : this.filtreSucces === 'success',
      dateFrom: this.filtreDateFrom ?? undefined,
      dateTo: this.filtreDateTo ?? undefined
    };

    this.auditService
      .rechercher(filtres, { page: this.pageIndex, size: this.pageSize })
      .subscribe({
        next: (page) => {
          this.logs.set(page.content);
          this.total.set(page.totalElements);
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          this.snackBar.open(
            'Erreur lors du chargement : ' + (err?.error?.message ?? err.message),
            'OK',
            { duration: 4000, panelClass: ['snackbar-error'] });
        }
      });
  }

  appliquerFiltres(): void {
    this.pageIndex = 0;
    this.charger();
  }

  reinitialiserFiltres(): void {
    this.filtreAction = '';
    this.filtreUserId = null;
    this.filtreEntityType = '';
    this.filtreEntityId = null;
    this.filtreSucces = 'all';
    this.filtreDateFrom = null;
    this.filtreDateTo = null;
    this.pageIndex = 0;
    this.charger();
  }

  rafraichir(): void {
    this.charger();
  }

  changerPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.charger();
  }

  // ------------------------------------------------------------------
  //  Détail
  // ------------------------------------------------------------------

  voirDetail(log: AuditLog): void {
    this.dialog.open(AuditDetailDialogComponent, {
      data: log,
      width: '720px',
      maxWidth: '95vw',
      autoFocus: false
    });
  }

  // ------------------------------------------------------------------
  //  Helpers d'affichage
  // ------------------------------------------------------------------

  /** Couleur de chip selon le type d'action (succès/échec/sécurité). */
  classeAction(log: AuditLog): string {
    if (!log.success) return 'chip-echec';
    switch (log.action) {
      case 'LOGIN_SUCCESS':
      case 'LOGOUT':
        return 'chip-auth';
      case 'OUVRIR_CAISSE':
      case 'CLOTURER_CAISSE':
      case 'VALIDER_JOURNAL':
        return 'chip-caisse';
      case 'CREER_OPERATION':
      case 'ANNULER_OPERATION':
        return 'chip-operation';
      case 'EXPORTER_JOURNAL_EXCEL':
      case 'IMPRIMER_RECU':
      case 'TELECHARGER_RECU_PDF':
      case 'ENVOYER_WHATSAPP':
        return 'chip-export';
      case 'CREER_UTILISATEUR':
      case 'MODIFIER_UTILISATEUR':
      case 'DESACTIVER_UTILISATEUR':
      case 'REACTIVER_UTILISATEUR':
      case 'REINITIALISER_MOT_DE_PASSE':
      case 'CHANGER_MOT_DE_PASSE':
        return 'chip-admin';
      default:
        return 'chip-default';
    }
  }

  /** Icône Material selon l'action. */
  iconeAction(log: AuditLog): string {
    if (!log.success) return 'error_outline';
    switch (log.action) {
      case 'LOGIN_SUCCESS':         return 'login';
      case 'LOGOUT':                return 'logout';
      case 'OUVRIR_CAISSE':         return 'lock_open';
      case 'CLOTURER_CAISSE':       return 'lock';
      case 'VALIDER_JOURNAL':       return 'verified';
      case 'CREER_OPERATION':       return 'add_circle';
      case 'ANNULER_OPERATION':     return 'cancel';
      case 'EXPORTER_JOURNAL_EXCEL': return 'file_download';
      case 'ENVOYER_WHATSAPP':      return 'chat';
      case 'IMPRIMER_RECU':         return 'print';
      case 'TELECHARGER_RECU_PDF':  return 'picture_as_pdf';
      case 'CREER_UTILISATEUR':     return 'person_add';
      case 'DESACTIVER_UTILISATEUR': return 'person_off';
      case 'REACTIVER_UTILISATEUR': return 'person';
      case 'REINITIALISER_MOT_DE_PASSE':
      case 'CHANGER_MOT_DE_PASSE':  return 'password';
      default:                       return 'info';
    }
  }
}
