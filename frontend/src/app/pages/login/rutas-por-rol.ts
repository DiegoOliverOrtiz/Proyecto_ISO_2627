import { RespuestaInicioSesion } from './auth.service';

export function rutaParaRol(rol: RespuestaInicioSesion['role']): string {
  switch (rol) {
    case 'ADMINISTRADOR':
      return '/admin';
    case 'CLIENTE':
      return '/cliente';
    case 'VENDEDOR':
      return '/vendedor';
  }
}
