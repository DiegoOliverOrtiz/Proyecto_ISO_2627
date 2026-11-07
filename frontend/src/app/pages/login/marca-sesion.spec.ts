import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { firstValueFrom, isObservable, Observable, throwError } from 'rxjs';
import { afterEach, describe, expect, it } from 'vitest';

import { AuthService } from './auth.service';
import { autenticadoGuard } from './autenticado.guard';
import { marcarSesionIniciada, motivoSinSesion, olvidarSesion } from './marca-sesion';
import { rolGuard } from './rol.guard';

describe('Motivo del login sin sesión (A12)', () => {
  afterEach(() => {
    olvidarSesion();
    TestBed.resetTestingModule();
  });

  it('sin un inicio de sesión previo el motivo es «sin-sesion»', () => {
    expect(motivoSinSesion()).toBe('sin-sesion');
  });

  it('tras un inicio de sesión correcto el motivo es «sesion-expirada» y la marca se consume', () => {
    marcarSesionIniciada();

    expect(motivoSinSesion()).toBe('sesion-expirada');
    expect(motivoSinSesion()).toBe('sin-sesion');
  });

  const ejecutar = async (guard: typeof rolGuard): Promise<string> => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { obtenerSesionActual: () => throwError(() => new Error('401')) } }
      ]
    });
    const ruta = { data: { rol: 'CLIENTE' } } as unknown as ActivatedRouteSnapshot;
    const resultado = TestBed.runInInjectionContext(() => guard(ruta, {} as RouterStateSnapshot));
    const arbol = (isObservable(resultado)
      ? await firstValueFrom(resultado as Observable<UrlTree>)
      : resultado) as UrlTree;
    return TestBed.inject(Router).serializeUrl(arbol);
  };

  it.each([['rolGuard', rolGuard], ['autenticadoGuard', autenticadoGuard]])(
    '%s lleva al login con «sin-sesion» si nunca hubo sesión', async (_nombre, guard) => {
      expect(await ejecutar(guard)).toBe('/login?motivo=sin-sesion');
    });

  it.each([['rolGuard', rolGuard], ['autenticadoGuard', autenticadoGuard]])(
    '%s lleva al login con «sesion-expirada» si la sesión se perdió', async (_nombre, guard) => {
      marcarSesionIniciada();

      expect(await ejecutar(guard)).toBe('/login?motivo=sesion-expirada');
    });
});
