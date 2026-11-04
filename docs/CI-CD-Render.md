# Integración y despliegue continuo de ESIBuy (CI/CD)

## 1. Objetivo

El objetivo de esta tarea es implementar un proceso de integración continua (CI) y despliegue continuo (CD) para ESIBuy, automatizando la compilación, las pruebas y la publicación de la aplicación.

Siguiendo las recomendaciones del profesor, la configuración se ha probado inicialmente sobre la rama `release/sprint2`, evitando realizar pruebas de integración y despliegue directamente en `main`.

Una vez validado el funcionamiento del proceso, se podrá incorporar la configuración definitiva a la rama principal del proyecto.

## 2. Tecnologías utilizadas

| Tecnología | Función |
|---|---|
| Git y GitHub | Control de versiones y gestión de Pull Requests |
| Azure Pipelines | Integración continua y ejecución de pruebas |
| GitHub Actions / CodeQL | Análisis de seguridad del código |
| Docker | Empaquetado y ejecución de la aplicación |
| Angular | Frontend de ESIBuy |
| Spring Boot 4 y Java 21 | Backend y API REST |
| MongoDB Atlas | Persistencia de datos e imágenes de productos |
| Render | Alojamiento y despliegue continuo de la aplicación |

## 3. Arquitectura del despliegue

ESIBuy se despliega como una única aplicación Docker que contiene el frontend Angular y el backend Spring Boot.

El archivo `Dockerfile`, situado en la raíz del repositorio, utiliza una construcción multietapa:

1. **Compilación del frontend:** se utiliza Node.js 24 para instalar las dependencias y generar la versión de producción de Angular.
2. **Compilación del backend:** se utiliza Maven con Java 21. Los archivos compilados de Angular se incorporan a los recursos estáticos de Spring Boot.
3. **Imagen de ejecución:** se utiliza una imagen de Java 21 JRE que ejecuta el JAR resultante.

De esta manera, Spring Boot sirve tanto la API REST como la aplicación Angular. No es necesario mantener un servidor independiente de Angular en Render.

El frontend utiliza rutas relativas para comunicarse con el backend, de modo que ambos comparten el mismo origen.

### Navegación y rutas Angular

Se ha incorporado `SpaController.java` para permitir acceder directamente a las rutas de Angular y recargarlas sin obtener errores 404.

También se ha adaptado la configuración de Spring Security para permitir las peticiones de navegación a las páginas HTML, manteniendo la autorización de los endpoints protegidos de la API.

Durante las pruebas se comprobó el funcionamiento de rutas como `/login`, `/vendedor`, `/vendedor/pedidos` y `/listas`.

### Persistencia de imágenes

Inicialmente, las imágenes de los productos se almacenaban en el sistema de archivos local.

Como el almacenamiento local del servicio gratuito de Render no es persistente, se implementó un sistema de almacenamiento en MongoDB Atlas.

Mediante la variable `APP_PRODUCTOS_IMAGENES_ALMACENAMIENTO`, la aplicación puede utilizar almacenamiento local o MongoDB.

En el despliegue de Render se utiliza el modo `mongo`, por lo que las imágenes nuevas se conservan aunque se reinicie el contenedor.

## 4. Integración continua con Azure Pipelines

El proyecto utiliza el archivo `azure-pipelines.yml` para definir el proceso de integración continua.

El pipeline se ejecuta al recibir cambios en las ramas configuradas y al abrir o actualizar las Pull Requests correspondientes, según la configuración de los disparadores.

Se ha añadido soporte para las ramas `release/*`, permitiendo validar los cambios dirigidos a `release/sprint2`.

Las comprobaciones incluyen:

- Compilación y ejecución de tests del backend con Maven.
- Compilación y ejecución de tests del frontend con Angular.
- Análisis de secretos mediante Gitleaks.
- Comprobación del resultado global del pipeline.

Adicionalmente, GitHub Actions ejecuta análisis de seguridad mediante CodeQL.

### Agente de ejecución

Azure Pipelines utiliza un agente autoalojado denominado `wsl-ubuntu`, perteneciente al grupo `Default`.

Este agente debe estar conectado para procesar los trabajos. Si el equipo que lo aloja está apagado o el agente está desconectado, las ejecuciones permanecen en cola.

Esta dependencia afecta a la disponibilidad de la integración continua, pero no al funcionamiento de ESIBuy una vez desplegada en Render.

### Diferencia entre CI y protección de ramas

La ejecución de los checks y la obligación de superarlos antes de hacer merge son mecanismos diferentes.

Azure Pipelines ejecuta las pruebas y publica sus resultados en GitHub. Para impedir que se fusionen cambios con comprobaciones pendientes o fallidas, es necesario disponer de reglas de protección de ramas con checks obligatorios.

La configuración de estas reglas debe ser revisada por el administrador del repositorio.

## 5. Configuración del despliegue en Render

Se ha creado un servicio web llamado `esibuy-sprint2`, conectado al repositorio de GitHub del proyecto.

La configuración utilizada es:

| Parámetro | Configuración |
|---|---|
| Nombre | `esibuy-sprint2` |
| Tipo de servicio | Web Service |
| Entorno | Docker |
| Rama | `release/sprint2` |
| Región | Frankfurt (EU Central) |
| Root Directory | Raíz del repositorio |
| Dockerfile Path | `./Dockerfile` |
| Plan | Free |
| Health Check Path | `/` |
| Auto-Deploy | `After CI Checks Pass` |

**URL del despliegue de pruebas:**

https://esibuy-sprint2.onrender.com

Render construye la imagen Docker a partir del código de la rama configurada y ejecuta el backend Spring Boot, que también sirve el frontend.

### Configuración HTTPS

Render proporciona HTTPS en la conexión pública y redirige internamente las peticiones al servidor HTTP del contenedor.

Por eso, en Render se desactiva el SSL interno de Spring Boot, manteniendo las cookies de sesión seguras para las conexiones HTTPS del navegador.

El despliegue no necesita incluir los certificados locales utilizados durante el desarrollo.

### Variables de entorno

Las variables necesarias se configuran en el panel de Render y no se incluyen en el repositorio.

| Variable | Finalidad |
|---|---|
| `PORT` | Puerto interno utilizado por Spring Boot |
| `SSL_ENABLED` | Controla el SSL interno del backend |
| `SESSION_COOKIE_SECURE` | Configura la marca Secure de las cookies |
| `FRONTEND_ORIGIN` | Define el origen público permitido |
| `APP_PRODUCTOS_IMAGENES_ALMACENAMIENTO` | Selecciona MongoDB como almacenamiento de imágenes |
| `MONGODB_URI` | Cadena privada de conexión a MongoDB Atlas |
| `TOTP_ENCRYPTION_KEY` | Clave de cifrado de secretos TOTP |
| `TOTP_MIGRATE_KEY` | Control de migración de la clave TOTP |
| `JAVA_TOOL_OPTIONS` | Ajustes de memoria para la JVM |

Las credenciales, claves de cifrado y enlaces privados de despliegue deben mantenerse fuera de GitHub.

### Acceso a MongoDB Atlas

Para que Spring Boot pudiera conectarse desde Render, se añadieron a la lista de acceso de MongoDB Atlas los rangos de direcciones de salida indicados en el panel del servicio.

De esta forma, se permitió la conexión desde Render sin autorizar indiscriminadamente todas las direcciones de Internet.

Los rangos deben revisarse si cambia la región, la configuración de red o la infraestructura del servicio.

## 6. Funcionamiento del despliegue continuo

Render se ha configurado con la opción `After CI Checks Pass`.

El flujo probado es el siguiente:

1. Se desarrolla un cambio en una rama `feature`.
2. Se abre una Pull Request hacia `release/sprint2`.
3. Azure Pipelines y GitHub Actions ejecutan las comprobaciones.
4. Una vez validadas, se fusiona la Pull Request.
5. El nuevo commit de `release/sprint2` activa otra ejecución de CI.
6. Render detecta el cambio y, tras las comprobaciones reconocidas, inicia automáticamente un nuevo despliegue.
7. La nueva versión queda disponible en la misma URL pública.

Esto permite automatizar la publicación sin ejecutar manualmente comandos de Docker ni iniciar los servidores desde los ordenadores de los desarrolladores.

La prueba realizada confirma el despliegue automático después de una ejecución correcta de CI. No se ha realizado todavía una prueba específica con un check fallido para verificar el bloqueo del despliegue en ese caso.

## 7. Pruebas realizadas

### 7.1. Construcción y ejecución local

Antes de desplegar, se construyó la imagen Docker y se ejecutó localmente.

Se comprobó que Spring Boot servía Angular, que el inicio de sesión funcionaba y que los endpoints protegidos mantenían sus restricciones de acceso.

También se ejecutaron correctamente los tests del backend y frontend.

### 7.2. Primera integración en release

Se creó la **Pull Request #30** desde `feature/CI-CD-Render` hacia `release/sprint2`.

La PR incorporaba la configuración Docker, los ajustes para Render, la persistencia de imágenes en MongoDB y los cambios integrados previamente desde `develop`.

Las comprobaciones de la PR terminaron correctamente.

Tras realizar el merge, Azure Pipelines ejecutó también las pruebas sobre el nuevo commit de `release/sprint2`.

Posteriormente se realizó el primer despliegue de ESIBuy en Render.

### 7.3. Validación de la aplicación pública

Una vez solucionada la conectividad con MongoDB Atlas, se comprobó el funcionamiento de:

- Acceso a la aplicación mediante HTTPS.
- Inicio de sesión como vendedor.
- Visualización del catálogo y de imágenes almacenadas en MongoDB.
- Recarga de páginas Angular sin errores 404.
- Acceso a la pantalla de pedidos del vendedor.
- Cierre de sesión y protección de rutas autenticadas.

Estas comprobaciones confirmaron el funcionamiento básico de la aplicación integrada en Render.

### 7.4. Prueba del despliegue automático

Se creó la **Pull Request #34** para comprobar el despliegue continuo mediante un cambio inocuo en `frontend/src/index.html`.

Los ocho checks de la PR terminaron correctamente.

Después del merge en `release/sprint2`, Azure Pipelines ejecutó las comprobaciones sobre el commit integrado.

Render detectó automáticamente el nuevo commit y realizó el despliegue sin utilizar la opción Manual Deploy.

| Evidencia | Identificador |
|---|---|
| PR de integración inicial | `#30` |
| PR de prueba de CD | `#34` |
| Pipeline posterior al segundo merge | `#20261104.12` |
| Commit desplegado automáticamente | `7357c728` |
| Resultado en Render | `Live` |
| Disparador en Render | `Auto-Deploy` |

Con esta prueba quedó demostrado el funcionamiento del despliegue automático sobre la rama de pruebas `release/sprint2`.

## 8. Limitaciones y consideraciones

### Agente autoalojado

El proceso de CI depende de la disponibilidad del agente `wsl-ubuntu`. Si permanece desconectado, los trabajos no pueden ejecutarse hasta que vuelva a conectarse.

### Plan gratuito de Render

La instancia gratuita puede suspenderse después de un período de inactividad. Al recibir una nueva visita, Render la reactiva automáticamente, lo que puede provocar una carga inicial más lenta.

La memoria disponible también es limitada, por lo que es importante vigilar el consumo del backend.

### Seguridad de credenciales

Se deben rotar las credenciales de MongoDB que hayan podido quedar expuestas durante las pruebas.

La rotación de la clave de cifrado TOTP requiere especial cuidado para conservar el acceso de los usuarios que ya tengan configurada la autenticación de dos factores.

### Validación de errores

Se ha comprobado el despliegue automático con los checks correctos, pero queda pendiente validar explícitamente que un fallo de CI impide publicar un nuevo commit.

## 9. Integración definitiva y versionado

Durante el desarrollo del Sprint 2, se ha utilizado la rama `release/sprint2` para realizar las pruebas de integración y despliegue continuo sin afectar a `main`.

Una vez comprobado que el proceso funciona correctamente, se ha consultado al profesor sobre la configuración definitiva. Su recomendación es que, al finalizar el Sprint 2, el despliegue automático se realice desde `main`, utilizando la última versión cerrada y etiquetada del proyecto.

Por tanto, el procedimiento previsto es:

1. Finalizar las historias de usuario e integrarlas en `develop`.
2. Incorporar los cambios a `release/sprint2` y validar la aplicación.
3. Fusionar la versión aprobada en `main` mediante una Pull Request.
4. Crear la etiqueta correspondiente a la versión definitiva del Sprint 2, siguiendo el criterio utilizado en Sprint 1 con `v1.0.0`.
5. Adaptar la configuración de CI/CD para que Render despliegue automáticamente la versión validada de `main`.

De esta forma, `release/sprint2` se utiliza para las pruebas y `main` queda como referencia de las versiones oficiales del proyecto.

**Nota técnica:** el despliegue automático basado en commits de `main` no se activa necesariamente al crear una etiqueta Git. Si se requiere que sea la creación del tag la que desencadene el despliegue, será necesario configurar explícitamente ese disparador.

## 10. Conclusión

Se ha implementado y probado un proceso de integración y despliegue continuo para ESIBuy utilizando GitHub, Azure Pipelines, Docker y Render.

El sistema permite compilar y validar automáticamente los cambios y publicar nuevas versiones desde `release/sprint2` sin tener que ejecutar manualmente los servidores de desarrollo.

También se ha comprobado el acceso público a la aplicación mediante HTTPS, su conexión con MongoDB Atlas, la autenticación de usuarios y la persistencia de imágenes.

El proceso queda preparado como base para la integración definitiva de CI/CD en `main`, una vez completada y validada la entrega del Sprint 2.