import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { describe, expect, it } from 'vitest';

import { routes } from '../../app.routes';
import { NoEncontrada } from './no-encontrada';

describe('NoEncontrada (A11)', () => {
  it('muestra la página 404 en una ruta inexistente, con enlace al inicio', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes)] });
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/no-existe', NoEncontrada);

    const pagina: HTMLElement = harness.routeNativeElement!;
    expect(pagina.querySelector('h1')?.textContent).toContain('No encontramos esta página');
    expect(pagina.querySelector('.accion-principal')?.getAttribute('href')).toBe('/');
  });

  it('la ruta comodín es la última, para no tapar a las demás', () => {
    expect(routes.at(-1)?.path).toBe('**');
    expect(routes.at(-1)?.title).toBe('Página no encontrada · ESIBuy');
  });
});
