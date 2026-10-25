import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { PerfilResponse, PerfilService } from './perfil.service';

@Component({
  selector: 'app-perfil',
  standalone: true,
  styleUrl: './perfil.css',
  templateUrl: './perfil.html'
})
export class Perfil {
  private readonly servicioPerfil = inject(PerfilService);
  private readonly enrutador = inject(Router);

  readonly perfil = signal<PerfilResponse | null>(null);
  readonly cargando = signal(true);
  readonly mensajeError = signal('');

  constructor() {
    this.cargarPerfil();
  }

  cargarPerfil(): void {
    this.cargando.set(true);
    this.mensajeError.set('');

    this.servicioPerfil.consultarPerfil().subscribe({
      next: (respuesta) => {
        this.perfil.set(respuesta);
        this.cargando.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.cargando.set(false);
        this.mensajeError.set(
          error.status === 401
            ? 'Tu sesión ya no es válida. Vuelve a iniciar sesión.'
            : error.status === 0
              ? 'No se pudo conectar con el servicio. Inténtalo de nuevo.'
              : 'No se pudo cargar el perfil. Inténtalo de nuevo.'
        );

        if (error.status === 401) {
          void this.enrutador.navigate(['/login'], {
            queryParams: { motivo: 'sesion-expirada' }
          });
        }
      }
    });
  }

  volverAlPanel(): void {
    const tipoUsuario = this.perfil()?.tipoUsuario;
    const ruta = tipoUsuario === 'ADMIN'
      ? '/admin'
      : tipoUsuario === 'VENDEDOR'
        ? '/vendedor'
        : '/cliente';

    void this.enrutador.navigateByUrl(ruta);
  }
}
