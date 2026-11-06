import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { AdministradorService } from './administrador.service';
import { CrearAdministrador } from './crear-administrador';

describe('CrearAdministrador (A10)', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CrearAdministrador],
      providers: [provideRouter([]), { provide: AdministradorService, useValue: {} }]
    });
  });

  afterEach(() => TestBed.resetTestingModule());

  function crear() {
    const fixture = TestBed.createComponent(CrearAdministrador);
    fixture.detectChanges();
    return fixture;
  }

  it('los botones del ojo tienen nombre accesible, estado y no usan emojis', () => {
    const vista = crear().nativeElement as HTMLElement;

    const ojos = Array.from(vista.querySelectorAll<HTMLButtonElement>('.boton-ojo'));
    expect(ojos.length).toBe(2);
    for (const ojo of ojos) {
      expect(ojo.getAttribute('aria-label')).toBe('Mostrar contraseña');
      expect(ojo.getAttribute('aria-pressed')).toBe('false');
      expect(ojo.querySelector('svg')).not.toBeNull();
      expect(ojo.textContent?.trim()).toBe('');
    }
  });

  it('pulsar el ojo muestra la contraseña y lo anuncia', () => {
    const fixture = crear();
    const vista = fixture.nativeElement as HTMLElement;
    const ojo = vista.querySelector('.boton-ojo') as HTMLButtonElement;

    ojo.click();
    fixture.detectChanges();

    expect(ojo.getAttribute('aria-label')).toBe('Ocultar contraseña');
    expect(ojo.getAttribute('aria-pressed')).toBe('true');
    expect(vista.querySelector('input[formcontrolname="contrasena"]')?.getAttribute('type')).toBe('text');
  });

  it('tiene un único h1 y un enlace de vuelta al listado como botón secundario', () => {
    const vista = crear().nativeElement as HTMLElement;

    expect(vista.querySelectorAll('h1').length).toBe(1);
    const volver = vista.querySelector('.pagina-cabecera a') as HTMLAnchorElement;
    expect(volver.classList).toContain('boton-secundario');
    expect(volver.getAttribute('href')).toBe('/admin/administradores');
  });
});
