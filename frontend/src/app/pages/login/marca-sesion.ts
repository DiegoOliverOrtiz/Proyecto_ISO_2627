/*
 * A12: el backend responde 401 igual si nunca hubo sesión que si caducó o se cerró por un bloqueo.
 * Para elegir el mensaje del login, se recuerda en esta pestaña que hubo un inicio de sesión correcto.
 * No guarda datos personales; si el almacenamiento no está disponible, se trata como «sin sesión».
 */

const CLAVE = 'esibuy.sesionIniciada';

export type MotivoLogin = 'sin-sesion' | 'sesion-expirada';

export function marcarSesionIniciada(): void {
  try {
    sessionStorage.setItem(CLAVE, '1');
  } catch {
    // Sin almacenamiento solo se pierde la distinción de mensajes.
  }
}

export function olvidarSesion(): void {
  try {
    sessionStorage.removeItem(CLAVE);
  } catch {
    // Nada que limpiar.
  }
}

function huboSesion(): boolean {
  try {
    return sessionStorage.getItem(CLAVE) === '1';
  } catch {
    return false;
  }
}

/** Motivo que el guard pasa al login cuando no hay sesión válida. */
export function motivoSinSesion(): MotivoLogin {
  const motivo: MotivoLogin = huboSesion() ? 'sesion-expirada' : 'sin-sesion';
  olvidarSesion();
  return motivo;
}
