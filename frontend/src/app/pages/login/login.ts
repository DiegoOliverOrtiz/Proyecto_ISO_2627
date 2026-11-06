import { Component, signal } from '@angular/core';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { AuthService } from './auth.service';
import { marcarSesionIniciada, olvidarSesion } from './marca-sesion';
import { rutaParaRol } from './rutas-por-rol';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [RouterLink, FormsModule, ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrls: ['../acceso/acceso.css', './login.css']
})
export class Login {
  loginForm: FormGroup;
  hidePassword = true;
  isLoading = signal(false);
  mensajeError = signal('');
  mensajeExito = signal('');
  mensajeInfo = signal('');
  requiereSegundoFactor = signal(false);
  codigoSegundoFactor = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', Validators.required]
    });

    // Al llegar al login la sesión anterior ya no cuenta (A12).
    olvidarSesion();

    const motivo = this.route.snapshot.queryParamMap.get('motivo');

    if (motivo === 'sin-sesion') {
      // A12: quien nunca inició sesión no ve el aviso de sesión caducada o bloqueada.
      this.mensajeInfo.set('Inicia sesión para continuar.');
    } else if (motivo === 'sesion-expirada') {
      this.mensajeError.set(
        'Tu sesión ya no es válida. Es posible que tu cuenta haya sido bloqueada.'
      );
    } else if (motivo === 'cuenta-eliminada') {
      this.mensajeExito.set('La cuenta se ha eliminado correctamente.');
    }
  }

  // Un campo se marca como erróneo solo después de que el usuario lo haya tocado.
  campoInvalido(nombre: 'email' | 'password'): boolean {
    const control = this.loginForm.get(nombre);
    return !!control && control.invalid && control.touched;
  }

  togglePassword(): void {
    this.hidePassword = !this.hidePassword;
  }

  onSubmit(): void {
    if (this.requiereSegundoFactor()) {
      this.verificarSegundoFactor();
      return;
    }
    if (this.loginForm.valid) {
      this.isLoading.set(true);
      this.mensajeError.set('');
      this.mensajeExito.set('');

      const { email, password } = this.loginForm.value;

      this.authService.iniciarSesion(
        email ?? '',
        password ?? ''
      ).subscribe({
        next: (respuesta) => {
          this.isLoading.set(false);
          if (respuesta.requiresTwoFactor) {
            this.requiereSegundoFactor.set(true);
            this.mensajeExito.set('Introduce el código de tu aplicación de autenticación.');
            return;
          }
          marcarSesionIniciada();
          this.router.navigateByUrl(
            rutaParaRol(respuesta.role)
          );
        },
        error: (error: HttpErrorResponse) => {
          this.isLoading.set(false);

          const cuerpo = error.error;

          this.mensajeError.set(
            typeof cuerpo === 'object' &&
            cuerpo !== null &&
            'error' in cuerpo &&
            typeof cuerpo.error === 'string'
              ? cuerpo.error
              : 'No se pudo conectar con el servicio. Inténtalo de nuevo más tarde.'
          );
        }
      });
    } else {
      this.loginForm.markAllAsTouched();
    }
  }

  verificarSegundoFactor(): void {
    const codigo = this.codigoSegundoFactor.replace(/[\s-]/g, '').toUpperCase();
    if (!/^([0-9]{6}|[A-Z2-7]{8}|[A-Z2-7]{16})$/.test(codigo)) {
      this.mensajeError.set('Introduce un código TOTP de 6 cifras o un código de respaldo válido.');
      return;
    }
    this.isLoading.set(true);
    this.mensajeError.set('');
    this.authService.verificarSegundoFactor(codigo).subscribe({
      next: (respuesta) => {
        this.isLoading.set(false);
        marcarSesionIniciada();
        this.router.navigateByUrl(rutaParaRol(respuesta.role));
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading.set(false);
        if (error.status === 410) {
          this.volverAlInicioSesion();
          this.mensajeError.set('La comprobación ha caducado. Vuelve a iniciar sesión.');
          return;
        }
        this.mensajeError.set(typeof error.error?.error === 'string'
          ? error.error.error
          : 'El código del segundo factor no es válido o ya se ha utilizado.');
      }
    });
  }

  volverAlInicioSesion(): void {
    this.requiereSegundoFactor.set(false);
    this.codigoSegundoFactor = '';
    this.loginForm.controls['password'].reset('');
    this.mensajeError.set('');
    this.mensajeExito.set('');
  }
}
