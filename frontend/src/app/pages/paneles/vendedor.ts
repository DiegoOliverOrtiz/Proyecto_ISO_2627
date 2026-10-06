import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../login/auth.service';
import { ProductoPropio, ProductoService } from './producto.service';
import { VendedorService } from './vendedor.service';
@Component({
  selector: 'app-panel-vendedor',
  standalone: true,
  styleUrl: './vendedor.css',
  templateUrl: './vendedor.html',
})
export class PanelVendedor implements OnInit{

  private readonly servicioAutenticacion = inject(AuthService);
  private readonly enrutador = inject(Router);
  readonly cerrandoSesion = signal(false);
  readonly mensajeError = signal('');

  private readonly servicioProductos = inject(ProductoService);
  readonly productos = signal<ProductoPropio[]>([]);
  readonly cargandoProductos = signal(true);
  readonly errorCatalogo = signal('');

  private readonly servicioVendedor = inject(VendedorService);
  readonly nombreComercial = signal('');

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
  ngOnInit(): void {
    this.servicioProductos.consultarProductosPropios().subscribe({
      next: (productos) => {
        this.productos.set(productos);
        this.cargandoProductos.set(false);
      },

      error: (error: HttpErrorResponse) => {
        this.cargandoProductos.set(false);
        this.errorCatalogo.set(
          error.status === 0
            ? 'No se pudo conectar con el servicio. Inténtalo de nuevo.'
            : 'No se pudieron cargar los productos.'
        );
      }
    });

    this.servicioVendedor.obtenerPerfil().subscribe({
      next: (perfil) => this.nombreComercial.set(perfil.nombreComercial),
      error: () => {

      }
    })
  }
}
