import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router } from '@angular/router';
import { Role } from '../../../core/models/models';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'rts-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly annee = new Date().getFullYear();
  readonly loading = signal(false);
  readonly showPassword = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    login: ['', [Validators.required]],
    motDePasse: ['', [Validators.required]]
  });

  togglePassword(): void {
    this.showPassword.update((v) => !v);
  }

  submit(): void {
    if (this.form.invalid || this.loading()) {
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);

    this.auth.login(this.form.getRawValue()).subscribe({
      next: (response) => {
        // Priorité 1 : redirection demandée par le guard (ex : l'utilisateur
        // voulait aller sur une page précise avant d'être envoyé au login).
        const redirect = this.route.snapshot.queryParamMap.get('redirect');
        if (redirect && redirect !== '/login' && redirect !== '/unauthorized') {
          void this.router.navigateByUrl(redirect);
          return;
        }
        // Priorité 2 : page d'atterrissage par défaut selon le rôle.
        void this.router.navigateByUrl(this.landingPageFor(response.role));
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(
          err.status === 401
            ? 'Identifiants invalides.'
            : err.error?.message ?? 'Connexion impossible.'
        );
      }
    });
  }

  /**
   * Chaque rôle a sa page d'atterrissage logique :
   *   - ADMIN / SUPERVISEUR : tableau de bord
   *   - CAISSIER : liste des caisses (d'où il peut enchaîner sur ses opérations)
   */
  private landingPageFor(role: Role): string {
    switch (role) {
      case 'ADMIN':
      case 'SUPERVISEUR':
        return '/dashboard';
      case 'CAISSIER':
        return '/caisses';
      default:
        return '/caisses';
    }
  }
}