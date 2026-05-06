// =====================================================================
//  Fichier : src/app/features/utilisateurs/dialogs/reinitialiser-mdp-dialog.component.ts
// =====================================================================
import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Utilisateur } from '@core/models/models';
import { UtilisateurService } from '@core/services/admin.services';

@Component({
  selector: 'rts-reinitialiser-mdp-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Réinitialiser le mot de passe</h2>

    <form [formGroup]="form" (ngSubmit)="valider()">
      <mat-dialog-content class="dialog-content">
        <p class="info">
          Utilisateur :
          <strong>{{ data.utilisateur.prenom }} {{ data.utilisateur.nom }}</strong>
          ({{ data.utilisateur.login }})
        </p>

        <mat-form-field appearance="outline">
          <mat-label>Nouveau mot de passe</mat-label>
          <input
            matInput
            [type]="afficher() ? 'text' : 'password'"
            formControlName="nouveau"
            autocomplete="new-password"
          />
          <button
            mat-icon-button
            matSuffix
            type="button"
            (click)="afficher.set(!afficher())"
            [attr.aria-label]="afficher() ? 'Masquer' : 'Afficher'"
          >
            <mat-icon>{{ afficher() ? 'visibility_off' : 'visibility' }}</mat-icon>
          </button>
          <mat-hint>Minimum 8 caractères</mat-hint>
          @if (form.controls.nouveau.hasError('minlength')) {
            <mat-error>Le mot de passe doit faire au moins 8 caractères</mat-error>
          }
        </mat-form-field>

        <button
          mat-stroked-button
          type="button"
          class="bouton-generer"
          (click)="genererMdp()"
        >
          <mat-icon>autorenew</mat-icon>
          Générer un mot de passe aléatoire
        </button>

        <p class="warn">
          <mat-icon>info</mat-icon>
          Pensez à communiquer ce mot de passe à l'utilisateur de manière sécurisée.
        </p>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-button type="button" (click)="dialogRef.close()">Annuler</button>
        <button
          mat-flat-button
          color="primary"
          type="submit"
          [disabled]="form.invalid || loading()"
        >
          <mat-icon>check</mat-icon>
          Réinitialiser
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: [`
    .dialog-content {
      display: flex;
      flex-direction: column;
      gap: 12px;
      min-width: 400px;
      padding-top: 8px;
    }
    .info {
      margin: 0 0 4px;
      color: rgba(0, 0, 0, 0.6);
      font-size: 14px;
    }
    .bouton-generer {
      align-self: flex-start;
    }
    .warn {
      display: flex;
      align-items: center;
      gap: 6px;
      margin: 8px 0 0;
      padding: 8px 12px;
      border-radius: 6px;
      background: #fff8e1;
      color: #8a6d00;
      font-size: 13px;
    }
    .warn mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }
  `]
})
export class ReinitialiserMdpDialogComponent {
  readonly dialogRef = inject(MatDialogRef<ReinitialiserMdpDialogComponent>);
  readonly data = inject<{ utilisateur: Utilisateur }>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(UtilisateurService);
  private readonly snackBar = inject(MatSnackBar);

  readonly loading = signal(false);
  readonly afficher = signal(false);

  readonly form = this.fb.nonNullable.group({
    nouveau: ['', [Validators.required, Validators.minLength(8)]]
  });

  /** Génère un mot de passe aléatoire conforme aux règles métier. */
  genererMdp(): void {
    const majuscules = 'ABCDEFGHJKMNPQRSTUVWXYZ';
    const minuscules = 'abcdefghjkmnpqrstuvwxyz';
    const chiffres   = '23456789';
    const speciaux   = '@#$%&*';
    const tous       = majuscules + minuscules + chiffres + speciaux;

    // Au moins un caractère de chaque famille
    let mdp =
      majuscules[Math.floor(Math.random() * majuscules.length)] +
      minuscules[Math.floor(Math.random() * minuscules.length)] +
      chiffres[Math.floor(Math.random() * chiffres.length)] +
      speciaux[Math.floor(Math.random() * speciaux.length)];

    // Compléter à 12 caractères
    for (let i = 0; i < 8; i++) {
      mdp += tous[Math.floor(Math.random() * tous.length)];
    }

    // Mélanger
    mdp = mdp.split('').sort(() => Math.random() - 0.5).join('');

    this.form.controls.nouveau.setValue(mdp);
    this.afficher.set(true);
  }

  valider(): void {
    if (this.form.invalid) return;
    const nouveau = this.form.controls.nouveau.value;

    this.loading.set(true);
    this.service.changerMotDePasse(this.data.utilisateur.id, nouveau).subscribe({
      next: () => {
        this.snackBar.open(
          `Mot de passe réinitialisé pour ${this.data.utilisateur.login}`,
          'OK',
          { duration: 3500, panelClass: ['snackbar-success'] }
        );
        this.dialogRef.close(true);
      },
      error: () => this.loading.set(false)
    });
  }
}
