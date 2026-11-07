# Informe de vulnerabilidades — Sprint 2

**Proyecto:** ESIBuy (grupo 2026-g01). **Tarea:** T-G.19. **Fecha del informe:** 7 de noviembre de 2026.
**Código analizado:** rama `develop` (commit `16ab6b8a`) más la corrección de dependencias de este informe (rama `feature/T-G19-informe-vulnerabilidades`).

## 1. Objetivo y requisito

El enunciado pide usar al menos una herramienta de análisis de código asistida por IA para detectar vulnerabilidades, integrarla en el flujo de desarrollo y documentar, de las vulnerabilidades principales, la fecha del análisis, la vulnerabilidad, su severidad, la corrección propuesta y la decisión del equipo. Las recomendaciones de la IA no se pueden aceptar de forma automática: cada cambio se revisa, se entiende y se corrige.

El objetivo de cierre del sprint es **cero vulnerabilidades críticas o bloqueantes abiertas**.

## 2. Herramientas

### 2.1 Herramientas de análisis asistido por IA

| Herramienta | Tipo | Integración en el flujo | Quién la usa |
|---|---|---|---|
| **GitHub CodeQL** con **Copilot Autofix** | Análisis estático (SAST) de Java y de JavaScript/TypeScript, suite `security-extended`. Autofix propone con IA una corrección para cada alerta. | GitHub Actions (`.github/workflows/codeql.yml`): cada push y PR a `main`, `develop`, `feature/**` y `release/**`, y un análisis semanal (lunes, 03:00 UTC). Los resultados aparecen en la pestaña *Security* y en cada PR. | Automático, para todo el equipo |
| **Claude** (Claude Code) | Asistente de IA generativa. Revisión de seguridad del código y de los cambios de cada PR, y comprobación de la documentación de seguridad contra el código. | Revisiones bajo demanda durante el desarrollo; sus hallazgos se registran y el equipo decide cada uno (apartado 4). | *[integrante]* |
| **ChatGPT** | Asistente de IA generativa. | *[completar: para qué se usó y en qué tareas]* | *[integrantes]* |

### 2.2 Herramientas complementarias (sin IA)

| Herramienta | Qué analiza | Dónde |
|---|---|---|
| **Dependabot** | Vulnerabilidades conocidas de las dependencias npm y Maven | GitHub (rama por defecto, `main`) |
| **npm audit** | Dependencias del frontend; el pipeline falla con severidad **crítica** | Azure Pipelines |
| **OWASP Dependency-Check 12.1.3** | Dependencias Maven del backend contra la NVD y OSS Index; el pipeline falla con **CVSS ≥ 9** | Azure Pipelines |
| **Gitleaks 8.24.3** | Secretos subidos por error al repositorio | Azure Pipelines (`SecretScan`) |
| **SonarQube** | Calidad, vulnerabilidades y *security hotspots* (`2026-g01-backend`, `2026-g01-frontend`) | Azure Pipelines y en local (`seralu4.esi.uclm.es`) |

## 3. Metodología: ninguna propuesta se acepta sin revisión

1. **Detección.** La herramienta (CodeQL, un asistente de IA o una de las complementarias) señala un posible problema.
2. **Revisión humana.** Un integrante lo comprueba en el código: si es real, en qué condiciones se explota y qué alcance tiene. Un falso positivo se documenta con el motivo.
3. **Decisión del equipo.** Corregir, aceptar el riesgo con justificación o descartarlo como falso positivo. Las que afectan a requisitos de seguridad se deciden con el responsable de seguridad.
4. **Corrección.** Siempre en una rama propia y una *pull request* revisada por otro integrante. La propuesta de la IA es un punto de partida, no se copia: el cambio se adapta al código, lleva tests y debe pasar el CI.
5. **Verificación.** La herramienta se vuelve a ejecutar y el hallazgo se cierra con la referencia al commit o a la PR.

## 4. Registro de hallazgos

Severidad: **Crítica**, **Alta**, **Media**, **Baja** (la que asigna la herramienta; para los asistentes de IA, la valoración del equipo con criterios de CVSS).

### 4.1 CodeQL

| Fecha | Resultado |
|---|---|
| 18/10/2026 – 07/11/2026 | **652 análisis** (Java: 120 reglas; JS/TS: 103 reglas; CodeQL 2.27.4 en los últimos). **0 alertas** en todos ellos, abiertas, corregidas o descartadas. Copilot Autofix no ha tenido ninguna alerta que corregir. |
| 07/11/2026 | El análisis de la PR \#56 (`refs/pull/56/merge`) y el de `develop` tras integrarla dan 0 alertas. |

Que CodeQL no encuentre nada es coherente con las medidas del proyecto (consultas parametrizadas de Spring Data, sin `$where`, búsquedas como texto literal, escape de Angular, sin `innerHTML`). Por eso los hallazgos principales del sprint vienen de las revisiones con asistentes de IA y de las herramientas complementarias.

### 4.2 Revisiones con asistentes de IA (Claude)

| ID | Fecha | Vulnerabilidad | Severidad | Corrección propuesta | Decisión del equipo | Estado |
|---|---|---|---|---|---|---|
| IA-01 | 05/11/2026 | El registro revela si un correo está registrado (respondía `409` solo en ese caso): permite enumerar cuentas. *Lo observó el equipo y se confirmó en el código con Claude.* | Alta | Responder igual en los dos casos y no crear duplicados. | Corregir (lo pidió el responsable de seguridad). La primera versión respondía `400`; el equipo la cambió a `202` con el mismo mensaje que un alta correcta. | ✅ PR \#40 y commit `20ddbdee` |
| IA-02 | 06/11/2026 | El tiempo de respuesta de «He olvidado mi contraseña» revela si el correo existe (el envío a Brevo era síncrono). | Alta | Enviar el correo en segundo plano. | Corregir. | ✅ PR \#41 (`@Async`) |
| IA-03 | 06/11/2026 | Sin límite de solicitudes de recuperación: permite saturar el buzón de un usuario y gastar el cupo de Brevo. | Alta | Limitar por correo y por IP, respondiendo igual por encima del límite. | Corregir: 3 por correo y 10 por IP cada 15 minutos. | ✅ PR \#41 (`LimiteSolicitudesRecuperacion`) |
| IA-04 | 06/11/2026 | El token de recuperación se queda en la URL (historial del navegador, cabecera `Referer`). | Media | Guardarlo en memoria y quitarlo de la barra de direcciones. | Corregir. | ✅ PR \#41 |
| IA-05 | 06/11/2026 | Los cambios de contraseña no quedan en la auditoría de seguridad. | Media | Registrar solicitud y restablecimiento sin datos personales. | Corregir. | ✅ PR \#41 (`AuditoriaService`) |
| IA-06 | 06/11/2026 | Se puede pegar o autocompletar la contraseña en el campo de confirmación. *Lo observó el equipo y se confirmó en el código con Claude.* | Baja | Bloquear `paste`, `copy`, `cut`, `drop` y el autocompletado en la confirmación. | Corregir en registro y recuperación (lo pidió seguridad). Bloquearlo también en el primer campo sigue sin decidir: el NIST (SP 800-63B) recomienda permitir pegar para no penalizar los gestores de contraseñas. | ✅ PR \#40 y \#41 |
| IA-07 | 06/11/2026 | La política de contraseñas no comprueba los datos personales. | Media | Rechazar contraseñas con nombre, apellidos, correo, fecha de nacimiento, teléfono o DNI. | Corregir. | 🟡 Corregida en registro y alta de administradores (`8f972e21`); la recuperación no le pasa los datos personales (IA-12) |
| IA-08 | 06/11/2026 | Faltan tests de la recuperación: validación `400` en el controlador y cuentas bloqueadas o sin activar. | Baja | Añadir los tests y decidir el comportamiento con cuentas bloqueadas. | Pendiente de decidir el comportamiento con cuentas bloqueadas. | 🟡 El límite (IA-03) ya tiene tests; faltan los demás |
| IA-09 | 06/11/2026 | El 2FA de vendedor y administrador era opcional, aunque la documentación del sprint lo exige. | Alta | Obligar al alta del TOTP y del tercer factor para esos roles. | Corregir según la documentación. | ✅ PR \#52 y \#55 |
| IA-10 | 07/11/2026 | CodeQL no analizaba `release/**` (comilla mal puesta en `codeql.yml`) y Azure ya no se ejecutaba en `release/*`: la rama que se despliega quedaba sin análisis. | Media | Corregir el patrón y volver a incluir `release/*` en Azure. | Corregir; `feature/*` sigue sin lanzar Azure. | ✅ PR \#56 |
| IA-11 | 07/11/2026 | Un cliente desactiva el tercer factor (`PUT /usuarios/3fa/preferencia`) sin presentar ningún código; con una sesión robada se podría quitar. | Media | Exigir un código TOTP para desactivarlo, como ya se hace con el segundo factor. | *[pendiente de decisión del equipo]* | ⛔ Abierta |
| IA-12 | 07/11/2026 | La recuperación de contraseña llama a `PasswordPolicy.validate` sin los datos personales del usuario. | Media | Pasarle nombre, apellidos, correo, teléfono y, si existen, DNI y fecha de nacimiento. | *[pendiente de decisión del equipo]* | ⛔ Abierta |
| IA-13 | 07/11/2026 | La duración máxima absoluta de la sesión (8 h) es la misma para el administrador, y el requisito pide que sea más estricta. | Baja | Un límite menor para `ROLE_ADMIN` en `SesionAbsolutaFilter`. | *[pendiente de decisión del equipo]* | ⛔ Abierta |
| IA-14 | 07/11/2026 | `docs/CI-CD-Render.md` no documenta `APP_SECURITY_PASSWORD_PEPPER` ni `APP_SECURITY_EMAIL_OTP_KEY`: sin el primero Render no arranca y sin el segundo vendedores y administradores no pueden entrar. | Media (disponibilidad) | Documentar las variables y darlas de alta en Render. | *[pendiente de decisión del equipo]* | ⛔ Abierta |

### 4.3 Revisiones con asistentes de IA (ChatGPT)

*[Apartado para los integrantes que han usado ChatGPT. Por cada hallazgo principal: fecha, vulnerabilidad, severidad, corrección propuesta, decisión del equipo y commit o PR.]*

| ID | Fecha | Vulnerabilidad | Severidad | Corrección propuesta | Decisión del equipo | Estado |
|---|---|---|---|---|---|---|
| GPT-01 | | | | | | |

### 4.4 Herramientas complementarias

| ID | Fecha | Herramienta | Vulnerabilidad | Severidad | Corrección propuesta | Decisión del equipo | Estado |
|---|---|---|---|---|---|---|---|
| H-01 | 01/11/2026 | Revisión del equipo | Una clave de cifrado TOTP de desarrollo llegó a estar publicada. | Alta | Cambiar la clave y rechazar la antigua. | Corregir: `TotpCryptoService` rechaza la clave expuesta por su huella SHA-256 y obliga a usar una nueva; `TotpKeyMigration` re-cifra los secretos. | ✅ `f4e73238` |
| H-02 | 07/11/2026 | SonarQube (`java:S5852`) | Expresiones regulares con *backtracking* en `PasswordPolicy` y `RecuperacionContrasenaService`: posible denegación de servicio (ReDoS) con una entrada larga. | Media | Sustituirlas por código lineal. | Corregir. | ✅ `e9a8fd02` (PR \#53) |
| H-03 | 07/11/2026 | SonarQube (`java:S4790`) | «Hash débil» por el uso de HMAC-SHA1 en `TotpService`. | — | — | **Falso positivo**: RFC 6238 define TOTP con HMAC-SHA1 y es lo que usan las aplicaciones de autenticación. El HMAC-SHA1 no tiene los problemas de colisión de SHA-1 como hash. La regla se desactivó en el perfil del grupo; cualquier MD5 o SHA-1 nuevo se revisa a mano. | Cerrada |
| H-04 | 07/11/2026 | Gitleaks | Posible secreto en `AuthController`: la constante `SESION_3FA_SECRETO`. | — | Renombrar la constante. | **Falso positivo**: es el nombre de un atributo de sesión, no una credencial. Se renombró a `SESION_3FA_TOTP_CIFRADO` para que no vuelva a saltar. | ✅ `ad290efd` |
| H-05 | 07/11/2026 | npm audit / Dependabot | 12 vulnerabilidades en dependencias del frontend (detalle en el apartado 5). | 7 altas, 4 medias, 1 baja | Actualizar a las versiones corregidas. | Corregir todas, aunque solo 2 llegan al código de producción. | ✅ `83879d42` |
| H-06 | 07/11/2026 | OWASP Dependency-Check | Dependencias Maven del backend. | Ninguna con CVSS ≥ 9 | — | El umbral del pipeline no se ha superado en ningún build. | Sin hallazgos bloqueantes |

## 5. Detalle de las dependencias del frontend (H-05)

Antes de la corrección, `npm audit` en `develop` daba **12 vulnerabilidades: 0 críticas, 7 altas, 4 moderadas y 1 baja**. Solo dos afectaban a dependencias de producción (`npm audit --omit=dev`), y ninguna era explotable en ESIBuy:

| Paquete | Severidad | Aviso | ¿Afecta a ESIBuy? |
|---|---|---|---|
| `@angular/router` 22.2.0 | Alta | GHSA-57xq-rjx2-v5xh, GHSA-62vg-58rm-qff7: DoS en el renderizado en servidor (SSR) | No: la aplicación no usa SSR |
| `zod` 4.6.5 | Alta | GHSA-mrhx-c7xc-m3w5: consumo de memoria al validar colecciones enormes | No directamente: llega por `@angular/forms` y `@angular/cli`; el código propio no lo usa |
| `vite` 8.3.2 (con `launch-editor`) | Alta | GHSA-587h-7wmj-h5pm y otros cuatro | Solo en el servidor de desarrollo |
| `undici` 8.11.2 | Alta | GHSA-qrxm-fqhp-jwvm, GHSA-2m5v-pch6-74hx, GHSA-xjj6-f33q-929c, GHSA-9fm3-97px-rjf6 | Solo en los tests (`jsdom`) |
| `source-map-js` | Alta | GHSA-68fv-2mgg-jv7q | Solo en la compilación |
| `@modelcontextprotocol/server` | Alta | GHSA-jm63-cq47-j5jp, GHSA-rvq5-wwqv-78pq | Solo en `@angular/cli` |
| `@vitest/mocker`, `vitest`, `@vitest/coverage-v8` | Media | GHSA-qxg6-89mg-rqv8, GHSA-xp26-3454-mjgp | Solo en los tests |
| `fast-uri` | Media | GHSA-wjr9-j795-jxcr | Solo en la compilación |

**Decisión:** corregirlas todas, aunque las de desarrollo no lleguen a producción, porque se ejecutan en los ordenadores de los integrantes y en el agente de CI.

**Corrección** (`83879d42`): Angular 22.2.0 → 22.2.5 (framework) y 22.2.6 (`@angular/build`, `@angular/cli`), y Vitest 5.0.2 → 5.1.0. Son versiones de parche, sin cambios de código. Las demás dependencias se actualizaron solas por ser transitivas.

**Verificación:** `npm audit` → **0 vulnerabilidades**; `npm ci`, `npm test -- --watch=false --coverage` (505 tests, 39 ficheros) y `npm run build` en verde.

Dependabot sigue mostrando 19 alertas porque analiza la rama por defecto (`main`), que va por detrás de `develop`. Se cerrarán al integrar `develop` en `main` al final del sprint.

## 6. Estado final

| Severidad | Abiertas | Notas |
|---|---|---|
| Crítica | **0** | Ninguna herramienta ha detectado vulnerabilidades críticas en el sprint. |
| Bloqueante | **0** | SonarQube: 0 *issues* y Quality Gate en verde en backend y frontend (PR \#53 y \#54). |
| Alta | **0** | Todas las altas (IA-01, IA-02, IA-03, IA-09, H-01 y H-05) están corregidas. |
| Media | 3 | IA-11, IA-12 e IA-14, pendientes de decisión del equipo. |
| Baja | 2 | IA-08 (en parte) e IA-13, pendientes de decisión del equipo. |

**Cumple el objetivo del sprint: cero vulnerabilidades críticas o bloqueantes abiertas.**

## 7. Cómo reproducir los análisis

- **CodeQL:** pestaña *Security → Code scanning* del repositorio, o `gh api repos/DiegoOliverOrtiz/Proyecto_ISO_2627/code-scanning/alerts`.
- **Dependencias del frontend:** desde `frontend/`, `npm audit` y `npm audit --omit=dev`.
- **Dependencias del backend:** la etapa *OWASP Dependency-Check* de Azure Pipelines (necesita `NVD_API_KEY`).
- **Secretos:** la etapa *Secret Scan* de Azure Pipelines.
- **Tests de seguridad del backend:** desde `backend/`, `./mvnw test -Dtest='!BackendApplicationTests'` (1191 tests, 0 fallos, 14 omitidos el 07/11/2026).
