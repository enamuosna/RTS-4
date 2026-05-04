import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { Client } from '../../core/models/models';
import { ClientService } from '../../core/services/admin.services';
import { ClientDialogComponent } from './dialogs/client-dialog.component';

@Component({
  selector: 'rts-clients',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './clients.component.html',
  styleUrls: ['./clients.component.css']
})
export class ClientsComponent implements OnInit {
  private readonly service = inject(ClientService);
  private readonly dialog = inject(MatDialog);

  readonly clients = signal<Client[]>([]);
  readonly colonnes = ['raisonSociale', 'ninea', 'telephone', 'email', 'actif', 'actions'];
  readonly recherche$ = new Subject<string>();
  terme = '';

  ngOnInit(): void {
    this.charger();
    this.recherche$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => this.service.lister(q || undefined))
      )
      .subscribe((list) => this.clients.set(list));
  }

  charger(): void {
    this.service.lister().subscribe((list) => this.clients.set(list));
  }

  creer(): void {
    this.dialog
      .open(ClientDialogComponent, { width: '620px' })
      .afterClosed()
      .subscribe((c) => {
        if (c) this.charger();
      });
  }

  modifier(c: Client): void {
    this.dialog
      .open(ClientDialogComponent, { width: '620px', data: c })
      .afterClosed()
      .subscribe((updated) => {
        if (updated) this.charger();
      });
  }
}