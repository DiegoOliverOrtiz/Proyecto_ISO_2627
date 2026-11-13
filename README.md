# ESIBuy — Entrega grupo 2026-g01

Proyecto Integrado de Ingeniería del Software · UCLM-ESI · curso 2026-27 · grupo `2026-g01`.

ESIBuy es una plataforma web de comercio electrónico (clientes, vendedores y administradores).
Monorepo con dos aplicaciones:

- `backend/` — API REST en Java 21 + Spring Boot (MongoDB Atlas).
- `frontend/` — SPA en Angular 22.

## 1. Contenido del paquete

| Elemento | Dónde |
|---|---|
| Proyecto software (Sprint 2) | Este directorio. Incluye `backend/.env` con la configuración necesaria para ejecutarlo. |
| Acceso a GitHub | Apartado 5 |
| Acceso a Azure DevOps | Apartado 6 |
| Aplicación desplegada | https://esibuy-sprint2.onrender.com (el plan gratuito de Render tarda ~1 min en despertar tras un rato sin uso) |

## 2. Requisitos

- Java 21 (el `mvnw` incluido descarga Maven).
- Node.js 22 o superior y npm.
- Windows con PowerShell y OpenSSL (el de Git para Windows sirve) para `npm run setup:https`.
- Conexión a Internet (MongoDB Atlas y Brevo).

## 3. Configuración (`backend/.env`)

El fichero `backend/.env` ya va incluido en este paquete; no se versiona en GitHub porque contiene secretos.
Variables que usa (solo nombres):

- `MONGODB_URI` — conexión a Atlas (base de datos `esibuy`).
- `TOTP_ENCRYPTION_KEY` — clave AES-256 (Base64) de los secretos TOTP.
- `PERSONAL_DATA_ENCRYPTION_KEY` — clave AES-256 (Base64) para DNI/NIF y teléfono.
- `APP_SECURITY_PASSWORD_PEPPER` — *pepper* de las contraseñas (≥ 32 caracteres).
- `APP_SECURITY_EMAIL_OTP_KEY` — clave HMAC del tercer factor por correo.
- `BREVO_API_KEY`, `BREVO_FROM_EMAIL`, `BREVO_FROM_NAME`, `APP_FRONTEND_URL` — envío de correos.
- `VIRUSTOTAL_API_KEY`, `VIRUSTOTAL_ENABLED`, `VIRUSTOTAL_FAIL_CLOSED` — antivirus de imágenes (opcional).
- `FRONTEND_ORIGIN`, `SSL_KEY_STORE`, `SSL_KEY_STORE_PASSWORD` — HTTPS local; los escribe `npm run setup:https`.

## 4. Ejecución en local

El backend debe arrancarse desde `backend/`, porque busca el `.env` relativo al directorio de trabajo.

```bash
# 1) Frontend: instalar y generar certificados HTTPS de desarrollo (una sola vez)
cd frontend
npm ci
npm run setup:https        # puede pedir aceptar la instalación del certificado en Windows

# 2) Backend
cd ../backend
./mvnw spring-boot:run     # en Windows: mvnw.cmd spring-boot:run  → https://localhost:8443

# 3) Frontend (otra terminal)
cd frontend
npm start                  # https://localhost:4200
```

Si el navegador rechaza la conexión, abrir una vez `https://localhost:8443` y aceptar la excepción.

### Tests

```bash
cd backend  && ./mvnw test -Dtest='!BackendApplicationTests'     # 1197 tests (14 omitidos)
cd frontend && npm test -- --watch=false                          # 509 tests
cd frontend && npm run build
```

### Seguridad de acceso (importante al probar)

- Los **vendedores y administradores** tienen 2FA (TOTP) y 3FA (código por correo) obligatorios: necesitan una app de autenticación y acceso al correo asociado a la cuenta.
- Los **clientes** pueden activar el 2FA/3FA opcionalmente.

## 5. Acceso a GitHub

- Repositorio: https://github.com/DiegoOliverOrtiz/Proyecto_ISO_2627
- Ramas: `develop` (integración del Sprint 2), `release/sprint2` (despliegue en Render), `main`.
- Análisis de seguridad: CodeQL en GitHub Actions (`.github/workflows/codeql.yml`), sin alertas abiertas.

## 6. Acceso a Azure DevOps

- Organización/proyecto: https://dev.azure.com/BlancaAM/ESIBuy_Equipo1
- Pipeline: `azure-pipelines.yml` (build backend con OWASP Dependency-Check, build frontend con cobertura ≥ 60 %, Gitleaks, SonarQube y notificación).
- Nota: el pipeline usa un agente propio (`wsl-ubuntu`). Si está apagado, las ejecuciones quedan en cola; los resultados de los builds ya ejecutados siguen visibles.
- SonarQube (`seralu4.esi.uclm.es`): claves `2026-g01-backend` y `2026-g01-frontend`, Quality Gate OK y 0 *issues*.

## 7. Estructura

```
backend/    API REST (Spring Boot)        frontend/   SPA (Angular)
scripts/    configurar-https.ps1          Dockerfile  imagen única para Render
azure-pipelines.yml   .github/workflows/codeql.yml
```