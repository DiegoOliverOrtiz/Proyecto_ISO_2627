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

  // Inicializamos los campos vacíos explícitamente
  readonly form = this.fb.nonNullable.group({
    nombre: ['', Validators.required],
    apellidos: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    sede: [''],
    imagenPerfil: [''],
    contrasena: ['', [Validators.required, Validators.minLength(8)]],
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