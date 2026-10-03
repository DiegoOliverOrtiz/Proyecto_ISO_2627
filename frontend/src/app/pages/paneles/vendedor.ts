import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../login/auth.service';

@Component({
  selector: 'app-panel-vendedor',
  standalone: true,
  template: `
    <p>Bienvenido vendedor</p>
    <button type="button" [disabled]="cerrandoSesion()" (click)="cerrarSesion()">
      {{ cerrandoSesion() ? 'Cerrando sesión...' : 'Cerrar sesión' }}
    </button>
    @if (mensajeError()) {
      <p role="alert">{{ mensajeError() }}</p>
    }
  `
})
export class PanelVendedor {
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
