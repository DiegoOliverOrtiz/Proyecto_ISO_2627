import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../login/auth.service';
import {
  CambioEstadoPedido,
  EstadoPedido,
  PedidoCliente,
  PedidosService
} from './pedidos.service';

@Component({
  selector: 'app-mis-pedidos',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink],
  templateUrl: './mis-pedidos.html',
  styleUrl: './mis-pedidos.css'
})
export class MisPedidos implements OnInit {
  private readonly servicio = inject(PedidosService);
  private readonly servicioAutenticacion = inject(AuthService);
  private readonly router = inject(Router);

  readonly cerrandoSesion = signal(false);
  readonly errorSesion = signal('');
  readonly pedidosActivos = signal<PedidoCliente[]>([]);
  readonly pedidosHistoricos = signal<PedidoCliente[]>([]);
  readonly cargandoActivos = signal(false);
  readonly cargandoHistorico = signal(false);
  readonly errorActivos = signal('');
  readonly errorHistorico = signal('');

  readonly pedidoSeleccionado = signal<PedidoCliente | null>(null);
  readonly cargandoDetalle = signal(false);
  readonly errorDetalle = signal('');

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

  cerrarSesion(): void {
    this.cerrandoSesion.set(true);
    this.errorSesion.set('');

    this.servicioAutenticacion.cerrarSesion().subscribe({
      next: () => {
        this.cerrandoSesion.set(false);
        void this.router.navigateByUrl('/login');
      },
      error: (error: HttpErrorResponse) => {
        this.cerrandoSesion.set(false);
        this.errorSesion.set(error.status === 0
          ? 'No se pudo conectar con el servicio. Inténtalo de nuevo.'
          : 'No se pudo cerrar la sesión. Inténtalo de nuevo.');
      }
    });
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
