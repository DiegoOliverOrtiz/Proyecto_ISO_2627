import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Perfil } from './perfil';
import { PerfilResponse, PerfilService } from './perfil.service';

const perfilCliente: PerfilResponse = {
  tipoUsuario: 'CLIENTE', nombre: 'Ana', apellidos: 'García',
  email: 'ana@esibuy.com', telefono: '600123123', imagenPerfil: null,
  dniNif: '12*******', fechaNacimiento: null, esPremium: true,
  nombreComercial: null, categoria: null, sede: null,
  fechaIncorporacion: null, rol: null, totpActivado: false,
};

const perfilVendedor: PerfilResponse = {
  tipoUsuario: 'VENDEDOR', nombre: 'Luis', apellidos: 'Martín',
  email: 'luis@esibuy.com', telefono: null, imagenPerfil: null,
  dniNif: '45*******', fechaNacimiento: null, esPremium: null,
  nombreComercial: 'UrbanStyle', categoria: 'Tecnología', sede: null,
  fechaIncorporacion: null, rol: null, categoriaId: 'cat-1', totpActivado: false,
};

const perfilAdministrador: PerfilResponse = {
  tipoUsuario: 'ADMIN', nombre: 'Ada', apellidos: 'Admin',
  email: 'admin@esibuy.com', telefono: null, imagenPerfil: null,
  dniNif: null, fechaNacimiento: null, esPremium: null,
  nombreComercial: null, categoria: null, sede: 'Central',
  fechaIncorporacion: '2026-01-01', rol: 'ADMIN', totpActivado: false,
};

describe('Perfil', () => {
  let fixture: ComponentFixture<Perfil>;
  let componente: Perfil;
  let perfilServiceMock: {
    consultarPerfil: ReturnType<typeof vi.fn>;
    obtenerCategorias: ReturnType<typeof vi.fn>;
    modificarPerfil: ReturnType<typeof vi.fn>;
    cambiarTipoCliente: ReturnType<typeof vi.fn>;
    confirmarSegundoFactor: ReturnType<typeof vi.fn>;
    desactivarSegundoFactor: ReturnType<typeof vi.fn>;
    darDeBajaCuenta: ReturnType<typeof vi.fn>;
  };

  function crearComponente(respuesta: Observable<PerfilResponse>) {
    perfilServiceMock.consultarPerfil.mockReturnValue(respuesta);
    fixture = TestBed.createComponent(Perfil);
    componente = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    perfilServiceMock = {
      consultarPerfil: vi.fn(),
      obtenerCategorias: vi.fn(() => of([])),
      modificarPerfil: vi.fn(),
      cambiarTipoCliente: vi.fn(),
      confirmarSegundoFactor: vi.fn(),
      desactivarSegundoFactor: vi.fn(),
      darDeBajaCuenta: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [Perfil],
      providers: [
        provideRouter([]),
        { provide: PerfilService, useValue: perfilServiceMock },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('carga y muestra el perfil del cliente', () => {
    crearComponente(of(perfilCliente));

    expect(componente.perfil()).toEqual(perfilCliente);
    expect(componente.cargando()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('CLIENTE');
    expect(fixture.nativeElement.textContent).toContain('Ana García');
    expect(fixture.nativeElement.textContent).toContain('12*******');
  });

  it('requiere un codigo antes de solicitar la desactivacion de TOTP', () => {
    crearComponente(of({ ...perfilCliente, totpActivado: true }));
    componente.desactivarTOTP();
    expect(perfilServiceMock.desactivarSegundoFactor).not.toHaveBeenCalled();
    expect(componente.perfil()?.totpActivado).toBe(true);
    expect(componente.errorTOTP()).toContain('Introduce un código');
  });

  it('envia el codigo escrito en el formulario y limpia los respaldos al desactivar', async () => {
    crearComponente(of({ ...perfilCliente, totpActivado: true }));
    await fixture.whenStable();
    const entrada: HTMLInputElement = fixture.nativeElement.querySelector('#totp-disable-code');
    entrada.value = '081804';
    entrada.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    perfilServiceMock.desactivarSegundoFactor.mockReturnValue(of(undefined));
    componente.codigosRespaldo.set(['ABCDEFGH234567AB']);

    componente.desactivarTOTP();

    expect(perfilServiceMock.desactivarSegundoFactor).toHaveBeenCalledWith('081804');
    expect(componente.perfil()?.totpActivado).toBe(false);
    expect(componente.codigoTOTP()).toBe('');
    expect(componente.codigosRespaldo()).toEqual([]);
  });

  it('mantiene TOTP activado y muestra el bloqueo temporal si se agotan los intentos', () => {
    crearComponente(of({ ...perfilCliente, totpActivado: true }));
    componente.codigoTOTP.set('123456');
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    perfilServiceMock.desactivarSegundoFactor.mockReturnValue(throwError(() =>
      new HttpErrorResponse({ status: 429, error: { error: 'Espera 5 minutos.' } })));
    componente.desactivarTOTP();
    expect(componente.perfil()?.totpActivado).toBe(true);
    expect(componente.errorTOTP()).toBe('Espera 5 minutos.');
    expect(componente.cargandoTOTP()).toBe(false);
  });

  it('muestra la tarjeta de tipo y el boton Premium para un cliente normal', () => {
    crearComponente(of({ ...perfilCliente, esPremium: false }));

    expect(fixture.nativeElement.querySelector('.account-type-card')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Hacerme cliente Premium');
    expect(fixture.nativeElement.textContent).toContain('Ventajas Premium');
  });

  it('no muestra el cambio de tipo para vendedor ni administrador', () => {
    crearComponente(of(perfilVendedor));
    expect(fixture.nativeElement.querySelector('.account-type-card')).toBeNull();

    fixture.destroy();
    crearComponente(of(perfilAdministrador));
    expect(fixture.nativeElement.querySelector('.account-type-card')).toBeNull();
  });

  it('solo ofrece la baja de cuenta a clientes y vendedores', () => {
    crearComponente(of(perfilCliente));
    expect(fixture.nativeElement.textContent).toContain('Eliminar mi cuenta');

    fixture.destroy();
    crearComponente(of(perfilVendedor));
    expect(fixture.nativeElement.textContent).toContain('Eliminar mi cuenta');

    fixture.destroy();
    crearComponente(of(perfilAdministrador));
    expect(fixture.nativeElement.textContent).not.toContain('Eliminar mi cuenta');
  });

  it('exige dos confirmaciones y permite cancelar sin enviar la baja', () => {
    crearComponente(of(perfilCliente));
    componente.iniciarBaja();
    componente.cancelarBaja();
    expect(componente.pasoConfirmacionBaja()).toBe(0);
    expect(perfilServiceMock.darDeBajaCuenta).not.toHaveBeenCalled();

    componente.iniciarBaja();
    componente.continuarBaja();
    expect(componente.pasoConfirmacionBaja()).toBe(2);
    expect(perfilServiceMock.darDeBajaCuenta).not.toHaveBeenCalled();
    componente.cancelarBaja();
    expect(perfilServiceMock.darDeBajaCuenta).not.toHaveBeenCalled();
  });

  it('envía la baja tras confirmar dos veces y vuelve al inicio de sesión', () => {
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate');
    crearComponente(of(perfilCliente));
    perfilServiceMock.darDeBajaCuenta.mockReturnValue(of(undefined));

    componente.iniciarBaja();
    componente.continuarBaja();
    componente.continuarBaja();

    expect(perfilServiceMock.darDeBajaCuenta).toHaveBeenCalledWith('CLIENTE');
    expect(navegar).toHaveBeenCalledWith(['/login'], { queryParams: { motivo: 'cuenta-eliminada' } });
  });

  it('muestra el rechazo de baja cuando el vendedor tiene pedidos pendientes', () => {
    crearComponente(of(perfilVendedor));
    perfilServiceMock.darDeBajaCuenta.mockReturnValue(throwError(() =>
      new HttpErrorResponse({ status: 409, error: { error: 'No puedes darte de baja porque tienes pedidos en curso.' } })));

    componente.iniciarBaja();
    componente.continuarBaja();
    componente.continuarBaja();

    expect(componente.errorBaja()).toBe('No puedes darte de baja porque tienes pedidos en curso.');
    expect(componente.bajaEnCurso()).toBe(false);
  });

  it('muestra la categoría del vendedor cuando está asociada', () => {
    crearComponente(of(perfilVendedor));

    expect(fixture.nativeElement.textContent).toContain('Tecnología');
    expect(fixture.nativeElement.textContent).not.toContain('No hay una categoría asociada');
  });

  it('muestra un mensaje cuando el vendedor no tiene categoría', () => {
    crearComponente(of({ ...perfilVendedor, categoria: null }));

    expect(fixture.nativeElement.textContent).toContain('No hay una categoría asociada');
  });

  it('muestra el error devuelto por el backend', () => {
    crearComponente(throwError(() => new HttpErrorResponse({ status: 500 })));

    expect(componente.cargando()).toBe(false);
    expect(componente.mensajeError()).toContain('No se pudo cargar el perfil');
  });

  it('redirige al login si la sesión ha caducado', () => {
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate');
    crearComponente(throwError(() => new HttpErrorResponse({ status: 401 })));

    expect(navegar).toHaveBeenCalledWith(['/login'], { queryParams: { motivo: 'sesion-expirada' } });
  });
  it('permite editar y guardar los campos autorizados de un cliente', () => {
    crearComponente(of(perfilCliente));
    componente.iniciarEdicion();
    componente.formulario.patchValue({
      nombre: 'Ana Nueva', apellidos: 'García López', telefono: '600123123',
      dniNif: '12345678A', fechaNacimiento: '2000-01-01', imagenPerfil: '',
    });
    const perfilActualizado = { ...perfilCliente, nombre: 'Ana Nueva' };
    perfilServiceMock.modificarPerfil.mockReturnValue(of(perfilActualizado));

    componente.guardarCambios();

    expect(perfilServiceMock.modificarPerfil).toHaveBeenCalledWith({
      nombre: 'Ana Nueva', apellidos: 'García López', telefono: '600123123',
      imagenPerfil: null, dniNif: '12345678A', fechaNacimiento: '2000-01-01',
    });
    expect(componente.perfil()).toEqual(perfilActualizado);
    expect(componente.mensajeExito()).toContain('actualizado');
    expect(componente.editando()).toBe(false);
  });

  it('permite al vendedor seleccionar una categoría', () => {
    perfilServiceMock.obtenerCategorias.mockReturnValue(of([
      { id: 'cat-1', nombre: 'Tecnología' }, { id: 'cat-2', nombre: 'Hogar' },
    ]));
    crearComponente(of(perfilVendedor));
    componente.iniciarEdicion();
    componente.formulario.patchValue({ nombre: 'Luis', apellidos: 'Martín', categoriaId: 'cat-2' });
    const perfilActualizado = { ...perfilVendedor, categoria: 'Hogar', categoriaId: 'cat-2' };
    perfilServiceMock.modificarPerfil.mockReturnValue(of(perfilActualizado));

    componente.guardarCambios();

    expect(perfilServiceMock.modificarPerfil).toHaveBeenCalledWith(expect.objectContaining({ categoriaId: 'cat-2' }));
    expect(componente.perfil()).toEqual(perfilActualizado);
  });

  it('rechaza DNI y teléfono con formato incorrecto antes de enviar', () => {
    crearComponente(of(perfilCliente));
    componente.iniciarEdicion();
    componente.formulario.patchValue({ nombre: 'Ana', apellidos: 'García', telefono: 'telefono-invalido', dniNif: '123' });

    componente.guardarCambios();

    expect(perfilServiceMock.modificarPerfil).not.toHaveBeenCalled();
    expect(componente.mensajeFormulario()).toContain('Revisa');
  });

  it('no incluye contraseña ni direcciones en el formulario de edición', () => {
    crearComponente(of(perfilCliente));
    componente.iniciarEdicion();

    expect(fixture.nativeElement.querySelector('[formcontrolname="password"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[formcontrolname="contrasena"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[formcontrolname="direccion"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[formcontrolname="direcciones"]')).toBeNull();
  });

  it('muestra el error del backend y mantiene el modo edición', () => {
    crearComponente(of(perfilCliente));
    componente.iniciarEdicion();
    componente.formulario.patchValue({ nombre: 'Ana', apellidos: 'García', telefono: '600123123', dniNif: '12345678A' });
    perfilServiceMock.modificarPerfil.mockReturnValue(throwError(() =>
      new HttpErrorResponse({ status: 400, error: { message: 'Datos inválidos' } })));

    componente.guardarCambios();

    expect(componente.mensajeFormulario()).toContain('Datos inválidos');
    expect(componente.guardando()).toBe(false);
    expect(componente.editando()).toBe(true);
  });

  it('pide confirmacion antes de cambiar a cliente premium', () => {
    const perfilNormal = { ...perfilCliente, esPremium: false };
    crearComponente(of(perfilNormal));
    const perfilActualizado = { ...perfilNormal, esPremium: true };
    perfilServiceMock.cambiarTipoCliente.mockReturnValue(of(perfilActualizado));

    componente.cambiarTipoCliente();
    expect(componente.mostrandoConfirmacionTipo()).toBe(true);
    componente.confirmarCambioTipoCliente();

    expect(perfilServiceMock.cambiarTipoCliente).toHaveBeenCalledWith(true);
    expect(componente.perfil()).toEqual(perfilActualizado);
  });

  it('cancela el cambio de tipo si el cliente rechaza la confirmacion', () => {
    crearComponente(of({ ...perfilCliente, esPremium: false }));

    componente.cambiarTipoCliente();
    expect(componente.mostrandoConfirmacionTipo()).toBe(true);
    componente.cancelarCambioTipoCliente();

    expect(perfilServiceMock.modificarPerfil).not.toHaveBeenCalled();
    expect(perfilServiceMock.cambiarTipoCliente).not.toHaveBeenCalled();
    expect(componente.mostrandoConfirmacionTipo()).toBe(false);
  });

  it('confirma el cambio de premium a normal mediante el servicio especifico', () => {
    crearComponente(of(perfilCliente));
    const perfilActualizado = { ...perfilCliente, esPremium: false };
    perfilServiceMock.cambiarTipoCliente.mockReturnValue(of(perfilActualizado));

    componente.cambiarTipoCliente();
    componente.confirmarCambioTipoCliente();

    expect(perfilServiceMock.cambiarTipoCliente).toHaveBeenCalledWith(false);
    expect(componente.perfil()).toEqual(perfilActualizado);
  });

  it('muestra el error y conserva el tipo anterior si falla el cambio', () => {
    crearComponente(of({ ...perfilCliente, esPremium: false }));
    perfilServiceMock.cambiarTipoCliente.mockReturnValue(throwError(() =>
      new HttpErrorResponse({ status: 400, error: { message: 'No se pudo actualizar' } })));

    componente.cambiarTipoCliente();
    componente.confirmarCambioTipoCliente();

    expect(componente.perfil()?.esPremium).toBe(false);
    expect(componente.mensajeFormulario()).toContain('No se pudo actualizar');
    expect(componente.guardando()).toBe(false);
  });

  it('activa TOTP con un código que empieza por cero y muestra los códigos de respaldo', () => {
    crearComponente(of(perfilCliente));
    componente.configuracionTOTP.set({ otpauthUri: 'otpauth://totp/ESIBuy', qrDataUrl: null });
    componente.codigoTOTP.set('081804');
    const backupCodes = ['ABCDEFG2', '234567AB'];
    perfilServiceMock.confirmarSegundoFactor.mockReturnValue(of({ enabled: true, backupCodes }));

    componente.confirmarTOTP();
    fixture.detectChanges();

    expect(perfilServiceMock.confirmarSegundoFactor).toHaveBeenCalledWith('081804');
    expect(componente.perfil()?.totpActivado).toBe(true);
    expect(componente.codigosRespaldo()).toEqual(backupCodes);
    expect(componente.configuracionTOTP()).toBeNull();
    expect(componente.cargandoTOTP()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('ABCDEFG2');
  });

  it.each([
    { status: 500, error: undefined, mensaje: 'No se pudo activar el segundo factor.' },
    { status: 500, error: { error: 'Internal Server Error' }, mensaje: 'No se pudo activar el segundo factor.' },
    { status: 0, error: undefined, mensaje: 'No se pudo conectar con el servicio.' },
    { status: 401, error: undefined, mensaje: 'Tu sesión ya no es válida.' },
    { status: 400, error: { error: 'No hay una configuración TOTP pendiente. Genera un QR nuevo.' },
      mensaje: 'No hay una configuración TOTP pendiente. Genera un QR nuevo.' },
    { status: 400, error: { error: 'El código TOTP no es válido.' }, mensaje: 'El código TOTP no es válido.' },
  ])('muestra el motivo real al fallar la confirmación ($status, $mensaje)', ({ status, error, mensaje }) => {
    crearComponente(of(perfilCliente));
    const configuracion = { otpauthUri: 'otpauth://totp/ESIBuy', qrDataUrl: null };
    componente.configuracionTOTP.set(configuracion);
    componente.codigoTOTP.set('081804');
    perfilServiceMock.confirmarSegundoFactor.mockReturnValue(throwError(() =>
      new HttpErrorResponse({ status, error })));

    componente.confirmarTOTP();
    fixture.detectChanges();

    expect(componente.errorTOTP()).toContain(mensaje);
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(mensaje);
    expect(componente.perfil()?.totpActivado).toBe(false);
    expect(componente.configuracionTOTP()).toEqual(configuracion);
    expect(componente.cargandoTOTP()).toBe(false);
  });
});
