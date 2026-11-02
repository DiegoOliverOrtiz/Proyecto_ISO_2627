import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { environment } from '../../../environments/environment';
import {
  PedidoVendedor,
  PedidosVendedorService
} from './pedidos-vendedor.service';

describe('PedidosVendedorService - HU-35', () => {
  let servicio: PedidosVendedorService;
  let http: HttpTestingController;

  const baseUrl = `${environment.apiUrl}/vendedor/pedidos`;

  const pedido: PedidoVendedor = {
    id: 'pedido-1',
    clienteNombre: 'cliente eliminado',
    estado: 'PAGADO_COMPLETADO',
    fechaCreacion: '2026-11-02T12:00:00Z',
    fechaPago: '2026-11-02T12:05:00Z',
    productos: [
      {
        productoId: 'producto-1',
        nombreProducto: 'Roncola',
        precioCompra: 5,
        cantidad: 1
      }
    ],
    importeProductosPropios: 5,
    direccionEnvio: null
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    servicio = TestBed.inject(PedidosVendedorService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('consulta los pedidos activos con la sesión del vendedor', () => {
    let resultado: PedidoVendedor[] | undefined;

    servicio.consultarActivos().subscribe(
      pedidos => resultado = pedidos
    );

    const peticion = http.expectOne(`${baseUrl}/activos`);

    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBe(true);

    peticion.flush([pedido]);

    expect(resultado).toEqual([pedido]);
  });

  it('consulta el histórico con la sesión del vendedor', () => {
    const finalizado: PedidoVendedor = {
      ...pedido,
      estado: 'FINALIZADO'
    };

    let resultado: PedidoVendedor[] | undefined;

    servicio.consultarHistorico().subscribe(
      pedidos => resultado = pedidos
    );

    const peticion = http.expectOne(`${baseUrl}/historico`);

    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBe(true);

    peticion.flush([finalizado]);

    expect(resultado).toEqual([finalizado]);
  });

  it('consulta el detalle de un pedido propio', () => {
    let resultado: PedidoVendedor | undefined;

    servicio.consultarDetalle('pedido-1').subscribe(
      detalle => resultado = detalle
    );

    const peticion = http.expectOne(`${baseUrl}/pedido-1`);

    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBe(true);

    peticion.flush(pedido);

    expect(resultado?.id).toBe('pedido-1');
    expect(resultado?.productos[0].nombreProducto).toBe('Roncola');
    expect(resultado?.importeProductosPropios).toBe(5);
  });

  it('codifica el identificador del pedido en la URL', () => {
    servicio.consultarDetalle('pedido/1?otro=2').subscribe();

    const peticion = http.expectOne(
      `${baseUrl}/pedido%2F1%3Fotro%3D2`
    );

    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBe(true);

    peticion.flush(pedido);
  });

  it('propaga los errores del backend al componente', () => {
    let codigoError: number | undefined;

    servicio.consultarDetalle('pedido-ajeno').subscribe({
      error: error => codigoError = error.status
    });

    http.expectOne(`${baseUrl}/pedido-ajeno`).flush(
      { mensaje: 'Pedido no encontrado' },
      { status: 404, statusText: 'Not Found' }
    );

    expect(codigoError).toBe(404);
  });
});
