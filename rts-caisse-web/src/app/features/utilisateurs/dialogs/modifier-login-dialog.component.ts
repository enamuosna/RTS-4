
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
  selector: 'rts-modifier-login-dialog',
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
    <h2 mat-dialog-title>Modifier le login</h2>

    <form [formGroup]="form" (ngSubmit)="valider()">
      <mat-dialog-content class="dialog-content">
        <p class="info">
          Utilisateur :
          <strong>{{ data.utilisateur.prenom }} {{ data.utilisateur.nom }}</strong>
          ({{ data.utilisateur.matricule }})
        </p>

        <mat-form-field appearance="outline">
          <mat-label>Ancien login</mat-label>
          <input matInput [value]="data.utilisateur.login" disabled />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Nouveau login</mat-label>
          <input matInput formControlName="nouveau" autocomplete="off" />
          <mat-hint>Minimum 3 caractères, sans espace</mat-hint>
          @if (form.controls.nouveau.hasError('minlength')) {
            <mat-error>Le login doit faire au moins 3 caractères</mat-error>
          }
          @if (form.controls.nouveau.hasError('pattern')) {
            <mat-error>Aucun espace ni caractère spécial</mat-error>
          }
        </mat-form-field>
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
          Enregistrer
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: [`
    .dialog-content {
      display: flex;
      flex-direction: column;
      gap: 12px;
      min-width: 380px;
      padding-top: 8px;
    }
    .info {
      margin: 0 0 4px;
      color: rgba(0, 0, 0, 0.6);
      font-size: 14px;
    }
  `]
})
export class ModifierLoginDialogComponent {
  readonly dialogRef = inject(MatDialogRef<ModifierLoginDialogComponent>);
  readonly data = inject<{ utilisateur: Utilisateur }>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(UtilisateurService);
  private readonly snackBar = inject(MatSnackBar);

  readonly loading = signal(false);

  readonly form = this.fb.nonNullable.group({
    nouveau: [
      '',
      [
        Validators.required,
        Validators.minLength(3),
        Validators.pattern(/^[A-Za-z0-9._-]+$/)
      ]
    ]
  });

  valider(): void {
    if (this.form.invalid) return;
    const nouveau = this.form.controls.nouveau.value.trim();

    if (nouveau === this.data.utilisateur.login) {
      this.snackBar.open('Le nouveau login est identique à l\'ancien.', 'OK', {
        duration: 3000,
        panelClass: ['snackbar-error']
      });
      return;
    }

    this.loading.set(true);
    this.service.modifierLogin(this.data.utilisateur.id, nouveau).subscribe({
      next: (u) => {
        this.snackBar.open(`Login modifié : ${u.login}`, 'OK', {
          duration: 3000,
          panelClass: ['snackbar-success']
        });
        this.dialogRef.close(u);
      },
      error: () => this.loading.set(false)
    });
  }
}
