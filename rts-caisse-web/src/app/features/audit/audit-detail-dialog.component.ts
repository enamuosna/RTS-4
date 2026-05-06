// =====================================================================
//  Dialog de détail d'une entrée d'audit
//
//  À placer dans :
//    rts-caisse-web/src/app/pages/audit/audit-detail-dialog.component.ts
// =====================================================================

import { CommonModule, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef
} from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuditLog } from '../../core/services/audit.service';

@Component({
  selector: 'rts-audit-detail-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    DatePipe,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule
  ],
  template: `
    <h2 mat-dialog-title class="dlg-title">
      <mat-icon class="title-icon" [class.ok]="log.success" [class.ko]="!log.success">
        {{ log.success ? 'check_circle' : 'cancel' }}
      </mat-icon>
      Détail de l'événement #{{ log.id }}
    </h2>

    <mat-dialog-content class="dlg-content">

      <section>
        <h3>Action</h3>
        <dl>
          <dt>Type</dt>             <dd>{{ log.actionLibelle }} <code>({{ log.action }})</code></dd>
          <dt>Date & heure</dt>     <dd>{{ log.createdAt | date: 'dd/MM/yyyy HH:mm:ss.SSS' }}</dd>
          <dt>Statut</dt>
          <dd>
            <span class="status" [class.ok]="log.success" [class.ko]="!log.success">
              {{ log.success ? 'Succès' : 'Échec' }}
            </span>
          </dd>
          @if (!log.success && log.errorMessage) {
            <dt>Message d'erreur</dt>
            <dd class="error-msg">{{ log.errorMessage }}</dd>
          }
        </dl>
      </section>

      <section>
        <h3>Auteur</h3>
        @if (log.userLogin) {
          <dl>
            <dt>Login</dt>      <dd><strong>{{ log.userLogin }}</strong></dd>
            @if (log.userMatricule) {
              <dt>Matricule</dt><dd><code>{{ log.userMatricule }}</code></dd>
            }
            @if (log.userNomComplet) {
              <dt>Nom complet</dt><dd>{{ log.userNomComplet }}</dd>
            }
            @if (log.userRole) {
              <dt>Rôle</dt>
              <dd>
                <span class="role-badge role-{{ log.userRole.toLowerCase() }}">
                  {{ log.userRole }}
                </span>
              </dd>
            }
            @if (log.userId) {
              <dt>ID utilisateur</dt><dd><code>{{ log.userId }}</code></dd>
            }
          </dl>
        } @else {
          <p class="muted">Aucun utilisateur authentifié (action anonyme).</p>
        }
      </section>

      @if (log.entityType || log.entityId) {
        <section>
          <h3>Entité affectée</h3>
          <dl>
            @if (log.entityType) {
              <dt>Type</dt><dd><code>{{ log.entityType }}</code></dd>
            }
            @if (log.entityId) {
              <dt>ID</dt><dd><code>#{{ log.entityId }}</code></dd>
            }
            @if (log.entityLabel) {
              <dt>Référence</dt><dd>{{ log.entityLabel }}</dd>
            }
          </dl>
        </section>
      }

      @if (log.ipAddress || log.userAgent || log.httpPath) {
        <section>
          <h3>Contexte HTTP</h3>
          <dl>
            @if (log.ipAddress) {
              <dt>Adresse IP</dt><dd><code>{{ log.ipAddress }}</code></dd>
            }
            @if (log.httpMethod || log.httpPath) {
              <dt>Requête</dt>
              <dd><code>{{ log.httpMethod }} {{ log.httpPath }}</code></dd>
            }
            @if (log.userAgent) {
              <dt>User-Agent</dt>
              <dd class="ua">{{ log.userAgent }}</dd>
            }
          </dl>
        </section>
      }

      @if (log.details) {
        <section>
          <h3>Détails</h3>
          <pre class="details-block">{{ log.details }}</pre>
        </section>
      }

    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-stroked-button (click)="copierJSON()">
        <mat-icon>content_copy</mat-icon>
        Copier en JSON
      </button>
      <button mat-flat-button color="primary" mat-dialog-close>Fermer</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .dlg-title {
      display: flex;
      align-items: center;
      gap: 10px;
      margin: 0;
      font-size: 18px;
      color: #0A4D8C;
    }
    .title-icon { font-size: 24px; height: 24px; width: 24px; }
    .title-icon.ok { color: #16a34a; }
    .title-icon.ko { color: #dc2626; }

    .dlg-content {
      padding-top: 12px;
      max-height: 70vh;
    }
    section {
      margin-bottom: 18px;
      padding-bottom: 14px;
      border-bottom: 1px solid #f1f5f9;
    }
    section:last-child { border-bottom: none; }

    section h3 {
      margin: 0 0 8px;
      font-size: 13px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: #6b7280;
      font-weight: 600;
    }

    dl {
      display: grid;
      grid-template-columns: 140px 1fr;
      gap: 6px 16px;
      margin: 0;
    }
    dt {
      color: #6b7280;
      font-size: 13px;
      font-weight: 500;
    }
    dd {
      margin: 0;
      font-size: 13px;
      color: #111827;
      word-break: break-word;
    }

    code {
      background: #f3f4f6;
      padding: 1px 5px;
      border-radius: 3px;
      font-size: 12px;
      font-family: 'Consolas', 'Monaco', monospace;
    }

    .status {
      padding: 2px 10px;
      border-radius: 12px;
      font-weight: 600;
      font-size: 12px;
    }
    .status.ok { background: #ecfdf5; color: #065f46; }
    .status.ko { background: #fef2f2; color: #b91c1c; }

    .error-msg {
      color: #b91c1c;
      background: #fef2f2;
      padding: 8px 10px;
      border-radius: 6px;
      border-left: 3px solid #dc2626;
    }

    .role-badge {
      display: inline-block;
      padding: 1px 8px;
      border-radius: 10px;
      font-size: 11px;
      font-weight: 600;
    }
    .role-admin       { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; }
    .role-superviseur { background: #eff6ff; color: #1d4ed8; border: 1px solid #bfdbfe; }
    .role-caissier    { background: #f0fdf4; color: #166534; border: 1px solid #bbf7d0; }

    .ua {
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 11px;
      color: #4b5563;
    }

    .details-block {
      background: #0f172a;
      color: #e2e8f0;
      padding: 12px;
      border-radius: 6px;
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 12px;
      white-space: pre-wrap;
      word-break: break-word;
      margin: 0;
      max-height: 240px;
      overflow: auto;
    }

    .muted { color: #9ca3af; font-style: italic; margin: 0; }
  `]
})
export class AuditDetailDialogComponent {
  protected readonly log: AuditLog = inject(MAT_DIALOG_DATA);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialogRef = inject(MatDialogRef<AuditDetailDialogComponent>);

  copierJSON(): void {
    const json = JSON.stringify(this.log, null, 2);
    navigator.clipboard.writeText(json).then(
      () => this.snackBar.open('Copié dans le presse-papiers', 'OK',
              { duration: 2000, panelClass: ['snackbar-info'] }),
      () => this.snackBar.open('Échec de la copie', 'OK',
              { duration: 2000, panelClass: ['snackbar-error'] })
    );
  }
}
