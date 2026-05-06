import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Role, Utilisateur } from '../../core/models/models';
import { UtilisateurService } from '../../core/services/admin.services';
import { UtilisateurDialogComponent } from './dialogs/utilisateur-dialog.component';

@Component({
  selector: 'rts-utilisateurs',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatSlideToggleModule,
    MatTooltipModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './utilisateurs.component.html',
  styleUrls: ['./utilisateurs.component.css']
})
export class UtilisateursComponent implements OnInit {
  private readonly service   = inject(UtilisateurService);
  private readonly dialog    = inject(MatDialog);
  private readonly snackBar  = inject(MatSnackBar);
  private readonly http      = inject(HttpClient);

  readonly utilisateurs = signal<Utilisateur[]>([]);
  readonly isSuperAdmin = signal(false);
  readonly colonnes = ['matricule', 'nom', 'login', 'email', 'role', 'actif', 'actions'];

  ngOnInit(): void {
    this.http
        .get<{ superAdmin: boolean }>('/api/utilisateurs/me/super-admin')
        .subscribe(r => this.isSuperAdmin.set(r.superAdmin));

    this.charger();
  }

  charger(): void {
    this.service.lister().subscribe(list => this.utilisateurs.set(list));
  }

  creer(): void {
    this.dialog
        .open(UtilisateurDialogComponent, { width: '620px' })
        .afterClosed()
        .subscribe(created => {
          if (created) this.charger();
        });
  }

  activer(u: Utilisateur, actif: boolean): void {
    this.service.activer(u.id, actif).subscribe(() => {
      this.snackBar.open(
          actif ? 'Utilisateur activé' : 'Utilisateur désactivé',
          'OK',
          { duration: 2500, panelClass: ['snackbar-info'] }
      );
      this.charger();
    });
  }

  supprimer(u: Utilisateur): void {
    if (!confirm(`Désactiver l'utilisateur "${u.login}" ?`)) return;
    this.service.supprimer(u.id).subscribe(() => {
      this.snackBar.open('Utilisateur désactivé', 'OK', {
        duration: 2500,
        panelClass: ['snackbar-info']
      });
      this.charger();
    });
  }

  modifierLogin(u: Utilisateur): void {
    // TODO: ouvrir un dialog de modification de login
    console.log('Modifier login de', u.login);
  }

  reinitialiserMdp(u: Utilisateur): void {
    this.http
        .post(`/api/utilisateurs/${u.id}/reinitialiser-mdp`, {})
        .subscribe(() => {
          this.snackBar.open(
              `Mot de passe réinitialisé pour "${u.login}"`,
              'OK',
              { duration: 3000, panelClass: ['snackbar-success'] }
          );
        });
  }

  roleBadge(role: Role): string {
    return role === 'ADMIN'
        ? 'badge-info'
        : role === 'SUPERVISEUR'
            ? 'badge-warning'
            : 'badge-neutral';
  }
}