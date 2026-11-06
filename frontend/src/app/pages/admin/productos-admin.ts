import { HttpErrorResponse } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ProductoService } from '../paneles/producto.service';
import { ProductoAdministracion, ProductosAdminService } from './productos-admin.service';

type EstadoFiltro = 'todos' | 'activos' | 'desactivados';

// HU-34: catálogo de todos los vendedores en modo solo lectura (sin crear, modificar ni eliminar).
@Component({
  selector: 'app-productos-admin',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './productos-admin.html',
  styleUrls: ['../tienda/tienda.css', './productos-admin.css']
})
export class ProductosAdmin implements OnInit {
  private readonly productosAdminService = inject(ProductosAdminService);
  private readonly servicioProductos = inject(ProductoService);

  readonly productos = signal<ProductoAdministracion[]>([]);
  readonly cargando = signal(true);
  readonly mensajeError = signal('');
  // Imágenes que no han cargado: se sustituyen por el icono de «sin imagen».
  readonly imagenesFallidas = signal<ReadonlySet<string>>(new Set<string>());

  marcarImagenFallida(idProducto: string): void {
    this.imagenesFallidas.update((ids) => new Set([...ids, idProducto]));
  }

  // Búsqueda y filtros en memoria, como en el catálogo del vendedor (HU-15).
  readonly terminoBusqueda = signal('');
  readonly categoriaFiltro = signal('');
  readonly estadoFiltro = signal<EstadoFiltro>('todos');
  readonly vendedorFiltro = signal('');
  readonly categoriasDisponibles = computed(() =>
    this.valoresUnicos(this.productos().map((producto) => producto.categoria))
  );
  readonly vendedoresDisponibles = computed(() =>
    this.valoresUnicos(this.productos().map((producto) => producto.vendedor))
  );
  readonly productosFiltrados = computed(() => {
    // El texto se compara como literal con includes(): nunca se interpreta como expresión regular.
    const termino = this.terminoBusqueda().trim().toLowerCase();
    const categoria = this.categoriaFiltro();
    const estado = this.estadoFiltro();
    const vendedor = this.vendedorFiltro();

    return this.productos().filter(
      (producto) =>
        producto.nombre.toLowerCase().includes(termino) &&
        (categoria === '' || producto.categoria === categoria) &&
        (vendedor === '' || producto.vendedor === vendedor) &&
        (estado === 'todos' || producto.esVisible === (estado === 'activos'))
    );
  });
  readonly hayFiltrosActivos = computed(
    () =>
      this.terminoBusqueda().trim() !== '' ||
      this.categoriaFiltro() !== '' ||
      this.vendedorFiltro() !== '' ||
      this.estadoFiltro() !== 'todos'
  );

  ngOnInit(): void {
    this.cargarProductos();
  }

  cargarProductos(): void {
    this.cargando.set(true);
    this.mensajeError.set('');

    this.productosAdminService.consultarProductos().subscribe({
      next: (productos) => {
        this.productos.set(productos);
        this.cargando.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.productos.set([]);
        this.cargando.set(false);
        this.mensajeError.set(
          error.status === 0
            ? 'No se pudo conectar con el servicio. Inténtalo de nuevo.'
            : 'No se pudieron cargar los productos. Inténtalo de nuevo.'
        );
      }
    });
  }

  actualizarBusqueda(evento: Event): void {
    this.terminoBusqueda.set((evento.target as HTMLInputElement).value);
  }

  actualizarCategoriaFiltro(evento: Event): void {
    this.categoriaFiltro.set((evento.target as HTMLSelectElement).value);
  }

  actualizarVendedorFiltro(evento: Event): void {
    this.vendedorFiltro.set((evento.target as HTMLSelectElement).value);
  }

  actualizarEstadoFiltro(evento: Event): void {
    this.estadoFiltro.set((evento.target as HTMLSelectElement).value as EstadoFiltro);
  }

  limpiarFiltros(): void {
    this.terminoBusqueda.set('');
    this.categoriaFiltro.set('');
    this.vendedorFiltro.set('');
    this.estadoFiltro.set('todos');
  }

  urlImagen(imagen: string): string {
    return this.servicioProductos.urlImagen(imagen);
  }

  private valoresUnicos(valores: (string | null)[]): string[] {
    return [...new Set(valores.filter((valor): valor is string => Boolean(valor)))].sort((a, b) =>
      a.localeCompare(b, 'es')
    );
  }
}
