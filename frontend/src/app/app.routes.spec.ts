import { describe, expect, it } from 'vitest';

import { routes } from './app.routes';

describe('rutas de la aplicación (A4)', () => {
  it('todas las rutas tienen un título en español que termina en «· ESIBuy»', () => {
    for (const ruta of routes) {
      expect(typeof ruta.title, `ruta '${ruta.path}'`).toBe('string');
      expect(ruta.title as string, `ruta '${ruta.path}'`).toMatch(/^\S.* · ESIBuy$/);
    }
  });

  it('no repite el mismo título en dos rutas', () => {
    const titulos = routes.map(ruta => ruta.title);

    expect(new Set(titulos).size).toBe(titulos.length);
  });

  it('oculta la cabecera común solo en las rutas públicas', () => {
    const sinCabecera = routes
      .filter((ruta) => ruta.data?.['cabecera'] === false)
      .map((ruta) => ruta.path);

    expect(sinCabecera).toEqual(['', 'login', 'registro', 'recuperar-contrasena', '**']);
  });
});
