import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { RecuperacionContrasenaService } from './recuperacion-contrasena.service';

const requisitosContrasena = [
  {
    label: '8 caracteres como mínimo',
    cumple: (valor: string) => Array.from(valor).length >= 8
  },
  {
    label: 'Al menos una letra mayúscula',
    cumple: (valor: string) => /\p{Lu}/u.test(valor)
  },
  {
    label: 'Al menos una letra minúscula',
    cumple: (valor: string) => /\p{Ll}/u.test(valor)
  },
  {
    label: 'Al menos un número',
    cumple: (valor: string) => /\p{Nd}/u.test(valor)
  },
  {
    label: 'Al menos un símbolo, sin contar espacios',
    cumple: (valor: string) => /[^\p{L}\p{N}\s]/u.test(valor)
  }
] as const;

const validadorRequisitosContrasena: ValidatorFn = (
  control: AbstractControl
): ValidationErrors | null => {
  const valor = typeof control.value === 'string' ? control.value : '';
  return requisitosContrasena.every(requisito => requisito.cumple(valor))
    ? null
    : { passwordRequirements: true };
};

const validadorCoincidenciaContrasenas: ValidatorFn = (
  control: AbstractControl
): ValidationErrors | null => {
  const contrasena = control.get('nuevaContrasena')?.value;
  const confirmacion = control.get('confirmarContrasena')?.value;
  return !confirmacion || contrasena === confirmacion
    ? null
    : { passwordMismatch: true };
};

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-recuperacion-contrasena',
  styleUrls: ['../acceso/acceso.css', './recuperacion-contrasena.css'],
  templateUrl: './recuperacion-contrasena.html',
})
export class RecuperacionContrasena {
  private readonly formBuilder = inject(FormBuilder);
  private readonly recuperacionService = inject(RecuperacionContrasenaService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly token = this.route.snapshot.queryParamMap.get('token') ?? '';

  readonly modoRestablecimiento = computed(() => this.token.length > 0);
  readonly isLoading = signal(false);
  readonly solicitudEnviada = signal(false);
  readonly restablecimientoCompletado = signal(false);
  readonly mensajeError = signal('');

  readonly solicitudForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]]
  });

  readonly restablecimientoForm = this.formBuilder.nonNullable.group({
    nuevaContrasena: [
      '',
      [
        Validators.required,
        Validators.minLength(8),
        validadorRequisitosContrasena
      ]
    ],
    confirmarContrasena: ['', Validators.required]
  }, {
    validators: validadorCoincidenciaContrasenas
  });

  nuevaContrasenaOculta = true;
  confirmacionOculta = true;

  constructor() {
    if (this.token) {
      // El token se queda en memoria y se quita de la URL para que no quede
      // en el historial del navegador ni en capturas de pantalla.
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { token: null },
        queryParamsHandling: 'merge',
        replaceUrl: true
      });
    }
  }

  requisitos(): { label: string; cumplido: boolean }[] {
    const valor = this.restablecimientoForm.controls.nuevaContrasena.value;
    return requisitosContrasena.map(requisito => ({
      label: requisito.label,
      cumplido: requisito.cumple(valor)
    }));
  }

  solicitarRecuperacion(): void {
    this.mensajeError.set('');
    this.solicitudForm.markAllAsTouched();
    if (this.solicitudForm.invalid || this.isLoading()) {
      return;
    }

    this.isLoading.set(true);
    this.solicitudEnviada.set(false);
    this.recuperacionService.solicitar(this.solicitudForm.controls.email.value).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.solicitudEnviada.set(true);
      },
      error: () => {
        this.isLoading.set(false);
        this.mensajeError.set(
          'No se pudo procesar la solicitud. Inténtalo de nuevo más tarde.'
        );
      }
    });
  }

  restablecerContrasena(): void {
    this.mensajeError.set('');
    this.restablecimientoForm.markAllAsTouched();
    if (this.restablecimientoForm.invalid || this.isLoading()) {
      return;
    }

    this.isLoading.set(true);
    this.recuperacionService.confirmar(
      this.token,
      this.restablecimientoForm.controls.nuevaContrasena.value
    ).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.restablecimientoCompletado.set(true);
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading.set(false);
        this.mensajeError.set(this.mensajeErrorBackend(error));
      }
    });
  }

  private mensajeErrorBackend(error: HttpErrorResponse): string {
    const cuerpo: unknown = error.error;
    if (
      typeof cuerpo === 'object' &&
      cuerpo !== null &&
      'error' in cuerpo &&
      typeof cuerpo.error === 'string'
    ) {
      return cuerpo.error;
    }
    return 'No se pudo cambiar la contraseña. Comprueba el enlace e inténtalo de nuevo.';
  }
}
