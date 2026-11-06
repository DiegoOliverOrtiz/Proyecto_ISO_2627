import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import {
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../login/auth.service';
import {
  CambioEstadoPedido,
  EstadoPedido,
  PedidoCliente,
  PedidosService
} from './pedidos.service';

interface EstadoValoracionUI {
  estado: 'cargando' | 'pendiente' | 'valorada' | 'error';
  mensaje: string;
  enviando: boolean;
}

@Component({
  selector: 'app-mis-pedidos',
  standalone: true,
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule, RouterLink],
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
  readonly pedidoCancelandoId = signal<string | null>(null);
  readonly resultadoCancelacion = signal('');
  readonly errorCancelacion = signal('');
  readonly pedidoConfirmandoId = signal<string | null>(null);
  readonly resultadoRecepcion = signal('');
  readonly errorRecepcion = signal('');
  readonly estadosValoracion = signal<Record<string, EstadoValoracionUI>>({});
  private readonly formulariosValoracion = new Map<
    string,
    FormGroup<{
      puntuacion: FormControl<number>;
      comentario: FormControl<string>;
    }>
  >();
  private readonly formularioBuilder = inject(NonNullableFormBuilder);

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
        this.estadosValoracion.set({});
        this.formulariosValoracion.clear();
        this.consultarEstadosValoracion(pedido);
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
    this.estadosValoracion.set({});
    this.formulariosValoracion.clear();
  }

  estadoValoracion(productoId: string): EstadoValoracionUI | undefined {
    return this.estadosValoracion()[productoId];
  }

  formularioValoracion(productoId: string): FormGroup<{
    puntuacion: FormControl<number>;
    comentario: FormControl<string>;
  }> | undefined {
    return this.formulariosValoracion.get(productoId);
  }

  reintentarConsultaValoracion(pedido: PedidoCliente, productoId: string): void {
    this.consultarEstadoValoracion(pedido, productoId);
  }

  enviarValoracion(pedido: PedidoCliente, productoId: string): void {
    const estado = this.estadoValoracion(productoId);
    const formulario = this.formularioValoracion(productoId);
    if (
      pedido.estado !== 'FINALIZADO'
      || estado?.estado !== 'pendiente'
      || estado.enviando
      || !formulario
    ) {
      return;
    }

    if (formulario.invalid) {
      formulario.markAllAsTouched();
      return;
    }

    this.actualizarEstadoValoracion(productoId, {
      ...estado,
      enviando: true,
      mensaje: ''
    });

    this.servicio.valorarProducto(pedido.id, productoId, {
      puntuacion: formulario.controls.puntuacion.value,
      comentario: formulario.controls.comentario.value
    }).subscribe({
      next: () => {
        this.actualizarEstadoValoracion(productoId, {
          estado: 'valorada',
          mensaje: '',
          enviando: false
        });
      },
      error: (error: HttpErrorResponse) => {
        this.actualizarEstadoValoracion(productoId, {
          ...estado,
          enviando: false,
          mensaje: this.mensajeErrorValoracion(error)
        });
      }
    });
  }

  private consultarEstadosValoracion(pedido: PedidoCliente): void {
    if (pedido.estado !== 'FINALIZADO') {
      return;
    }

    for (const productoId of new Set(pedido.lineas.map((linea) => linea.productoId))) {
      this.consultarEstadoValoracion(pedido, productoId);
    }
  }

  private consultarEstadoValoracion(pedido: PedidoCliente, productoId: string): void {
    this.actualizarEstadoValoracion(productoId, {
      estado: 'cargando',
      mensaje: '',
      enviando: false
    });

    this.servicio.consultarEstadoValoracion(pedido.id, productoId).subscribe({
      next: ({ valorada }) => {
        if (valorada) {
          this.actualizarEstadoValoracion(productoId, {
            estado: 'valorada',
            mensaje: '',
            enviando: false
          });
          return;
        }

        this.formulariosValoracion.set(
          productoId,
          this.formularioBuilder.group({
            puntuacion: [5, [Validators.required, Validators.min(1), Validators.max(5)]],
            comentario: ['', [Validators.maxLength(500)]]
          })
        );
        this.actualizarEstadoValoracion(productoId, {
          estado: 'pendiente',
          mensaje: '',
          enviando: false
        });
      },
      error: (error: HttpErrorResponse) => {
        this.actualizarEstadoValoracion(productoId, {
          estado: 'error',
          mensaje: this.mensajeErrorValoracion(error),
          enviando: false
        });
      }
    });
  }

  private actualizarEstadoValoracion(productoId: string, estado: EstadoValoracionUI): void {
    this.estadosValoracion.update((estados) => ({
      ...estados,
      [productoId]: estado
    }));
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
          this.estadosValoracion.set({});
          this.formulariosValoracion.clear();
          this.consultarEstadosValoracion(pedidoFinalizado);
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

  private mensajeErrorValoracion(error: HttpErrorResponse): string {
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
      return 'El pedido o producto no existe o no está disponible para valorar.';
    }

    if (error.status === 409) {
      return 'Este producto ya está valorado.';
    }

    if (error.status === 403) {
      return 'No tienes permiso para valorar este producto.';
    }

    return 'No se pudo guardar la valoración. Inténtalo de nuevo.';
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
