import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../login/auth.service';
import { CarritoService } from '../carrito/carrito.service';
import { FiltrosCatalogo, ProductoCatalogo, ProductoService } from './producto.service';

@Component({
  selector: 'app-panel-cliente',
  standalone: true,
  imports: [DecimalPipe, RouterLink],
  styleUrl: './cliente.css',
  templateUrl: './cliente.html'
})
export class PanelCliente implements OnInit {

  private readonly servicioAutenticacion = inject(AuthService);
  private readonly enrutador = inject(Router);
  private readonly servicioProductos = inject(ProductoService);
  private readonly servicioCarrito = inject(CarritoService);

  readonly anadiendoProducto = signal<string | null>(null);
  readonly productoConMensaje = signal<string | null>(null);
  readonly exitoCarrito = signal('');
  readonly errorCarrito = signal('');
  readonly importeCarrito = signal<number | null>(null);

  readonly cerrandoSesion = signal(false);
  readonly mensajeError = signal('');

  readonly productos = signal<ProductoCatalogo[]>([]);
  readonly categoriasCatalogo = signal<string[]>([]);

  readonly cargandoCatalogo = signal(true);
  readonly errorCatalogo = signal('');

  readonly terminoBusqueda = signal('');
  readonly categoriaFiltro = signal('');
  readonly precioMinimo = signal('');
  readonly precioMaximo = signal('');

  readonly disponibilidadFiltro =
    signal<'todos' | 'disponibles' | 'agotados'>('todos');

  readonly detalle = signal<ProductoCatalogo | null>(null);
  readonly cargandoDetalle = signal(false);
  readonly errorDetalle = signal('');

  /*
   * Cantidad seleccionada en el modal de detalle.
   * Siempre comienza en 1.
   */
  readonly cantidadDetalle = signal(1);

  readonly categoriasDisponibles = computed(() =>
    [...this.categoriasCatalogo()]
      .sort((a, b) => a.localeCompare(b, 'es'))
  );

  readonly hayFiltrosActivos = computed(() =>
    this.terminoBusqueda().trim() !== '' ||
    this.categoriaFiltro() !== '' ||
    this.precioMinimo() !== '' ||
    this.precioMaximo() !== '' ||
    this.disponibilidadFiltro() !== 'todos'
  );


  /* =========================================================
     INICIALIZACIÓN
     ========================================================= */

  ngOnInit(): void {
    this.cargarProductos({});
  }


  /* =========================================================
     CATÁLOGO
     ========================================================= */

  cargarProductos(
    filtros: FiltrosCatalogo = this.obtenerFiltros()
  ): void {

    this.cargandoCatalogo.set(true);
    this.errorCatalogo.set('');

    this.servicioProductos.consultarCatalogo(filtros).subscribe({

      next: (productos) => {

        this.productos.set(productos);

        this.categoriasCatalogo.update((categorias) => [
          ...new Set([
            ...categorias,
            ...productos.map(
              (producto) => producto.categoria
            )
          ])
        ]);

        this.cargandoCatalogo.set(false);
      },

      error: (error: HttpErrorResponse) => {

        this.cargandoCatalogo.set(false);

        this.errorCatalogo.set(
          error.status === 0
            ? 'No se pudo conectar con el servicio. Inténtalo de nuevo.'
            : 'No se pudo cargar el catálogo. Inténtalo de nuevo.'
        );

        if (error.status === 401) {

          void this.enrutador.navigate(
            ['/login'],
            {
              queryParams: {
                motivo: 'sesion-expirada'
              }
            }
          );
        }
      }
    });
  }


  /* =========================================================
     FILTROS
     ========================================================= */

  actualizarBusqueda(evento: Event): void {

    this.terminoBusqueda.set(
      (evento.target as HTMLInputElement).value
    );
  }


  actualizarCategoria(evento: Event): void {

    this.categoriaFiltro.set(
      (evento.target as HTMLSelectElement).value
    );
  }


  actualizarPrecioMinimo(evento: Event): void {

    this.precioMinimo.set(
      (evento.target as HTMLInputElement).value
    );
  }


  actualizarPrecioMaximo(evento: Event): void {

    this.precioMaximo.set(
      (evento.target as HTMLInputElement).value
    );
  }


  actualizarDisponibilidad(evento: Event): void {

    this.disponibilidadFiltro.set(
      (evento.target as HTMLSelectElement).value as
        'todos' |
        'disponibles' |
        'agotados'
    );
  }


  limpiarFiltros(): void {

    this.terminoBusqueda.set('');
    this.categoriaFiltro.set('');
    this.precioMinimo.set('');
    this.precioMaximo.set('');
    this.disponibilidadFiltro.set('todos');

    this.cargarProductos({});
  }


  aplicarFiltros(evento: Event): void {

    evento.preventDefault();

    const minimo =
      this.precioMinimo().trim();

    const maximo =
      this.precioMaximo().trim();


    if (
      minimo !== '' &&
      (
        !Number.isFinite(Number(minimo)) ||
        Number(minimo) < 0
      )
    ) {

      this.errorCatalogo.set(
        'El precio mínimo debe ser un valor válido igual o mayor que cero.'
      );

      return;
    }


    if (
      maximo !== '' &&
      (
        !Number.isFinite(Number(maximo)) ||
        Number(maximo) < 0
      )
    ) {

      this.errorCatalogo.set(
        'El precio máximo debe ser un valor válido igual o mayor que cero.'
      );

      return;
    }


    if (
      minimo !== '' &&
      maximo !== '' &&
      Number(minimo) > Number(maximo)
    ) {

      this.errorCatalogo.set(
        'El precio mínimo no puede superar el precio máximo.'
      );

      return;
    }


    this.cargarProductos(
      this.obtenerFiltros()
    );
  }


  private obtenerFiltros(): FiltrosCatalogo {

    const minimo =
      this.precioMinimo().trim();

    const maximo =
      this.precioMaximo().trim();

    const disponibilidad =
      this.disponibilidadFiltro();


    return {

      nombre:
        this.terminoBusqueda().trim() ||
        undefined,

      categoria:
        this.categoriaFiltro() ||
        undefined,

      precioMinimo:
        minimo === ''
          ? undefined
          : Number(minimo),

      precioMaximo:
        maximo === ''
          ? undefined
          : Number(maximo),

      disponible:
        disponibilidad === 'todos'
          ? undefined
          : disponibilidad === 'disponibles'
    };
  }


  /* =========================================================
     DETALLE DE PRODUCTO
     ========================================================= */

  verDetalle(producto: ProductoCatalogo): void {

    if (
      !this.puedeConsultarDetalle(producto)
    ) {
      return;
    }


    this.detalle.set(null);

    this.errorDetalle.set('');

    this.cargandoDetalle.set(true);

    /*
     * Reiniciamos la cantidad al abrir
     * un nuevo producto.
     */
    this.cantidadDetalle.set(1);


    this.servicioProductos
      .consultarDetalleProducto(producto.id)
      .subscribe({

        next: (detalle) => {

          this.detalle.set(detalle);

          /*
           * Garantizamos que cada producto
           * comienza con cantidad 1.
           */
          this.cantidadDetalle.set(1);

          this.cargandoDetalle.set(false);
        },


        error: (error: HttpErrorResponse) => {

          this.cargandoDetalle.set(false);

          this.errorDetalle.set(
            error.status === 403
              ? 'Este producto es solo para clientes Premium.'
              : 'No se pudo cargar el detalle del producto. Inténtalo de nuevo.'
          );


          if (
            error.status === 401
          ) {

            void this.enrutador.navigate(
              ['/login'],
              {
                queryParams: {
                  motivo:
                    'sesion-expirada'
                }
              }
            );
          }
        }
      });
  }


  /* =========================================================
     SELECTOR DE CANTIDAD
     ========================================================= */

  anadirAlCarrito(producto: ProductoCatalogo, cantidad: number): void {
    if (this.anadiendoProducto() !== null) return;
    this.productoConMensaje.set(producto.id);
    this.exitoCarrito.set('');
    this.errorCarrito.set('');
    this.importeCarrito.set(null);

    if (!Number.isSafeInteger(cantidad) || cantidad <= 0) {
      this.errorCarrito.set('La cantidad debe ser un número entero mayor que cero.');
      return;
    }
    if (!this.puedeConsultarDetalle(producto)) {
      this.errorCarrito.set('Este producto solo está disponible para clientes Premium.');
      return;
    }
    if (!producto.esVisible) {
      this.errorCarrito.set('El producto ya no está disponible.');
      return;
    }
    if (cantidad > producto.stock) {
      this.errorCarrito.set('No hay stock suficiente para la cantidad solicitada.');
      return;
    }

    this.anadiendoProducto.set(producto.id);
    this.servicioCarrito.anadirProducto(producto.id, cantidad).subscribe({
      next: (carrito) => {
        this.anadiendoProducto.set(null);
        this.exitoCarrito.set('Producto añadido al carrito.');
        this.importeCarrito.set(carrito.importe);
      },
      error: (error: HttpErrorResponse) => {
        this.anadiendoProducto.set(null);
        this.errorCarrito.set(this.mensajeErrorCarrito(error));
        if (error.status === 401) this.irALogin();
        if (error.status === 403) {
          // La seguridad actual también responde 403 cuando no hay sesión.
          this.servicioAutenticacion.obtenerSesionActual().subscribe({
            next: () => {},
            error: (sesion: HttpErrorResponse) => {
              if (sesion.status === 401 || sesion.status === 403) this.irALogin();
            }
          });
        }
      }
    });
  }

  private irALogin(): void {
    void this.enrutador.navigate(['/login'], { queryParams: { motivo: 'sesion-expirada' } });
  }

  private mensajeErrorCarrito(error: HttpErrorResponse): string {
    if (error.status === 0) return 'No se pudo conectar con el servicio. Inténtalo de nuevo.';
    if (error.status === 401) return 'Tu sesión ha caducado. Inicia sesión de nuevo.';
    // Solo se muestran mensajes funcionales conocidos, nunca detalles internos.
    const mensajesPermitidos = new Set([
      'La cantidad debe ser mayor que cero.',
      'El producto ya no está disponible.',
      'Un producto del carrito ya no está disponible.',
      'No hay stock suficiente para la cantidad total solicitada.',
      'No hay stock suficiente para un producto del carrito.',
      'Este producto solo está disponible para clientes Premium.',
      'Un producto del carrito solo está disponible para clientes Premium.',
      'Un producto del carrito no tiene un precio disponible.'
    ]);
    const mensaje = error.error?.error;
    if ([400, 403, 404].includes(error.status) && mensajesPermitidos.has(mensaje)) return mensaje;
    if (error.status === 404) return 'El producto ya no está disponible o ha sido eliminado.';
    if (error.status === 403) return 'No tienes permiso para añadir el producto. Comprueba tu sesión e inténtalo de nuevo.';
    if (error.status === 400) return 'No se pudo añadir el producto. Revisa la cantidad y la disponibilidad.';
    if (error.status === 409) return 'El carrito se ha modificado. Inténtalo de nuevo.';
    return 'No se pudo añadir el producto al carrito. Inténtalo de nuevo.';
  }

  reducirCantidad(): void {

    this.cantidadDetalle.update(
      (cantidad) =>
        Math.max(
          1,
          cantidad - 1
        )
    );
  }


  aumentarCantidad(stock: number): void {

    this.cantidadDetalle.update(
      (cantidad) =>
        Math.min(
          stock,
          cantidad + 1
        )
    );
  }


  /* =========================================================
     CERRAR DETALLE
     ========================================================= */

  cerrarDetalle(): void {

    this.detalle.set(null);

    this.cantidadDetalle.set(1);

    this.errorDetalle.set('');
  }


  /* =========================================================
     IMÁGENES
     ========================================================= */

  urlImagen(imagen: string): string {

    return this.servicioProductos
      .urlImagen(imagen);
  }


  /* =========================================================
     PRODUCTOS PREMIUM
     ========================================================= */

  puedeConsultarDetalle(
    producto: ProductoCatalogo
  ): boolean {

    return (
      producto.puedeConsultarDetalle ??
      !producto.soloPremium
    );
  }


  /* =========================================================
     CERRAR SESIÓN
     ========================================================= */

  cerrarSesion(): void {

    this.cerrandoSesion.set(true);

    this.mensajeError.set('');


    this.servicioAutenticacion
      .cerrarSesion()
      .subscribe({

        next: () => {

          this.cerrandoSesion.set(false);

          void this.enrutador
            .navigateByUrl('/login');
        },


        error: (
          error: HttpErrorResponse
        ) => {

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
