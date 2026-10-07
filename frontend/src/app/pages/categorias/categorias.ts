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
  CrearCategoriaRequest
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
  readonly mensajeCreacion = signal('');
  readonly errorCreacion = signal('');

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
    this.mostrandoFormulario.set(true);
    this.mensajeCreacion.set('');
    this.errorCreacion.set('');
  }

  cancelarCreacion(): void {
    this.mostrandoFormulario.set(false);
    this.formularioCategoria.reset();
    this.errorCreacion.set('');
  }

  crearCategoria(): void {
    this.mensajeCreacion.set('');
    this.errorCreacion.set('');

    this.formularioCategoria.markAllAsTouched();

    if (this.formularioCategoria.invalid || this.creando()) {
      return;
    }

    const valores = this.formularioCategoria.getRawValue();

    const request: CrearCategoriaRequest = {
      nombre: valores.nombre.trim(),
      descripcion: valores.descripcion.trim()
    };

    if (!request.nombre || !request.descripcion) {
      this.errorCreacion.set(
        'El nombre y la descripción no pueden estar vacíos.'
      );
      return;
    }

    this.creando.set(true);

    this.categoriasService.crearCategoria(request).subscribe({
      next: () => {
        this.creando.set(false);
        this.mostrandoFormulario.set(false);
        this.formularioCategoria.reset();

        this.mensajeCreacion.set(
          'Categoría creada correctamente.'
        );

        this.cargarCategorias();
      },
      error: (error: HttpErrorResponse) => {
        this.creando.set(false);
        this.errorCreacion.set(
          this.mensajeParaErrorCreacion(error)
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

  private mensajeParaErrorCreacion(
    error: HttpErrorResponse
  ): string {
    if (error.status === 400 && error.error?.error) {
      return error.error.error;
    }

    if (error.status === 401 || error.status === 403) {
      return 'No tienes permisos para crear categorías.';
    }

    if (error.status === 0) {
      return 'No se pudo conectar con el servicio. Inténtalo de nuevo.';
    }

    return 'No se pudo crear la categoría. Inténtalo de nuevo.';
  }
}