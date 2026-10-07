import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
// Ruta corregida: subimos un nivel y entramos en usuarios
import { ListadoUsuarios, UsuariosService } from '../usuarios/usuarios.service';

@Component({
  selector: 'app-administradores',
  standalone: true,
  imports: [DecimalPipe, FormsModule, RouterLink],
  templateUrl: './administradores.html',
  // Ruta corregida para reutilizar el CSS
  styleUrl: '../usuarios/usuarios.css' 
})
export class Administradores implements OnInit {
  private readonly usuariosService = inject(UsuariosService);
  readonly listado = signal<ListadoUsuarios | null>(null);
  readonly cargando = signal(false);
  readonly mensajeError = signal('');
  readonly busqueda = signal('');
  readonly estado = signal('');
  
  // El rol siempre será ADMIN para esta vista
  readonly rol = 'ADMIN'; 
  readonly pagina = signal(0);
  readonly tamano = 20;

  ngOnInit(): void {
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
    return Boolean(this.busqueda() || this.estado());
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
    return total === 0 || this.pagina() >= total - 1;
  }

  private cargarAdministradores(): void {
    this.cargando.set(true);
    this.mensajeError.set('');
    this.usuariosService
      .consultarUsuarios(this.busqueda(), this.estado(), this.rol, this.pagina(), this.tamano)
      .subscribe({
        next: (respuesta: ListadoUsuarios) => {
          this.listado.set(respuesta);
          this.cargando.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.listado.set(null);
          this.cargando.set(false);
          this.mensajeError.set('No se pudo cargar el listado de administradores. Inténtalo de nuevo.');
        }
      });
  }
}