import { CommonModule, CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Caisse, StatutCaisse } from '../../core/models/models';
import { CaisseService } from '../../core/services/admin.services';
import { AffecterCaissierDialogComponent } from './dialogs/affecter-caissier-dialog.component';
import { CaisseDialogComponent } from './dialogs/caisse-dialog.component';

@Component({
  selector: 'rts-caisses',
  standalone: true,
  imports: [
    CommonModule,
    CurrencyPipe,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './caisses.component.html',
  styleUrls: ['./caisses.component.css']
})
export class CaissesComponent implements OnInit {
  private readonly service = inject(CaisseService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly caisses = signal<Caisse[]>([]);

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.service.lister().subscribe((list) => this.caisses.set(list));
  }

  creer(): void {
    this.dialog
      .open(CaisseDialogComponent, { width: '540px' })
      .afterClosed()
      .subscribe((c) => {
        if (c) this.charger();
      });
  }

  modifier(caisse: Caisse): void {
    this.dialog
      .open(CaisseDialogComponent, { width: '540px', data: caisse })
      .afterClosed()
      .subscribe((c) => {
        if (c) this.charger();
      });
  }

  affecter(caisse: Caisse): void {
    this.dialog
      .open(AffecterCaissierDialogComponent, { width: '480px' })
      .afterClosed()
      .subscribe((caissierId: number | undefined) => {
        if (!caissierId) return;
        this.service.affecterCaissier(caisse.id, caissierId).subscribe(() => {
          this.snackBar.open('Caissier affecté', 'OK', {
            duration: 2500,
            panelClass: ['snackbar-success']
          });
          this.charger();
        });
      });
  }

  suspendre(caisse: Caisse): void {
    const suspendre = caisse.statut !== 'SUSPENDUE';
    this.service.suspendre(caisse.id, suspendre).subscribe(() => {
      this.snackBar.open(suspendre ? 'Caisse suspendue' : 'Caisse réactivée', 'OK', {
        duration: 2500,
        panelClass: ['snackbar-info']
      });
      this.charger();
    });
  }

  statutBadge(statut: StatutCaisse): string {
    return statut === 'OUVERTE'
      ? 'badge-success'
      : statut === 'SUSPENDUE'
        ? 'badge-warning'
        : 'badge-neutral';
  }
}