import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import {
  CambioEstadoPedido,
  EstadoPedido,
  PedidoCliente,
  PedidosService
} from './pedidos.service';

@Component({
  selector: 'app-mis-pedidos',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './mis-pedidos.html',
  styleUrl: './mis-pedidos.css'
})
export class MisPedidos implements OnInit {
  private readonly servicio = inject(PedidosService);
  private readonly router = inject(Router);

  readonly pedidosActivos = signal<PedidoCliente[]>([]);
  readonly pedidosHistoricos = signal<PedidoCliente[]>([]);
  readonly cargandoActivos = signal(false);
  readonly cargandoHistorico = signal(false);
  readonly errorActivos = signal('');
  readonly errorHistorico = signal('');

  readonly pedidoSeleccionado = signal<PedidoCliente | null>(null);
  readonly cargandoDetalle = signal(false);
  readonly errorDetalle = signal('');
  readonly pedidoCancelandoId = signal<string | null>(null);
  readonly resultadoCancelacion = signal('');
  readonly errorCancelacion = signal('');
  readonly pedidoConfirmandoId = signal<string | null>(null);
  readonly resultadoRecepcion = signal('');
  readonly errorRecepcion = signal('');

  readonly estadosSeguimiento: EstadoPedido[] = [
    'EN_PROCESO',
    'PAGADO_COMPLETADO',
    'TRAMITADO_ENVIADO',
    'FINALIZADO'
  ];

  ngOnInit(): void {
    this.cargarPedidos();
  }

  cargarPedidos(): void {
    const pedidoSeleccionadoId = this.pedidoSeleccionado()?.id;

    this.cargandoActivos.set(true);
    this.cargandoHistorico.set(true);
    this.errorActivos.set('');
    this.errorHistorico.set('');

    this.servicio.consultarActivos().subscribe({
      next: (pedidos) => {
        this.pedidosActivos.set(pedidos);
        this.cargandoActivos.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.cargandoActivos.set(false);
        this.errorActivos.set(this.mensajeError(error));
      }
    });

    this.servicio.consultarHistorico().subscribe({
      next: (pedidos) => {
        this.pedidosHistoricos.set(pedidos);
        this.cargandoHistorico.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.cargandoHistorico.set(false);
        this.errorHistorico.set(this.mensajeError(error));
      }
    });

    if (pedidoSeleccionadoId) {
      this.abrirDetalle(pedidoSeleccionadoId);
    }
  }

  abrirDetalle(pedidoId: string): void {
    this.pedidoSeleccionado.set(null);
    this.cargandoDetalle.set(true);
    this.errorDetalle.set('');

    this.servicio.consultarDetalle(pedidoId).subscribe({
      next: (pedido) => {
        this.pedidoSeleccionado.set(pedido);
        this.cargandoDetalle.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.cargandoDetalle.set(false);
        this.errorDetalle.set(this.mensajeError(error));
      }
    });
  }

  cerrarDetalle(): void {
    this.pedidoSeleccionado.set(null);
    this.errorDetalle.set('');
    this.cargandoDetalle.set(false);
  }

  cancelarPedido(pedido: PedidoCliente): void {
    if (this.pedidoCancelandoId() !== null || !this.puedeCancelar(pedido.estado)) {
      return;
    }

    const confirmado = window.confirm(
      `¿Quieres cancelar el pedido #${this.referenciaPedido(pedido.id)}?`
    );
    if (!confirmado) {
      return;
    }

    this.pedidoCancelandoId.set(pedido.id);
    this.resultadoCancelacion.set('');
    this.errorCancelacion.set('');

    this.servicio.cancelarPedido(pedido.id).subscribe({
      next: (pedidoCancelado) => {
        this.pedidosActivos.update((pedidos) =>
          pedidos.filter((actual) => actual.id !== pedidoCancelado.id)
        );
        if (this.pedidoSeleccionado()?.id === pedidoCancelado.id) {
          this.cerrarDetalle();
        }
        this.resultadoCancelacion.set(
          `El pedido #${this.referenciaPedido(pedidoCancelado.id)} se ha cancelado correctamente.`
        );
        this.pedidoCancelandoId.set(null);
      },
      error: (error: HttpErrorResponse) => {
        this.errorCancelacion.set(this.mensajeErrorCancelacion(error));
        this.pedidoCancelandoId.set(null);
      }
    });
  }

  puedeCancelar(estado: EstadoPedido): boolean {
    return estado === 'EN_PROCESO' || estado === 'PAGADO_COMPLETADO';
  }

  puedeConfirmarRecepcion(estado: EstadoPedido): boolean {
    return estado === 'TRAMITADO_ENVIADO';
  }

  confirmarRecepcion(pedido: PedidoCliente): void {
    if (
      this.pedidoConfirmandoId() !== null ||
      this.pedidoCancelandoId() !== null ||
      !this.puedeConfirmarRecepcion(pedido.estado)
    ) {
      return;
    }

    const confirmado = window.confirm(
      `¿Confirmas que has recibido el pedido #${this.referenciaPedido(pedido.id)}?`
    );

    if (!confirmado) {
      return;
    }

    this.pedidoConfirmandoId.set(pedido.id);
    this.resultadoRecepcion.set('');
    this.errorRecepcion.set('');

    this.servicio.confirmarRecepcion(pedido.id).subscribe({
      next: (pedidoFinalizado) => {
        this.pedidosActivos.update((pedidos) =>
          pedidos.filter((actual) => actual.id !== pedidoFinalizado.id)
        );

        this.pedidosHistoricos.update((pedidos) => [
          pedidoFinalizado,
          ...pedidos.filter((actual) => actual.id !== pedidoFinalizado.id)
        ]);

        if (this.pedidoSeleccionado()?.id === pedidoFinalizado.id) {
          this.pedidoSeleccionado.set(pedidoFinalizado);
        }

        this.resultadoRecepcion.set(
          `El pedido #${this.referenciaPedido(pedidoFinalizado.id)} se ha finalizado correctamente.`
        );

        this.pedidoConfirmandoId.set(null);
      },
      error: (error: HttpErrorResponse) => {
        this.errorRecepcion.set(this.mensajeErrorRecepcion(error));
        this.pedidoConfirmandoId.set(null);
      }
    });
  }

  private mensajeErrorRecepcion(error: HttpErrorResponse): string {
    if (error.status === 401) {
      void this.router.navigate(['/login'], {
        queryParams: { motivo: 'sesion-expirada' }
      });
      return 'Tu sesión ha caducado. Inicia sesión nuevamente.';
    }

    if (error.status === 0) {
      return 'No se pudo conectar con el servicio. Inténtalo de nuevo.';
    }

    const cuerpo = error.error;

    if (
      typeof cuerpo === 'object' &&
      cuerpo !== null &&
      'error' in cuerpo &&
      typeof cuerpo.error === 'string'
    ) {
      return cuerpo.error;
    }

    if (error.status === 404) {
      return 'El pedido no existe o no está disponible.';
    }

    if (error.status === 409) {
      return 'El pedido ya no está en estado enviado.';
    }

    if (error.status === 403) {
      return 'No tienes permiso para confirmar este pedido.';
    }

    return 'No se pudo confirmar la recepción. Inténtalo de nuevo.';
  }

  textoEstado(estado: EstadoPedido): string {
    switch (estado) {
      case 'EN_PROCESO':
        return 'En proceso';
      case 'PAGADO_COMPLETADO':
        return 'Pagado / Completado';
      case 'TRAMITADO_ENVIADO':
        return 'Tramitado / Enviado';
      case 'FINALIZADO':
        return 'Finalizado';
      case 'CANCELADO':
        return 'Cancelado';
    }
  }

  referenciaPedido(id: string): string {
    return id.slice(-6).toUpperCase();
  }

  cambioEstado(historial: CambioEstadoPedido[], estado: EstadoPedido): CambioEstadoPedido | undefined {
    return historial.find((cambio) => cambio.estado === estado);
  }

  conectorCompletado(
    historial: CambioEstadoPedido[],
    estado: EstadoPedido,
    siguienteEstado: EstadoPedido
  ): boolean {
    return this.cambioEstado(historial, estado) !== undefined
      && this.cambioEstado(historial, siguienteEstado) !== undefined;
  }

  private mensajeErrorCancelacion(error: HttpErrorResponse): string {
    if (error.status === 401) {
      void this.router.navigate(['/login'], {
        queryParams: { motivo: 'sesion-expirada' }
      });
      return 'Tu sesión ha caducado. Inicia sesión nuevamente.';
    }

    if (error.status === 0) {
      return 'No se pudo conectar con el servicio. Inténtalo de nuevo.';
    }

    const cuerpo = error.error;
    if (typeof cuerpo === 'object' && cuerpo !== null
      && 'error' in cuerpo && typeof cuerpo.error === 'string') {
      return cuerpo.error;
    }

    if (error.status === 404) {
      return 'El pedido no existe o no está disponible.';
    }

    return 'No se pudo cancelar el pedido. Inténtalo de nuevo.';
  }

  private mensajeError(error: HttpErrorResponse): string {
    if (error.status === 401) {
      void this.router.navigate(['/login'], {
        queryParams: { motivo: 'sesion-expirada' }
      });
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
}
