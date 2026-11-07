import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { PerfilService } from './perfil.service';
import { environment } from '../../../environments/environment';

describe('Preferencia de correo del perfil', () => {
  let servicio: PerfilService;
  let http: HttpTestingController;
  const csrf = `${environment.apiUrl}/usuarios/csrf`;
  const url = `${environment.apiUrl}/usuarios/3fa/preferencia`;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(PerfilService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); TestBed.resetTestingModule(); });
  it.each([true, false])('guarda %s después de obtener CSRF', activado => {
    servicio.modificarPreferenciaTercerFactor(activado).subscribe();
    http.expectNone(url);
    const token = http.expectOne(csrf);
    expect(token.request.withCredentials).toBe(true);
    token.flush({ token: 'csrf-prueba' });
    const request = http.expectOne(url);
    expect(request.request.method).toBe('PUT');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-prueba');
    expect(request.request.body).toEqual({ activado });
    request.flush(null);
  });
  it('no cambia la preferencia si falla CSRF', () => {
    let status = 0;
    servicio.modificarPreferenciaTercerFactor(true).subscribe({ error: error => status = error.status });
    http.expectOne(csrf).flush({}, { status: 403, statusText: 'Forbidden' });
    http.expectNone(url);
    expect(status).toBe(403);
  });
});
