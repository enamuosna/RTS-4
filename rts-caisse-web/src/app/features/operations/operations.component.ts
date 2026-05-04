import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Caisse, OperationCaisse } from '../../core/models/models';
import { CaisseService } from '../../core/services/admin.services';
import { OperationService } from '../../core/services/caisse.services';

@Component({
  selector: 'rts-operations',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CurrencyPipe,
    DatePipe,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './operations.component.html',
  styleUrls: ['./operations.component.css']
})
export class OperationsComponent implements OnInit {
  private readonly caisseService = inject(CaisseService);
  private readonly operationService = inject(OperationService);
  private readonly snackBar = inject(MatSnackBar);

  readonly caisses = signal<Caisse[]>([]);
  readonly operations = signal<OperationCaisse[]>([]);
  readonly total = signal(0);

  readonly colonnes = [
    'date',
    'numero',
    'type',
    'categorie',
    'motif',
    'mode',
    'caissier',
    'montant',
    'actions'
  ];

  caisseSelectionneeId: number | null = null;
  pageIndex = 0;
  pageSize = 20;

  ngOnInit(): void {
    this.caisseService.lister().subscribe((list) => this.caisses.set(list));
  }

  charger(): void {
    if (!this.caisseSelectionneeId) return;
    this.operationService
      .historiqueParCaisse(this.caisseSelectionneeId, this.pageIndex, this.pageSize)
      .subscribe((page) => {
        this.operations.set(page.content);
        this.total.set(page.totalElements);
      });
  }

  changerPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.charger();
  }

  annuler(operation: OperationCaisse): void {
    const motif = prompt("Motif de l'annulation :");
    if (!motif) return;
    this.operationService.annuler(operation.id, motif).subscribe(() => {
      this.snackBar.open('Opération annulée (contre-passation effectuée)', 'OK', {
        duration: 3000,
        panelClass: ['snackbar-info']
      });
      this.charger();
    });
  }
}