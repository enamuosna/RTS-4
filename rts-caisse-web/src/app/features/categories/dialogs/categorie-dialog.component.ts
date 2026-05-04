import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CategorieOperation, TypeOperation } from '../../../core/models/models';
import { CategorieService } from '../../../core/services/admin.services';

@Component({
  selector: 'rts-categorie-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatSlideToggleModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './categorie-dialog.component.html',
  styleUrls: ['./categorie-dialog.component.css']
})
export class CategorieDialogComponent {
  readonly dialogRef = inject(MatDialogRef<CategorieDialogComponent>);
  readonly data = inject<CategorieOperation | null>(MAT_DIALOG_DATA, { optional: true });
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CategorieService);
  private readonly snackBar = inject(MatSnackBar);

  readonly form = this.fb.nonNullable.group({
    code: [this.data?.code ?? '', Validators.required],
    libelle: [this.data?.libelle ?? '', Validators.required],
    typeOperation: [(this.data?.typeOperation ?? 'ENTREE') as TypeOperation, Validators.required],
    actif: [this.data?.actif ?? true]
  });

  valider(): void {
    if (this.form.invalid) return;
    const dto = this.form.getRawValue();
    const obs = this.data
      ? this.service.modifier(this.data.id, dto)
      : this.service.creer(dto);
    obs.subscribe((c) => {
      this.snackBar.open('Catégorie enregistrée', 'OK', {
        duration: 2500,
        panelClass: ['snackbar-success']
      });
      this.dialogRef.close(c);
    });
  }
}