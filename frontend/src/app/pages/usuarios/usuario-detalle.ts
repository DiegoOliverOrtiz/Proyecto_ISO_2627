import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { UsuarioAdministracion, UsuariosService } from './usuarios.service';

@Component({
  selector: 'app-usuario-detalle',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="detalle-page">
      <header class="topbar"><a class="brand" routerLink="/admin">EsiBuy</a><span>Gestión / Usuarios / Detalle</span><a routerLink="/admin/usuarios">Volver al listado</a></header>
      <div class="content">
      <a class="back-link" routerLink="/admin/usuarios">← Volver al listado</a>
      @if (cargando()) { <p class="status">Cargando información del usuario...</p> }
      @else if (mensajeError()) { <p class="status error" role="alert">{{ mensajeError() }}</p> }
      @else if (usuario(); as datos) {
        <header class="page-header"><p class="eyebrow">Ficha administrativa</p><h1>{{ datos.nombre }} {{ datos.apellidos }}</h1><p>Información útil para la gestión de la cuenta.</p></header>
        <section class="detail-card"><div class="status-row"><span>Estado de la cuenta</span><strong>{{ datos.estado }}</strong></div><dl>
          <div><dt>Email</dt><dd>{{ datos.email }}</dd></div>
          <div><dt>Teléfono</dt><dd>{{ datos.telefono || 'No indicado' }}</dd></div>
          <div><dt>Estado</dt><dd>{{ datos.estado }}</dd></div>
          <div><dt>Rol</dt><dd>{{ datos.rol }}</dd></div>
          @if (datos.sede) { <div><dt>Sede</dt><dd>{{ datos.sede }}</dd></div> }
          @if (datos.fechaIncorporacion) { <div><dt>Fecha de incorporación</dt><dd>{{ datos.fechaIncorporacion }}</dd></div> }
          @if (datos.esPremium !== null) { <div><dt>Cuenta premium</dt><dd>{{ datos.esPremium ? 'Sí' : 'No' }}</dd></div> }
          @if (datos.nombreComercial) { <div><dt>Nombre comercial</dt><dd>{{ datos.nombreComercial }}</dd></div> }
        </dl></section>
      }
      </div>
    </main>
  `,
  styles: `
    :host { display: block; min-height: 100vh; background: #F7F9F8; color: #333; }
    .detalle-page { min-height: 100vh; }
    .topbar { display: flex; justify-content: space-between; align-items: center; gap: 1rem; padding: 1rem 2rem; border-bottom: 1px solid #D9E3DE; color: #536A60; background: #E8EEE9; font-size: .9rem; }
    .topbar a { color: #255B4D; font-weight: 700; text-decoration: none; }
    .brand { font-size: 1.2rem; }
    .content { width: min(820px, calc(100% - 2rem)); margin: 0 auto; padding: 2rem 0 3rem; }
    .back-link { color: #255B4D; font-weight: 700; text-decoration: none; }
    .page-header { margin: 1.75rem 0 1.5rem; }
    .eyebrow { margin: 0 0 .35rem; color: #D36B54; font-size: .8rem; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
    h1 { margin: 0; color: #173042; font-size: clamp(1.8rem, 4vw, 2.8rem); line-height: 1.08; }
    .page-header p:not(.eyebrow) { color: #536A60; }
    .detail-card { padding: 1.25rem; border: 1px solid #D9E3DE; background: #fff; box-shadow: 0 5px 18px rgba(37, 91, 77, .05); }
    .status-row { display: flex; justify-content: space-between; gap: 1rem; margin-bottom: 1rem; padding-bottom: 1rem; border-bottom: 1px solid #E5EDE7; color: #536A60; }
    .status-row strong { color: #255B4D; }
    dl { display: grid; gap: .75rem; margin: 0; }
    dl div { display: grid; grid-template-columns: minmax(150px, .7fr) 1fr; gap: 1rem; padding: 1rem; border: 1px solid #E5EDE7; background: #F7F9F8; }
    dt { color: #536A60; font-weight: 700; }
    dd { margin: 0; overflow-wrap: anywhere; }
    .status { padding: 2rem; border: 1px solid #D9E3DE; background: #fff; text-align: center; }
    .error { border-color: #D36B54; color: #A63C28; }
    @media (max-width: 560px) { .topbar { align-items: flex-start; flex-direction: column; padding: .8rem 1rem; } dl div { grid-template-columns: 1fr; gap: .35rem; } }
  `
})
export class UsuarioDetalle implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly usuariosService = inject(UsuariosService);
  readonly usuario = signal<UsuarioAdministracion | null>(null);
  readonly cargando = signal(true);
  readonly mensajeError = signal('');

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.cargando.set(false);
      this.mensajeError.set('No se ha indicado el usuario que quieres consultar.');
      return;
    }
    this.usuariosService.consultarUsuario(id).subscribe({
      next: (respuesta) => { this.usuario.set(respuesta); this.cargando.set(false); },
      error: (error: HttpErrorResponse) => {
        this.cargando.set(false);
        this.mensajeError.set(this.mensajeParaError(error));
      }
    });
  }

  private mensajeParaError(error: HttpErrorResponse): string {
    if (error.status === 404) return 'No se ha encontrado el usuario solicitado.';
    if (error.status === 401 || error.status === 403) return 'No tienes permisos para consultar este usuario.';
    if (error.status === 0) return 'No se pudo conectar con el servicio. Inténtalo de nuevo.';
    return 'No se pudo cargar la información del usuario.';
  }
}