import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../login/auth.service';

@Component({
  selector: 'app-panel-administrador',
  standalone: true,
  imports: [RouterLink],
  styleUrl: './administrador.css',
  template: `
    <main class="admin-shell">
      <section class="admin-content">
        <header class="topbar">
          <a class="topbar-brand" routerLink="/admin">EsiBuy</a>
          <span class="breadcrumb">
            Gestión
            <span aria-hidden="true">/</span>
            Panel de administración
          </span>
          <a class="account profile-account" routerLink="/perfil" aria-label="Abrir mi perfil">
            <svg class="profile-icon" aria-hidden="true" viewBox="0 0 24 24" focusable="false">
              <circle cx="12" cy="8" r="3.5"></circle>
              <path d="M5 20c.7-3.4 3.1-5 7-5s6.3 1.6 7 5"></path>
            </svg>
            <span>Administrador</span>
          </a>
        </header>

        <div class="welcome-block">
          <p class="eyebrow">Espacio de gestión</p>
          <h1>Bienvenido, administrador</h1>
          <p>
            Consulta y gestiona la información administrativa de EsiBuy.
          </p>

          <a class="primary-action" routerLink="/admin/usuarios">
            Consultar usuarios
          </a>
        </div>

        <div class="overview-grid">
          <article>
            <span class="card-label">Área activa</span>
            <strong>Usuarios</strong>
            <small>Consulta administrativa</small>

            <a routerLink="/admin/usuarios">
              Abrir listado
              <span aria-hidden="true">→</span>
            </a>
          </article>

          <article>
            <span class="card-label">Área activa</span>
            <strong>Categorías</strong>
            <small>Consulta administrativa</small>

            <a routerLink="/admin/categorias">
              Abrir categorías
              <span aria-hidden="true">→</span>
            </a>
          </article>

          <article>
            <span class="card-label">Área activa</span>
            <strong>Administradores</strong>
            <small>Gestión interna</small>

            <a routerLink="/admin/administradores">
              Abrir administradores
              <span aria-hidden="true">→</span>
            </a>
          </article>
        </div>

        <button
          class="logout-button"
          type="button"
          [disabled]="cerrandoSesion()"
          (click)="cerrarSesion()">
          {{ cerrandoSesion()
            ? 'Cerrando sesión...'
            : 'Cerrar sesión'
          }}
        </button>

        @if (mensajeError()) {
          <p class="error-message" role="alert">
            {{ mensajeError() }}
          </p>
        }
      </section>
    </main>
  `
})
export class PanelAdministrador {
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
