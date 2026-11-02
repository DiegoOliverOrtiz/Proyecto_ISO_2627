import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import {
  PedidoVendedor,
  PedidosVendedorService
} from './pedidos-vendedor.service';

type PestanaPedidos = 'activos' | 'historico';

@Component({
  selector: 'app-pedidos-vendedor',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './pedidos-vendedor.html',
  styleUrl: './pedidos-vendedor.css'
})
export class PedidosVendedor implements OnInit {
  private readonly servicio = inject(PedidosVendedorService);
  private readonly router = inject(Router);

  readonly pestana = signal<PestanaPedidos>('activos');
  readonly pedidos = signal<PedidoVendedor[]>([]);
  readonly cargando = signal(false);
  readonly error = signal('');

  readonly pedidoSeleccionado = signal<PedidoVendedor | null>(null);
  readonly cargandoDetalle = signal(false);
  readonly errorDetalle = signal('');

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
    this.pedidoSeleccionado.set(null);
    this.cargandoDetalle.set(true);
    this.errorDetalle.set('');

    this.servicio.consultarDetalle(pedido.id).subscribe({
      next: (detalle) => {
        this.pedidoSeleccionado.set(detalle);
        this.cargandoDetalle.set(false);
      },
      error: (fallo: HttpErrorResponse) => {
        this.cargandoDetalle.set(false);
        this.errorDetalle.set(this.mensajeError(fallo));
      }
    });
  }

  cerrarDetalle(): void {
    this.pedidoSeleccionado.set(null);
    this.errorDetalle.set('');
    this.cargandoDetalle.set(false);
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
      void this.router.navigateByUrl('/login');
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
