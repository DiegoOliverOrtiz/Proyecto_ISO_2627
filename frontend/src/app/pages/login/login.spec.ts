import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { FormBuilder } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { throwError, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService, RespuestaInicioSesion } from './auth.service';
import { Login } from './login';

describe('Login', () => {
  let componente: Login;
  let servicioAutenticacion: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideRouter([])]
    });

    servicioAutenticacion = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    componente = new Login(new FormBuilder(), servicioAutenticacion, router);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('marca los campos como tocados y no intenta autenticar si el formulario es inválido', () => {
    const iniciarSesion = vi.spyOn(servicioAutenticacion, 'iniciarSesion');

    componente.onSubmit();

    expect(componente.loginForm.invalid).toBe(true);
    expect(componente.loginForm.get('email')?.touched).toBe(true);
    expect(componente.loginForm.get('password')?.touched).toBe(true);
    expect(iniciarSesion).not.toHaveBeenCalled();
  });

  it.each([
    { rol: 'ADMIN', ruta: '/admin' },
    { rol: 'CLIENTE', ruta: '/cliente' },
    { rol: 'VENDEDOR', ruta: '/vendedor' }
  ] satisfies Array<{ rol: RespuestaInicioSesion['role']; ruta: string }>)(
    'autentica correctamente y redirige al panel del rol $rol',
    ({ rol, ruta }) => {
      vi.spyOn(servicioAutenticacion, 'iniciarSesion').mockReturnValue(
        of({ email: 'usuario@example.com', role: rol })
      );
      const navegar = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
      componente.loginForm.setValue({
        email: 'usuario@example.com',
        password: 'ClaveSegura9!'
      });

      componente.onSubmit();

      expect(servicioAutenticacion.iniciarSesion).toHaveBeenCalledWith(
        'usuario@example.com',
        'ClaveSegura9!'
      );
      expect(navegar).toHaveBeenCalledWith(ruta);
      expect(componente.isLoading()).toBe(false);
    }
  );

  it('muestra el error devuelto por el backend y termina el estado de carga', () => {
    vi.spyOn(servicioAutenticacion, 'iniciarSesion').mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: { error: 'Correo o contraseña incorrectos.' }
          })
      )
    );
    componente.loginForm.setValue({
      email: 'usuario@example.com',
      password: 'incorrecta'
    });

    componente.onSubmit();

    expect(componente.mensajeError()).toBe('Correo o contraseña incorrectos.');
    expect(componente.isLoading()).toBe(false);
  });
});
