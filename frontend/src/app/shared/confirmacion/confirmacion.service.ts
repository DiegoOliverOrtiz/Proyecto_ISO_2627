import { Injectable, signal } from '@angular/core';

export interface OpcionesConfirmacion {
  titulo: string;
  mensaje: string;
  /** Texto del botón que confirma; por defecto «Aceptar». Mejor un verbo concreto («Eliminar»). */
  aceptar?: string;
  /** Texto del botón que cancela; por defecto «Cancelar». */
  cancelar?: string;
  /** La acción es destructiva: el botón de confirmar va en rojo. */
  peligro?: boolean;
}

export interface SolicitudConfirmacion extends Required<OpcionesConfirmacion> {
  resolver: (aceptada: boolean) => void;
}

/**
 * M8: sustituye a window.confirm con un diálogo propio y accesible (shared/confirmacion).
 * Uso: if (!(await confirmacion.confirmar({ titulo, mensaje, aceptar: 'Eliminar', peligro: true }))) return;
 */
@Injectable({ providedIn: 'root' })
export class ConfirmacionService {
  readonly solicitud = signal<SolicitudConfirmacion | null>(null);

  confirmar(opciones: OpcionesConfirmacion): Promise<boolean> {
    // Si había otra pregunta abierta, se da por cancelada.
    this.solicitud()?.resolver(false);

    return new Promise<boolean>((resolver) => {
      this.solicitud.set({
        titulo: opciones.titulo,
        mensaje: opciones.mensaje,
        aceptar: opciones.aceptar ?? 'Aceptar',
        cancelar: opciones.cancelar ?? 'Cancelar',
        peligro: opciones.peligro ?? false,
        resolver
      });
    });
  }

  responder(aceptada: boolean): void {
    const actual = this.solicitud();
    if (!actual) {
      return;
    }
    this.solicitud.set(null);
    actual.resolver(aceptada);
  }
}
