import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AdministradorService, RegistroAdministradorRequest } from './administrador.service';

const passwordMatchValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const password = control.get('contrasena')?.value;
  const confirmation = control.get('repetirContrasena')?.value;
  return password === confirmation ? null : { passwordMismatch: true };
};

const passwordRules = [
  {
    label: '8 caracteres mínimo',
    test: (password: string) => password.length >= 8,
  },
  {
    label: '1 mayúscula (A-Z)',
    test: (password: string) => /[A-Z]/.test(password),
  },
  {
    label: '1 minúscula (a-z)',
    test: (password: string) => /[a-z]/.test(password),
  },
  {
    label: '1 número (0-9)',
    test: (password: string) => /[0-9]/.test(password),
  },
  {
    label: '1 carácter especial (@, #, $, ...)',
    test: (password: string) => /[^A-Za-z0-9\s]/.test(password),
  },
] as const;

const passwordRequirementsValidator: ValidatorFn = (
  control: AbstractControl
): ValidationErrors | null => {
  const password = typeof control.value === 'string'
    ? control.value
    : '';

  const unmetRequirements = passwordRules
    .filter(rule => !rule.test(password))
    .map(rule => rule.label);

  return unmetRequirements.length
    ? { passwordRequirements: unmetRequirements }
    : null;
};

@Component({
  selector: 'app-crear-administrador',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './crear-administrador.html',
  styleUrl: './crear-administrador.css'
})
export class CrearAdministrador {
  private readonly fb = inject(FormBuilder);
  private readonly administradorService = inject(AdministradorService);
  private readonly router = inject(Router);

  readonly isSubmitting = signal(false);
  readonly mensajeError = signal('');

  hidePassword = true;
  hideRepeatPassword = true;
  passwordFocused = false;

  readonly form = this.fb.nonNullable.group({
    nombre: ['', Validators.required],
    apellidos: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    sede: [''],
    imagenPerfil: [''],
    contrasena: ['', [
      Validators.required, 
      Validators.minLength(8), 
      passwordRequirementsValidator
    ]],
    repetirContrasena: ['', Validators.required]
  }, { validators: passwordMatchValidator });

  togglePassword(): void {
    this.hidePassword = !this.hidePassword;
  }

  toggleRepeatPassword(): void {
    this.hideRepeatPassword = !this.hideRepeatPassword;
  }

  passwordMismatch(): boolean {
    return this.form.hasError('passwordMismatch') && Boolean(this.form.get('repetirContrasena')?.touched);
  }

  controlError(name: string): string {
    const control = this.form.get(name);

    if (!control?.touched || !control.errors) {
      return '';
    }

    if (control.hasError('required')) {
      return 'Este campo es obligatorio.';
    }

    if (control.hasError('email')) {
      return 'Introduce un correo electrónico válido.';
    }

    if (name === 'contrasena' && control.hasError('minlength')) {
      return 'La contraseña debe tener al menos 8 caracteres.';
    }

    if (name === 'contrasena' && control.hasError('passwordRequirements')) {
      return 'La contraseña debe cumplir todos los requisitos indicados.';
    }

    return 'Revisa este campo.';
  }

  passwordRequirements(): { label: string; met: boolean }[] {
    const password = this.form.get('contrasena')?.value;
    const value = typeof password === 'string' ? password : '';

    return passwordRules.map(rule => ({
      label: rule.label,
      met: rule.test(value),
    }));
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    this.mensajeError.set('');

    const values = this.form.getRawValue();
    const hoy = new Date();
    const fechaActual = hoy.toISOString().split('T')[0];

    const request: RegistroAdministradorRequest = {
      nombre: values.nombre.trim(),
      apellidos: values.apellidos.trim(),
      email: values.email.trim(),
      contrasena: values.contrasena,
      fechaIncorporacion: fechaActual,
      ...(values.sede.trim() ? { sede: values.sede.trim() } : {}),
      ...(values.imagenPerfil.trim() ? { imagenPerfil: values.imagenPerfil.trim() } : {})
    };

    this.administradorService.crearAdministrador(request).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        void this.router.navigate(['/admin/administradores']);
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting.set(false);
        const backendMessage = typeof error.error?.error === 'string' ? error.error.error : null;
        this.mensajeError.set(backendMessage ?? 'No se pudo crear el administrador. Verifica los datos.');
      }
    });
  }
}