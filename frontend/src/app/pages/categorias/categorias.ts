import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { RouterLink } from '@angular/router';

import {
  Categoria,
  CategoriasService,
  CrearCategoriaRequest,
  ModificarCategoriaRequest
} from './categorias.service';

@Component({
  selector: 'app-categorias',
  imports: [
    ReactiveFormsModule,
    RouterLink
  ],
  templateUrl: './categorias.html',
  styleUrl: './categorias.css',
})
export class Categorias implements OnInit {
  private readonly categoriasService = inject(CategoriasService);
  private readonly formBuilder = inject(FormBuilder);

  readonly categorias = signal<Categoria[]>([]);
  readonly cargando = signal(false);
  readonly error = signal(false);

  readonly mostrandoFormulario = signal(false);
  readonly creando = signal(false);
  readonly modificando = signal(false);
  readonly eliminando = signal<string | null>(null);
  readonly categoriaEnEdicion = signal<string | null>(null);
  readonly mensajeResultado = signal('');
  readonly errorFormulario = signal('');

  readonly formularioCategoria = this.formBuilder.nonNullable.group({
    nombre: ['', Validators.required],
    descripcion: ['', Validators.required]
  });

  ngOnInit(): void {
    this.cargarCategorias();
  }

  cargarCategorias(): void {
    this.cargando.set(true);
    this.error.set(false);

    this.categoriasService.obtenerCategorias().subscribe({
      next: (categorias) => {
        this.categorias.set(categorias);
        this.cargando.set(false);
      },
      error: () => {
        this.categorias.set([]);
        this.error.set(true);
        this.cargando.set(false);
      },
    });
  }

  abrirFormulario(): void {
    this.categoriaEnEdicion.set(null);
    this.formularioCategoria.reset();
    this.mostrandoFormulario.set(true);
    this.mensajeResultado.set('');
    this.errorFormulario.set('');
    this.desplazarAlFormulario();
  }

  iniciarEdicion(categoria: Categoria): void {
    this.categoriaEnEdicion.set(categoria.id);
    this.formularioCategoria.setValue({
      nombre: categoria.nombre,
      descripcion: categoria.descripcion
    });
    this.mostrandoFormulario.set(true);
    this.mensajeResultado.set('');
    this.errorFormulario.set('');
    this.desplazarAlFormulario();
  }

  cancelarFormulario(): void {
    this.mostrandoFormulario.set(false);
    this.categoriaEnEdicion.set(null);
    this.formularioCategoria.reset();
    this.errorFormulario.set('');
  }

  guardarCategoria(): void {
    this.mensajeResultado.set('');
    this.errorFormulario.set('');

    this.formularioCategoria.markAllAsTouched();

    if (this.formularioCategoria.invalid || this.creando() || this.modificando()) {
      return;
    }

    const valores = this.formularioCategoria.getRawValue();

    const request: CrearCategoriaRequest = {
      nombre: valores.nombre.trim(),
      descripcion: valores.descripcion.trim()
    };

    if (!request.nombre || !request.descripcion) {
      this.errorFormulario.set(
        'El nombre y la descripción no pueden estar vacíos.'
      );
      return;
    }

    const idEnEdicion = this.categoriaEnEdicion();
    if (idEnEdicion) {
      this.guardarModificacion(idEnEdicion, request);
      return;
    }

    this.guardarCreacion(request);
  }

  private guardarCreacion(request: CrearCategoriaRequest): void {
    this.creando.set(true);

    this.categoriasService.crearCategoria(request).subscribe({
      next: () => {
        this.creando.set(false);
        this.mostrandoFormulario.set(false);
        this.categoriaEnEdicion.set(null);
        this.formularioCategoria.reset();

        this.mensajeResultado.set(
          'Categoría creada correctamente.'
        );

        this.cargarCategorias();
      },
      error: (error: HttpErrorResponse) => {
        this.creando.set(false);
        this.errorFormulario.set(
          this.mensajeParaError(error, 'crear')
        );
      },
    });
  }

  private guardarModificacion(
    id: string,
    request: ModificarCategoriaRequest
  ): void {
    this.modificando.set(true);

    this.categoriasService.modificarCategoria(id, request).subscribe({
      next: (categoriaActualizada) => {
        this.categorias.update((categorias) =>
          categorias.map((categoria) =>
            categoria.id === categoriaActualizada.id
              ? categoriaActualizada
              : categoria
          )
        );
        this.modificando.set(false);
        this.mostrandoFormulario.set(false);
        this.categoriaEnEdicion.set(null);
        this.formularioCategoria.reset();
        this.mensajeResultado.set('Categoría modificada correctamente.');
      },
      error: (error: HttpErrorResponse) => {
        this.modificando.set(false);
        this.errorFormulario.set(
          this.mensajeParaError(error, 'modificar')
        );
      },
    });
  }

  eliminarCategoria(categoria: Categoria): void {
    const confirmar = window.confirm(
      `¿Seguro que quieres eliminar la categoría "${categoria.nombre}"?`
    );

    if (!confirmar) {
      return;
    }

    this.mensajeResultado.set('');
    this.errorFormulario.set('');
    this.eliminando.set(categoria.id);

    this.categoriasService.eliminarCategoria(categoria.id).subscribe({
      next: () => {
        this.categorias.update((categorias) =>
          categorias.filter((actual) => actual.id !== categoria.id)
        );

        this.eliminando.set(null);
        this.mensajeResultado.set('Categoría eliminada correctamente.');
      },
      error: (error: HttpErrorResponse) => {
        this.eliminando.set(null);

        this.mensajeResultado.set(
          this.mensajeParaError(error, 'eliminar')
        );
      },
    });
  }

  nombreInvalido(): boolean {
    const control = this.formularioCategoria.controls.nombre;

    return control.touched &&
      (!control.value.trim() || control.invalid);
  }

  descripcionInvalida(): boolean {
    const control = this.formularioCategoria.controls.descripcion;

    return control.touched &&
      (!control.value.trim() || control.invalid);
  }

  private mensajeParaError(
    error: HttpErrorResponse,
    accion: 'crear' | 'modificar' | 'eliminar'
  ): string {
    if ((error.status === 400 || error.status === 409) && error.error?.error) {
      return error.error.error;
    }

    if (error.status === 404 && accion === 'modificar') {
      return 'La categoría que intentas modificar ya no existe.';
    }

    if (error.status === 404 && accion === 'eliminar') {
      return 'La categoría que intentas eliminar ya no existe.';
    }

    if (error.status === 401 || error.status === 403) {
      return `No tienes permisos para ${accion} categorías.`;
    }

    if (error.status === 0) {
      return 'No se pudo conectar con el servicio. Inténtalo de nuevo.';
    }

    return `No se pudo ${accion} la categoría. Inténtalo de nuevo.`;
  }

  private desplazarAlFormulario(): void {
    setTimeout(() => {
      document
        .getElementById('formulario-categoria')
        ?.scrollIntoView({
          behavior: 'smooth',
          block: 'start'
        });
    });
  }
}