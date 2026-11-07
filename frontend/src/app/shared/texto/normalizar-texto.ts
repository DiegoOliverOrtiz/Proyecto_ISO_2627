/**
 * Normaliza un texto para compararlo en las búsquedas: sin tildes ni diéresis y en minúsculas.
 * «Ratón» y «raton» dan el mismo resultado. Se compara siempre como texto literal.
 */
export function normalizarTexto(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase();
}
