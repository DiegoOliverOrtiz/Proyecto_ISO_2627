import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { AbstractControl, FormControl, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ProductoService } from '../paneles/producto.service';
import type { ProductoFavorito } from '../favoritos/favoritos.service';
import { ListaProductos, ListasService, ResumenLista } from './listas.service';

export const MAX_NOMBRE_LISTA = 50;

// Igual que el backend: el nombre se recorta antes de validarlo.
function nombreNoVacio(control: AbstractControl<string>): ValidationErrors | null {
  return (control.value ?? '').trim() === '' ? { required: true } : null;
}

function crearControlNombre(): FormControl<string> {
  return new FormControl('', {
    nonNullable: true,
    validators: [nombreNoVacio, Validators.maxLength(MAX_NOMBRE_LISTA)]
  });
}

@Component({
  selector: 'app-listas',
  standalone: true,
  imports: [DecimalPipe, ReactiveFormsModule, RouterLink],
  templateUrl: './listas.html',
  styleUrl: './listas.css'
})
export class Listas implements OnInit {

  private readonly servicioListas = inject(ListasService);
  private readonly servicioProductos = inject(ProductoService);
  private readonly enrutador = inject(Router);

  readonly maxNombre = MAX_NOMBRE_LISTA;

  readonly listas = signal<ResumenLista[]>([]);
  readonly seleccionada = signal<ListaProductos | null>(null);

  readonly cargando = signal(true);
  readonly cargandoLista = signal(false);
  readonly operacion = signal(false);
  readonly renombrando = signal<string | null>(null);

  readonly error = signal('');
  readonly exito = signal('');

  readonly nombreNueva = crearControlNombre();
  readonly nombreRenombrar = crearControlNombre();

  ngOnInit(): void {
    this.cargarListas();
  }

  cargarListas(): void {
    this.cargando.set(true);
    this.error.set('');

    this.servicioListas.consultarListas().subscribe({
      next: (listas) => {
        this.listas.set(listas);
        this.cargando.set(false);

        const actual = this.seleccionada();
        const siguiente = listas.find((lista) => lista.id === actual?.id) ?? listas[0];
        if (siguiente) {
          this.seleccionar(siguiente);
        } else {
          this.seleccionada.set(null);
        }
      },
      error: (fallo: HttpErrorResponse) => {
        this.cargando.set(false);
        this.gestionarError(fallo, 'No se pudieron cargar tus listas.');
      }
    });
  }

  seleccionar(lista: ResumenLista): void {
    this.cargandoLista.set(true);
    this.renombrando.set(null);

    this.servicioListas.consultarLista(lista.id).subscribe({
      next: (detalle) => {
        this.seleccionada.set(detalle);
        this.cargandoLista.set(false);
      },
      error: (fallo: HttpErrorResponse) => {
        this.cargandoLista.set(false);
        this.gestionarError(fallo, 'No se pudo cargar la lista.');
      }
    });
  }

  crear(): void {
    if (this.nombreNueva.invalid || this.operacion()) {
      this.nombreNueva.markAsTouched();
      return;
    }

    this.iniciarOperacion();

    this.servicioListas.crearLista(this.nombreNueva.value.trim()).subscribe({
      next: (lista) => {
        this.operacion.set(false);
        this.nombreNueva.reset();
        this.exito.set(`Lista «${lista.nombre}» creada.`);
        this.seleccionada.set(lista);
        this.cargarListas();
      },
      error: (fallo: HttpErrorResponse) => {
        this.operacion.set(false);
        this.gestionarError(fallo, 'No se pudo crear la lista.');
      }
    });
  }

  iniciarRenombrar(lista: ListaProductos): void {
    this.renombrando.set(lista.id);
    this.nombreRenombrar.setValue(lista.nombre);
  }

  cancelarRenombrar(): void {
    this.renombrando.set(null);
  }

  renombrar(lista: ListaProductos): void {
    if (this.nombreRenombrar.invalid || this.operacion()) {
      this.nombreRenombrar.markAsTouched();
      return;
    }

    this.iniciarOperacion();

    this.servicioListas.renombrarLista(lista.id, this.nombreRenombrar.value.trim()).subscribe({
      next: (renombrada) => {
        this.operacion.set(false);
        this.renombrando.set(null);
        this.exito.set(`Lista renombrada a «${renombrada.nombre}».`);
        this.seleccionada.set(renombrada);
        this.cargarListas();
      },
      error: (fallo: HttpErrorResponse) => {
        this.operacion.set(false);
        this.gestionarError(fallo, 'No se pudo renombrar la lista.');
      }
    });
  }

  eliminar(lista: ListaProductos): void {
    if (lista.predeterminada || this.operacion()) {
      return;
    }

    const confirmado = confirm(
      `¿Eliminar la lista «${lista.nombre}»? Sus productos seguirán en el catálogo.`
    );
    if (!confirmado) {
      return;
    }

    this.iniciarOperacion();

    this.servicioListas.eliminarLista(lista.id).subscribe({
      next: () => {
        this.operacion.set(false);
        this.exito.set(`Lista «${lista.nombre}» eliminada.`);
        this.seleccionada.set(null);
        this.cargarListas();
      },
      error: (fallo: HttpErrorResponse) => {
        this.operacion.set(false);
        this.gestionarError(fallo, 'No se pudo eliminar la lista.');
      }
    });
  }

  quitarProducto(lista: ListaProductos, producto: ProductoFavorito): void {
    if (this.operacion()) {
      return;
    }

    this.iniciarOperacion();

    this.servicioListas.quitarProducto(lista.id, producto.id).subscribe({
      next: (actualizada) => {
        this.operacion.set(false);
        this.seleccionada.set(actualizada);
        this.actualizarResumen(actualizada);
        this.exito.set('Producto quitado de la lista.');
      },
      error: (fallo: HttpErrorResponse) => {
        this.operacion.set(false);
        this.gestionarError(fallo, 'No se pudo quitar el producto de la lista.');
      }
    });
  }

  urlImagen(imagen: string): string {
    return this.servicioProductos.urlImagen(imagen);
  }

  private iniciarOperacion(): void {
    this.operacion.set(true);
    this.error.set('');
    this.exito.set('');
  }

  private actualizarResumen(lista: ListaProductos): void {
    this.listas.update((listas) =>
      listas.map((resumen) =>
        resumen.id === lista.id
          ? { ...resumen, productoIds: lista.productos.map((producto) => producto.id) }
          : resumen
      )
    );
  }

  private gestionarError(fallo: HttpErrorResponse, mensajePredeterminado: string): void {
    if (fallo.status === 401) {
      this.error.set('Tu sesión ha caducado.');
      void this.enrutador.navigate(['/login'], { queryParams: { motivo: 'sesion-expirada' } });
      return;
    }

    if (fallo.status === 0) {
      this.error.set('No se pudo conectar con el servidor.');
      return;
    }

    // Solo mensajes funcionales del backend de listas (validación, no encontrada, conflicto).
    const mensaje = fallo.error?.error;
    if ([400, 404, 409].includes(fallo.status) && typeof mensaje === 'string' && mensaje) {
      this.error.set(mensaje);
      return;
    }

    this.error.set(mensajePredeterminado);
  }
}
