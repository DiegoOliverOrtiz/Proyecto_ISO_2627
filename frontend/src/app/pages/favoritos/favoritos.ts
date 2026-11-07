import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { CarritoService } from '../carrito/carrito.service';
import { ProductoService } from '../paneles/producto.service';
import {
  FavoritosService,
  FavoritosResponse,
  ProductoFavorito
} from './favoritos.service';

@Component({
  selector: 'app-favoritos',
  standalone: true,
  imports: [DecimalPipe, RouterLink],
  templateUrl: './favoritos.html',
  styleUrls: ['../tienda/tienda.css', './favoritos.css']
})
export class Favoritos implements OnInit {

  private readonly servicioFavoritos = inject(FavoritosService);
  private readonly servicioCarrito = inject(CarritoService);
  private readonly servicioProductos = inject(ProductoService);
  private readonly enrutador = inject(Router);

  readonly productos = signal<ProductoFavorito[]>([]);

  readonly cargando = signal(true);
  readonly operacion = signal<string | null>(null);

  readonly error = signal('');
  readonly exito = signal('');

  ngOnInit(): void {
    this.cargarFavoritos();
  }

  cargarFavoritos(): void {
    this.cargando.set(true);
    this.error.set('');
    this.exito.set('');

    this.servicioFavoritos.consultar().subscribe({
      next: (respuesta) => {
        this.actualizarFavoritos(respuesta);
        this.cargando.set(false);
      },

      error: (fallo: HttpErrorResponse) => {
        this.cargando.set(false);
        this.gestionarError(
          fallo,
          'No se pudieron cargar tus favoritos.'
        );
      }
    });
  }

  quitarFavorito(producto: ProductoFavorito): void {
    if (this.cargando() || this.operacion() !== null) {
      return;
    }

    this.operacion.set(producto.id);
    this.error.set('');
    this.exito.set('');

    this.servicioFavoritos.quitar(producto.id).subscribe({
      next: (respuesta) => {
        this.actualizarFavoritos(respuesta);
        this.operacion.set(null);
        this.exito.set('Producto eliminado de favoritos.');
      },

      error: (fallo: HttpErrorResponse) => {
        this.operacion.set(null);
        this.gestionarError(
          fallo,
          'No se pudo quitar el producto de favoritos.'
        );
      }
    });
  }

  anadirAlCarrito(producto: ProductoFavorito): void {
    if (
      this.cargando() ||
      this.operacion() !== null ||
      !producto.disponible
    ) {
      return;
    }

    this.operacion.set(producto.id);
    this.error.set('');
    this.exito.set('');

    this.servicioCarrito.anadirProducto(producto.id, 1)
      .subscribe({
        next: () => {
          this.operacion.set(null);
          this.exito.set(
            `"${producto.nombre}" añadido al carrito.`
          );
        },

        error: (fallo: HttpErrorResponse) => {
          this.operacion.set(null);
          this.gestionarError(
            fallo,
            'No se pudo añadir el producto al carrito.'
          );
        }
      });
  }

  urlImagen(imagen: string): string {
    return this.servicioProductos.urlImagen(imagen);
  }

  private actualizarFavoritos(
    respuesta: FavoritosResponse
  ): void {
    this.productos.set(respuesta.productos);
  }

  private gestionarError(
    fallo: HttpErrorResponse,
    mensajePredeterminado: string
  ): void {
    if (fallo.status === 401) {
      this.error.set('Tu sesión ha caducado.');
      void this.enrutador.navigate(
        ['/login'],
        {
          queryParams: {
            motivo: 'sesion-expirada'
          }
        }
      );
      return;
    }

    if (fallo.status === 0) {
      this.error.set(
        'No se pudo conectar con el servidor.'
      );
      return;
    }

    if (fallo.status === 409) {
      this.error.set(
        'Los favoritos se han modificado. Recarga la lista e inténtalo de nuevo.'
      );
      return;
    }

    this.error.set(mensajePredeterminado);
  }
}
