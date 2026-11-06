import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { Cabecera } from './shared/cabecera/cabecera';

@Component({
  imports: [RouterOutlet, Cabecera],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly enrutador = inject(Router);

  // Las rutas públicas (inicio, login, registro…) marcan `data: { cabecera: false }`.
  protected readonly mostrarCabecera = toSignal(
    this.enrutador.events.pipe(
      filter((evento) => evento instanceof NavigationEnd),
      map(() => App.rutaMuestraCabecera(this.enrutador.routerState.snapshot.root))
    ),
    { initialValue: false }
  );

  private static rutaMuestraCabecera(ruta: ActivatedRouteSnapshot): boolean {
    let actual = ruta;
    while (actual.firstChild) {
      actual = actual.firstChild;
    }
    return actual.data['cabecera'] !== false;
  }
}
