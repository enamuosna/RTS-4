import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Role } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';

interface NavItem {
  label: string;
  icon: string;
  route: string;
  roles: Role[];
}

@Component({
  selector: 'rts-main-layout',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatIconModule,
    MatButtonModule,
    MatListModule,
    MatDividerModule,
    MatMenuModule,
    MatTooltipModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './main-layout.component.html',
  styleUrls: ['./main-layout.component.css']
})
export class MainLayoutComponent {
  private readonly auth = inject(AuthService);

  readonly currentUser = this.auth.currentUser;

  readonly initiales = computed(() => {
    const name = this.currentUser()?.nomComplet ?? '';
    return name
      .split(' ')
      .filter((p) => p.length > 0)
      .slice(0, 2)
      .map((p) => p.charAt(0).toUpperCase())
      .join('');
  });

  private readonly navItems = signal<NavItem[]>([
    { label: 'Tableau de bord', icon: 'dashboard',      route: '/dashboard',    roles: ['ADMIN', 'SUPERVISEUR'] },
    { label: 'Caisses',         icon: 'point_of_sale',  route: '/caisses',      roles: ['ADMIN', 'SUPERVISEUR', 'CAISSIER'] },
    { label: 'Opérations',      icon: 'receipt_long',   route: '/operations',   roles: ['ADMIN', 'SUPERVISEUR', 'CAISSIER'] },
    { label: 'Journaux',        icon: 'event_note',     route: '/journaux',     roles: ['ADMIN', 'SUPERVISEUR'] },
    { label: 'Clients',         icon: 'business',       route: '/clients',      roles: ['ADMIN', 'SUPERVISEUR', 'CAISSIER'] },
    { label: 'Catégories',      icon: 'category',       route: '/categories',   roles: ['ADMIN'] },
    { label: 'Utilisateurs',    icon: 'people',         route: '/utilisateurs', roles: ['ADMIN'] }
  ]);

  readonly visibleNavItems = computed(() =>
    this.navItems().filter((item) => this.auth.hasRole(...item.roles))
  );

  logout(): void {
    this.auth.logout();
  }
}