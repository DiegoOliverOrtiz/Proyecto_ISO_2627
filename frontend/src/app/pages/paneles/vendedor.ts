import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../login/auth.service';
import { CategoriasService } from '../categorias/categorias.service';
import { ProductoPropio, ProductoService } from './producto.service';
import { VendedorService } from './vendedor.service';

@Component({
  selector: 'app-panel-vendedor',
  standalone: true,
  imports: [ReactiveFormsModule],
  styleUrl: './vendedor.css',
  templateUrl: './vendedor.html',
})
export class PanelVendedor implements OnInit {
  private readonly servicioAutenticacion = inject(AuthService);
  private readonly enrutador = inject(Router);
  private readonly formBuilder = inject(FormBuilder);
  private readonly servicioCategorias = inject(CategoriasService);
  readonly cerrandoSesion = signal(false);
  readonly mensajeError = signal('');

  private readonly servicioProductos = inject(ProductoService);
  readonly productos = signal<ProductoPropio[]>([]);
  readonly cargandoProductos = signal(true);
  readonly errorCatalogo = signal('');
  readonly productoCambiandoEstado = signal<string | null>(null);
  readonly productoEnEdicion = signal<ProductoPropio | null>(null);

  private readonly servicioVendedor = inject(VendedorService);
  readonly nombreComercial = signal('');
  readonly mostrandoFormulario = signal(false);
  readonly enviandoProducto = signal(false);
  readonly mensajeExito = signal('');
  readonly categorias = signal<string[]>([]);
  readonly cargandoCategorias = signal(false);
  readonly errorCategorias = signal('');
  readonly imagenSeleccionada = signal<File | null>(null);
  readonly vistaPreviaImagen = signal('');
  readonly errorImagen = signal('');

  readonly formularioProducto = this.formBuilder.nonNullable.group({
    nombre: ['', [Validators.required, Validators.minLength(2)]],
    categoria: ['', Validators.required],
    descripcion: [''],
    esVisible: [true],
    precio: [0, [Validators.required, Validators.min(0.01)]],
    stock: [0, [Validators.required, Validators.min(0)]],
    descuento: [0, [Validators.required, Validators.min(0), Validators.max(100)]],
    descuentoPremium: [0, [Validators.required, Validators.min(0), Validators.max(100)]],
  });

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

  alternarFormulario(): void {
    this.mensajeExito.set('');
    this.mensajeError.set('');
    if (this.mostrandoFormulario()) {
      this.cerrarFormulario();
    } else {
      this.mostrandoFormulario.set(true);
    }
  }

  private cerrarFormulario(): void {
    this.mostrandoFormulario.set(false);
    this.productoEnEdicion.set(null);
    this.limpiarImagenSeleccionada();
    this.formularioProducto.reset({
      nombre: '',
      categoria: '',
      descripcion: '',
      esVisible: true,
      precio: 0,
      stock: 0,
      descuento: 0,
      descuentoPremium: 0,
    });
  }

  editarProducto(producto: ProductoPropio): void {
    this.mensajeExito.set('');
    this.mensajeError.set('');
    this.limpiarImagenSeleccionada();
    this.productoEnEdicion.set(producto);
    // Se reutiliza el formulario de creación cargando los valores actuales del producto.
    this.formularioProducto.reset({
      nombre: producto.nombre,
      categoria: producto.categoria,
      descripcion: producto.descripcion ?? '',
      esVisible: producto.esVisible,
      precio: producto.precio ?? 0,
      stock: producto.stock,
      descuento: producto.descuento,
      descuentoPremium: producto.descuentoPremium,
    });
    this.mostrandoFormulario.set(true);
  }

  cargarProductos(): void {
    this.cargandoProductos.set(true);
    this.errorCatalogo.set('');
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
  }

  cargarCategorias(): void {
    this.cargandoCategorias.set(true);
    this.errorCategorias.set('');

    this.servicioCategorias.obtenerCategorias().subscribe({
      next: (categorias) => {
        this.categorias.set(categorias.map((categoria) => categoria.nombre));
        this.cargandoCategorias.set(false);
      },
      error: () => {
        this.categorias.set([]);
        this.errorCategorias.set('No se pudieron cargar las categorías disponibles.');
        this.cargandoCategorias.set(false);
      }
    });
  }

  cambiarVisibilidad(esVisible: boolean): void {
    this.formularioProducto.patchValue({ esVisible });
  }

  seleccionarImagen(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const imagen = entrada.files?.[0] ?? null;
    this.errorImagen.set('');

    if (!imagen) {
      this.limpiarImagenSeleccionada();
      return;
    }

    if (!['image/jpeg', 'image/png', 'image/gif'].includes(imagen.type) || imagen.size > 5 * 1024 * 1024) {
      this.limpiarImagenSeleccionada();
      entrada.value = '';
      this.errorImagen.set('Selecciona una imagen PNG, JPG o GIF de hasta 5 MB.');
      return;
    }

    this.limpiarImagenSeleccionada();
    this.imagenSeleccionada.set(imagen);
    this.vistaPreviaImagen.set(URL.createObjectURL(imagen));
  }

  private limpiarImagenSeleccionada(): void {
    const vistaPrevia = this.vistaPreviaImagen();
    if (vistaPrevia) {
      URL.revokeObjectURL(vistaPrevia);
    }
    this.imagenSeleccionada.set(null);
    this.vistaPreviaImagen.set('');
  }

  guardarProducto(): void {
    if (this.formularioProducto.invalid) {
      this.formularioProducto.markAllAsTouched();
      this.mensajeError.set('Completa todos los campos obligatorios y corrige los valores indicados.');
      return;
    }

    this.enviandoProducto.set(true);
    this.mensajeError.set('');
    this.mensajeExito.set('');

    const productoEnEdicion = this.productoEnEdicion();
    if (productoEnEdicion) {
      this.modificarProducto(productoEnEdicion.id);
      return;
    }

    this.servicioProductos.crearProducto(
      this.formularioProducto.getRawValue(),
      this.imagenSeleccionada()
    ).subscribe({
      next: () => {
        this.enviandoProducto.set(false);
        this.mensajeExito.set('Producto creado correctamente.');
        this.cerrarFormulario();
        this.cargarProductos();
      },
      error: (error: HttpErrorResponse) => {
        this.enviandoProducto.set(false);
        this.mensajeError.set(
          error.status === 0
            ? 'No se pudo conectar con el servicio. Inténtalo de nuevo.'
            : 'No se pudo crear el producto. Revisa los datos e inténtalo otra vez.'
        );
      }
    });
  }

  private modificarProducto(id: string): void {
    const { nombre, categoria, descripcion, precio, stock, descuento, descuentoPremium } =
      this.formularioProducto.getRawValue();

    this.servicioProductos.modificarProducto(
      id,
      { nombre, categoria, descripcion, precio, stock, descuento, descuentoPremium },
      this.imagenSeleccionada()
    ).subscribe({
      next: () => {
        this.enviandoProducto.set(false);
        this.mensajeExito.set('Producto modificado correctamente.');
        this.cerrarFormulario();
        this.cargarProductos();
      },
      error: (error: HttpErrorResponse) => {
        this.enviandoProducto.set(false);
        const mensajeBackend = typeof error.error?.error === 'string' ? error.error.error : null;
        this.mensajeError.set(
          error.status === 0
            ? 'No se pudo conectar con el servicio. Inténtalo de nuevo.'
            : mensajeBackend ?? 'No se pudo modificar el producto. Revisa los datos e inténtalo otra vez.'
        );
      }
    });
  }

  cambiarEstadoProducto(producto: ProductoPropio): void {
    const activar = !producto.esVisible;
    this.productoCambiandoEstado.set(producto.id);
    this.mensajeError.set('');
    this.mensajeExito.set('');

    const peticion = activar
      ? this.servicioProductos.activarProducto(producto.id)
      : this.servicioProductos.desactivarProducto(producto.id);

    peticion.subscribe({
      next: () => {
        this.productoCambiandoEstado.set(null);
        this.mensajeExito.set(
          activar ? 'Producto activado correctamente.' : 'Producto desactivado correctamente.'
        );
        this.cargarProductos();
      },
      error: (error: HttpErrorResponse) => {
        this.productoCambiandoEstado.set(null);
        const mensajeBackend = typeof error.error?.error === 'string' ? error.error.error : null;
        this.mensajeError.set(
          error.status === 0
            ? 'No se pudo conectar con el servicio. Inténtalo de nuevo.'
            : mensajeBackend ?? 'No se pudo cambiar el estado del producto.'
        );
        this.cargarProductos();
      }
    });
  }

  campoInvalido(nombreCampo: string): boolean {
    const control = this.formularioProducto.get(nombreCampo);
    return !!control && control.invalid && (control.dirty || control.touched);
  }

  urlImagen(imagen: string): string {
    return this.servicioProductos.urlImagen(imagen);
  }

  ngOnInit(): void {
    this.cargarProductos();
    this.cargarCategorias();

    this.servicioVendedor.obtenerPerfil().subscribe({
      next: (perfil) => this.nombreComercial.set(perfil.nombreComercial),
      error: () => {
        this.nombreComercial.set('Vendedor');
      }
    });
  }
}
