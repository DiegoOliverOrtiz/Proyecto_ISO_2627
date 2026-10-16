import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ModificarUsuarioRequest,
  UsuarioAdministracion,
  UsuariosService
} from './usuarios.service';
import { AdministradorService } from '../admin/administrador.service';

function mayorDeEdadValidator(
  control: AbstractControl
): ValidationErrors | null {

  const valor = control.value;

  if (!valor) {
    return null;
  }

  const partes = valor.split('-');

  if (partes.length !== 3) {
    return { fechaInvalida: true };
  }

  const anio = Number(partes[0]);
  const mes = Number(partes[1]);
  const dia = Number(partes[2]);

  const nacimiento = new Date(
    anio,
    mes - 1,
    dia
  );

  if (Number.isNaN(nacimiento.getTime())) {
    return { fechaInvalida: true };
  }

  const hoy = new Date();

  let edad =
    hoy.getFullYear() -
    nacimiento.getFullYear();

  const diferenciaMes =
    hoy.getMonth() -
    nacimiento.getMonth();

  if (
    diferenciaMes < 0 ||
    (
      diferenciaMes === 0 &&
      hoy.getDate() < nacimiento.getDate()
    )
  ) {
    edad--;
  }

  return edad >= 18
    ? null
    : { menorDeEdad: true };
}

@Component({
  selector: 'app-usuario-detalle',
  standalone: true,
  imports: [
    RouterLink,
    ReactiveFormsModule
  ],
  template: `
    <main class="detalle-page">

      <header class="topbar">
        <a
          class="brand"
          routerLink="/admin"
        >
          EsiBuy
        </a>

        <span>
          Gestión /
          {{ modoEdicion() ? 'Modificar usuario' : 'Detalle' }}
        </span>

        <a [routerLink]="volverUrl()">
          Volver al listado
        </a>
      </header>

      <div class="content">

        <a
          class="back-link"
          [routerLink]="volverUrl()"
        >
          ← Volver al listado
        </a>

        @if (cargando()) {

          <p class="status">
            Cargando información...
          </p>

        } @else if (mensajeError()) {

          <p
            class="status error"
            role="alert"
          >
            {{ mensajeError() }}
          </p>

        } @else if (usuario(); as datos) {

          <header class="page-header">

            <div>

              <p class="eyebrow">
                Ficha administrativa
              </p>

              <h1>
                {{ datos.nombre }}
                {{ datos.apellidos }}
              </h1>

              @if (modoEdicion()) {

                <p>
                  Modifica los datos del usuario.
                </p>

              } @else {

                <p>
                  Información útil para la gestión de la cuenta.
                </p>

              }

            </div>

            <div class="header-actions">

              @if (!modoEdicion()) {

                <button
                  type="button"
                  class="button primary"
                  (click)="iniciarEdicion(datos)"
                >
                  Modificar usuario
                </button>

                @if (datos.rol === 'ADMIN') {

                  <button
                    type="button"
                    class="button danger"
                    (click)="
                      eliminarAdministrador(
                        datos.id,
                        datos.nombre
                      )
                    "
                  >
                    Eliminar administrador
                  </button>

                }

              }

            </div>

          </header>

          @if (!modoEdicion()) {

            <section class="detail-card">

              <div class="status-row">

                <span>
                  Estado de la cuenta
                </span>

                <strong>
                  {{ datos.estado }}
                </strong>

              </div>

              <dl>

                <div>
                  <dt>Email</dt>
                  <dd>
                    {{ datos.email }}
                  </dd>
                </div>

                <div>
                  <dt>Teléfono</dt>
                  <dd>
                    {{ datos.telefono || 'No indicado' }}
                  </dd>
                </div>

                <div>
                  <dt>Estado</dt>
                  <dd>
                    {{ datos.estado }}
                  </dd>
                </div>

                <div>
                  <dt>Rol</dt>
                  <dd>
                    {{ datos.rol }}
                  </dd>
                </div>

                @if (datos.dniNif) {

                  <div>
                    <dt>DNI / NIF</dt>
                    <dd>
                      {{ datos.dniNif }}
                    </dd>
                  </div>

                }

                @if (
                  datos.rol === 'CLIENTE' &&
                  datos.fechaNacimiento
                ) {

                  <div>
                    <dt>
                      Fecha de nacimiento
                    </dt>

                    <dd>
                      {{ datos.fechaNacimiento }}
                    </dd>
                  </div>

                }

                @if (datos.sede) {

                  <div>
                    <dt>Sede</dt>
                    <dd>
                      {{ datos.sede }}
                    </dd>
                  </div>

                }

                @if (datos.fechaIncorporacion) {

                  <div>
                    <dt>
                      Fecha de incorporación
                    </dt>

                    <dd>
                      {{ datos.fechaIncorporacion }}
                    </dd>
                  </div>

                }

                @if (datos.esPremium !== null) {

                  <div>
                    <dt>
                      Cuenta premium
                    </dt>

                    <dd>
                      {{ datos.esPremium ? 'Sí' : 'No' }}
                    </dd>
                  </div>

                }

                @if (datos.nombreComercial) {

                  <div>
                    <dt>
                      Nombre comercial
                    </dt>

                    <dd>
                      {{ datos.nombreComercial }}
                    </dd>
                  </div>

                }

              </dl>

            </section>

          } @else {

            <section class="detail-card">

              <div class="status-row">

                <span>
                  Modificando usuario
                </span>

                <strong>
                  {{ datos.rol }}
                </strong>

              </div>

              <form
                class="edit-form"
                [formGroup]="formulario"
                (ngSubmit)="guardarCambios()"
              >

                <div class="field">

                  <label for="nombre">
                    Nombre
                  </label>

                  <input
                    id="nombre"
                    type="text"
                    formControlName="nombre"
                  />

                  @if (
                    formulario.controls.nombre.touched &&
                    formulario.controls.nombre.hasError('required')
                  ) {

                    <small class="field-error">
                      El nombre es obligatorio.
                    </small>

                  }

                </div>

                <div class="field">

                  <label for="apellidos">
                    Apellidos
                  </label>

                  <input
                    id="apellidos"
                    type="text"
                    formControlName="apellidos"
                  />

                  @if (
                    formulario.controls.apellidos.touched &&
                    formulario.controls.apellidos.hasError('required')
                  ) {

                    <small class="field-error">
                      Los apellidos son obligatorios.
                    </small>

                  }

                </div>

                <div class="field">

                  <label for="email">
                    Email
                  </label>

                  <input
                    id="email"
                    type="email"
                    [value]="datos.email"
                    disabled
                  />

                  <small class="protected-info">
                    El email no se puede modificar.
                  </small>

                </div>

                <div class="field">

                  <label for="telefono">
                    Teléfono
                  </label>

                  <input
                    id="telefono"
                    type="text"
                    formControlName="telefono"
                  />

                </div>

                <div class="field">

                  <label for="imagenPerfil">
                    Imagen de perfil
                  </label>

                  <input
                    id="imagenPerfil"
                    type="text"
                    formControlName="imagenPerfil"
                    placeholder="URL de la imagen"
                  />

                </div>

                @if (datos.rol === 'CLIENTE') {

                  <div class="field">

                    <label for="dniCliente">
                      DNI
                    </label>

                    <input
                      id="dniCliente"
                      type="text"
                      formControlName="dniNif"
                    />

                  </div>

                  <div class="field">

                    <label for="fechaNacimiento">
                      Fecha de nacimiento
                    </label>

                    <input
                      id="fechaNacimiento"
                      type="date"
                      formControlName="fechaNacimiento"
                    />

                    @if (
                      formulario.controls.fechaNacimiento.touched &&
                      formulario.controls.fechaNacimiento.hasError(
                        'menorDeEdad'
                      )
                    ) {

                      <small class="field-error">
                        El cliente debe ser mayor de edad.
                      </small>

                    }

                  </div>

                }

                @if (datos.rol === 'VENDEDOR') {

                  <div class="field">

                    <label for="dniVendedor">
                      DNI / NIF
                    </label>

                    <input
                      id="dniVendedor"
                      type="text"
                      formControlName="dniNif"
                    />

                  </div>

                }

                @if (datos.rol === 'ADMIN') {

                  <div class="field">

                    <label for="sede">
                      Sede
                    </label>

                    <input
                      id="sede"
                      type="text"
                      formControlName="sede"
                    />

                  </div>

                  <div class="field">

                    <label for="fechaIncorporacion">
                      Fecha de incorporación
                    </label>

                    <input
                      id="fechaIncorporacion"
                      type="text"
                      [value]="
                        datos.fechaIncorporacion || ''
                      "
                      disabled
                    />

                    <small class="protected-info">
                      La fecha de incorporación no se puede modificar.
                    </small>

                  </div>

                }

                <div class="protected-section">

                  <strong>
                    Contraseña
                  </strong>

                  <p>
                    La contraseña no puede modificarse desde esta pantalla.
                  </p>

                </div>

                @if (mensajeFormulario()) {

                  <p class="form-message">
                    {{ mensajeFormulario() }}
                  </p>

                }

                <div class="form-actions">

                  <button
                    type="button"
                    class="button secondary"
                    (click)="cancelarEdicion()"
                  >
                    Cancelar
                  </button>

                  <button
                    type="submit"
                    class="button primary"
                  >
                    Guardar cambios
                  </button>

                </div>

              </form>

            </section>

          }

        }

      </div>

    </main>
  `,
  styles: `
    :host {
      display: block;
      min-height: 100vh;
      background: #F7F9F8;
      color: #333;
    }

    .detalle-page {
      min-height: 100vh;
    }

    .topbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
      padding: 1rem 2rem;
      border-bottom: 1px solid #D9E3DE;
      color: #536A60;
      background: #E8EEE9;
      font-size: .9rem;
    }

    .topbar a {
      color: #255B4D;
      font-weight: 700;
      text-decoration: none;
    }

    .brand {
      font-size: 1.2rem;
    }

    .content {
      width: min(
        820px,
        calc(100% - 2rem)
      );
      margin: 0 auto;
      padding: 2rem 0 3rem;
    }

    .back-link {
      color: #255B4D;
      font-weight: 700;
      text-decoration: none;
      display: inline-block;
      margin-bottom: 1rem;
    }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      gap: 1.5rem;
      margin: 1.75rem 0 1.5rem;
    }

    .eyebrow {
      margin: 0 0 .35rem;
      color: #D36B54;
      font-size: .8rem;
      font-weight: 700;
      letter-spacing: .08em;
      text-transform: uppercase;
    }

    h1 {
      margin: 0;
      color: #173042;
      font-size: clamp(
        1.8rem,
        4vw,
        2.8rem
      );
      line-height: 1.08;
    }

    .page-header p:not(.eyebrow) {
      color: #536A60;
    }

    .header-actions {
      display: flex;
      gap: .75rem;
      flex-wrap: wrap;
      justify-content: flex-end;
    }

    .detail-card {
      padding: 1.25rem;
      border: 1px solid #D9E3DE;
      background: #fff;
      box-shadow:
        0 5px 18px
        rgba(37, 91, 77, .05);
    }

    .status-row {
      display: flex;
      justify-content: space-between;
      gap: 1rem;
      margin-bottom: 1rem;
      padding-bottom: 1rem;
      border-bottom: 1px solid #E5EDE7;
      color: #536A60;
    }

    .status-row strong {
      color: #255B4D;
    }

    dl {
      display: grid;
      gap: .75rem;
      margin: 0;
    }

    dl div {
      display: grid;
      grid-template-columns:
        minmax(150px, .7fr)
        1fr;
      gap: 1rem;
      padding: 1rem;
      border: 1px solid #E5EDE7;
      background: #F7F9F8;
    }

    dt {
      color: #536A60;
      font-weight: 700;
    }

    dd {
      margin: 0;
      overflow-wrap: anywhere;
    }

    .status {
      padding: 2rem;
      border: 1px solid #D9E3DE;
      background: #fff;
      text-align: center;
    }

    .error {
      border-color: #D36B54;
      color: #A63C28;
    }

    .edit-form {
      display: grid;
      gap: 1rem;
    }

    .field {
      display: grid;
      gap: .4rem;
    }

    .field label {
      color: #536A60;
      font-weight: 700;
    }

    .field input {
      width: 100%;
      box-sizing: border-box;
      padding: .75rem .85rem;
      border: 1px solid #CBD8D1;
      border-radius: 4px;
      background: #fff;
      color: #173042;
      font: inherit;
    }

    .field input:focus {
      outline:
        2px solid
        rgba(37, 91, 77, .18);
      border-color: #255B4D;
    }

    .field input:disabled {
      background: #F1F4F2;
      color: #718078;
      cursor: not-allowed;
    }

    .field-error {
      color: #A63C28;
      font-weight: 600;
    }

    .protected-info {
      color: #718078;
      font-size: .85rem;
    }

    .protected-section {
      padding: 1rem;
      border: 1px solid #E5EDE7;
      background: #F7F9F8;
    }

    .protected-section strong {
      color: #173042;
    }

    .protected-section p {
      margin: .35rem 0 0;
      color: #536A60;
    }

    .form-actions {
      display: flex;
      justify-content: flex-end;
      gap: .75rem;
      padding-top: .5rem;
    }

    .button {
      padding: .65rem 1rem;
      border-radius: 4px;
      font-weight: 700;
      cursor: pointer;
      font: inherit;
    }

    .primary {
      border: 1px solid #255B4D;
      background: #255B4D;
      color: white;
    }

    .secondary {
      border: 1px solid #CBD8D1;
      background: white;
      color: #255B4D;
    }

    .danger {
      border: 1px solid #F0B3A9;
      background: #FFF1F0;
      color: #A63C28;
    }

    .form-message {
      padding: .85rem 1rem;
      border: 1px solid #D9E3DE;
      background: #F7F9F8;
      color: #A63C28;
    }

    @media (max-width: 700px) {
      .page-header {
        align-items: stretch;
        flex-direction: column;
      }

      .header-actions {
        justify-content: flex-start;
      }
    }

    @media (max-width: 560px) {
      .topbar {
        align-items: flex-start;
        flex-direction: column;
        padding: .8rem 1rem;
      }

      dl div {
        grid-template-columns: 1fr;
        gap: .35rem;
      }

      .form-actions {
        flex-direction: column-reverse;
      }

      .button {
        width: 100%;
      }
    }
  `
})
export class UsuarioDetalle implements OnInit {

  private readonly route =
    inject(ActivatedRoute);

  private readonly router =
    inject(Router);

  private readonly usuariosService =
    inject(UsuariosService);

  private readonly administradorService =
    inject(AdministradorService);

  private readonly formBuilder =
    inject(FormBuilder);

  readonly usuario =
    signal<UsuarioAdministracion | null>(
      null
    );

  readonly cargando =
    signal(true);

  readonly mensajeError =
    signal('');

  readonly mensajeFormulario =
    signal('');

  readonly modoEdicion =
    signal(false);

  readonly volverUrl =
    signal('/admin/usuarios');

  readonly formulario =
    this.formBuilder.nonNullable.group({

      nombre: [
        '',
        Validators.required
      ],

      apellidos: [
        '',
        Validators.required
      ],

      telefono: [''],

      imagenPerfil: [''],

      dniNif: [''],

      fechaNacimiento: [
        '',
        mayorDeEdadValidator
      ],

      sede: ['']

    });

  ngOnInit(): void {

    const id =
      this.route.snapshot.paramMap.get(
        'id'
      );

    if (!id) {

      this.cargando.set(false);

      this.mensajeError.set(
        'No se ha indicado el usuario que quieres consultar.'
      );

      return;
    }

    this.usuariosService
      .consultarUsuario(id)
      .subscribe({

        next: (respuesta) => {

          this.usuario.set(
            respuesta
          );

          this.cargando.set(
            false
          );

          if (
            respuesta.rol === 'ADMIN'
          ) {

            this.volverUrl.set(
              '/admin/administradores'
            );

          }

          const editar =
            this.route.snapshot
              .queryParamMap
              .get('editar');

          if (editar === 'true') {

            this.iniciarEdicion(
              respuesta
            );

          }

        },

        error: (
          error: HttpErrorResponse
        ) => {

          this.cargando.set(false);

          this.mensajeError.set(
            this.mensajeParaError(
              error
            )
          );

        }

      });

  }

  iniciarEdicion(
    usuario: UsuarioAdministracion
  ): void {

    this.mensajeFormulario.set(
      ''
    );

    this.formulario.reset({

      nombre:
        usuario.nombre ?? '',

      apellidos:
        usuario.apellidos ?? '',

      telefono:
        usuario.telefono ?? '',

      imagenPerfil:
        usuario.imagenPerfil ?? '',

      dniNif:
        usuario.dniNif ?? '',

      fechaNacimiento:
        usuario.fechaNacimiento ?? '',

      sede:
        usuario.sede ?? ''

    });

    this.modoEdicion.set(true);

  }

  cancelarEdicion(): void {

    this.modoEdicion.set(false);

    this.mensajeFormulario.set('');

    this.formulario.reset();

  }

  guardarCambios(): void {

    if (this.formulario.invalid) {

      this.formulario
        .markAllAsTouched();

      return;
    }

    const usuarioActual =
      this.usuario();

    if (!usuarioActual) {
      return;
    }

    const valores =
      this.formulario
        .getRawValue();

    const datos:
      ModificarUsuarioRequest = {

      nombre:
        valores.nombre.trim(),

      apellidos:
        valores.apellidos.trim(),

      telefono:
        valores.telefono.trim()
        || null,

      imagenPerfil:
        valores.imagenPerfil.trim()
        || null

    };

    if (
      usuarioActual.rol ===
      'CLIENTE'
    ) {

      datos.dniNif =
        valores.dniNif.trim()
        || null;

      datos.fechaNacimiento =
        valores.fechaNacimiento
        || null;

    }

    if (
      usuarioActual.rol ===
      'VENDEDOR'
    ) {

      datos.dniNif =
        valores.dniNif.trim()
        || null;

    }

    if (
      usuarioActual.rol ===
      'ADMIN'
    ) {

      datos.sede =
        valores.sede.trim()
        || null;

    }

    this.mensajeFormulario.set('');

    this.usuariosService
      .modificarUsuario(
        usuarioActual.id,
        datos
      )
      .subscribe({

        next: (respuesta) => {

          this.usuario.set(
            respuesta
          );

          this.modoEdicion.set(
            false
          );

          window.alert(
            'Los datos del usuario se han actualizado correctamente.'
          );

        },

        error: (
          error: HttpErrorResponse
        ) => {

          if (error.status === 400) {

            this.mensajeFormulario.set(
              error.error?.error ||
              'Los datos introducidos no son válidos.'
            );

            return;
          }

          if (error.status === 404) {

            this.mensajeFormulario.set(
              'No se ha encontrado el usuario.'
            );

            return;
          }

          if (
            error.status === 401 ||
            error.status === 403
          ) {

            this.mensajeFormulario.set(
              'No tienes permisos para modificar este usuario.'
            );

            return;
          }

          if (error.status === 0) {

            this.mensajeFormulario.set(
              'No se pudo conectar con el servicio.'
            );

            return;
          }

          this.mensajeFormulario.set(
            'No se pudieron guardar los cambios.'
          );

        }

      });

  }

  eliminarAdministrador(
    id: string,
    nombre: string
  ): void {

    const seguro =
      window.confirm(
        `¿Estás seguro de que deseas eliminar al administrador ${nombre}?\n\nEsta es una acción destructiva y no se puede deshacer.`
      );

    if (!seguro) {
      return;
    }

    this.administradorService
      .eliminarAdministrador(id)
      .subscribe({

        next: () => {

          void this.router.navigate([
            '/admin/administradores'
          ]);

        },

        error: (
          error: HttpErrorResponse
        ) => {

          const mensajeBackend =
            error.error?.error;

          window.alert(
            mensajeBackend ||
            'No se pudo eliminar el administrador. Inténtalo de nuevo.'
          );

        }

      });

  }

  private mensajeParaError(
    error: HttpErrorResponse
  ): string {

    if (error.status === 404) {

      return 'No se ha encontrado el usuario solicitado.';

    }

    if (
      error.status === 401 ||
      error.status === 403
    ) {

      return 'No tienes permisos para consultar este usuario.';

    }

    if (error.status === 0) {

      return 'No se pudo conectar con el servicio. Inténtalo de nuevo.';

    }

    return 'No se pudo cargar la información del usuario.';

  }

}