import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ListadoUsuarios, UsuariosService } from './usuarios.service';

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

  private cargarUsuarios(): void {
    this.cargando.set(true);
    this.mensajeError.set('');
    this.usuariosService
      .consultarUsuarios(this.busqueda(), this.estado(), this.rol(), this.pagina(), this.tamano)
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

  private mensajeParaError(error: HttpErrorResponse): string {
    if (error.status === 401) return 'Tu sesión ha caducado. Inicia sesión de nuevo.';
    if (error.status === 403) return 'No tienes permisos para consultar los usuarios.';
    if (error.status === 0) return 'No se pudo conectar con el servicio. Inténtalo de nuevo.';
    return 'No se pudo cargar el listado de usuarios. Inténtalo de nuevo.';
  }
}