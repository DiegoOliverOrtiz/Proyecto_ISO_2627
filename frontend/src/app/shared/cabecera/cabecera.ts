import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService, RespuestaInicioSesion } from '../../pages/login/auth.service';
import { rutaParaRol } from '../../pages/login/rutas-por-rol';

type Rol = RespuestaInicioSesion['role'];

interface EnlaceCabecera {
  texto: string;
  ruta: string;
  // Los paneles son prefijo de otras rutas: solo se marcan activos si coinciden exactamente.
  exacto: boolean;
}

export const ENLACES_POR_ROL: Record<Rol, readonly EnlaceCabecera[]> = {
  CLIENTE: [
    { texto: 'Catálogo', ruta: '/cliente', exacto: true },
    { texto: 'Favoritos', ruta: '/cliente/favoritos', exacto: false },
    { texto: 'Mis listas', ruta: '/listas', exacto: false },
    { texto: 'Mis pedidos', ruta: '/cliente/pedidos', exacto: false },
    { texto: 'Carrito', ruta: '/carrito', exacto: false },
    { texto: 'Mi perfil', ruta: '/perfil', exacto: false }
  ],
  VENDEDOR: [
    { texto: 'Mis productos', ruta: '/vendedor', exacto: true },
    { texto: 'Pedidos', ruta: '/vendedor/pedidos', exacto: false },
    { texto: 'Mi perfil', ruta: '/perfil', exacto: false }
  ],
  ADMIN: [
    { texto: 'Panel', ruta: '/admin', exacto: true },
    { texto: 'Usuarios', ruta: '/admin/usuarios', exacto: false },
    { texto: 'Categorías', ruta: '/admin/categorias', exacto: false },
    { texto: 'Productos', ruta: '/admin/productos', exacto: false },
    { texto: 'Administradores', ruta: '/admin/administradores', exacto: false },
    { texto: 'Mi perfil', ruta: '/perfil', exacto: false }
  ]
};

@Component({
  imports: [RouterLink, RouterLinkActive],
  selector: 'app-cabecera',
  styleUrl: './cabecera.css',
  templateUrl: './cabecera.html',
})
export class Cabecera {
  private readonly servicioAutenticacion = inject(AuthService);
  private readonly enrutador = inject(Router);

  readonly rol = signal<Rol | null>(null);
  readonly menuAbierto = signal(false);
  readonly cerrandoSesion = signal(false);
  readonly mensajeError = signal('');

  readonly enlaces = computed(() => {
    const rol = this.rol();
    return rol ? ENLACES_POR_ROL[rol] : [];
  });

  // La marca lleva al panel de cada rol, o al inicio si no hay sesión.
  readonly rutaInicio = computed(() => {
    const rol = this.rol();
    return rol ? rutaParaRol(rol) : '/';
  });

  constructor() {
    this.servicioAutenticacion.obtenerSesionActual().subscribe({
      next: (sesion) => this.rol.set(sesion.role),
      error: () => this.rol.set(null)
    });
  }

  alternarMenu(): void {
    this.menuAbierto.update((abierto) => !abierto);
  }

  cerrarMenu(): void {
    this.menuAbierto.set(false);
  }

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
