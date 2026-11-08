import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, Injector, OnInit, afterNextRender, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import {
  PedidoVendedor,
  PedidosVendedorService
} from './pedidos-vendedor.service';

type PestanaPedidos = 'activos' | 'historico';

@Component({
  selector: 'app-pedidos-vendedor',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './pedidos-vendedor.html',
  styleUrls: ['../tienda/tienda.css', './pedidos-vendedor.css']
})
export class PedidosVendedor implements OnInit {
  private readonly servicio = inject(PedidosVendedorService);
  private readonly router = inject(Router);
  private readonly inyector = inject(Injector);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
  // Pedido cuyo «Ver detalle» abrió el panel, para devolverle el foco al cerrarlo.
  private origenDetalle: string | null = null;

  readonly pestana = signal<PestanaPedidos>('activos');
  readonly pedidos = signal<PedidoVendedor[]>([]);
  readonly cargando = signal(false);
  readonly error = signal('');

  readonly pedidoSeleccionado = signal<PedidoVendedor | null>(null);
  readonly cargandoDetalle = signal(false);
  readonly errorDetalle = signal('');

  readonly pedidoEnProceso = signal<string | null>(null);
  readonly mensajeExito = signal('');
  readonly errorAccion = signal('');

  ngOnInit(): void {
    this.cargarPedidos();
  }

  cambiarPestana(pestana: PestanaPedidos): void {
    if (this.pestana() === pestana) {
      return;
    }

    this.pestana.set(pestana);
    this.cerrarDetalle();
    this.cargarPedidos();
  }

  /** M15: flechas, Inicio y Fin cambian de pestaña y mueven el foco a la nueva. */
  teclaPestana(evento: KeyboardEvent): void {
    const orden: PestanaPedidos[] = ['activos', 'historico'];
    const actual = orden.indexOf(this.pestana());
    let destino: number;
    switch (evento.key) {
      case 'ArrowRight':
      case 'ArrowLeft':
        destino = (actual + 1) % orden.length;
        break;
      case 'Home':
        destino = 0;
        break;
      case 'End':
        destino = orden.length - 1;
        break;
      default:
        return;
    }
    evento.preventDefault();
    this.cambiarPestana(orden[destino]);
    afterNextRender(
      () => this.elemento.nativeElement.querySelector<HTMLElement>('[role="tab"][aria-selected="true"]')?.focus(),
      { injector: this.inyector }
    );
  }

  cargarPedidos(): void {
    const pestanaSolicitada = this.pestana();

    this.cargando.set(true);
    this.error.set('');
    this.pedidos.set([]);

    const consulta = pestanaSolicitada === 'activos'
      ? this.servicio.consultarActivos()
      : this.servicio.consultarHistorico();

    consulta.subscribe({
      next: (pedidos) => {
        if (this.pestana() !== pestanaSolicitada) {
          return;
        }

        this.pedidos.set(pedidos);
        this.cargando.set(false);
      },
      error: (fallo: HttpErrorResponse) => {
        if (this.pestana() !== pestanaSolicitada) {
          return;
        }

        this.cargando.set(false);
        this.error.set(this.mensajeError(fallo));
      }
    });
  }

  abrirDetalle(pedido: PedidoVendedor): void {
    this.origenDetalle = pedido.id;
    this.pedidoSeleccionado.set(null);
    this.cargandoDetalle.set(true);
    this.errorDetalle.set('');

    this.servicio.consultarDetalle(pedido.id).subscribe({
      next: (detalle) => {
        this.pedidoSeleccionado.set(detalle);
        this.cargandoDetalle.set(false);
        // El detalle se abre debajo de la lista: se lleva a la vista y recibe el foco.
        afterNextRender(
          () => {
            const titulo = this.elemento.nativeElement.querySelector<HTMLElement>('#titulo-detalle');
            const sinMovimiento = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
            titulo?.scrollIntoView?.({ block: 'start', behavior: sinMovimiento ? 'auto' : 'smooth' });
            titulo?.focus({ preventScroll: true });
          },
          { injector: this.inyector }
        );
      },
      error: (fallo: HttpErrorResponse) => {
        this.cargandoDetalle.set(false);
        this.errorDetalle.set(this.mensajeError(fallo));
      }
    });
  }

  cerrarDetalle(): void {
    const origen = this.origenDetalle;
    this.origenDetalle = null;
    if (origen !== null && this.pedidoSeleccionado()) {
      afterNextRender(
        () =>
          Array.from(this.elemento.nativeElement.querySelectorAll<HTMLElement>('.boton-detalle'))
            .find((boton) => boton.dataset['pedido'] === origen)
            ?.focus(),
        { injector: this.inyector }
      );
    }
    this.pedidoSeleccionado.set(null);
    this.errorDetalle.set('');
    this.cargandoDetalle.set(false);
  }

  puedeAceptar(pedido: PedidoVendedor): boolean {
    return pedido.estado === 'PAGADO_COMPLETADO';
  }

  aceptarPedido(pedido: PedidoVendedor): void {
    if (this.pedidoEnProceso() || !this.puedeAceptar(pedido)) {
      return;
    }

    this.pedidoEnProceso.set(pedido.id);
    this.mensajeExito.set('');
    this.errorAccion.set('');

    this.servicio.aceptarPedido(pedido.id).subscribe({
      next: (aceptado) => {
        this.pedidoEnProceso.set(null);
        this.mensajeExito.set(
          `Pedido #${this.referenciaPedido(aceptado.id)} aceptado. El envío (simulado) está en curso.`
        );

        if (this.pedidoSeleccionado()?.id === aceptado.id) {
          this.pedidoSeleccionado.set(aceptado);
        }

        this.cargarPedidos();
      },
      error: (fallo: HttpErrorResponse) => {
        this.pedidoEnProceso.set(null);
        this.errorAccion.set(this.mensajeErrorAceptar(fallo));

        // El pedido puede haber cambiado (cancelado o ya aceptado): se refresca la lista.
        if (fallo.status === 404 || fallo.status === 409) {
          this.cargarPedidos();
        }
      }
    });
  }

  // M6: igual que en «Mis pedidos» del cliente; el detalle muestra la referencia completa.
  referenciaPedido(id: string): string {
    return id.slice(-6).toUpperCase();
  }

  /** Color del chip de estado: el pagado espera acción del vendedor. */
  claseChip(estado: PedidoVendedor['estado']): string {
    switch (estado) {
      case 'PAGADO_COMPLETADO':
        return 'chip-aviso';
      case 'FINALIZADO':
        return 'chip-exito';
      default:
        return 'chip-neutro';
    }
  }

  textoEstado(estado: PedidoVendedor['estado']): string {
    switch (estado) {
      case 'PAGADO_COMPLETADO':
        return 'Pagado';
      case 'TRAMITADO_ENVIADO':
        return 'Enviado';
      case 'FINALIZADO':
        return 'Finalizado';
    }
  }

  private mensajeError(error: HttpErrorResponse): string {
    if (error.status === 401) {
      this.router.navigateByUrl('/login');
      return 'Tu sesión ha caducado. Inicia sesión nuevamente.';
    }

    if (error.status === 403) {
      return 'No tienes permiso para consultar estos pedidos.';
    }

    if (error.status === 404) {
      return 'El pedido no existe o no está disponible.';
    }

    if (error.status === 0) {
      return 'No se puede conectar con el servidor.';
    }

    return 'Ha ocurrido un error al consultar los pedidos.';
  }

  private mensajeErrorAceptar(error: HttpErrorResponse): string {
    if (error.status === 409) {
      return 'El pedido ya no está pendiente de tramitar. Se ha actualizado la lista.';
    }

    if (error.status === 403) {
      return 'No tienes permiso para gestionar este pedido.';
    }

    if (error.status === 401 || error.status === 404 || error.status === 0) {
      return this.mensajeError(error);
    }

    return 'No se ha podido aceptar el pedido. Inténtalo de nuevo.';
  }
}
