import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Caisse } from '../../../core/models/models';
import { CaisseService } from '../../../core/services/admin.services';

@Component({
  selector: 'rts-caisse-dialog',
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
  templateUrl: './caisse-dialog.component.html',
  styleUrls: ['./caisse-dialog.component.css']
})
export class CaisseDialogComponent {
  readonly dialogRef = inject(MatDialogRef<CaisseDialogComponent>);
  readonly data = inject<Caisse | null>(MAT_DIALOG_DATA, { optional: true });
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CaisseService);
  private readonly snackBar = inject(MatSnackBar);

  readonly form = this.fb.nonNullable.group({
    code: [this.data?.code ?? '', Validators.required],
    libelle: [this.data?.libelle ?? '', Validators.required],
    emplacement: [this.data?.emplacement ?? '']
  });

  valider(): void {
    if (this.form.invalid) return;
    const dto = this.form.getRawValue();
    const obs = this.data
      ? this.service.modifier(this.data.id, dto)
      : this.service.creer(dto);
    obs.subscribe((c) => {
      this.snackBar.open('Caisse enregistrée', 'OK', {
        duration: 2500,
        panelClass: ['snackbar-success']
      });
      this.dialogRef.close(c);
    });
  }
}