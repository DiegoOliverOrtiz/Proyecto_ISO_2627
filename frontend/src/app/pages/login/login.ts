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
  requiereAltaTotp = signal(false);
  requiereVerificacionEmail = signal(false);
  emailOtpEnviado = signal(false);
  qrTotp = signal('');
  codigosRespaldo = signal<string[]>([]);
  rolTrasAlta: 'ADMIN' | 'VENDEDOR' | null = null;
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
    if (this.requiereAltaTotp()) {
      this.confirmarAltaTotp();
      return;
    }

    if (this.requiereVerificacionEmail()) {
      this.verificarTercerFactor();
      return;
    }

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
          if (respuesta.requiresTwoFactorSetup) {
            this.requiereAltaTotp.set(true);

            this.rolTrasAlta =
              respuesta.role === 'ADMIN' ? 'ADMIN' : 'VENDEDOR';

            this.cargarQrTotp();
            return;
          }
          if (respuesta.requiresTwoFactor) {
            this.requiereSegundoFactor.set(true);
            this.mensajeExito.set('Introduce el código de tu aplicación de autenticación.');
            return;
          }
          if (respuesta.requiresEmailVerification) {
            this.requiereVerificacionEmail.set(true);
            this.emailOtpEnviado.set(!!respuesta.emailVerificationEmailSent);
            this.mensajeExito.set(
              this.emailOtpEnviado()
                ? 'Introduce el código enviado a tu correo electrónico.'
                : 'No se pudo enviar el código. Usa el botón para solicitarlo de nuevo.'
            );
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

  cargarQrTotp(): void {
    this.isLoading.set(true);

    this.authService.configurarTotpObligatorio().subscribe({
      next: ({ qrDataUrl }) => {
        this.isLoading.set(false);
        this.qrTotp.set(qrDataUrl);

        this.mensajeExito.set(
          'Escanea el QR con tu aplicación de autenticación y confirma el código.'
        );
      },
      error: () => {
        this.isLoading.set(false);
        this.mensajeError.set(
          'No se pudo generar el QR. Vuelve a iniciar sesión.'
        );
      }
    });
  }

  confirmarAltaTotp(): void {
    if (!/^\d{6}$/.test(this.codigoSegundoFactor.trim())) {
      this.mensajeError.set(
        'Introduce el código de 6 cifras de la aplicación.'
      );
      return;
    }

    this.isLoading.set(true);
    this.mensajeError.set('');

    this.authService
      .confirmarTotpObligatorio(this.codigoSegundoFactor.trim())
      .subscribe({
        next: ({ login, backupCodes }) => {
          this.isLoading.set(false);
          this.requiereAltaTotp.set(false);
          this.codigosRespaldo.set(backupCodes);
          this.requiereVerificacionEmail.set(!!login.requiresEmailVerification);
          this.emailOtpEnviado.set(!!login.emailVerificationEmailSent);

          this.rolTrasAlta =
            login.role === 'ADMIN' ? 'ADMIN' : 'VENDEDOR';

          this.mensajeExito.set(
            this.emailOtpEnviado()
              ? 'Verificación TOTP activada. Guarda los códigos y después verifica tu correo.'
              : 'Verificación TOTP activada. Guarda los códigos y solicita el código por correo para continuar.'
          );
          this.codigoSegundoFactor = '';
        },
        error: (error: HttpErrorResponse) => {
          this.isLoading.set(false);

          if (error.status === 401 || error.status === 410) {
            this.volverAlInicioSesion();
            this.mensajeError.set(
              'La configuración ha caducado. Inicia sesión de nuevo.'
            );
            return;
          }

          this.mensajeError.set(
            'El código no es válido o ha caducado. Compruébalo y vuelve a intentarlo.'
          );
        }
      });
  }

  continuarTrasAlta(): void {
    const rol = this.rolTrasAlta;

    if (!rol) {
      return;
    }

    this.codigosRespaldo.set([]);
    if (this.requiereVerificacionEmail()) {
      this.mensajeExito.set(
        this.emailOtpEnviado()
          ? 'Introduce el código enviado a tu correo electrónico.'
          : 'No se pudo enviar el código. Solicítalo de nuevo para continuar.'
      );
      return;
    }

    marcarSesionIniciada();
    this.router.navigateByUrl(rutaParaRol(rol));
  }

  verificarSegundoFactor(): void {
    const codigo = this.codigoSegundoFactor.replace(/[\s-]/g, '').toUpperCase();
    if (!/^(\d{6}|[A-Z2-7]{8}|[A-Z2-7]{16})$/.test(codigo)) {
      this.mensajeError.set('Introduce un código TOTP de 6 cifras o un código de respaldo válido.');
      return;
    }
    this.isLoading.set(true);
    this.mensajeError.set('');
    this.authService.verificarSegundoFactor(codigo).subscribe({
      next: (respuesta) => {
        this.isLoading.set(false);
        if (respuesta.requiresEmailVerification) {
          this.requiereSegundoFactor.set(false);
          this.requiereVerificacionEmail.set(true);
          this.emailOtpEnviado.set(!!respuesta.emailVerificationEmailSent);
          this.mensajeExito.set(
            this.emailOtpEnviado()
              ? 'Introduce el código enviado a tu correo electrónico.'
              : 'No se pudo enviar el código. Usa el botón para solicitarlo de nuevo.'
          );
          this.codigoSegundoFactor = '';
          return;
        }
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

  verificarTercerFactor(): void {
    const codigo = this.codigoSegundoFactor.trim();
    if (!/^\d{6}$/.test(codigo)) {
      this.mensajeError.set('Introduce el código de seis cifras enviado a tu correo.');
      return;
    }

    this.isLoading.set(true);
    this.mensajeError.set('');
    this.authService.verificarTercerFactor(codigo).subscribe({
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
        const mensaje = typeof error.error?.error === 'string'
          ? error.error.error
          : 'El código de correo no es válido. Compruébalo y vuelve a intentarlo.';
        if (error.status === 429 && mensaje.includes('Inicia sesión de nuevo')) {
          this.volverAlInicioSesion();
        }
        this.mensajeError.set(mensaje);
      }
    });
  }

  reenviarCodigoEmail(): void {
    this.isLoading.set(true);
    this.mensajeError.set('');
    this.authService.reenviarTercerFactor().subscribe({
      next: () => {
        this.isLoading.set(false);
        this.emailOtpEnviado.set(true);
        this.mensajeExito.set('Hemos enviado un nuevo código a tu correo electrónico.');
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading.set(false);
        this.mensajeError.set(typeof error.error?.error === 'string'
          ? error.error.error
          : 'No se pudo enviar el código. Inténtalo de nuevo más tarde.');
      }
    });
  }

  volverAlInicioSesion(): void {
    this.requiereSegundoFactor.set(false);
    this.requiereAltaTotp.set(false);
    this.requiereVerificacionEmail.set(false);
    this.emailOtpEnviado.set(false);
    this.qrTotp.set('');
    this.codigosRespaldo.set([]);
    this.rolTrasAlta = null;
    this.codigoSegundoFactor = '';

    this.loginForm.controls['password'].reset('');
    this.mensajeError.set('');
    this.mensajeExito.set('');
  }
}
