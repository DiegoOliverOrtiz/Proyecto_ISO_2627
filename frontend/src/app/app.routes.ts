import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { Login } from './pages/login/login';
import { Register } from './pages/register/register';
import { RecuperacionContrasena } from './pages/recuperacion-contrasena/recuperacion-contrasena';
import { PanelAdministrador } from './pages/paneles/administrador';
import { PanelCliente } from './pages/paneles/cliente';
import { PanelVendedor } from './pages/paneles/vendedor';
import { rolGuard } from './pages/login/rol.guard';
import { UsuarioDetalle } from './pages/usuarios/usuario-detalle';
import { Usuarios } from './pages/usuarios/usuarios';

export const routes: Routes = [
  {
    path: '',
    component: Home
  },
  {
    path: 'login',
    component: Login
  },
  {
    path: 'registro',
    component: Register
  },
  {
    path: 'recuperar-contrasena',
    component: RecuperacionContrasena
  },
  {
    path: 'admin',
    component: PanelAdministrador,
    canActivate: [rolGuard],
    data: { rol: 'ADMIN' }
  },
  {
    path: 'admin/usuarios',
    component: Usuarios,
    canActivate: [rolGuard],
    data: { rol: 'ADMIN' }
  },
  {
    path: 'admin/usuarios/:id',
    component: UsuarioDetalle,
    canActivate: [rolGuard],
    data: { rol: 'ADMIN' }
  },
  {
    path: 'cliente',
    component: PanelCliente,
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE' }
  },
  {
    path: 'vendedor',
    component: PanelVendedor,
    canActivate: [rolGuard],
    data: { rol: 'VENDEDOR' }
  }
];