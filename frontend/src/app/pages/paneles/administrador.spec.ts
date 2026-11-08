import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { AuthService } from '../login/auth.service';
import { PanelAdministrador } from './administrador';

describe('PanelAdministrador', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('enlaza la consulta de productos en solo lectura (HU-34)', () => {
    TestBed.configureTestingModule({
      imports: [PanelAdministrador],
      providers: [provideRouter([]), { provide: AuthService, useValue: {} }]
    });

    const fixture = TestBed.createComponent(PanelAdministrador);
    fixture.detectChanges();
    const enlace = (fixture.nativeElement as HTMLElement).querySelector(
      'a[href="/admin/productos"]'
    );

    expect(enlace?.textContent).toContain('Abrir productos');
  });
  it('muestra las cuatro áreas como tarjetas con título y un único h1', () => {
    TestBed.configureTestingModule({
      imports: [PanelAdministrador],
      providers: [provideRouter([]), { provide: AuthService, useValue: {} }]
    });

    const fixture = TestBed.createComponent(PanelAdministrador);
    fixture.detectChanges();
    const vista = fixture.nativeElement as HTMLElement;

    expect(vista.querySelectorAll('h1').length).toBe(1);
    expect(Array.from(vista.querySelectorAll('.tarjeta h2')).map((h) => h.textContent?.trim())).toEqual([
      'Usuarios',
      'Categorías',
      'Productos',
      'Administradores'
    ]);
    expect(vista.textContent).toContain('ESIBuy');
    expect(vista.textContent).not.toContain('EsiBuy');
  });
});
