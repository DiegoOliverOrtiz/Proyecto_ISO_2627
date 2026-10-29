import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import {
  ListadoUsuarios,
  UsuarioAdministracion,
  UsuariosService
} from '../usuarios/usuarios.service';

import { AdministradorService } from './administrador.service';
import { AuthService } from '../login/auth.service';

@Component({
  selector: 'app-administradores',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './administradores.html',
  styleUrl: '../usuarios/usuarios.css'
})
export class Administradores implements OnInit {

  private readonly usuariosService = inject(UsuariosService);
  private readonly administradorService = inject(AdministradorService);
  private readonly authService = inject(AuthService);

  readonly listado = signal<ListadoUsuarios | null>(null);

  readonly cargando = signal(false);

  readonly mensajeError = signal('');
  readonly mensajeExito = signal('');

  readonly usuarioEnProceso = signal<string | null>(null);

  readonly emailAdministradorActual = signal('');

  readonly busqueda = signal('');
  readonly estado = signal('');

  readonly rol = 'ADMIN';

  readonly pagina = signal(0);
  readonly tamano = 20;

  ngOnInit(): void {
    this.cargarSesionActual();
    this.cargarAdministradores();
  }

  buscar(): void {
    this.pagina.set(0);
    this.cargarAdministradores();
  }

  cambiarEstado(estado: string): void {
    this.estado.set(estado);
    this.buscar();
  }

  limpiarFiltros(): void {
    this.busqueda.set('');
    this.estado.set('');
    this.buscar();
  }

  hayFiltros(): boolean {
    return Boolean(
      this.busqueda() ||
      this.estado()
    );
  }

  cambiarPagina(direccion: number): void {
    const siguiente = this.pagina() + direccion;
    const total = this.listado()?.totalPaginas ?? 0;

    if (siguiente >= 0 && siguiente < total) {
      this.pagina.set(siguiente);
      this.cargarAdministradores();
    }
  }

  esPrimeraPagina(): boolean {
    return this.pagina() === 0;
  }

  esUltimaPagina(): boolean {
    const total = this.listado()?.totalPaginas ?? 0;

    return total === 0 ||
      this.pagina() >= total - 1;
  }

  esAdministradorActual(email: string): boolean {
    return (
      this.emailAdministradorActual()
        .trim()
        .toLowerCase()
      ===
      email
        .trim()
        .toLowerCase()
    );
  }

  bloquear(admin: UsuarioAdministracion): void {

    this.mensajeError.set('');
    this.mensajeExito.set('');

    if (this.esAdministradorActual(admin.email)) {
      this.mensajeError.set(
        'No puedes bloquear tu propia cuenta de administrador.'
      );
      return;
    }

    const seguro = window.confirm(
      `¿Estás seguro de que deseas bloquear al administrador ${admin.nombre} ${admin.apellidos}?`
    );

    if (!seguro) {
      return;
    }

    this.ejecutarCambioBloqueo(
      admin,
      true
    );
  }

  desbloquear(admin: UsuarioAdministracion): void {

    this.mensajeError.set('');
    this.mensajeExito.set('');

    if (this.esAdministradorActual(admin.email)) {
      this.mensajeError.set(
        'No puedes desbloquear tu propia cuenta de administrador.'
      );
      return;
    }

    const seguro = window.confirm(
      `¿Estás seguro de que deseas desbloquear al administrador ${admin.nombre} ${admin.apellidos}?`
    );

    if (!seguro) {
      return;
    }

    this.ejecutarCambioBloqueo(
      admin,
      false
    );
  }

  eliminar(id: string, nombre: string): void {

    const seguro = window.confirm(
      `¿Estás seguro de que deseas eliminar al administrador ${nombre}?\n\nEsta es una acción destructiva y no se puede deshacer.`
    );

    if (!seguro) {
      return;
    }

    this.administradorService
      .eliminarAdministrador(id)
      .subscribe({

        next: () => {

          const actuales =
            this.listado()?.contenido?.length ?? 0;

          if (
            actuales === 1 &&
            this.pagina() > 0
          ) {
            this.pagina.update(
              pagina => pagina - 1
            );
          }

          this.cargarAdministradores();
        },

        error: (error: HttpErrorResponse) => {

          const mensajeBackend =
            error.error?.error;

          alert(
            mensajeBackend ||
            'No se pudo eliminar el administrador. Inténtalo de nuevo.'
          );
        }

      });
  }

  private ejecutarCambioBloqueo(
    admin: UsuarioAdministracion,
    bloquear: boolean
  ): void {

    this.usuarioEnProceso.set(admin.id);

    this.mensajeError.set('');
    this.mensajeExito.set('');

    const operacion = bloquear
      ? this.usuariosService.bloquearUsuario(admin.id)
      : this.usuariosService.desbloquearUsuario(admin.id);

    operacion.subscribe({

      next: () => {

        this.usuarioEnProceso.set(null);

        if (bloquear) {
          this.mostrarMensajeExito(
            `${admin.nombre} ${admin.apellidos} ha sido bloqueado correctamente.`
          );
        } else {
          this.mostrarMensajeExito(
            `${admin.nombre} ${admin.apellidos} ha sido desbloqueado correctamente.`
          );
        }

        this.cargarAdministradores();
      },

      error: (error: HttpErrorResponse) => {

        this.usuarioEnProceso.set(null);

        const mensajeBackend =
          error.error?.error;

        if (mensajeBackend) {

          this.mensajeError.set(
            mensajeBackend
          );

        } else if (error.status === 401) {

          this.mensajeError.set(
            'Tu sesión ha caducado. Inicia sesión de nuevo.'
          );

        } else if (error.status === 403) {

          this.mensajeError.set(
            'No tienes permisos para realizar esta operación.'
          );

        } else if (error.status === 404) {

          this.mensajeError.set(
            'El administrador ya no existe.'
          );

        } else if (error.status === 409) {

          this.mensajeError.set(
            'La operación no se puede realizar sobre este administrador.'
          );

        } else if (error.status === 0) {

          this.mensajeError.set(
            'No se pudo conectar con el servicio. Inténtalo de nuevo.'
          );

        } else {

          this.mensajeError.set(
            'No se pudo realizar la operación. Inténtalo de nuevo.'
          );
        }
      }

    });
  }

  private mostrarMensajeExito(mensaje: string): void {

    this.mensajeExito.set(mensaje);

    setTimeout(() => {
      this.mensajeExito.set('');
    }, 3000);
  }

  private cargarSesionActual(): void {

    this.authService
      .obtenerSesionActual()
      .subscribe({

        next: (sesion) => {

          this.emailAdministradorActual.set(
            sesion.email
          );
        },

        error: () => {

          this.emailAdministradorActual.set('');
        }

      });
  }

  private cargarAdministradores(): void {

    this.cargando.set(true);
    this.mensajeError.set('');

    this.usuariosService
      .consultarUsuarios(
        this.busqueda(),
        this.estado(),
        this.rol,
        this.pagina(),
        this.tamano
      )
      .subscribe({

        next: (respuesta: ListadoUsuarios) => {

          this.listado.set(respuesta);
          this.cargando.set(false);
        },

        error: (error: HttpErrorResponse) => {

          this.listado.set(null);
          this.cargando.set(false);

          if (error.status === 401) {

            this.mensajeError.set(
              'Tu sesión ha caducado. Inicia sesión de nuevo.'
            );

          } else if (error.status === 403) {

            this.mensajeError.set(
              'No tienes permisos para consultar los administradores.'
            );

          } else if (error.status === 0) {

            this.mensajeError.set(
              'No se pudo conectar con el servicio. Inténtalo de nuevo.'
            );

          } else {

            this.mensajeError.set(
              'No se pudo cargar el listado de administradores. Inténtalo de nuevo.'
            );
          }
        }

      });
  }
}
