import { afterNextRender, Component, effect, ElementRef, inject, Injector } from '@angular/core';

import { ConfirmacionService } from './confirmacion.service';

/** Diálogo de confirmación común (M8). Se monta una vez en app.html. */
@Component({
  selector: 'app-confirmacion',
  styleUrl: './confirmacion.css',
  templateUrl: './confirmacion.html',
})
export class Confirmacion {
  protected readonly servicio = inject(ConfirmacionService);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly inyector = inject(Injector);
  private origen: HTMLElement | null = null;

  constructor() {
    effect(() => {
      if (!this.servicio.solicitud()) {
        return;
      }
      this.origen = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      // El foco empieza en «Cancelar»: es la opción segura si se pulsa Intro sin leer.
      afterNextRender(
        () => this.elemento.nativeElement.querySelector<HTMLElement>('.confirmacion-cancelar')?.focus(),
        { injector: this.inyector }
      );
    });
  }

  responder(aceptada: boolean): void {
    const origen = this.origen;
    this.origen = null;
    this.servicio.responder(aceptada);
    if (origen?.isConnected) {
      origen.focus();
    }
  }

  manejarTeclado(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      this.responder(false);
      return;
    }
    if (evento.key !== 'Tab') {
      return;
    }
    const botones = Array.from(
      (evento.currentTarget as HTMLElement).querySelectorAll<HTMLElement>('button')
    );
    const primero = botones[0];
    const ultimo = botones[botones.length - 1];
    if (evento.shiftKey && document.activeElement === primero) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && document.activeElement === ultimo) {
      evento.preventDefault();
      primero.focus();
    }
  }
}
