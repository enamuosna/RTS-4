import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Role } from '../../../core/models/models';
import { UtilisateurService } from '../../../core/services/admin.services';

@Component({
  selector: 'rts-utilisateur-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './utilisateur-dialog.component.html',
  styleUrls: ['./utilisateur-dialog.component.css']
})
export class UtilisateurDialogComponent {
  readonly dialogRef = inject(MatDialogRef<UtilisateurDialogComponent>);
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(UtilisateurService);
  private readonly snackBar = inject(MatSnackBar);

  readonly loading = signal(false);

  readonly form = this.fb.nonNullable.group({
    matricule: ['', Validators.required],
    login: ['', [Validators.required, Validators.minLength(3)]],
    motDePasse: ['', [Validators.required, Validators.minLength(8)]],
    prenom: ['', Validators.required],
    nom: ['', Validators.required],
    email: [''],
    telephone: [''],
    role: ['CAISSIER' as Role, Validators.required]
  });

  valider(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.service.creer(this.form.getRawValue()).subscribe({
      next: (u) => {
        this.snackBar.open(`Utilisateur créé : ${u.login}`, 'OK', {
          duration: 3000,
          panelClass: ['snackbar-success']
        });
        this.dialogRef.close(u);
      },
      error: () => this.loading.set(false)
    });
  }
}