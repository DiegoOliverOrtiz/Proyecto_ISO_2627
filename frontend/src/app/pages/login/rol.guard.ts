import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';
import { rutaParaRol } from './rutas-por-rol';

export const rolGuard: CanActivateFn = (ruta) => {
  const servicioAutenticacion = inject(AuthService);
  const enrutador = inject(Router);
  const rolPermitido = ruta.data['rol'];

  return servicioAutenticacion.obtenerSesionActual().pipe(
    map((sesion) =>
      sesion.role === rolPermitido
        ? true
        : enrutador.parseUrl(rutaParaRol(sesion.role))
    ),
    catchError(() => of(enrutador.parseUrl('/login')))
  );
};
