import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { CategoriaPerfil, ModificarPerfilRequest, PerfilResponse, PerfilService } from './perfil.service';

const mayorDeEdad: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  if (!control.value) {
    return null;
  }

  const fechaNacimiento = new Date(`${control.value}T00:00:00`);
  if (Number.isNaN(fechaNacimiento.getTime())) {
    return { fechaInvalida: true };
  }

  const hoy = new Date();
  let edad = hoy.getFullYear() - fechaNacimiento.getFullYear();
  const cumpleEsteAno = new Date(hoy.getFullYear(), fechaNacimiento.getMonth(), fechaNacimiento.getDate());
  if (hoy < cumpleEsteAno) {
    edad--;
  }

  return edad >= 18 ? null : { menorDeEdad: true };
};

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [ReactiveFormsModule],
  styleUrl: './perfil.css',
  templateUrl: './perfil.html'
})
export class Perfil {
  private readonly servicioPerfil = inject(PerfilService);
  private readonly enrutador = inject(Router);
  private readonly constructorFormularios = inject(FormBuilder);

  readonly perfil = signal<PerfilResponse | null>(null);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly editando = signal(false);
  readonly mensajeError = signal('');
  readonly mensajeFormulario = signal('');
  readonly mensajeExito = signal('');
  readonly fechaMaximaNacimiento = this.calcularFechaMaximaNacimiento();
  readonly categorias = signal<CategoriaPerfil[]>([]);

  readonly formulario = this.constructorFormularios.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    apellidos: ['', [Validators.required, Validators.maxLength(150)]],
    telefono: ['', [Validators.maxLength(30), Validators.pattern(/^\+?[0-9\s().-]{7,20}$/)]],
    imagenPerfil: ['', [Validators.maxLength(1000), Validators.pattern(/^https?:\/\/.+/i)]],
    dniNif: ['', [Validators.maxLength(9), Validators.pattern(/^[0-9]{8}[A-Za-z]$/)]],
    fechaNacimiento: ['', [mayorDeEdad]],
    sede: ['', [Validators.maxLength(150)]],
    categoriaId: ['']
  });

  constructor() {
    this.cargarPerfil();
  }

  cargarPerfil(): void {
    this.cargando.set(true);
    this.mensajeError.set('');

    this.servicioPerfil.consultarPerfil().subscribe({
      next: (respuesta) => {
        this.perfil.set(respuesta);
        if (respuesta.tipoUsuario === 'VENDEDOR') {
          this.servicioPerfil.obtenerCategorias().subscribe({
            next: (categorias) => this.categorias.set(categorias),
            error: () => this.categorias.set([])
          });
        }
        this.cargando.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.cargando.set(false);
        this.mensajeError.set(this.obtenerMensajeError(error, 'No se pudo cargar el perfil. Inténtalo de nuevo.'));

        if (error.status === 401) {
          void this.enrutador.navigate(['/login'], {
            queryParams: { motivo: 'sesion-expirada' }
          });
        }
      }
    });
  }

  iniciarEdicion(): void {
    const datos = this.perfil();
    if (!datos) {
      return;
    }

    this.formulario.reset({
      nombre: datos.nombre,
      apellidos: datos.apellidos,
      telefono: datos.tipoUsuario === 'ADMIN' ? '' : datos.telefono ?? '',
      imagenPerfil: datos.imagenPerfil ?? '',
      dniNif: '',
      fechaNacimiento: datos.tipoUsuario === 'CLIENTE' ? datos.fechaNacimiento ?? '' : '',
      sede: datos.tipoUsuario === 'ADMIN' ? datos.sede ?? '' : '',
      categoriaId: ''
    });
    this.formulario.controls.fechaNacimiento.setValidators(
      datos.tipoUsuario === 'CLIENTE' ? [mayorDeEdad] : []
    );
    this.formulario.controls.fechaNacimiento.updateValueAndValidity();
    this.mensajeFormulario.set('');
    this.mensajeExito.set('');
    this.editando.set(true);
  }

  cancelarEdicion(): void {
    this.editando.set(false);
    this.mensajeFormulario.set('');
  }

  guardarCambios(): void {
    const datos = this.perfil();
    if (!datos || this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.mensajeFormulario.set('Revisa los campos marcados antes de guardar.');
      return;
    }

    const valores = this.formulario.getRawValue();
    const solicitud: ModificarPerfilRequest = {
      nombre: valores.nombre.trim(),
      apellidos: valores.apellidos.trim(),
      telefono: datos.tipoUsuario === 'ADMIN' ? datos.telefono : this.valorNulo(valores.telefono),
      imagenPerfil: this.valorNulo(valores.imagenPerfil)
    };

    if (datos.tipoUsuario === 'CLIENTE' || datos.tipoUsuario === 'VENDEDOR') {
      solicitud.dniNif = this.valorNulo(valores.dniNif);
    }
    if (datos.tipoUsuario === 'VENDEDOR') {
      solicitud.categoriaId = this.valorNulo(valores.categoriaId);
    }
    if (datos.tipoUsuario === 'CLIENTE') {
      solicitud.fechaNacimiento = this.valorNulo(valores.fechaNacimiento);
    }
    if (datos.tipoUsuario === 'ADMIN') {
      solicitud.sede = this.valorNulo(valores.sede);
    }

    this.guardando.set(true);
    this.mensajeFormulario.set('');
    this.mensajeExito.set('');

    this.servicioPerfil.modificarPerfil(solicitud).subscribe({
      next: (perfilActualizado) => {
        this.perfil.set(perfilActualizado);
        this.editando.set(false);
        this.guardando.set(false);
        this.mensajeExito.set('Los datos del perfil se han actualizado correctamente.');
      },
      error: (error: HttpErrorResponse) => {
        this.guardando.set(false);
        this.mensajeFormulario.set(this.obtenerMensajeError(error, 'No se pudieron guardar los cambios.'));

        if (error.status === 401) {
          void this.enrutador.navigate(['/login'], {
            queryParams: { motivo: 'sesion-expirada' }
          });
        }
      }
    });
  }

  volverAlPanel(): void {
    const tipoUsuario = this.perfil()?.tipoUsuario;
    const ruta = tipoUsuario === 'ADMIN'
      ? '/admin'
      : tipoUsuario === 'VENDEDOR'
        ? '/vendedor'
        : '/cliente';

    void this.enrutador.navigateByUrl(ruta);
  }

  private valorNulo(valor: string): string | null {
    const limpio = valor.trim();
    return limpio.length > 0 ? limpio : null;
  }

  private calcularFechaMaximaNacimiento(): string {
    const hoy = new Date();
    hoy.setFullYear(hoy.getFullYear() - 18);
    return hoy.toISOString().slice(0, 10);
  }

  private obtenerMensajeError(error: HttpErrorResponse, mensajeGenerico: string): string {
    if (error.status === 401) {
      return 'Tu sesión ya no es válida. Vuelve a iniciar sesión.';
    }
    if (error.status === 403) {
      return 'No tienes permiso para modificar este perfil.';
    }
    if (error.status === 0) {
      return 'No se pudo conectar con el servicio. Inténtalo de nuevo.';
    }
    return error.error?.message ?? mensajeGenerico;
  }
}
