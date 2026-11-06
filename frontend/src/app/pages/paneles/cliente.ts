import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  Component,
  computed,
  ElementRef,
  inject,
  Injector,
  OnInit,
  signal
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FavoritosService } from '../favoritos/favoritos.service';
import { ListasService, ResumenLista } from '../listas/listas.service';

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

  // C2: elemento que abrió el detalle, para devolverle el foco al cerrarlo.
  private origenDetalle: HTMLElement | null = null;
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly inyector = inject(Injector);
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
    this.disponibilidadFiltro() !== 'todos' ||
    this.soloFavoritos()
  );

  private readonly servicioFavoritos = inject(FavoritosService);

  readonly idsFavoritos = signal<ReadonlySet<string>>(new Set<string>());
  readonly favoritosDisponibles = signal(false);
  readonly cargandoFavoritos = signal(false);
  readonly modificandoFavorito = signal<string | null>(null);
  readonly soloFavoritos = signal(false);
  readonly errorFavoritos = signal('');
  readonly mensajeFavoritos = signal('');

  readonly productosMostrados = computed(() =>
    this.soloFavoritos()
      ? this.productos().filter(producto =>
        this.idsFavoritos().has(producto.id)
      )
      : this.productos()
  );


  /* =========================================================
     INICIALIZACIÓN
     ========================================================= */

  ngOnInit(): void {
    this.cargarProductos({});
    this.cargarFavoritos();
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
    this.soloFavoritos.set(false);

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

    this.origenDetalle = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null;

    this.cargarListas();

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

          // C2: al abrirse, el foco entra en el diálogo (botón «Cerrar»).
          afterNextRender(
            () => this.elemento.nativeElement
              .querySelector<HTMLElement>('.product-detail .detail-close')
              ?.focus(),
            { injector: this.inyector }
          );

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
            next: () => { },
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

    // C2: el foco vuelve al botón «Ver detalle» que abrió el diálogo.
    const origen = this.origenDetalle;
    this.origenDetalle = null;
    if (origen?.isConnected) {
      origen.focus();
    }
  }

  /*
   * C2: Escape cierra el detalle y Tab no sale de él
   * (del último elemento vuelve al primero y al revés).
   */
  manejarTecladoDetalle(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      this.cerrarDetalle();
      return;
    }

    if (evento.key !== 'Tab') {
      return;
    }

    const dialogo = evento.currentTarget as HTMLElement;
    const enfocables = Array.from(
      dialogo.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      )
    );

    if (enfocables.length === 0) {
      return;
    }

    const primero = enfocables[0];
    const ultimo = enfocables[enfocables.length - 1];

    if (evento.shiftKey && document.activeElement === primero) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && document.activeElement === ultimo) {
      evento.preventDefault();
      primero.focus();
    }
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

  cargarFavoritos(): void {
    this.cargandoFavoritos.set(true);
    this.favoritosDisponibles.set(false);
    this.errorFavoritos.set('');

    this.servicioFavoritos.consultar().subscribe({
      next: respuesta => {
        this.idsFavoritos.set(new Set(respuesta.ids));
        this.favoritosDisponibles.set(true);
        this.cargandoFavoritos.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.cargandoFavoritos.set(false);
        this.errorFavoritos.set(
          error.status === 0
            ? 'No se pudo conectar con el servicio de favoritos.'
            : 'No se pudieron consultar tus favoritos.'
        );

        if (error.status === 401) {
          this.irALogin();
        }
      }
    });
  }

  esFavorito(productoId: string): boolean {
    return this.idsFavoritos().has(productoId);
  }

  cambiarFavorito(producto: ProductoCatalogo): void {
    if (
      !this.favoritosDisponibles() ||
      this.modificandoFavorito() !== null
    ) {
      return;
    }

    const yaGuardado = this.esFavorito(producto.id);

    this.modificandoFavorito.set(producto.id);
    this.errorFavoritos.set('');
    this.mensajeFavoritos.set('');

    const peticion = yaGuardado
      ? this.servicioFavoritos.quitar(producto.id)
      : this.servicioFavoritos.anadir(producto.id);

    peticion.subscribe({
      next: respuesta => {
        this.idsFavoritos.set(new Set(respuesta.ids));
        this.modificandoFavorito.set(null);
        this.mensajeFavoritos.set(
          yaGuardado
            ? 'Producto eliminado de favoritos.'
            : 'Producto guardado en favoritos.'
        );
      },
      error: (error: HttpErrorResponse) => {
        this.modificandoFavorito.set(null);

        this.errorFavoritos.set(
          error.status === 0
            ? 'No se pudo conectar con el servidor.'
            : 'No se pudo actualizar el favorito.'
        );

        if (error.status === 401) {
          this.irALogin();
        }
      }
    });
  }

  actualizarFiltroFavoritos(evento: Event): void {
    this.soloFavoritos.set(
      (evento.target as HTMLInputElement).checked
    );
  }

  /* =========================================================
     LISTAS DE PRODUCTOS (HU-39)
     ========================================================= */

  private readonly servicioListas = inject(ListasService);

  readonly listas = signal<ResumenLista[]>([]);
  readonly listaSeleccionada = signal('');
  readonly cargandoListas = signal(false);
  readonly modificandoLista = signal(false);
  readonly errorListas = signal('');
  readonly mensajeListas = signal('');

  // Favoritos tiene su propio botón; aquí solo se ofrecen las listas creadas por el usuario.
  readonly listasPersonales = computed(() =>
    this.listas().filter(lista => !lista.predeterminada)
  );

  cargarListas(): void {
    this.cargandoListas.set(true);
    this.errorListas.set('');
    this.mensajeListas.set('');

    this.servicioListas.consultarListas().subscribe({
      next: listas => {
        this.listas.set(listas);
        this.cargandoListas.set(false);

        const personales = this.listasPersonales();
        if (!personales.some(lista => lista.id === this.listaSeleccionada())) {
          this.listaSeleccionada.set(personales[0]?.id ?? '');
        }
      },
      error: (error: HttpErrorResponse) => {
        this.cargandoListas.set(false);
        this.errorListas.set(
          error.status === 0
            ? 'No se pudo conectar con el servicio de listas.'
            : 'No se pudieron consultar tus listas.'
        );

        if (error.status === 401) {
          this.irALogin();
        }
      }
    });
  }

  actualizarListaSeleccionada(evento: Event): void {
    this.listaSeleccionada.set((evento.target as HTMLSelectElement).value);
    this.errorListas.set('');
    this.mensajeListas.set('');
  }

  estaEnListaSeleccionada(productoId: string): boolean {
    return this.listas()
      .find(lista => lista.id === this.listaSeleccionada())
      ?.productoIds.includes(productoId) ?? false;
  }

  cambiarProductoEnLista(producto: ProductoCatalogo): void {
    const listaId = this.listaSeleccionada();

    if (!listaId || this.modificandoLista()) {
      return;
    }

    const yaIncluido = this.estaEnListaSeleccionada(producto.id);

    this.modificandoLista.set(true);
    this.errorListas.set('');
    this.mensajeListas.set('');

    const peticion = yaIncluido
      ? this.servicioListas.quitarProducto(listaId, producto.id)
      : this.servicioListas.anadirProducto(listaId, producto.id);

    peticion.subscribe({
      next: lista => {
        const productoIds = lista.productos.map(item => item.id);
        this.listas.update(listas =>
          listas.map(resumen => resumen.id === lista.id ? { ...resumen, productoIds } : resumen)
        );
        this.modificandoLista.set(false);
        this.mensajeListas.set(
          yaIncluido
            ? `Producto quitado de «${lista.nombre}».`
            : `Producto añadido a «${lista.nombre}».`
        );
      },
      error: (error: HttpErrorResponse) => {
        this.modificandoLista.set(false);
        this.errorListas.set(
          error.status === 0
            ? 'No se pudo conectar con el servidor.'
            : error.status === 404
              ? 'El producto o la lista ya no están disponibles.'
              : 'No se pudo actualizar la lista.'
        );

        if (error.status === 401) {
          this.irALogin();
        }
      }
    });
  }
}
