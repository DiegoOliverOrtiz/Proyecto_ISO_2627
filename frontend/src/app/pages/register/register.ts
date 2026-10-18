import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject } from '@angular/core';

import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators
} from '@angular/forms';

import { RouterLink } from '@angular/router';

import {
  RegistrationService,
  type CategoriaRegistro,
  type ClienteRegistrationRequest,
  type VendedorRegistrationRequest
} from './registration.service';


type UserRole = 'cliente' | 'vendedor';


type Feedback = {
  kind: 'success' | 'error';
  text: string;
  details?: string[];
} | null;


const getLatestBirthDate = (): string => {
  const today = new Date();
  const year = today.getFullYear() - 18;
  const month = today.getMonth();
  const day = Math.min(
    today.getDate(),
    new Date(year, month + 1, 0).getDate()
  );

  return `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
};


const passwordMatchValidator: ValidatorFn = (
  control: AbstractControl
): ValidationErrors | null => {
  const password = control.get('contrasena')?.value;
  const confirmation = control.get('repetirContrasena')?.value;

  return password === confirmation
    ? null
    : { passwordMismatch: true };
};


const adultDateValidator: ValidatorFn = (
  control: AbstractControl
): ValidationErrors | null => {
  if (!control.value) return null;

  return control.value > getLatestBirthDate()
    ? { underage: true }
    : null;
};


const optionalPhoneValidator =
  Validators.pattern(/^\+?[0-9\s().-]{7,20}$/);


const optionalImageUrlValidator =
  Validators.pattern(/^https?:\/\/.+/i);


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
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-register',
  styleUrl: './register.css',
  templateUrl: './register.html',
})
export class Register {

  private readonly formBuilder = inject(FormBuilder);
  private readonly registrationService = inject(RegistrationService);
  private readonly cdr = inject(ChangeDetectorRef);


  // Categorías obtenidas del backend.
  sellerCategories: CategoriaRegistro[] = [];

  categoriesLoading = false;
  categoriesError = false;
  private categoriesLoaded = false;

  readonly latestBirthDate = getLatestBirthDate();

  role: UserRole = 'cliente';
  isSubmitting = false;
  avatarFailed = false;
  feedback: Feedback = null;
  customerPasswordFocused = false;
  sellerPasswordFocused = false;


  readonly clienteForm = this.formBuilder.nonNullable.group({
    nombre: ['', Validators.required],

    apellidos: ['', Validators.required],

    fechaNacimiento: ['', [
      Validators.required,
      adultDateValidator
    ]],

    dniNif: [
      '',
      [
        Validators.required,
        Validators.minLength(9),
        Validators.maxLength(9),
        Validators.pattern(/^[0-9]{8}[A-Za-z]$/)
      ]
    ],

    email: ['', [
      Validators.required,
      Validators.email
    ]],

    telefono: ['', optionalPhoneValidator],

    imagenPerfil: ['', optionalImageUrlValidator],

    contrasena: ['', [
      Validators.required,
      Validators.minLength(8),
      passwordRequirementsValidator
    ]],

    repetirContrasena: ['', Validators.required],

    tipoCliente: ['normal'],
  }, {
    validators: passwordMatchValidator
  });


  readonly vendedorForm = this.formBuilder.nonNullable.group({
    nombre: ['', Validators.required],

    apellidos: ['', Validators.required],

    categoriaId: ['', Validators.required],

    nombreComercial: ['', Validators.required],

    dniNif: [
      '',
      [
        Validators.required,
        Validators.minLength(9),
        Validators.maxLength(9),
        Validators.pattern(/^[0-9]{8}[A-Za-z]$/)
      ]
    ],

    email: ['', [
      Validators.required,
      Validators.email
    ]],

    telefono: ['', optionalPhoneValidator],

    imagenPerfil: ['', optionalImageUrlValidator],

    contrasena: ['', [
      Validators.required,
      Validators.minLength(8),
      passwordRequirementsValidator
    ]],

    repetirContrasena: ['', Validators.required],
  }, {
    validators: passwordMatchValidator
  });


  get fallbackAvatar(): string {
    return this.role === 'cliente'
      ? '/avatars/cliente.svg'
      : '/avatars/vendedor.svg';
  }


  get avatarSource(): string {
    if (this.avatarFailed) {
      return this.fallbackAvatar;
    }

    const imageUrl = this.role === 'cliente'
      ? this.clienteForm.controls.imagenPerfil.value
      : this.vendedorForm.controls.imagenPerfil.value;

    return imageUrl.trim() || this.fallbackAvatar;
  }


  selectRole(role: UserRole): void {
    this.role = role;
    this.feedback = null;
    this.avatarFailed = false;

    if (
      role === 'vendedor' &&
      !this.categoriesLoaded &&
      !this.categoriesLoading
    ) {
      this.loadSellerCategories();
    }
  }


  private loadSellerCategories(): void {
    this.categoriesLoading = true;
    this.categoriesError = false;

    this.registrationService.obtenerCategoriasRegistro().subscribe({
      next: (categories) => {
        this.sellerCategories = categories;
        this.categoriesLoaded = true;
        this.categoriesLoading = false;

        const selectedId =
          this.vendedorForm.controls.categoriaId.value;

        if (
          selectedId &&
          !categories.some(category => category.id === selectedId)
        ) {
          this.vendedorForm.controls.categoriaId.setValue('');
        }

        // Actualizamos la vista al recibir las categorías.
        this.cdr.markForCheck();
      },

      error: () => {
        this.sellerCategories = [];
        this.categoriesError = true;
        this.categoriesLoaded = false;
        this.categoriesLoading = false;

        this.vendedorForm.controls.categoriaId.setValue('');

        // Actualizamos la vista si falla la consulta.
        this.cdr.markForCheck();
      }
    });
  }


  resetAvatarError(): void {
    this.avatarFailed = false;
  }


  controlError(form: AbstractControl, name: string): string {
    const control = form.get(name);

    if (!control?.touched || !control.errors) {
      return '';
    }

    if (control.hasError('required')) {
      return 'Este campo es obligatorio.';
    }

    if (control.hasError('email')) {
      return 'Introduce un correo electrónico válido.';
    }

    if (
      name === 'dniNif' &&
      (
        control.hasError('minlength') ||
        control.hasError('maxlength') ||
        control.hasError('pattern')
      )
    ) {
      return 'El DNI debe tener 8 números y una letra.';
    }

    if (
      name === 'contrasena' &&
      control.hasError('minlength')
    ) {
      return 'La contraseña debe tener al menos 8 caracteres.';
    }

    if (
      name === 'contrasena' &&
      control.hasError('passwordRequirements')
    ) {
      return 'La contraseña debe cumplir todos los requisitos indicados.';
    }

    if (control.hasError('underage')) {
      return 'Debes tener al menos 18 años para registrarte como cliente.';
    }

    if (
      control.hasError('pattern') &&
      name === 'telefono'
    ) {
      return 'Introduce un teléfono válido o deja el campo vacío.';
    }

    if (
      control.hasError('pattern') &&
      name === 'imagenPerfil'
    ) {
      return 'Introduce una URL que empiece por http:// o https://.';
    }

    return 'Revisa este campo.';
  }


  passwordMismatch(form: AbstractControl): boolean {
    return form.hasError('passwordMismatch') &&
      Boolean(form.get('repetirContrasena')?.touched);
  }


  passwordRequirements(
    form: AbstractControl
  ): { label: string; met: boolean }[] {
    const password = form.get('contrasena')?.value;
    const value = typeof password === 'string' ? password : '';

    return passwordRules.map(rule => ({
      label: rule.label,
      met: rule.test(value),
    }));
  }


  register(): void {
    this.feedback = null;

    const form = this.role === 'cliente'
      ? this.clienteForm
      : this.vendedorForm;

    form.markAllAsTouched();

    if (form.invalid || this.isSubmitting) {
      return;
    }

    // Comprobación adicional para vendedores.
    if (this.role === 'vendedor') {
      const selectedId =
        this.vendedorForm.controls.categoriaId.value;

      const validCategory = this.sellerCategories.some(
        category => category.id === selectedId
      );

      if (
        this.categoriesLoading ||
        this.categoriesError ||
        !validCategory
      ) {
        return;
      }
    }

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

        ...this.optionalDetails(
          values.telefono,
          values.imagenPerfil
        ),
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

      // Enviamos el identificador, no el nombre de la categoría.
      categoriaId: values.categoriaId,

      nombreComercial: values.nombreComercial.trim(),

      ...this.optionalDetails(
        values.telefono,
        values.imagenPerfil
      ),
    };

    this.registrationService.registerVendedor(request).subscribe({
      next: () => this.completeRegistration(),
      error: (error: unknown) => this.failRegistration(error),
    });
  }


  private optionalDetails(
    telefono: string,
    imagenPerfil: string
  ): Pick<ClienteRegistrationRequest, 'telefono' | 'imagenPerfil'> {
    return {
      ...(telefono.trim()
        ? { telefono: telefono.trim() }
        : {}),

      ...(imagenPerfil.trim()
        ? { imagenPerfil: imagenPerfil.trim() }
        : {}),
    };
  }


  private completeRegistration(): void {
    this.isSubmitting = false;

    this.feedback = {
      kind: 'success',
      text: 'Registro realizado correctamente. El equipo administrador revisará tus datos antes de activar la cuenta.',
    };

    this.avatarFailed = false;

    // Refrescamos la interfaz al completar el registro.
    this.cdr.markForCheck();
  }


  private failRegistration(error: unknown): void {
    this.isSubmitting = false;

    const responseBody = error instanceof HttpErrorResponse
      ? error.error
      : null;

    const backendMessage =
      typeof responseBody?.error === 'string'
        ? responseBody.error
        : null;

    const fieldErrors = responseBody?.errores;

    const fieldLabels: Record<string, string> = {
      nombre: 'Nombre',
      apellidos: 'Apellidos',
      fechaNacimiento: 'Fecha de nacimiento',
      dniNif: 'DNI/NIF',
      email: 'Correo electrónico',
      contrasena: 'Contraseña',
      categoriaId: 'Categoría',
      nombreComercial: 'Nombre comercial',
    };

    const details =
      fieldErrors && typeof fieldErrors === 'object'
        ? Object.entries(
            fieldErrors as Record<string, unknown>
          ).flatMap(([field, messages]) =>
            Array.isArray(messages)
              ? messages
                  .filter(
                    (message): message is string =>
                      typeof message === 'string'
                  )
                  .map(
                    message =>
                      `${fieldLabels[field] ?? field}: ${message}`
                  )
              : []
          )
        : [];

    this.feedback = {
      kind: 'error',

      text:
        backendMessage ??
        'No se pudo completar el registro. Comprueba los datos e inténtalo de nuevo.',

      ...(details.length
        ? { details }
        : {}),
    };

    // Refrescamos también la interfaz si hay algún error.
    this.cdr.markForCheck();
  }
}