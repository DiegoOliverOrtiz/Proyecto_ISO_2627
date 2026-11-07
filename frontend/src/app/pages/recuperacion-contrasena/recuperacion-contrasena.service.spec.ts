import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { environment } from '../../../environments/environment';
import { RecuperacionContrasenaService } from './recuperacion-contrasena.service';

describe('RecuperacionContrasenaService', () => {
  let servicio: RecuperacionContrasenaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    servicio = TestBed.inject(RecuperacionContrasenaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('obtiene CSRF y solicita la recuperación con sesión y token CSRF', () => {
    servicio.solicitar('cliente@example.com').subscribe();

    const csrf = http.expectOne(`${environment.apiUrl}/usuarios/csrf`);
    expect(csrf.request.method).toBe('GET');
    expect(csrf.request.withCredentials).toBe(true);
    csrf.flush({ token: 'csrf-recuperacion' });

    const solicitud = http.expectOne(`${environment.apiUrl}/usuarios/recuperar-contrasena`);
    expect(solicitud.request.method).toBe('POST');
    expect(solicitud.request.body).toEqual({ email: 'cliente@example.com' });
    expect(solicitud.request.withCredentials).toBe(true);
    expect(solicitud.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-recuperacion');
    solicitud.flush({ message: 'Solicitud procesada.' });
  });

  it('obtiene CSRF y confirma el cambio con el token de recuperación', () => {
    servicio.confirmar('token-recuperacion', 'ClavePrueba9!').subscribe();

    const csrf = http.expectOne(`${environment.apiUrl}/usuarios/csrf`);
    csrf.flush({ token: 'csrf-confirmacion' });

    const confirmacion = http.expectOne(
      `${environment.apiUrl}/usuarios/recuperar-contrasena/confirmar`
    );
    expect(confirmacion.request.method).toBe('POST');
    expect(confirmacion.request.body).toEqual({
      token: 'token-recuperacion',
      nuevaContrasena: 'ClavePrueba9!'
    });
    expect(confirmacion.request.withCredentials).toBe(true);
    expect(confirmacion.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-confirmacion');
    confirmacion.flush(null);
  });

  it('no envía la solicitud de recuperación si falla la obtención de CSRF', () => {
    let codigoError: number | undefined;
    servicio.solicitar('cliente@example.com').subscribe({ error: error => codigoError = error.status });

    http.expectOne(`${environment.apiUrl}/usuarios/csrf`)
      .flush({}, { status: 403, statusText: 'Forbidden' });

    http.expectNone(`${environment.apiUrl}/usuarios/recuperar-contrasena`);
    expect(codigoError).toBe(403);
  });
});
