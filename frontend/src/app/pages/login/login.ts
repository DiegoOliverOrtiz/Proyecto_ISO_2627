import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { rutaParaRol } from './rutas-por-rol';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class Login {
  loginForm: FormGroup;
  hidePassword = true;
  isLoading = signal(false);
  mensajeError = signal('');
  mensajeExito = signal('');

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', Validators.required]
    });
  }

  togglePassword() {
    this.hidePassword = !this.hidePassword;
  }

  onSubmit() {
    if (this.loginForm.valid) {
      this.isLoading.set(true);
      this.mensajeError.set('');
      this.mensajeExito.set('');
      const { email, password } = this.loginForm.value;

      this.authService.iniciarSesion(email ?? '', password ?? '').subscribe({
        next: (respuesta) => {
          this.isLoading.set(false);
          this.router.navigateByUrl(rutaParaRol(respuesta.role));
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
}