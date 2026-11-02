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
import { Categorias } from './pages/categorias/categorias';
import { Administradores } from './pages/admin/administradores';
import { CrearAdministrador } from './pages/admin/crear-administrador';
import { Perfil } from './pages/perfil/perfil';
import { autenticadoGuard } from './pages/login/autenticado.guard';
import { Carrito } from './pages/carrito/carrito';
import { Checkout } from './pages/checkout/checkout';

import { Direcciones } from './pages/direcciones/direcciones';
import { Pagos } from './pages/pagos/pagos'; // <--- IMPORTACIÓN NUEVA

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
    path: 'perfil',
    component: Perfil,
    canActivate: [autenticadoGuard]
  },
  {
    path: 'perfil/direcciones',
    component: Direcciones,
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE' }
  },
  // ---> NUEVA RUTA DE MÉTODOS DE PAGO <---
  {
    path: 'perfil/pagos',
    component: Pagos,
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE' }
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
    path: 'admin/categorias',
    component: Categorias,
    canActivate: [rolGuard],
    data: { rol: 'ADMIN' }
  },
  {
    path: 'admin/administradores',
    component: Administradores,
    canActivate: [rolGuard],
    data: { rol: 'ADMIN' }
  },
  {
    path: 'admin/administradores/nuevo',
    component: CrearAdministrador,
    canActivate: [rolGuard],
    data: { rol: 'ADMIN' }
  },
  {
    path: 'cliente/carrito',
    component: Carrito,
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE' }
  },
  {
    path: 'cliente/checkout',
    component: Checkout,
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE' }
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
