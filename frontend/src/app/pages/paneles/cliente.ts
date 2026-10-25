import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../login/auth.service';

@Component({
  selector: 'app-panel-cliente',
  standalone: true,
  imports: [RouterLink],
  styleUrl: './cliente.css',
  template: `
    <header class="client-header">
      <h1>ESIBuy</h1>
      <nav class="client-actions" aria-label="Cuenta">
        <a class="profile-link" routerLink="/perfil" aria-label="Abrir mi perfil">
          <svg class="profile-icon" aria-hidden="true" viewBox="0 0 24 24" focusable="false">
            <circle cx="12" cy="8" r="3.5"></circle>
            <path d="M5 20c.7-3.4 3.1-5 7-5s6.3 1.6 7 5"></path>
          </svg>
          Mi perfil
        </a>
        <button class="logout-button" type="button" [disabled]="cerrandoSesion()" (click)="cerrarSesion()">
          {{ cerrandoSesion() ? 'Cerrando sesión...' : 'Cerrar sesión' }}
        </button>
      </nav>
    </header>

    <main class="client-content">
      <p>Bienvenido cliente</p>
      @if (mensajeError()) {
        <p class="error-message" role="alert">{{ mensajeError() }}</p>
      }
    </main>
  `
})
export class PanelCliente {
  private readonly servicioAutenticacion = inject(AuthService);
  private readonly enrutador = inject(Router);
  readonly cerrandoSesion = signal(false);
  readonly mensajeError = signal('');

  cerrarSesion(): void {
    this.cerrandoSesion.set(true);
    this.mensajeError.set('');

    this.servicioAutenticacion.cerrarSesion().subscribe({
      next: () => {
        this.cerrandoSesion.set(false);
        void this.enrutador.navigateByUrl('/login');
      },
      error: (error: HttpErrorResponse) => {
        this.cerrandoSesion.set(false);
        this.mensajeError.set(
          error.status === 0
            ? 'No se pudo conectar con el servicio. Inténtalo de nuevo.'
            : 'No se pudo cerrar la sesión. Inténtalo de nuevo.'
        );
      }
    });
  }
}
