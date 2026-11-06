import { describe, expect, it } from 'vitest';

import { normalizarTexto } from './normalizar-texto';

describe('normalizarTexto', () => {
  it('quita las tildes y pasa a minúsculas', () => {
    expect(normalizarTexto('Ratón Inalámbrico')).toBe('raton inalambrico');
  });

  it('trata la ñ como n, igual que la búsqueda del catálogo en el backend', () => {
    expect(normalizarTexto('Año')).toBe('ano');
  });

  it('trata los caracteres de expresión regular como texto literal', () => {
    expect(normalizarTexto('.*[a-z]')).toBe('.*[a-z]');
  });
});
