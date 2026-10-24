import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { AuthService } from './auth.service';

export const autenticadoGuard: CanActivateFn = () => {
  const servicioAutenticacion = inject(AuthService);
  const enrutador = inject(Router);

  return servicioAutenticacion.obtenerSesionActual().pipe(
    map(() => true),
    catchError(() =>
      of(
        enrutador.createUrlTree(['/login'], {
          queryParams: { motivo: 'sesion-expirada' }
        })
      )
    )
  );
};
