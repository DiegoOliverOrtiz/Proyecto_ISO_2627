import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CarritoResponse, CarritoService } from '../carrito/carrito.service';
import { DireccionResponse, DireccionesService } from '../direcciones/direcciones.service';
import { AuthService } from '../login/auth.service';
import { MetodoPagoResponse, PagosService } from '../pagos/pagos.service';
import { CheckoutService, CrearPedidoRequest, ErrorPreparacionPago, PedidoResponse } from './checkout.service';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [DecimalPipe, FormsModule, RouterLink],
  templateUrl: './checkout.html',
  styleUrls: ['../carrito/carrito.css', './checkout.css']
})
export class Checkout {
  private readonly carritos = inject(CarritoService);
  private readonly direccionesService = inject(DireccionesService);
  private readonly pagos = inject(PagosService);
  private readonly servicio = inject(CheckoutService);
  private readonly autenticacion = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly email = signal('');

  readonly carrito = signal<CarritoResponse | null>(null);
  readonly direcciones = signal<DireccionResponse[]>([]);
  readonly metodos = signal<MetodoPagoResponse[]>([]);
  readonly direccionId = signal('');
  readonly metodoPagoId = signal('');
  readonly cargando = signal(false);
  readonly procesando = signal(false);
  readonly error = signal('');
  readonly pendiente = signal<CrearPedidoRequest | null>(null);
  readonly pedido = signal<PedidoResponse | null>(null);
  readonly puedePagar = computed(() => !this.cargando() && !this.procesando() && !this.pedido() && !!this.email()
    && (!!this.pendiente() || (!!this.carrito()?.productos.length
      && this.carrito()!.productos.every(linea => linea.disponible)
      && this.direcciones().some(d => d.id === this.direccionId())
      && this.metodos().some(m => m.id === this.metodoPagoId()))));

  constructor() { this.cargar(); }

  cargar(limpiarError = true): void {
    if (this.cargando() || this.procesando() || this.pedido()) return;
    this.cargando.set(true);
    if (limpiarError) this.error.set('');
    this.email.set('');
    this.autenticacion.obtenerSesionActual().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: sesion => {
        if (sesion.role !== 'CLIENTE') {
          this.cargando.set(false);
          this.error.set('Solo los clientes pueden realizar compras.');
          return;
        }
        try {
          const pendiente = this.servicio.obtenerOperacion(sesion.email);
          this.pendiente.set(pendiente);
          if (pendiente) {
            this.direccionId.set(pendiente.direccionId);
            this.metodoPagoId.set(pendiente.metodoPagoId);
          }
          this.email.set(sesion.email);
        } catch {
          this.cargando.set(false);
          this.error.set('No se pudo recuperar la compra pendiente en este navegador. Revisa su almacenamiento antes de continuar.');
          return;
        }
        forkJoin({ carrito: this.carritos.consultarCarrito(),
          direcciones: this.direccionesService.consultarDirecciones(), metodos: this.pagos.consultarMetodos()
        }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
          next: datos => {
            this.carrito.set(datos.carrito);
            this.direcciones.set(datos.direcciones);
            this.metodos.set(datos.metodos);
            this.cargando.set(false);
          },
          error: (fallo: HttpErrorResponse) => {
            this.carrito.set(null);
            this.cargando.set(false);
            this.tratarError(fallo, 'No se pudieron cargar los datos de compra. Inténtalo de nuevo.');
          }
        });
      },
      error: (fallo: HttpErrorResponse) => {
        this.cargando.set(false);
        this.tratarError(fallo, 'No se pudo comprobar tu sesión. Inténtalo de nuevo.');
      }
    });
  }

  pagar(): void {
    if (!this.puedePagar()) return;
    let request = this.pendiente();
    if (!request) {
      try {
        request = { direccionId: this.direccionId(), metodoPagoId: this.metodoPagoId(), operacionId: crypto.randomUUID() };
        this.servicio.guardarOperacion(this.email(), request);
        this.pendiente.set(request);
      } catch {
        this.error.set('No se pudo preparar la compra en este navegador. Revisa su almacenamiento e inténtalo de nuevo.');
        return;
      }
    }
    this.procesando.set(true);
    this.error.set('');
    this.servicio.pagar(request).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: pedido => {
        this.pedido.set(pedido);
        this.procesando.set(false);
        this.servicio.limpiarOperacion(this.email());
        this.pendiente.set(null);
      },
      error: (errorPago: HttpErrorResponse | ErrorPreparacionPago) => {
        const fallo = errorPago instanceof ErrorPreparacionPago ? errorPago.causa : errorPago;
        const rechazado = !(errorPago instanceof ErrorPreparacionPago) && fallo.status === 400;
        this.procesando.set(false);
        if (rechazado) {
          // Validación rechazada: no hay compra confirmada con esta clave y selección.
          this.servicio.limpiarOperacion(this.email());
          this.pendiente.set(null);
        }
        this.tratarError(fallo, 'No se pudo confirmar la compra. Comprueba el resultado antes de iniciar otra.');
        if (rechazado) this.cargar(false);
      }
    });
  }

  urlImagen(imagen: string): string {
    const ruta = imagen.trim();
    if (/^https?:\/\//i.test(ruta)) return ruta;
    return `${environment.apiUrl}${ruta.startsWith('/') ? ruta : `/product-images/${ruta}`}`;
  }

  private tratarError(fallo: HttpErrorResponse, mensaje: string): void {
    if (fallo.status === 400 && typeof fallo.error?.error === 'string') mensaje = fallo.error.error;
    if (fallo.status === 409) mensaje = 'La compra ha cambiado o se está procesando. Comprueba de nuevo el resultado.';
    if ([0, 503].includes(fallo.status) && this.pendiente()) {
      mensaje = 'No hemos podido confirmar el resultado del pago. Comprueba el resultado para evitar otra compra.';
    }
    if (fallo.status === 401) {
      mensaje = 'Tu sesión ha caducado. Inicia sesión de nuevo para continuar.';
      void this.router.navigate(['/login'], { queryParams: { motivo: 'sesion-expirada' } });
    }
    if (fallo.status === 403) {
      mensaje = 'No tienes permiso para completar esta operación. Comprueba tu sesión e inténtalo de nuevo.';
      this.autenticacion.obtenerSesionActual().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => {},
        error: (sesion: HttpErrorResponse) => {
          if ([401, 403].includes(sesion.status)) {
            void this.router.navigate(['/login'], { queryParams: { motivo: 'sesion-expirada' } });
          }
        }
      });
    }
    this.error.set(mensaje);
  }
}
