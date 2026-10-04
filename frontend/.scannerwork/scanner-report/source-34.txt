import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { RegistrationService, type ClienteRegistrationRequest, type VendedorRegistrationRequest } from './registration.service';

type UserRole = 'cliente' | 'vendedor';
type Feedback = { kind: 'success' | 'error'; text: string } | null;

const SELLER_CATEGORIES: readonly string[] = [];
const passwordMatchValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const password = control.get('contrasena')?.value;
  const confirmation = control.get('repetirContrasena')?.value;
  return password === confirmation ? null : { passwordMismatch: true };
};

const notFutureDateValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  if (!control.value) return null;
  return control.value > new Date().toISOString().slice(0, 10) ? { futureDate: true } : null;
};

const optionalPhoneValidator = Validators.pattern(/^\+?[0-9\s().-]{7,20}$/);
const optionalImageUrlValidator = Validators.pattern(/^https?:\/\/.+/i);

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-register',
  styleUrl: './register.css',
  templateUrl: './register.html',
})
export class Register {
  private readonly formBuilder = inject(FormBuilder);
  private readonly registrationService = inject(RegistrationService);

  readonly sellerCategories = SELLER_CATEGORIES;
  readonly today = new Date().toISOString().slice(0, 10);
  role: UserRole = 'cliente';
  isSubmitting = false;
  avatarFailed = false;
  feedback: Feedback = null;

  readonly clienteForm = this.formBuilder.nonNullable.group({
    nombre: ['', Validators.required],
    apellidos: ['', Validators.required],
    fechaNacimiento: ['', [Validators.required, notFutureDateValidator]],
    dniNif: ['', [Validators.required, Validators.maxLength(9)]],
    email: ['', [Validators.required, Validators.email]],
    telefono: ['', optionalPhoneValidator],
    imagenPerfil: ['', optionalImageUrlValidator],
    contrasena: ['', [Validators.required, Validators.minLength(8)]],
    repetirContrasena: ['', Validators.required],
    tipoCliente: ['normal'],
  }, { validators: passwordMatchValidator });

  readonly vendedorForm = this.formBuilder.nonNullable.group({
    nombre: ['', Validators.required],
    apellidos: ['', Validators.required],
    categoria: ['', Validators.required],
    nombreComercial: ['', Validators.required],
    dniNif: ['', [Validators.required, Validators.maxLength(9)]],
    email: ['', [Validators.required, Validators.email]],
    telefono: ['', optionalPhoneValidator],
    imagenPerfil: ['', optionalImageUrlValidator],
    contrasena: ['', [Validators.required, Validators.minLength(8)]],
    repetirContrasena: ['', Validators.required],
  }, { validators: passwordMatchValidator });

  get fallbackAvatar(): string {
    return this.role === 'cliente' ? '/avatars/cliente.svg' : '/avatars/vendedor.svg';
  }

  get avatarSource(): string {
    if (this.avatarFailed) return this.fallbackAvatar;
    const imageUrl = this.role === 'cliente'
      ? this.clienteForm.controls.imagenPerfil.value
      : this.vendedorForm.controls.imagenPerfil.value;
    return imageUrl.trim() || this.fallbackAvatar;
  }

  selectRole(role: UserRole): void {
    this.role = role;
    this.feedback = null;
    this.avatarFailed = false;
  }

  resetAvatarError(): void {
    this.avatarFailed = false;
  }

  controlError(form: AbstractControl, name: string): string {
    const control = form.get(name);
    if (!control?.touched || !control.errors) return '';
    if (control.hasError('required')) return 'Este campo es obligatorio.';
    if (control.hasError('email')) return 'Introduce un correo electrónico válido.';
    if (control.hasError('maxlength')) return 'El DNI/NIF no puede superar los 9 caracteres.';
    if (control.hasError('minlength')) return 'La contraseña debe tener al menos 8 caracteres.';
    if (control.hasError('futureDate')) return 'La fecha no puede ser posterior a hoy.';
    if (control.hasError('pattern') && name === 'telefono') return 'Introduce un teléfono válido o deja el campo vacío.';
    if (control.hasError('pattern')) return 'Introduce una URL que empiece por http:// o https://.';
    return 'Revisa este campo.';
  }

  passwordMismatch(form: AbstractControl): boolean {
    return form.hasError('passwordMismatch') && Boolean(form.get('repetirContrasena')?.touched);
  }

  register(): void {
    this.feedback = null;
    const form = this.role === 'cliente' ? this.clienteForm : this.vendedorForm;
    form.markAllAsTouched();
    if (form.invalid || this.isSubmitting) return;

    this.isSubmitting = true;
    if (this.role === 'cliente') {
      const values = this.clienteForm.getRawValue();
      const request: ClienteRegistrationRequest = {
        nombre: values.nombre.trim(),
        apellidos: values.apellidos.trim(),
        fechaNacimiento: values.fechaNacimiento,
        dniNif: values.dniNif.trim(),
        email: values.email.trim(),
        contrasena: values.contrasena,
        esPremium: values.tipoCliente === 'premium',
        ...this.optionalDetails(values.telefono, values.imagenPerfil),
      };
      this.registrationService.registerCliente(request).subscribe({
        next: () => this.completeRegistration(),
        error: (error: unknown) => this.failRegistration(error),
      });
      return;
    }

    const values = this.vendedorForm.getRawValue();
    const request: VendedorRegistrationRequest = {
      nombre: values.nombre.trim(),
      apellidos: values.apellidos.trim(),
      dniNif: values.dniNif.trim(),
      email: values.email.trim(),
      contrasena: values.contrasena,
      categoria: values.categoria,
      nombreComercial: values.nombreComercial.trim(),
      ...this.optionalDetails(values.telefono, values.imagenPerfil),
    };
    this.registrationService.registerVendedor(request).subscribe({
      next: () => this.completeRegistration(),
      error: (error: unknown) => this.failRegistration(error),
    });
  }

  private optionalDetails(telefono: string, imagenPerfil: string): Pick<ClienteRegistrationRequest, 'telefono' | 'imagenPerfil'> {
    return {
      ...(telefono.trim() ? { telefono: telefono.trim() } : {}),
      ...(imagenPerfil.trim() ? { imagenPerfil: imagenPerfil.trim() } : {}),
    };
  }

  private completeRegistration(): void {
    this.isSubmitting = false;
    this.feedback = {
      kind: 'success',
      text: 'Registro realizado correctamente. El equipo administrador revisará tus datos antes de activar la cuenta.',
    };
    this.avatarFailed = false;
  }

  private failRegistration(error: unknown): void {
    this.isSubmitting = false;
    const backendMessage = error instanceof HttpErrorResponse && typeof error.error?.error === 'string'
      ? error.error.error
      : null;
    this.feedback = {
      kind: 'error',
      text: backendMessage ?? 'No se pudo completar el registro. Comprueba los datos e inténtalo de nuevo.',
    };
  }
}
