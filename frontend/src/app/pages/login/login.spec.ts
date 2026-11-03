import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { FormBuilder } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  provideRouter,
  Router
} from '@angular/router';
import { throwError, of } from 'rxjs';
import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi
} from 'vitest';

import {
  AuthService,
  RespuestaInicioSesion
} from './auth.service';
import { Login } from './login';

describe('Login', () => {
  let componente: Login;
  let servicioAutenticacion: AuthService;
  let router: Router;
  let route: ActivatedRoute;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideRouter([])
      ]
    });

    servicioAutenticacion = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    route = TestBed.inject(ActivatedRoute);

    componente = new Login(
      new FormBuilder(),
      servicioAutenticacion,
      router,
      route
    );
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it.each(['081804', 'ABCDEFG2', 'ABCDEFGH234567AB', 'abcd-efgh-2345-67ab'])(
    'admite el codigo de segundo factor %s y normaliza el formato', (codigo) => {
      const verificar = vi.spyOn(servicioAutenticacion, 'verificarSegundoFactor')
        .mockReturnValue(of({ email: 'ana@example.com', role: 'CLIENTE' }));
      vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
      componente.requiereSegundoFactor.set(true);
      componente.codigoSegundoFactor = codigo;
      componente.onSubmit();
      expect(verificar).toHaveBeenCalledWith(codigo.replace(/[\s-]/g, '').toUpperCase());
    }
  );

  it('vuelve al primer paso cuando ha caducado el reto y borra la contrasena', () => {
    vi.spyOn(servicioAutenticacion, 'verificarSegundoFactor').mockReturnValue(throwError(() =>
      new HttpErrorResponse({ status: 410 })));
    componente.loginForm.patchValue({ password: 'secreto' });
    componente.requiereSegundoFactor.set(true);
    componente.codigoSegundoFactor = '123456';
    componente.onSubmit();
    expect(componente.requiereSegundoFactor()).toBe(false);
    expect(componente.loginForm.value.password).toBe('');
    expect(componente.codigoSegundoFactor).toBe('');
    expect(componente.mensajeError()).toContain('caducado');
  });

  it('muestra el limite de intentos indicado por el servidor', () => {
    vi.spyOn(servicioAutenticacion, 'verificarSegundoFactor').mockReturnValue(throwError(() =>
      new HttpErrorResponse({ status: 429, error: { error: 'Espera 5 minutos.' } })));
    componente.requiereSegundoFactor.set(true);
    componente.codigoSegundoFactor = '123456';
    componente.onSubmit();
    expect(componente.requiereSegundoFactor()).toBe(true);
    expect(componente.mensajeError()).toBe('Espera 5 minutos.');
  });

  it('marca los campos como tocados y no intenta autenticar si el formulario es inválido', () => {
    const iniciarSesion = vi.spyOn(
      servicioAutenticacion,
      'iniciarSesion'
    );

    componente.onSubmit();

    expect(componente.loginForm.invalid).toBe(true);
    expect(
      componente.loginForm.get('email')?.touched
    ).toBe(true);
    expect(
      componente.loginForm.get('password')?.touched
    ).toBe(true);
    expect(iniciarSesion).not.toHaveBeenCalled();
  });

  it.each([
    { rol: 'ADMIN', ruta: '/admin' },
    { rol: 'CLIENTE', ruta: '/cliente' },
    { rol: 'VENDEDOR', ruta: '/vendedor' }
  ] satisfies Array<{
    rol: RespuestaInicioSesion['role'];
    ruta: string;
  }>)(
    'autentica correctamente y redirige al panel del rol $rol',
    ({ rol, ruta }) => {
      vi.spyOn(
        servicioAutenticacion,
        'iniciarSesion'
      ).mockReturnValue(
        of({
          email: 'usuario@example.com',
          role: rol
        })
      );

      const navegar = vi
        .spyOn(router, 'navigateByUrl')
        .mockResolvedValue(true);

      componente.loginForm.setValue({
        email: 'usuario@example.com',
        password: 'ClaveSegura9!'
      });

      componente.onSubmit();

      expect(
        servicioAutenticacion.iniciarSesion
      ).toHaveBeenCalledWith(
        'usuario@example.com',
        'ClaveSegura9!'
      );

      expect(navegar).toHaveBeenCalledWith(ruta);
      expect(componente.isLoading()).toBe(false);
    }
  );

  it('muestra el error devuelto por el backend y termina el estado de carga', () => {
    vi.spyOn(
      servicioAutenticacion,
      'iniciarSesion'
    ).mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: {
              error: 'Correo o contraseña incorrectos.'
            }
          })
      )
    );

    componente.loginForm.setValue({
      email: 'usuario@example.com',
      password: 'incorrecta'
    });

    componente.onSubmit();

    expect(componente.mensajeError()).toBe(
      'Correo o contraseña incorrectos.'
    );

    expect(componente.isLoading()).toBe(false);
  });

  it('muestra aviso si la sesion ha expirado o ha sido invalidada', () => {
    const routeConMotivo = {
      snapshot: {
        queryParamMap: {
          get: (clave: string) =>
            clave === 'motivo'
              ? 'sesion-expirada'
              : null
        }
      }
    } as unknown as ActivatedRoute;

    const login = new Login(
      new FormBuilder(),
      servicioAutenticacion,
      router,
      routeConMotivo
    );

    expect(login.mensajeError()).toBe(
      'Tu sesión ya no es válida. Es posible que tu cuenta haya sido bloqueada.'
    );
  });
});
