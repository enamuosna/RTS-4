import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Client } from '../../../core/models/models';
import { ClientService } from '../../../core/services/admin.services';

@Component({
  selector: 'rts-client-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatSlideToggleModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './client-dialog.component.html',
  styleUrls: ['./client-dialog.component.css']
})
export class ClientDialogComponent {
  readonly dialogRef = inject(MatDialogRef<ClientDialogComponent>);
  readonly data = inject<Client | null>(MAT_DIALOG_DATA, { optional: true });
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ClientService);
  private readonly snackBar = inject(MatSnackBar);

  readonly form = this.fb.nonNullable.group({
    raisonSociale: [this.data?.raisonSociale ?? '', Validators.required],
    identifiantFiscal: [this.data?.identifiantFiscal ?? ''],
    telephone: [this.data?.telephone ?? ''],
    email: [this.data?.email ?? ''],
    adresse: [this.data?.adresse ?? ''],
    actif: [this.data?.actif ?? true]
  });

  valider(): void {
    if (this.form.invalid) return;
    const dto = this.form.getRawValue();
    const obs = this.data
      ? this.service.modifier(this.data.id, dto)
      : this.service.creer(dto);
    obs.subscribe((c) => {
      this.snackBar.open('Client enregistré', 'OK', {
        duration: 2500,
        panelClass: ['snackbar-success']
      });
      this.dialogRef.close(c);
    });
  }
}