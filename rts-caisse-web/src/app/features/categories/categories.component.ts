import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { CategorieOperation, TypeOperation } from '../../core/models/models';
import { CategorieService } from '../../core/services/admin.services';
import { CategorieDialogComponent } from './dialogs/categorie-dialog.component';

@Component({
  selector: 'rts-categories',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatButtonToggleModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './categories.component.html',
  styleUrls: ['./categories.component.css']
})
export class CategoriesComponent implements OnInit {
  private readonly service = inject(CategorieService);
  private readonly dialog = inject(MatDialog);

  readonly categories = signal<CategorieOperation[]>([]);
  readonly colonnes = ['code', 'libelle', 'type', 'actif', 'actions'];

  filtreType: '' | TypeOperation = '';

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    const type = this.filtreType === '' ? undefined : this.filtreType;
    this.service.lister(type).subscribe((list) => this.categories.set(list));
  }

  creer(): void {
    this.dialog
      .open(CategorieDialogComponent, { width: '500px' })
      .afterClosed()
      .subscribe((c) => {
        if (c) this.charger();
      });
  }

  modifier(c: CategorieOperation): void {
    this.dialog
      .open(CategorieDialogComponent, { width: '500px', data: c })
      .afterClosed()
      .subscribe((updated) => {
        if (updated) this.charger();
      });
  }
}