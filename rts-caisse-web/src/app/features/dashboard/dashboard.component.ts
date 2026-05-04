import { CurrencyPipe, DatePipe, DecimalPipe, NgClass } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { ChartConfiguration, ChartData } from 'chart.js';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { DashboardResponse } from '../../core/models/models';
import { ReportingService } from '../../core/services/caisse.services';

@Component({
  selector: 'rts-dashboard',
  standalone: true,
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    NgClass,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatTableModule,
    BaseChartDirective
  ],
  providers: [provideCharts(withDefaultRegisterables())],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  private readonly reporting = inject(ReportingService);

  readonly loading = signal(false);
  readonly data = signal<DashboardResponse | null>(null);

  selectedDate: Date = new Date();

  readonly colonnesCaisse = ['code', 'libelle', 'entrees', 'sorties', 'solde'];
  readonly colonnesCategorie = ['code', 'libelle', 'type', 'nombre', 'montant'];

  // Options des graphiques
  readonly doughnutOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'bottom', labels: { padding: 16, font: { size: 12 } } }
    }
  };

  readonly barOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'bottom', labels: { padding: 16, font: { size: 12 } } }
    },
    scales: {
      y: {
        beginAtZero: true,
        ticks: {
          callback: (value) => new Intl.NumberFormat('fr-FR').format(value as number)
        }
      }
    }
  };

  // Données réactives des graphiques (computed)
  readonly doughnutData = computed<ChartData<'doughnut'>>(() => {
    const d = this.data();
    if (!d) return { labels: [], datasets: [] };
    return {
      labels: d.repartitionParCaisse.map((c) => c.libelleCaisse),
      datasets: [
        {
          data: d.repartitionParCaisse.map((c) => c.entrees),
          backgroundColor: ['#0a4d8c', '#e8a317', '#2e7d32', '#1565c0', '#6a1b9a', '#ad1457'],
          borderWidth: 2,
          borderColor: '#fff'
        }
      ]
    };
  });

  readonly barData = computed<ChartData<'bar'>>(() => {
    const d = this.data();
    if (!d) return { labels: [], datasets: [] };

    const labels = d.repartitionParCategorie.map((c) => c.codeCategorie);
    const entrees = d.repartitionParCategorie.map((c) =>
      c.typeOperation === 'ENTREE' ? c.montantTotal : 0
    );
    const sorties = d.repartitionParCategorie.map((c) =>
      c.typeOperation === 'SORTIE' ? c.montantTotal : 0
    );

    return {
      labels,
      datasets: [
        { label: 'Entrées', data: entrees, backgroundColor: '#2e7d32' },
        { label: 'Sorties', data: sorties, backgroundColor: '#c62828' }
      ]
    };
  });

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.loading.set(true);
    const isoDate = this.selectedDate.toISOString().slice(0, 10);
    this.reporting.dashboard(isoDate).subscribe({
      next: (resp) => {
        this.data.set(resp);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  rafraichir(): void {
    this.charger();
  }
}