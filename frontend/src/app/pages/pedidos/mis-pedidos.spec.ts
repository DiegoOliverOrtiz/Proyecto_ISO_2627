import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { MisPedidos } from './mis-pedidos';
import { EstadoPedido, PedidoCliente } from './pedidos.service';

describe('MisPedidos - HU-38 confirmar recepción', () => {
  let fixture: ComponentFixture<MisPedidos>;
  let http: HttpTestingController;

  const baseUrl = `${environment.apiUrl}/cliente/pedidos`;
  const csrfUrl = `${environment.apiUrl}/usuarios/csrf`;
  const valoracionUrl = (
    pedidoId: string,
    productoId: string
  ): string =>
    `${baseUrl}/${pedidoId}/productos/${productoId}/valoraciones`;
  const estadoValoracionUrl = (
    pedidoId: string,
    productoId: string
  ): string => `${valoracionUrl(pedidoId, productoId)}/estado`;

  const pedido = (
    id: string,
    estado: EstadoPedido
  ): PedidoCliente => ({
    id,
    estado,
    lineas: [],
    importe: 25,
    fechaCreacion: '2026-11-05T10:00:00Z',
    fechaPago: '2026-11-05T10:05:00Z',
    direccionEnvio: null,
    historialEstados: [
      { estado: 'PAGADO_COMPLETADO', fecha: '2026-11-05T10:05:00Z' },
      ...(estado === 'TRAMITADO_ENVIADO' || estado === 'FINALIZADO'
        ? [{
            estado: 'TRAMITADO_ENVIADO' as EstadoPedido,
            fecha: '2026-11-05T11:00:00Z'
          }]
        : []),
      ...(estado === 'FINALIZADO'
        ? [{
            estado: 'FINALIZADO' as EstadoPedido,
            fecha: '2026-11-05T12:00:00Z'
          }]
        : [])
    ]
  });

  const enviado = pedido('pedido-enviado', 'TRAMITADO_ENVIADO');
  const pagado = pedido('pedido-pagado', 'PAGADO_COMPLETADO');
  const finalizado = pedido('pedido-enviado', 'FINALIZADO');
  const pedidoFinalizadoConProducto: PedidoCliente = {
    ...finalizado,
    id: 'pedido-finalizado',
    lineas: [{
      productoId: 'producto-1',
      nombreProducto: 'Producto de prueba',
      precioCompra: 25,
      cantidad: 1,
      vendedorId: 'vendedor-1',
      nombreVendedor: 'Tienda de prueba'
    }]
  };

  function cargar(
    activos: PedidoCliente[] = [enviado],
    historico: PedidoCliente[] = []
  ): void {
    http.expectOne(`${baseUrl}/activos`).flush(activos);
    http.expectOne(`${baseUrl}/historico`).flush(historico);
    fixture.detectChanges();
  }

  function botonConfirmar(): HTMLButtonElement | undefined {
    const botones: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll(
        '#titulo-activos ~ .lista-pedidos button'
      )
    );

    return botones.find(boton =>
      boton.textContent?.includes('Confirmar recepción')
    );
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MisPedidos],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    fixture = TestBed.createComponent(MisPedidos);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('solo muestra confirmar recepción en pedidos enviados', () => {
    cargar([pagado, enviado]);

    const tarjetas: HTMLElement[] = Array.from(
      fixture.nativeElement.querySelectorAll(
        '#titulo-activos ~ .lista-pedidos .tarjeta-pedido'
      )
    );

    expect(tarjetas).toHaveLength(2);

    expect(tarjetas[0].textContent)
      .not.toContain('Confirmar recepción');

    expect(tarjetas[1].textContent)
      .toContain('Confirmar recepción');
  });

  it('no envía la petición si el cliente rechaza la confirmación', () => {
    cargar();

    const confirmar = vi.spyOn(window, 'confirm')
      .mockReturnValue(false);

    const boton = botonConfirmar();
    expect(boton).toBeDefined();

    boton!.click();

    expect(confirmar).toHaveBeenCalledOnce();
    http.expectNone(csrfUrl);
    http.expectNone(`${baseUrl}/pedido-enviado/confirmar-recepcion`);

    expect(fixture.componentInstance.pedidosActivos())
      .toHaveLength(1);
  });

  it('mueve al histórico el pedido confirmado correctamente', () => {
    cargar();

    vi.spyOn(window, 'confirm').mockReturnValue(true);

    botonConfirmar()!.click();

    http.expectOne(csrfUrl).flush({ token: 'csrf' });

    const peticion = http.expectOne(
      `${baseUrl}/pedido-enviado/confirmar-recepcion`
    );

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.headers.get('X-XSRF-TOKEN'))
      .toBe('csrf');

    peticion.flush(finalizado);
    fixture.detectChanges();

    expect(fixture.componentInstance.pedidosActivos())
      .toHaveLength(0);

    expect(fixture.componentInstance.pedidosHistoricos())
      .toContainEqual(finalizado);

    expect(fixture.nativeElement.textContent)
      .toContain('Finalizado');
  });

  it('impide confirmaciones duplicadas mientras está procesando', () => {
    cargar();

    vi.spyOn(window, 'confirm').mockReturnValue(true);

    botonConfirmar()!.click();
    fixture.detectChanges();

    const boton = botonConfirmar();
    expect(boton).toBeDefined();
    expect(boton!.disabled).toBe(true);

    expect(http.match(csrfUrl)).toHaveLength(1);

    // La petición todavía no debe haberse completado.
    http.expectNone(`${baseUrl}/pedido-enviado/confirmar-recepcion`);
  });

  it('mantiene el pedido activo cuando el servidor devuelve 409', () => {
    cargar();

    vi.spyOn(window, 'confirm').mockReturnValue(true);

    botonConfirmar()!.click();

    http.expectOne(csrfUrl).flush({ token: 'csrf' });

    http.expectOne(
      `${baseUrl}/pedido-enviado/confirmar-recepcion`
    ).flush(
      { error: 'El pedido ya no está en estado enviado.' },
      { status: 409, statusText: 'Conflict' }
    );

    fixture.detectChanges();

    expect(fixture.componentInstance.pedidosActivos())
      .toContainEqual(enviado);

    expect(fixture.componentInstance.pedidosHistoricos())
      .toHaveLength(0);

    expect(fixture.nativeElement.textContent)
      .toContain('El pedido ya no está en estado enviado.');
  });

  it('solo permite valorar productos de pedidos finalizados', () => {
    cargar([enviado], [pedidoFinalizadoConProducto]);

    fixture.componentInstance.abrirDetalle(pedidoFinalizadoConProducto.id);
    http.expectOne(`${baseUrl}/${pedidoFinalizadoConProducto.id}`)
      .flush(pedidoFinalizadoConProducto);
    http.expectOne(estadoValoracionUrl(
      pedidoFinalizadoConProducto.id,
      'producto-1'
    )).flush({ valorada: false });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Valorar');

    fixture.componentInstance.abrirDetalle(enviado.id);
    http.expectOne(`${baseUrl}/${enviado.id}`).flush(enviado);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent)
      .not.toContain('Valorar producto');
  });

  it('muestra el formulario para una valoración pendiente y la marca tras enviarla', () => {
    cargar([], [pedidoFinalizadoConProducto]);

    fixture.componentInstance.abrirDetalle(pedidoFinalizadoConProducto.id);
    http.expectOne(`${baseUrl}/${pedidoFinalizadoConProducto.id}`)
      .flush(pedidoFinalizadoConProducto);
    http.expectOne(estadoValoracionUrl(
      pedidoFinalizadoConProducto.id,
      'producto-1'
    )).flush({ valorada: false });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent)
      .toContain('Valorar producto');

    const puntuacion: HTMLSelectElement | null =
      fixture.nativeElement.querySelector('#puntuacion-producto-1');
    const comentario: HTMLTextAreaElement | null =
      fixture.nativeElement.querySelector('#comentario-producto-1');
    const boton: HTMLButtonElement | null =
      fixture.nativeElement.querySelector('#formulario-valoracion-producto-1 button[type="submit"]');

    expect(puntuacion).not.toBeNull();
    expect(comentario).not.toBeNull();
    expect(comentario?.getAttribute('maxlength')).toBe('500');
    expect(boton).not.toBeNull();

    fixture.componentInstance.formularioValoracion('producto-1')!
      .setValue({ puntuacion: 5, comentario: 'Buen producto' });
    expect(boton!.disabled).toBe(false);
    fixture.nativeElement
      .querySelector('#formulario-valoracion-producto-1')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    http.expectOne(csrfUrl).flush({ token: 'csrf-token' });
    const peticion = http.expectOne(
      valoracionUrl(pedidoFinalizadoConProducto.id, 'producto-1')
    );
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({
      puntuacion: 5,
      comentario: 'Buen producto'
    });
    peticion.flush({
      id: 'valoracion-1',
      pedidoId: pedidoFinalizadoConProducto.id,
      productoId: 'producto-1',
      puntuacion: 5,
      comentario: 'Buen producto'
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent)
      .toContain('Ya valorado');
    expect(fixture.nativeElement.textContent)
      .not.toContain('Valorar producto');
  });

  it('no muestra formulario si el producto ya está valorado', () => {
    cargar([], [pedidoFinalizadoConProducto]);

    fixture.componentInstance.abrirDetalle(pedidoFinalizadoConProducto.id);
    http.expectOne(`${baseUrl}/${pedidoFinalizadoConProducto.id}`)
      .flush(pedidoFinalizadoConProducto);
    http.expectOne(estadoValoracionUrl(
      pedidoFinalizadoConProducto.id,
      'producto-1'
    )).flush({ valorada: true });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent)
      .toContain('Ya valorado');
    expect(fixture.nativeElement.textContent)
      .not.toContain('Valorar producto');
  });
});
