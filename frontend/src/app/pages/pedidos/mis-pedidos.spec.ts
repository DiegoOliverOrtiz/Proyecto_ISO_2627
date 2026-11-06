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
import { ConfirmacionService } from '../../shared/confirmacion/confirmacion.service';
// M8: la confirmación es un diálogo propio (asíncrono); se simula su respuesta.
const simularConfirmacion = (aceptada: boolean) =>
  vi.spyOn(TestBed.inject(ConfirmacionService), 'confirmar').mockResolvedValue(aceptada);
const esperarConfirmacion = () => new Promise<void>((resolver) => setTimeout(resolver));


describe('MisPedidos - HU-38 confirmar recepción', () => {
  let fixture: ComponentFixture<MisPedidos>;
  let http: HttpTestingController;

  const baseUrl = `${environment.apiUrl}/cliente/pedidos`;
  const csrfUrl = `${environment.apiUrl}/usuarios/csrf`;

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

  it('no envía la petición si el cliente rechaza la confirmación', async () => {
    cargar();

    const confirmar = simularConfirmacion(false);

    const boton = botonConfirmar();
    expect(boton).toBeDefined();

    boton!.click();
    await esperarConfirmacion();

    expect(confirmar).toHaveBeenCalledOnce();
    http.expectNone(csrfUrl);
    http.expectNone(`${baseUrl}/pedido-enviado/confirmar-recepcion`);

    expect(fixture.componentInstance.pedidosActivos())
      .toHaveLength(1);
  });

  it('mueve al histórico el pedido confirmado correctamente', async () => {
    cargar();

    simularConfirmacion(true);

    botonConfirmar()!.click();
    await esperarConfirmacion();

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

  it('impide confirmaciones duplicadas mientras está procesando', async () => {
    cargar();

    simularConfirmacion(true);

    botonConfirmar()!.click();
    await esperarConfirmacion();
    fixture.detectChanges();

    const boton = botonConfirmar();
    expect(boton).toBeDefined();
    expect(boton!.disabled).toBe(true);

    expect(http.match(csrfUrl)).toHaveLength(1);

    // La petición todavía no debe haberse completado.
    http.expectNone(`${baseUrl}/pedido-enviado/confirmar-recepcion`);
  });

  it('mantiene el pedido activo cuando el servidor devuelve 409', async () => {
    cargar();

    simularConfirmacion(true);

    botonConfirmar()!.click();
    await esperarConfirmacion();

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
});
