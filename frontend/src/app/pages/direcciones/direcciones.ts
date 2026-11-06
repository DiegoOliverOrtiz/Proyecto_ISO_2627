import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { DireccionesService, DireccionResponse, DireccionRequest } from './direcciones.service';
import { ConfirmacionService } from '../../shared/confirmacion/confirmacion.service';

@Component({
  selector: 'app-direcciones',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  styleUrl: './direcciones.css',
  templateUrl: './direcciones.html'
})
export class Direcciones {
  private readonly servicioDirecciones = inject(DireccionesService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly constructorFormularios = inject(FormBuilder);
  private readonly enrutador = inject(Router);

  readonly direcciones = signal<DireccionResponse[]>([]);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly editando = signal(false);
  readonly direccionIdActiva = signal<string | null>(null);
  
  readonly mensajeError = signal('');
  readonly mensajeExito = signal('');
  readonly mensajeFormulario = signal('');

  readonly formulario = this.constructorFormularios.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(50)]],
    calle: ['', [Validators.required, Validators.maxLength(150)]],
    numero: ['', [Validators.required, Validators.maxLength(10)]],
    portal: ['', [Validators.maxLength(10)]],
    piso: ['', [Validators.maxLength(10)]],
    codigoPostal: ['', [Validators.required, Validators.pattern(/^\d{5}$/)]],
    ciudad: ['', [Validators.required, Validators.maxLength(80)]],
    provincia: ['', [Validators.required, Validators.maxLength(80)]]
  });

  constructor() {
    this.cargarDirecciones();
  }

  cargarDirecciones(): void {
    this.cargando.set(true);
    this.mensajeError.set('');
    this.servicioDirecciones.consultarDirecciones().subscribe({
      next: (resultado) => {
        this.direcciones.set(resultado);
        this.cargando.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.cargando.set(false);
        this.mensajeError.set(this.obtenerMensajeError(error, 'No se pudieron cargar las direcciones.'));
      }
    });
  }

  iniciarCreacion(): void {
    this.direccionIdActiva.set(null);
    this.formulario.reset();
    this.mensajeFormulario.set('');
    this.mensajeExito.set('');
    this.editando.set(true);
  }

  iniciarEdicion(direccion: DireccionResponse): void {
    this.direccionIdActiva.set(direccion.id);
    this.formulario.reset({
      nombre: direccion.nombre,
      calle: direccion.calle,
      numero: direccion.numero,
      portal: direccion.portal ?? '',
      piso: direccion.piso ?? '',
      codigoPostal: direccion.codigoPostal,
      ciudad: direccion.ciudad,
      provincia: direccion.provincia
    });
    this.mensajeFormulario.set('');
    this.mensajeExito.set('');
    this.editando.set(true);
  }

  cancelarEdicion(): void {
    this.editando.set(false);
    this.mensajeFormulario.set('');
  }

  guardarDireccion(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.mensajeFormulario.set('Revisa los campos marcados antes de guardar.');
      return;
    }

    const valores = this.formulario.getRawValue();
    const solicitud: DireccionRequest = {
      nombre: valores.nombre.trim(),
      calle: valores.calle.trim(),
      numero: valores.numero.trim(),
      portal: this.valorNulo(valores.portal),
      piso: this.valorNulo(valores.piso),
      codigoPostal: valores.codigoPostal.trim(),
      ciudad: valores.ciudad.trim(),
      provincia: valores.provincia.trim()
    };

    this.guardando.set(true);
    this.mensajeFormulario.set('');
    this.mensajeExito.set('');

    const idActual = this.direccionIdActiva();
    const operacion$ = idActual 
      ? this.servicioDirecciones.modificarDireccion(idActual, solicitud)
      : this.servicioDirecciones.agregarDireccion(solicitud);

    operacion$.subscribe({
      next: () => {
        this.guardando.set(false);
        this.editando.set(false);
        this.mensajeExito.set('Dirección guardada correctamente.');
        this.cargarDirecciones();
      },
      error: (error: HttpErrorResponse) => {
        this.guardando.set(false);
        this.mensajeFormulario.set(this.obtenerMensajeError(error, 'No se pudo guardar la dirección.'));
      }
    });
  }

  async eliminarDireccion(id: string): Promise<void> {
    const confirmado = await this.confirmacion.confirmar({
      titulo: 'Eliminar dirección',
      mensaje: '¿Seguro que quieres eliminar esta dirección? No se puede deshacer.',
      aceptar: 'Eliminar',
      peligro: true
    });
    if (!confirmado) {
      return;
    }

    this.mensajeError.set('');
    this.mensajeExito.set('');
    this.servicioDirecciones.eliminarDireccion(id).subscribe({
      next: () => {
        this.mensajeExito.set('Dirección eliminada correctamente.');
        this.cargarDirecciones();
      },
      error: (error: HttpErrorResponse) => {
        this.mensajeError.set(this.obtenerMensajeError(error, 'No se pudo eliminar la dirección.'));
      }
    });
  }

  private valorNulo(valor: string): string | null {
    const limpio = valor.trim();
    return limpio.length > 0 ? limpio : null;
  }

  private obtenerMensajeError(error: HttpErrorResponse, mensajeGenerico: string): string {
    if (error.status === 401) {
      void this.enrutador.navigate(['/login'], { queryParams: { motivo: 'sesion-expirada' } });
      return 'Tu sesión ya no es válida. Vuelve a iniciar sesión.';
    }
    if (error.status === 403) {
      return 'No tienes permiso para acceder a estas direcciones.';
    }
    if (error.status === 409) {
      return error.error?.message ?? 'No se puede modificar ni eliminar esta dirección porque está vinculada a un pedido en curso.';
    }
    return error.error?.message ?? mensajeGenerico;
  }
}