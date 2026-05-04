import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { Utilisateur } from '../../../core/models/models';
import { UtilisateurService } from '../../../core/services/admin.services';

@Component({
  selector: 'rts-affecter-caissier-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './affecter-caissier-dialog.component.html',
  styleUrls: ['./affecter-caissier-dialog.component.css']
})
export class AffecterCaissierDialogComponent implements OnInit {
  readonly dialogRef = inject(MatDialogRef<AffecterCaissierDialogComponent>);
  private readonly service = inject(UtilisateurService);

  readonly caissiers = signal<Utilisateur[]>([]);
  caissierId: number | null = null;

  ngOnInit(): void {
    this.service.lister().subscribe((list) =>
      this.caissiers.set(list.filter((u) => u.role === 'CAISSIER' && u.actif))
    );
  }
}