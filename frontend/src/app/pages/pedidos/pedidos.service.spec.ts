import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { environment } from '../../../environments/environment';
import {
  PedidoCliente,
  PedidosService
} from './pedidos.service';

describe('PedidosService - HU-38 confirmar recepción', () => {
  let servicio: PedidosService;
  let http: HttpTestingController;

  const baseUrl = `${environment.apiUrl}/cliente/pedidos`;
  const csrfUrl = `${environment.apiUrl}/usuarios/csrf`;

  const pedidoFinalizado: PedidoCliente = {
    id: 'pedido-1',
    lineas: [],
    estado: 'FINALIZADO',
    importe: 25,
    fechaCreacion: '2026-11-05T10:00:00Z',
    fechaPago: '2026-11-05T10:05:00Z',
    direccionEnvio: null,
    historialEstados: [
      {
        estado: 'PAGADO_COMPLETADO',
        fecha: '2026-11-05T10:05:00Z'
      },
      {
        estado: 'TRAMITADO_ENVIADO',
        fecha: '2026-11-05T11:00:00Z'
      },
      {
        estado: 'FINALIZADO',
        fecha: '2026-11-05T12:00:00Z'
      }
    ]
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    servicio = TestBed.inject(PedidosService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('obtiene el token CSRF antes de confirmar recepción', () => {
    let resultado: PedidoCliente | undefined;

    servicio.confirmarRecepcion('pedido-1').subscribe(
      pedido => resultado = pedido
    );

    const csrf = http.expectOne(csrfUrl);

    expect(csrf.request.method).toBe('GET');
    expect(csrf.request.withCredentials).toBe(true);

    // No se permite confirmar antes de obtener el token.
    http.expectNone(`${baseUrl}/pedido-1/confirmar-recepcion`);

    csrf.flush({ token: 'token-seguro' });

    const peticion = http.expectOne(
      `${baseUrl}/pedido-1/confirmar-recepcion`
    );

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.withCredentials).toBe(true);
    expect(peticion.request.headers.get('X-XSRF-TOKEN'))
      .toBe('token-seguro');
    expect(peticion.request.body).toBeNull();

    peticion.flush(pedidoFinalizado);

    expect(resultado).toEqual(pedidoFinalizado);
    expect(resultado?.estado).toBe('FINALIZADO');
  });

  it('codifica el identificador del pedido en la URL', () => {
    servicio.confirmarRecepcion('pedido/1?otro=2').subscribe();

    http.expectOne(csrfUrl).flush({ token: 'token' });

    const peticion = http.expectOne(
      `${baseUrl}/pedido%2F1%3Fotro%3D2/confirmar-recepcion`
    );

    expect(peticion.request.method).toBe('POST');

    peticion.flush(pedidoFinalizado);
  });

  it('no envía la confirmación si falla la obtención del CSRF', () => {
    let codigoError: number | undefined;

    servicio.confirmarRecepcion('pedido-1').subscribe({
      error: error => codigoError = error.status
    });

    http.expectOne(csrfUrl).flush(
      { error: 'Error al obtener CSRF' },
      { status: 500, statusText: 'Internal Server Error' }
    );

    http.expectNone(`${baseUrl}/pedido-1/confirmar-recepcion`);

    expect(codigoError).toBe(500);
  });

  it('propaga el error 409 si el pedido no está enviado', () => {
    let codigoError: number | undefined;

    servicio.confirmarRecepcion('pedido-1').subscribe({
      error: error => codigoError = error.status
    });

    http.expectOne(csrfUrl).flush({ token: 'token' });

    http.expectOne(
      `${baseUrl}/pedido-1/confirmar-recepcion`
    ).flush(
      { error: 'El pedido no está en estado enviado.' },
      { status: 409, statusText: 'Conflict' }
    );

    expect(codigoError).toBe(409);
  });

  it('consulta si el producto del pedido ya está valorado', () => {
    let resultado: { valorada: boolean } | undefined;

    servicio.consultarEstadoValoracion('pedido-1', 'producto-1')
      .subscribe(estado => resultado = estado);

    const peticion = http.expectOne(
      `${baseUrl}/pedido-1/productos/producto-1/valoraciones/estado`
    );

    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBe(true);

    peticion.flush({ valorada: true });

    expect(resultado).toEqual({ valorada: true });
  });

  it('obtiene el token CSRF antes de enviar una valoración', () => {
    let resultado: { id: string; puntuacion: number } | undefined;

    servicio.valorarProducto('pedido-1', 'producto-1', {
      puntuacion: 5,
      comentario: 'Buen producto'
    }).subscribe(valoracion => resultado = valoracion);

    const csrf = http.expectOne(csrfUrl);
    expect(csrf.request.method).toBe('GET');
    expect(csrf.request.withCredentials).toBe(true);
    http.expectNone(
      `${baseUrl}/pedido-1/productos/producto-1/valoraciones`
    );

    csrf.flush({ token: 'token-seguro' });

    const peticion = http.expectOne(
      `${baseUrl}/pedido-1/productos/producto-1/valoraciones`
    );

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.withCredentials).toBe(true);
    expect(peticion.request.headers.get('X-XSRF-TOKEN'))
      .toBe('token-seguro');
    expect(peticion.request.body).toEqual({
      puntuacion: 5,
      comentario: 'Buen producto'
    });

    const respuesta = {
      id: 'valoracion-1',
      pedidoId: 'pedido-1',
      productoId: 'producto-1',
      puntuacion: 5,
      comentario: 'Buen producto'
    };
    peticion.flush(respuesta);

    expect(resultado).toEqual(respuesta);
  });

  it('no envía la valoración si falla la obtención del CSRF', () => {
    let codigoError: number | undefined;

    servicio.valorarProducto('pedido-1', 'producto-1', {
      puntuacion: 4,
      comentario: ''
    }).subscribe({
      error: error => codigoError = error.status
    });

    http.expectOne(csrfUrl).flush(
      { error: 'Error al obtener CSRF' },
      { status: 500, statusText: 'Internal Server Error' }
    );

    http.expectNone(
      `${baseUrl}/pedido-1/productos/producto-1/valoraciones`
    );
    expect(codigoError).toBe(500);
  });
});
