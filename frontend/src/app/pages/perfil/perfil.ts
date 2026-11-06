import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, FormsModule, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { CategoriaPerfil, ModificarPerfilRequest, PerfilResponse, PerfilService } from './perfil.service';
import { ConfirmacionService } from '../../shared/confirmacion/confirmacion.service';

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

const NOMBRES_TIPO_USUARIO: Record<string, string> = {
  CLIENTE: 'Cliente',
  VENDEDOR: 'Vendedor',
  ADMIN: 'Administrador'
};

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [DatePipe, FormsModule, ReactiveFormsModule, RouterLink],
  styleUrls: ['../tienda/tienda.css', './perfil.css'],
  templateUrl: './perfil.html'
})
export class Perfil {
  private readonly servicioPerfil = inject(PerfilService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly enrutador = inject(Router);
  private readonly constructorFormularios = inject(FormBuilder);

  readonly perfil = signal<PerfilResponse | null>(null);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly editando = signal(false);
  readonly mensajeError = signal('');
  readonly mensajeFormulario = signal('');
  readonly mensajeExito = signal('');
  readonly mostrandoConfirmacionTipo = signal(false);
  readonly tipoPremiumPendiente = signal(false);
  readonly pasoConfirmacionBaja = signal<0 | 1 | 2>(0);
  readonly bajaEnCurso = signal(false);
  readonly errorBaja = signal('');
  readonly fechaMaximaNacimiento = this.calcularFechaMaximaNacimiento();
  readonly categorias = signal<CategoriaPerfil[]>([]);
  readonly configuracionTOTP = signal<{ otpauthUri: string | null; qrDataUrl: string | null } | null>(null);
  readonly codigosRespaldo = signal<string[]>([]);
  readonly codigoTOTP = signal('');
  readonly mensajeTOTP = signal('');
  readonly errorTOTP = signal('');

  /** Nombre legible del tipo de usuario (el backend envía el valor del enum). */
  nombreTipoUsuario(tipo: string): string {
    return NOMBRES_TIPO_USUARIO[tipo] ?? tipo;
  }

  /** Inicial del avatar, siempre en mayúscula (B9). */
  inicial(nombre: string | null | undefined): string {
    return (nombre ?? '').trim().charAt(0).toLocaleUpperCase('es-ES');
  }
  readonly cargandoTOTP = signal(false);

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

  cambiarTipoCliente(): void {
    const datos = this.perfil();
    if (!datos || datos.tipoUsuario !== 'CLIENTE' || this.guardando()) {
      return;
    }

    const nuevoTipoPremium = datos.esPremium !== true;
    this.tipoPremiumPendiente.set(nuevoTipoPremium);
    this.mostrandoConfirmacionTipo.set(true);
  }

  cancelarCambioTipoCliente(): void {
    this.mostrandoConfirmacionTipo.set(false);
  }

  confirmarCambioTipoCliente(): void {
    if (!this.mostrandoConfirmacionTipo() || this.guardando()) {
      return;
    }

    const nuevoTipoPremium = this.tipoPremiumPendiente();
    this.mostrandoConfirmacionTipo.set(false);
    this.guardando.set(true);
    this.mensajeFormulario.set('');
    this.mensajeExito.set('');

    this.servicioPerfil.cambiarTipoCliente(nuevoTipoPremium).subscribe({
      next: (perfilActualizado) => {
        this.perfil.set(perfilActualizado);
        this.guardando.set(false);
        this.mensajeExito.set('El tipo de cliente se ha actualizado correctamente.');
      },
      error: (error: HttpErrorResponse) => {
        this.guardando.set(false);
        this.mensajeFormulario.set(this.obtenerMensajeError(error, 'No se pudo actualizar el tipo de cliente.'));

        if (error.status === 401) {
          void this.enrutador.navigate(['/login'], {
            queryParams: { motivo: 'sesion-expirada' }
          });
        }
      }
    });
  }

  iniciarBaja(): void {
    const tipoUsuario = this.perfil()?.tipoUsuario;
    if ((tipoUsuario !== 'CLIENTE' && tipoUsuario !== 'VENDEDOR') || this.bajaEnCurso()) {
      return;
    }
    this.errorBaja.set('');
    this.pasoConfirmacionBaja.set(1);
  }

  continuarBaja(): void {
    if (this.pasoConfirmacionBaja() === 1) {
      this.pasoConfirmacionBaja.set(2);
      return;
    }
    if (this.pasoConfirmacionBaja() !== 2) {
      return;
    }

    const tipoUsuario = this.perfil()?.tipoUsuario;
    if (tipoUsuario !== 'CLIENTE' && tipoUsuario !== 'VENDEDOR') {
      this.pasoConfirmacionBaja.set(0);
      return;
    }

    this.bajaEnCurso.set(true);
    this.errorBaja.set('');
    this.servicioPerfil.darDeBajaCuenta(tipoUsuario).subscribe({
      next: () => {
        this.bajaEnCurso.set(false);
        this.pasoConfirmacionBaja.set(0);
        void this.enrutador.navigate(['/login'], { queryParams: { motivo: 'cuenta-eliminada' } });
      },
      error: (error: HttpErrorResponse) => {
        this.bajaEnCurso.set(false);
        this.pasoConfirmacionBaja.set(0);
        if (error.status === 401) {
          void this.enrutador.navigate(['/login'], { queryParams: { motivo: 'sesion-expirada' } });
          return;
        }
        this.errorBaja.set(this.obtenerMensajeError(error,
          'No se pudo eliminar la cuenta. Inténtalo de nuevo.'));
      }
    });
  }

  cancelarBaja(): void {
    if (!this.bajaEnCurso()) {
      this.pasoConfirmacionBaja.set(0);
    }
  }

  configurarTOTP(): void {
    this.cargandoTOTP.set(true);
    this.errorTOTP.set('');
    this.codigoTOTP.set('');
    this.mensajeTOTP.set('');
    this.servicioPerfil.configurarSegundoFactor().subscribe({
      next: (respuesta) => {
        this.cargandoTOTP.set(false);
        if (respuesta.enabled) {
          this.mensajeTOTP.set('El segundo factor ya está activado.');
          return;
        }
        this.configuracionTOTP.set({ otpauthUri: respuesta.otpauthUri, qrDataUrl: respuesta.qrDataUrl });
        this.mensajeTOTP.set('Escanea el QR con Google Authenticator o Authy y confirma el código.');
      },
      error: () => {
        this.cargandoTOTP.set(false);
        this.errorTOTP.set('No se pudo iniciar la configuración del segundo factor.');
      }
    });
  }

  confirmarTOTP(): void {
    const codigo = this.codigoTOTP().replace(/[\s-]/g, '');
    if (!/^\d{6}$/.test(codigo)) {
      this.errorTOTP.set('Introduce un código TOTP de 6 cifras.');
      return;
    }
    this.cargandoTOTP.set(true);
    this.errorTOTP.set('');
    this.servicioPerfil.confirmarSegundoFactor(codigo).subscribe({
      next: (respuesta) => {
        this.cargandoTOTP.set(false);
        this.codigosRespaldo.set(respuesta.backupCodes);
        this.configuracionTOTP.set(null);
        this.codigoTOTP.set('');
        this.mensajeTOTP.set('TOTP activado. Guarda estos códigos de respaldo en un lugar seguro.');
        this.perfil.update((valor) => valor ? { ...valor, totpActivado: true } : valor);
      },
      error: (error: HttpErrorResponse) => {
        this.cargandoTOTP.set(false);
        this.errorTOTP.set(this.obtenerMensajeError(error, 'No se pudo activar el segundo factor. Inténtalo de nuevo.'));
      }
    });
  }

  async desactivarTOTP(): Promise<void> {
    const codigo = this.codigoTOTP().replace(/[\s-]/g, '').toUpperCase();
    if (!/^(\d{6}|[A-Z2-7]{8}|[A-Z2-7]{16})$/.test(codigo)) {
      this.errorTOTP.set('Introduce un código actual de la aplicación o un código de respaldo para desactivar TOTP.');
      return;
    }
    const confirmado = await this.confirmacion.confirmar({
      titulo: 'Desactivar el segundo factor',
      mensaje: 'Tu cuenta quedará protegida solo con la contraseña.',
      aceptar: 'Desactivar',
      peligro: true
    });
    if (!confirmado) {
      return;
    }
    this.cargandoTOTP.set(true);
    this.errorTOTP.set('');
    this.mensajeTOTP.set('');
    this.servicioPerfil.desactivarSegundoFactor(codigo).subscribe({
      next: () => {
        this.cargandoTOTP.set(false);
        this.perfil.update((valor) => valor ? { ...valor, totpActivado: false } : valor);
        this.codigoTOTP.set('');
        this.codigosRespaldo.set([]);
        this.configuracionTOTP.set(null);
        this.mensajeTOTP.set('Segundo factor desactivado.');
      },
      error: (error: HttpErrorResponse) => {
        this.cargandoTOTP.set(false);
        this.errorTOTP.set(this.obtenerMensajeError(error, 'No se pudo desactivar el segundo factor.'));
      }
    });
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
    if (error.status >= 500) {
      return mensajeGenerico;
    }
    const mensaje = error.error?.error ?? error.error?.message;
    return typeof mensaje === 'string' ? mensaje : mensajeGenerico;
  }
}
