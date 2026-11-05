import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { RecuperacionContrasenaService } from './recuperacion-contrasena.service';
import { RecuperacionContrasena } from './recuperacion-contrasena';

describe('RecuperacionContrasena', () => {
  const recuperacionServiceMock = {
    solicitar: vi.fn<RecuperacionContrasenaService['solicitar']>(),
    confirmar: vi.fn<RecuperacionContrasenaService['confirmar']>()
  };
  let componente: RecuperacionContrasena;

  const crearComponente = (token = '') => {
    TestBed.configureTestingModule({
      providers: [
        FormBuilder,
        { provide: RecuperacionContrasenaService, useValue: recuperacionServiceMock },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (clave: string) => clave === 'token' ? token : null
              }
            }
          }
        }
      ]
    });

    return TestBed.runInInjectionContext(() => new RecuperacionContrasena());
  };

  beforeEach(() => {
    recuperacionServiceMock.solicitar.mockReturnValue(of({ message: 'Solicitud procesada.' }));
    recuperacionServiceMock.confirmar.mockReturnValue(of(undefined));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('muestra el modo solicitud cuando la URL no contiene token', () => {
    componente = crearComponente();

    expect(componente.modoRestablecimiento()).toBe(false);
    expect(componente.solicitudEnviada()).toBe(false);
  });

  it('activa el modo restablecimiento cuando la URL contiene token', () => {
    componente = crearComponente('token-recuperacion');

    expect(componente.modoRestablecimiento()).toBe(true);
  });

  it('no solicita la recuperación con un correo inválido y marca el formulario', () => {
    componente = crearComponente();
    componente.solicitudForm.controls.email.setValue('correo-invalido');

    componente.solicitarRecuperacion();

    expect(componente.solicitudForm.invalid).toBe(true);
    expect(componente.solicitudForm.controls.email.touched).toBe(true);
    expect(recuperacionServiceMock.solicitar).not.toHaveBeenCalled();
  });

  it('envía la solicitud con correo válido y muestra la confirmación', () => {
    componente = crearComponente();
    componente.solicitudForm.controls.email.setValue('cliente@example.com');

    componente.solicitarRecuperacion();

    expect(recuperacionServiceMock.solicitar).toHaveBeenCalledWith('cliente@example.com');
    expect(componente.solicitudEnviada()).toBe(true);
    expect(componente.isLoading()).toBe(false);
  });

  it('muestra un error comprensible cuando falla la solicitud', () => {
    recuperacionServiceMock.solicitar.mockReturnValue(throwError(() => new Error('fallo')));
    componente = crearComponente();
    componente.solicitudForm.controls.email.setValue('cliente@example.com');

    componente.solicitarRecuperacion();

    expect(componente.mensajeError()).toBe(
      'No se pudo procesar la solicitud. Inténtalo de nuevo más tarde.'
    );
    expect(componente.isLoading()).toBe(false);
  });

  it('valida los requisitos y la coincidencia de las contraseñas', () => {
    componente = crearComponente('token-recuperacion');
    componente.restablecimientoForm.setValue({
      nuevaContrasena: 'ClavePrueba9!',
      confirmarContrasena: 'OtraClave9!'
    });

    expect(componente.requisitos().every(requisito => requisito.cumplido)).toBe(true);
    expect(componente.restablecimientoForm.hasError('passwordMismatch')).toBe(true);
    expect(componente.restablecimientoForm.invalid).toBe(true);
  });

  it('no intenta restablecer si la contraseña incumple requisitos', () => {
    componente = crearComponente('token-recuperacion');
    componente.restablecimientoForm.setValue({
      nuevaContrasena: 'debil',
      confirmarContrasena: 'debil'
    });

    componente.restablecerContrasena();

    expect(componente.restablecimientoForm.invalid).toBe(true);
    expect(recuperacionServiceMock.confirmar).not.toHaveBeenCalled();
  });

  it('confirma el restablecimiento si ambas contraseñas válidas coinciden', () => {
    componente = crearComponente('token-recuperacion');
    componente.restablecimientoForm.setValue({
      nuevaContrasena: 'ClavePrueba9!',
      confirmarContrasena: 'ClavePrueba9!'
    });

    componente.restablecerContrasena();

    expect(recuperacionServiceMock.confirmar)
      .toHaveBeenCalledWith('token-recuperacion', 'ClavePrueba9!');
    expect(componente.restablecimientoCompletado()).toBe(true);
    expect(componente.isLoading()).toBe(false);
  });

  it('muestra el error concreto del backend al fallar el restablecimiento', () => {
    recuperacionServiceMock.confirmar.mockReturnValue(throwError(() =>
      new HttpErrorResponse({ status: 400, error: { error: 'El enlace ha caducado.' } })
    ));
    componente = crearComponente('token-recuperacion');
    componente.restablecimientoForm.setValue({
      nuevaContrasena: 'ClavePrueba9!',
      confirmarContrasena: 'ClavePrueba9!'
    });

    componente.restablecerContrasena();

    expect(componente.mensajeError()).toBe('El enlace ha caducado.');
    expect(componente.isLoading()).toBe(false);
  });

  it('usa un mensaje general si el backend devuelve un error sin descripción', () => {
    recuperacionServiceMock.confirmar.mockReturnValue(throwError(() =>
      new HttpErrorResponse({ status: 500, error: null })
    ));
    componente = crearComponente('token-recuperacion');
    componente.restablecimientoForm.setValue({
      nuevaContrasena: 'ClavePrueba9!',
      confirmarContrasena: 'ClavePrueba9!'
    });

    componente.restablecerContrasena();

    expect(componente.mensajeError()).toBe(
      'No se pudo cambiar la contraseña. Comprueba el enlace e inténtalo de nuevo.'
    );
    expect(componente.isLoading()).toBe(false);
  });
});
