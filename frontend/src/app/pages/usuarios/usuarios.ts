import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import {
  ListadoUsuarios,
  UsuarioAdministracion,
  UsuariosService
} from './usuarios.service';

@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [DecimalPipe, FormsModule, RouterLink],
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.css'
})
export class Usuarios implements OnInit {
  private readonly usuariosService = inject(UsuariosService);

  readonly listado = signal<ListadoUsuarios | null>(null);
  readonly cargando = signal(false);
  readonly mensajeError = signal('');
  readonly mensajeExito = signal('');
  readonly usuarioEnProceso = signal<string | null>(null);

  readonly busqueda = signal('');
  readonly estado = signal('');
  readonly rol = signal('');
  readonly pagina = signal(0);
  readonly tamano = 20;

  ngOnInit(): void {
    this.cargarUsuarios();
  }

  buscar(): void {
    this.pagina.set(0);
    this.cargarUsuarios();
  }

  cambiarEstado(estado: string): void {
    this.estado.set(estado);
    this.buscar();
  }

  cambiarRol(rol: string): void {
    this.rol.set(rol);
    this.buscar();
  }

  limpiarFiltros(): void {
    this.busqueda.set('');
    this.estado.set('');
    this.rol.set('');
    this.buscar();
  }

  hayFiltros(): boolean {
    return Boolean(this.busqueda() || this.estado() || this.rol());
  }

  cambiarPagina(direccion: number): void {
    const siguiente = this.pagina() + direccion;
    const total = this.listado()?.totalPaginas ?? 0;

    if (siguiente >= 0 && siguiente < total) {
      this.pagina.set(siguiente);
      this.cargarUsuarios();
    }
  }

  esPrimeraPagina(): boolean {
    return this.pagina() === 0;
  }

  esUltimaPagina(): boolean {
    const total = this.listado()?.totalPaginas ?? 0;
    return total === 0 || this.pagina() >= total - 1;
  }

  activar(usuario: UsuarioAdministracion): void {
    const seguro = window.confirm(
      `¿Quieres activar la cuenta de ${usuario.nombre} ${usuario.apellidos}?`
    );

    if (!seguro) {
      return;
    }

    this.usuarioEnProceso.set(usuario.id);
    this.mensajeError.set('');
    this.mensajeExito.set('');

    this.usuariosService.activarUsuario(usuario.id).subscribe({
      next: () => {
        this.usuarioEnProceso.set(null);
        this.mensajeExito.set(`${usuario.nombre} ${usuario.apellidos} ha sido activado correctamente.`);
        this.cargarUsuarios();
      },
      error: (error: HttpErrorResponse) => {
        this.usuarioEnProceso.set(null);
        this.mensajeError.set(this.mensajeParaOperacion(error));
      }
    });
  }

  bloquear(usuario: UsuarioAdministracion): void {
    const seguro = window.confirm(
      `¿Quieres bloquear a ${usuario.nombre} ${usuario.apellidos}?`
    );

    if (!seguro) {
      return;
    }

    this.ejecutarCambioBloqueo(usuario, true);
  }

  desbloquear(usuario: UsuarioAdministracion): void {
    const seguro = window.confirm(
      `¿Quieres desbloquear a ${usuario.nombre} ${usuario.apellidos}?`
    );

    if (!seguro) {
      return;
    }

    this.ejecutarCambioBloqueo(usuario, false);
  }

  private ejecutarCambioBloqueo(
    usuario: UsuarioAdministracion,
    bloquear: boolean
  ): void {
    this.usuarioEnProceso.set(usuario.id);
    this.mensajeError.set('');
    this.mensajeExito.set('');

    const operacion = bloquear
      ? this.usuariosService.bloquearUsuario(usuario.id)
      : this.usuariosService.desbloquearUsuario(usuario.id);

    operacion.subscribe({
      next: () => {
        this.usuarioEnProceso.set(null);

        this.mensajeExito.set(
          bloquear
            ? `${usuario.nombre} ${usuario.apellidos} ha sido bloqueado correctamente.`
            : `${usuario.nombre} ${usuario.apellidos} ha sido desbloqueado correctamente.`
        );

        this.cargarUsuarios();
      },

      error: (error: HttpErrorResponse) => {
        this.usuarioEnProceso.set(null);
        this.mensajeError.set(this.mensajeParaOperacion(error));
      }
    });
  }

  private cargarUsuarios(): void {
    this.cargando.set(true);
    this.mensajeError.set('');

    this.usuariosService
      .consultarUsuarios(
        this.busqueda(),
        this.estado(),
        this.rol(),
        this.pagina(),
        this.tamano
      )
      .subscribe({
        next: (respuesta) => {
          this.listado.set(respuesta);
          this.cargando.set(false);
        },

        error: (error: HttpErrorResponse) => {
          this.listado.set(null);
          this.cargando.set(false);
          this.mensajeError.set(this.mensajeParaError(error));
        }
      });
  }

  private mensajeParaOperacion(error: HttpErrorResponse): string {
    const mensajeBackend = error.error?.error || error.error?.message;

    if (mensajeBackend) {
      return mensajeBackend;
    }

    if (error.status === 401) {
      return 'Tu sesión ha caducado. Inicia sesión de nuevo.';
    }

    if (error.status === 403) {
      return 'No tienes permisos para realizar esta operación.';
    }

    if (error.status === 404) {
      return 'El usuario ya no existe.';
    }

    if (error.status === 409) {
      return 'La operación no se puede realizar sobre este usuario.';
    }

    if (error.status === 0) {
      return 'No se pudo conectar con el servicio. Inténtalo de nuevo.';
    }

    return 'No se pudo realizar la operación. Inténtalo de nuevo.';
  }

  private mensajeParaError(error: HttpErrorResponse): string {
    if (error.status === 401) {
      return 'Tu sesión ha caducado. Inicia sesión de nuevo.';
    }

    if (error.status === 403) {
      return 'No tienes permisos para consultar los usuarios.';
    }

    if (error.status === 0) {
      return 'No se pudo conectar con el servicio. Inténtalo de nuevo.';
    }

    return 'No se pudo cargar el listado de usuarios. Inténtalo de nuevo.';
  }
}