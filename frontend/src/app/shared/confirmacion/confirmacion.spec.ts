import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { Confirmacion } from './confirmacion';
import { ConfirmacionService } from './confirmacion.service';

describe('Confirmacion (M8)', () => {
  let fixture: ComponentFixture<Confirmacion>;
  let servicio: ConfirmacionService;
  let origen: HTMLButtonElement;

  // Se devuelve envuelta: un async que devuelve una promesa la aplanaría y esperaría la respuesta.
  const abrir = async (peligro = false): Promise<{ respuesta: Promise<boolean> }> => {
    origen.focus();
    const respuesta = servicio.confirmar({ titulo: 'Eliminar dirección', mensaje: 'No se puede deshacer.', aceptar: 'Eliminar', peligro });
    fixture.detectChanges();
    await fixture.whenStable();
    return { respuesta };
  };
  const dialogo = (): HTMLElement | null => fixture.nativeElement.querySelector('[role="alertdialog"]');
  const pulsar = (key: string, shiftKey = false): void => {
    (document.activeElement ?? document.body).dispatchEvent(new KeyboardEvent('keydown', { key, shiftKey, bubbles: true, cancelable: true }));
    fixture.detectChanges();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Confirmacion] });
    fixture = TestBed.createComponent(Confirmacion);
    servicio = TestBed.inject(ConfirmacionService);
    origen = document.createElement('button');
    document.body.appendChild(origen);
    fixture.detectChanges();
  });

  afterEach(() => {
    origen.remove();
    TestBed.resetTestingModule();
  });

  it('muestra un diálogo accesible con el título, el mensaje y el foco en «Cancelar»', async () => {
    await abrir();

    expect(dialogo()?.getAttribute('aria-modal')).toBe('true');
    expect(dialogo()?.textContent).toContain('Eliminar dirección');
    expect(dialogo()?.textContent).toContain('No se puede deshacer.');
    expect(document.activeElement?.classList).toContain('confirmacion-cancelar');
  });

  it('resuelve true al aceptar, cierra el diálogo y devuelve el foco al origen', async () => {
    const { respuesta } = await abrir(true);
    const aceptar: HTMLButtonElement = fixture.nativeElement.querySelector('.confirmacion-aceptar');
    expect(aceptar.classList).toContain('peligro');
    expect(aceptar.textContent?.trim()).toBe('Eliminar');

    aceptar.click();
    fixture.detectChanges();

    await expect(respuesta).resolves.toBe(true);
    expect(dialogo()).toBeNull();
    expect(document.activeElement).toBe(origen);
  });

  it('Escape y «Cancelar» resuelven false', async () => {
    const { respuesta: conEscape } = await abrir();
    pulsar('Escape');
    await expect(conEscape).resolves.toBe(false);

    const { respuesta: conBoton } = await abrir();
    fixture.nativeElement.querySelector('.confirmacion-cancelar').click();
    await expect(conBoton).resolves.toBe(false);
  });

  it('Tab no sale del diálogo', async () => {
    await abrir();
    const [cancelar, aceptar] = fixture.nativeElement.querySelectorAll('.confirmacion button');

    aceptar.focus();
    pulsar('Tab');
    expect(document.activeElement).toBe(cancelar);
    pulsar('Tab', true);
    expect(document.activeElement).toBe(aceptar);
  });

  it('una pregunta nueva cancela la anterior', async () => {
    const { respuesta: primera } = await abrir();
    await abrir();

    await expect(primera).resolves.toBe(false);
  });
});
