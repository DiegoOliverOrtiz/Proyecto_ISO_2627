import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { AuthService } from '../login/auth.service';
import { CarritoResponse, CarritoService, LineaCarritoResponse } from './carrito.service';

@Component({
  selector: 'app-carrito',
  standalone: true,
  imports: [DecimalPipe, FormsModule, RouterLink],
  templateUrl: './carrito.html',
  styleUrl: './carrito.css'
})
export class Carrito {
  private readonly servicio = inject(CarritoService);
  private readonly autenticacion = inject(AuthService);
  private readonly enrutador = inject(Router);

  readonly carrito = signal<CarritoResponse | null>(null);
  readonly cargando = signal(false);
  readonly operacion = signal<string | null>(null);
  readonly error = signal('');
  readonly exito = signal('');
  readonly cantidades = signal<Record<string, number | null>>({});
  readonly hayAvisos = computed(() => this.carrito()?.productos.some(linea => !linea.disponible) ?? false);

  constructor() {
    this.cargarCarrito();
  }

  cargarCarrito(): void {
    if (this.cargando() || this.operacion() !== null) return;
    this.cargando.set(true);
    this.error.set('');
    this.exito.set('');
    this.servicio.consultarCarrito().subscribe({
      next: respuesta => {
        this.actualizarCarrito(respuesta);
        this.cargando.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.cargando.set(false);
        this.tratarError(error, 'No se pudo cargar el carrito. Inténtalo de nuevo.');
      }
    });
  }

  puedeModificar(linea: LineaCarritoResponse): boolean {
    return (linea.stock ?? 0) > 0 && (linea.disponible
      || linea.aviso === 'No hay stock suficiente para la cantidad solicitada.');
  }

  cambiarCantidad(linea: LineaCarritoResponse, cantidad: number | null): void {
    if (this.operacion() !== null || !this.puedeModificar(linea)) return;
    this.cantidades.update(actuales => ({ ...actuales, [linea.productoId]: cantidad }));
  }

  ajustarCantidad(linea: LineaCarritoResponse, cambio: number): void {
    if (this.operacion() !== null || this.cargando() || !this.puedeModificar(linea)) return;
    const actual = this.cantidades()[linea.productoId];
    const cantidad = Number.isSafeInteger(actual) ? actual! : linea.cantidad;
    this.cambiarCantidad(linea, Math.max(1, Math.min(linea.stock ?? 1, cantidad + cambio)));
    this.guardarCantidad(linea);
  }

  guardarCantidad(linea: LineaCarritoResponse): void {
    if (this.operacion() !== null || this.cargando() || !this.puedeModificar(linea)) return;
    const cantidad = this.cantidades()[linea.productoId];
    this.exito.set('');
    if (cantidad == null || !Number.isSafeInteger(cantidad) || cantidad <= 0) {
      this.error.set('La cantidad debe ser un número entero mayor que cero.');
      return;
    }
    if (cantidad > (linea.stock ?? 0)) {
      this.error.set('No hay stock suficiente para la cantidad solicitada.');
      return;
    }
    if (cantidad === linea.cantidad) return;
    this.ejecutar(this.servicio.modificarCantidad(linea.productoId, cantidad), linea.productoId,
      'Cantidad actualizada.', 'No se pudo modificar la cantidad. Revisa el stock y vuelve a intentarlo.', true);
  }

  eliminarProducto(linea: LineaCarritoResponse): void {
    if (this.operacion() !== null || this.cargando()) return;
    this.ejecutar(this.servicio.eliminarProducto(linea.productoId), linea.productoId,
      'Producto eliminado del carrito.', 'No se pudo eliminar el producto. Inténtalo de nuevo.', true);
  }

  descartarCarrito(): void {
    if (this.operacion() !== null || this.cargando() || !this.carrito()?.productos.length) return;
    if (!window.confirm('¿Quieres descartar todo el carrito? Se eliminarán todos sus productos.')) return;
    this.ejecutar(this.servicio.descartarCarrito(), 'descarte',
      'Carrito descartado.', 'No se pudo descartar el carrito. Inténtalo de nuevo.');
  }

  private ejecutar(peticion: Observable<CarritoResponse>, operacion: string, exito: string, error: string,
      operacionDeLinea = false): void {
    this.operacion.set(operacion);
    this.error.set('');
    this.exito.set('');
    peticion.subscribe({
      next: respuesta => {
        this.actualizarCarrito(respuesta);
        this.operacion.set(null);
        this.exito.set(exito);
      },
      error: (fallo: HttpErrorResponse) => {
        this.operacion.set(null);
        this.tratarError(fallo, error, operacionDeLinea);
      }
    });
  }

  private actualizarCarrito(respuesta: CarritoResponse): void {
    this.carrito.set(respuesta);
    this.cantidades.set(Object.fromEntries(respuesta.productos.map(linea => [linea.productoId, linea.cantidad])));
  }

  private tratarError(error: HttpErrorResponse, mensaje: string, operacionDeLinea = false): void {
    const permitidos = new Set([
      'La cantidad debe ser mayor que cero.',
      'El producto ya no está disponible.',
      'El producto está agotado.',
      'Este producto solo está disponible para clientes Premium.',
      'No hay stock suficiente para la cantidad solicitada.',
      'Un producto del carrito no tiene un precio disponible.'
    ]);
    if (error.status === 0) mensaje = 'No se pudo conectar con el servicio. Inténtalo de nuevo.';
    if ([400, 403].includes(error.status) && permitidos.has(error.error?.error)) mensaje = error.error.error;
    if (error.status === 404 && operacionDeLinea) {
      mensaje = 'El producto ya no está en el carrito o ha sido eliminado.';
    }
    if (error.status === 409) mensaje = 'El carrito se ha modificado. Vuelve a intentarlo.';
    if (error.status === 401) {
      mensaje = 'Tu sesión ha caducado. Inicia sesión de nuevo.';
      this.irALogin();
    }
    if (error.status === 403) {
      this.autenticacion.obtenerSesionActual().subscribe({
        next: () => {},
        error: (sesion: HttpErrorResponse) => {
          if ([401, 403].includes(sesion.status)) this.irALogin();
        }
      });
    }
    this.error.set(mensaje);
  }

  private irALogin(): void {
    void this.enrutador.navigate(['/login'], { queryParams: { motivo: 'sesion-expirada' } });
  }
}
