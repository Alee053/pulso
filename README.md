# Pulso

Pulso permite descubrir y evaluar proyectos del ecosistema emprendedor. Este
monorepo contiene la aplicación web, la API y el historial de la base de datos.
Esta guía describe cómo preparar un equipo nuevo y trabajar **completamente en
local**, sin pedir credenciales de un proyecto Supabase alojado.

Accesos rápidos: [primer arranque](#developer-onboarding),
[uso diario](#uso-diario-y-actualización-del-código),
[validación](#comandos-de-validación) y [problemas frecuentes](#problemas-frecuentes).

| Parte | Ubicación | Tecnología | Cómo se ejecuta |
| --- | --- | --- | --- |
| Frontend | `frontend/` | Angular 22, TypeScript 6, Tailwind CSS 4, RxJS 7 | `pnpm dev` |
| Backend | `backend/` | Java 21, Spring Boot 4.1, Gradle Wrapper 9.4.1 | `pnpm backend:run` |
| Servicios locales | `compose.yaml`, `docker/supabase/` | PostgreSQL 17 y servicios Supabase | `docker compose up -d` |
| Esquema y datos iniciales | `database/supabase/` | Migraciones SQL y seed | Servicio `migrator` de Compose |

Docker ejecuta PostgreSQL, Auth, REST, Realtime, Storage, Studio, Mailpit y su
gateway. **Frontend y backend corren en la máquina anfitriona**, en terminales
separadas. El navegador consume los datos de producto por la API `/api/v1` del
backend y usa Supabase directamente solo para Auth. Spring valida los JWT y
controla la autorización.

## Requisitos

Instala estas herramientas antes de continuar:

| Herramienta | Versión / requisito | Para qué se necesita |
| --- | --- | --- |
| Git | Disponible en la terminal y acceso al repositorio | Clonar y actualizar el código |
| Node.js | **24 LTS, mínimo 24.15.0**, recomendado por `.nvmrc`; también se admite 26.x | Frontend y herramientas del workspace; Node 25 no está admitido |
| pnpm | **11.17.0**, declarado en `package.json` | Instalar dependencias y ejecutar scripts |
| JDK | **21**, con `java` y `javac`; `JAVA_HOME` debe apuntar al JDK si está definido | Compilar y ejecutar Spring Boot |
| Docker | Engine/Desktop iniciado, contenedores Linux y Compose v2 | Servicios locales y pruebas con Testcontainers |
| Shell POSIX (`sh`) | En Linux/macOS, la del sistema; en Windows, Git Bash de Git for Windows | `backend:run`, `backend:test`, `backend:build` y verificaciones |

En Windows puedes usar PowerShell, siempre que encuentre `sh` de Git Bash en
`PATH`. Si no lo encuentra, abre Git Bash y ejecuta allí los comandos `pnpm`;
comprueba que también encuentre Node, pnpm y Java. Evita mezclar herramientas
de Windows y WSL en la misma instalación del workspace.

No hace falta instalar Angular CLI, Gradle, PostgreSQL ni Supabase CLI
globalmente. Angular y Supabase CLI **2.116.0** son dependencias del workspace;
el backend descarga Gradle con su Wrapper y las dependencias Java desde Maven
Central al primer arranque. Necesitas conexión a Internet para esas descargas,
las dependencias pnpm y las imágenes Docker.

Para las verificaciones opcionales se necesita además `curl`. El smoke test
completo requiere **Python 3** (`python3` o `python`) y **Chrome/Chromium**;
no hacen falta para ejecutar `pnpm dev` y `pnpm backend:run`.

<a id="developer-onboarding"></a>

## Primer arranque

### 1. Clonar y seleccionar las herramientas

```sh
git clone https://github.com/Alee053/pulso.git
cd pulso
```

Todos los comandos siguientes se ejecutan desde esta raíz, salvo indicación
contraria. Usa tu gestor de Node para seleccionar la versión 24. Con `nvm` de
Linux/macOS, `.nvmrc` permite hacerlo así:

```sh
nvm install
nvm use
```

En Windows selecciona una versión 24.x instalada que cumpla el mínimo mediante
tu gestor de Node; la sintaxis de `nvm` para Linux/macOS no es la de nvm-windows.
Si todavía no tienes pnpm, después de seleccionar Node instala la versión del
repositorio:

```sh
npm install --global pnpm@11.17.0
```

Si ya administras pnpm mediante Corepack, úsalo para activar la versión de
`packageManager` en lugar de instalar otro ejecutable. Comprueba las herramientas
en la misma terminal desde la que trabajarás:

```sh
git --version
node --version
pnpm --version
java --version
javac --version
sh -c 'echo sh disponible'
docker info
docker compose version
```

`docker info` debe conectar con el motor, no solo mostrar la versión del cliente.
En Docker Desktop espera a que el motor Linux esté listo.

### 2. Instalar las dependencias del workspace

```sh
pnpm install --frozen-lockfile
```

Esto instala las dependencias de la raíz y de `frontend/` usando
`pnpm-lock.yaml`. No necesitas ejecutar otra instalación dentro de `frontend/`.
No sustituyas pnpm por npm/yarn ni regeneres el lockfile para resolver un error
de versión. Las dependencias Java se descargan por separado con Gradle al
ejecutar `pnpm backend:run`, `pnpm backend:test` o `pnpm backend:build`.

### 3. Crear los archivos de configuración local

Solo hacen falta **dos archivos** para el entorno Compose inicial:

| Archivo local | Plantilla versionada | Consumidor |
| --- | --- | --- |
| `.env` | [`.env.example`](.env.example) | Runner del backend y Docker Compose |
| `frontend/.env.development` | [`frontend/.env.development.example`](frontend/.env.development.example) | Scripts de arranque/build de Angular |

Ejecuta **una** de las siguientes variantes solo si esos archivos no existen.
Si ya tienes configuración propia, compárala con las plantillas sin sobrescribirla.
Los comandos normalizan los finales de línea de `.env` a **LF**: el lector actual
del backend rechaza CRLF, comillas y espacios en los valores. Guarda ese archivo
como UTF-8 sin BOM y usa entradas `KEY=value` sin comillas ni comentarios al final.

**PowerShell (Windows):**

```powershell
$utf8 = [System.Text.UTF8Encoding]::new($false)
$localEnv = [System.IO.File]::ReadAllText("$PWD/.env.example").Replace("`r`n", "`n")
[System.IO.File]::WriteAllText("$PWD/.env", $localEnv, $utf8)
Copy-Item frontend/.env.development.example frontend/.env.development
```

**Bash / Git Bash / Linux / macOS:**

```sh
tr -d '\r' < .env.example > .env
cp frontend/.env.development.example frontend/.env.development
```

Las plantillas ya contienen valores compatibles con el Compose del repositorio:
PostgreSQL local, Auth con una clave de prueba HS256 y correo local mediante
Mailpit. No necesitas un proyecto remoto, `supabase login`, vincular un proyecto
ni copiar secretos de otro integrante. Estos valores son exclusivamente locales.
Los `.env` están ignorados por Git; nunca subas credenciales reales.

**Selección del archivo del backend:** `pnpm backend:run` carga el primer archivo
disponible en este orden, sin combinar los archivos:

1. El indicado por `PULSO_BACKEND_ENV_FILE`, si definiste esa variable.
2. `backend/.env.development`.
3. `backend/.env`.
4. `.env` de la raíz.

Las variables ya definidas en la terminal tienen prioridad sobre el archivo.
En un clon nuevo basta el `.env` raíz; si existen archivos bajo `backend/`,
verifica que no apunten a otra base. Para seleccionar explícitamente la raíz,
antes de arrancar el backend usa `$env:PULSO_BACKEND_ENV_FILE = '.env'` en
PowerShell o `export PULSO_BACKEND_ENV_FILE=.env` en Bash.

### 4. Levantar los servicios Docker

```sh
docker compose up -d
docker compose ps -a
docker compose logs --no-color migrator
```

La primera ejecución puede tardar por la descarga e inicialización de imágenes.
El servicio `migrator` espera a PostgreSQL y Auth, aplica las migraciones pendientes
en orden y ejecuta el [seed](database/supabase/seed.sql). Al terminar debe mostrar
`Migraciones y seed local listos.` y quedar en **Exited (0)**; es normal porque
es una tarea de una sola ejecución. Los servicios permanentes deben seguir
levantados; los que tienen healthcheck deben llegar a `healthy`.

El seed incorpora catálogos y dos proyectos publicados de ejemplo, **EcoPack
Bolivia** y **AndeSolar**. No crea una cuenta de usuario para iniciar sesión.
Spring Boot no crea las tablas: si falla el migrador, revisa sus logs antes de
continuar.

**No ejecutes `pnpm db:up` junto con Compose.** Ese comando arranca otro entorno
mediante Supabase CLI y utiliza los mismos puertos predeterminados.

### 5. Levantar el backend — terminal dedicada

```sh
pnpm backend:run
```

Espera al mensaje de Spring Boot que confirma el arranque. La API escucha en
`http://localhost:8080`. La primera ejecución descarga Gradle y dependencias Java;
mantén esta terminal abierta. Un cambio en Java requiere reiniciar este comando.

### 6. Levantar el frontend — otra terminal

```sh
pnpm dev
```

Abre **<http://localhost:4200>** cuando Angular termine de compilar. Mantén también
esta terminal abierta. Angular recarga al modificar su código; si cambias el
archivo de entorno, reinicia `pnpm dev` para incorporar los nuevos valores.

### 7. Comprobar el entorno y crear tu cuenta

En **PowerShell**:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
Invoke-RestMethod 'http://localhost:8080/api/v1/startups?offset=0&limit=2'
```

En **Bash / Git Bash**:

```sh
curl --fail --show-error http://localhost:8080/api/health
curl --fail --show-error http://localhost:8080/actuator/health/readiness
curl --fail --show-error 'http://localhost:8080/api/v1/startups?offset=0&limit=2'
```

Resultados esperados:

- `/api/health`: `status: UP`, `service: pulso-backend`; solo confirma que la API vive.
- `/actuator/health/readiness`: `status: UP`; también comprueba PostgreSQL.
- `/api/v1/startups`: HTTP 200 con `data` y `meta`; en una base recién creada
  aparecen los dos proyectos del seed. Esta consulta pública no requiere sesión.
- En la aplicación, crea una cuenta desde el formulario de registro, completa
  el onboarding e inicia sesión. El Compose local tiene confirmación automática
  de correo; los usuarios y sesiones son independientes de cualquier entorno remoto.
- Los correos locales, incluidas las invitaciones de equipo, se consultan en
  **<http://localhost:54324>** (Mailpit); no llegan a una bandeja de correo externa.

## URLs y puertos locales

| Servicio | Dirección / puerto del anfitrión | Uso |
| --- | --- | --- |
| Frontend | <http://localhost:4200> | Aplicación web |
| Backend | <http://localhost:8080> | API; liveness en `/api/health` |
| Salud del backend | <http://localhost:8080/actuator/health> | Dependencias; probes `/liveness` y `/readiness` |
| Supabase API/Auth | <http://127.0.0.1:54321> | Auth en `/auth/v1`; no es la interfaz de Studio |
| PostgreSQL | `127.0.0.1:54322` | Usa la conexión local definida en la plantilla del backend |
| Supabase Studio | <http://localhost:54323> | Inspeccionar datos del entorno local |
| Mailpit web | <http://localhost:54324> | Ver correos capturados |
| Mailpit SMTP | `localhost:54325` | Envío de invitaciones desde Spring Boot; no es una URL web |

## Uso diario y actualización del código

Una vez completada la preparación, el arranque habitual es:

```sh
docker compose up -d
```

En una terminal ejecuta `pnpm backend:run` y en otra `pnpm dev`.
Después de actualizar tu rama, ejecuta `pnpm install --frozen-lockfile` si cambió
el lockfile. Si hay nuevas migraciones o cambios al seed, con Compose levantado:

```sh
pnpm db:compose:seed
```

Aunque el nombre diga `seed`, este comando ejecuta de nuevo el migrador:
**aplica migraciones pendientes y luego el seed**, sin resetear la base. El seed
es repetible y puede actualizar sus datos de ejemplo. Reinicia el backend si
cambió Java o su configuración.

Para detener el entorno, pulsa `Ctrl+C` en las terminales de frontend y backend:

```sh
docker compose down
```

Esto conserva los volúmenes de PostgreSQL y Storage. No uses `down -v` para
actualizar normalmente: borra ambos volúmenes. Solo si quieres descartar todos
los datos de tu entorno local y recrearlo desde cero:

```sh
docker compose down -v
docker compose up -d
```

Puedes ejecutar únicamente `pnpm dev` para trabajar en presentación, pero Auth
y los datos de producto requieren sus servicios. El catálogo muestra el error
si la API no responde; no sustituye la respuesta con datos ficticios.

## Configuración y cambios de puertos

El detalle de las variables soportadas por el runner está en
[`scripts/run-backend.sh`](scripts/run-backend.sh), y los valores de Spring en
[`application.yml`](backend/src/main/resources/application.yml).

| Configuración | Variables relevantes |
| --- | --- |
| Conexión PostgreSQL del backend | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |
| Puerto y logs del backend | `SERVER_PORT`, `PULSO_LOG_LEVEL` |
| Validación de Auth | `SUPABASE_JWT_ISSUER`, `SUPABASE_JWT_AUDIENCE`, `SUPABASE_JWT_JWK_SET_URI`, `SUPABASE_JWT_SECRET` |
| Orígenes permitidos del navegador | `CORS_ALLOWED_ORIGINS`, separados por coma, sin espacios |
| Correo de invitaciones | `SMTP_HOST`, `PULSO_SMTP_PORT` (o `SMTP_PORT`), `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS` |
| Enlaces y remitente de invitaciones | `PULSO_INVITATION_FRONTEND_URL`, `PULSO_INVITATION_FROM` |
| Frontend → backend | `PULSO_API_BASE_URL` en `frontend/.env.development`; URL base **sin** `/api/v1` |
| Frontend → Auth | `PULSO_SUPABASE_URL`, `PULSO_SUPABASE_PUBLISHABLE_KEY` en `frontend/.env.development` |

El frontend carga `frontend/.env.development` al ejecutar `dev`, `start`, `preview`
y `build`; las variables de la terminal prevalecen. Puedes seleccionar otro
archivo con `PULSO_FRONTEND_ENV_FILE` (usa una ruta absoluta para evitar confundir
la raíz del repositorio con el directorio de trabajo del frontend). Estos valores
se incorporan al código público del navegador: solo coloca la clave
publishable/anon. Sin esa clave, el gateway de Auth queda inactivo. El build
rechaza claves `service_role` y variables de servidor como contraseñas o secretos JWT.

Para evitar puertos ocupados, primero identifica qué servicio los usa. Si cambias
la configuración, mantén alineados ambos extremos:

- Backend: cambia `SERVER_PORT` y `PULSO_API_BASE_URL` del frontend.
- Frontend: usa `pnpm dev --port 4201`; actualiza `CORS_ALLOWED_ORIGINS`,
  `PULSO_INVITATION_FRONTEND_URL` y los redirects de Auth en Compose
  (`SITE_URL`, `ADDITIONAL_REDIRECT_URLS`).
- PostgreSQL: `PULSO_POSTGRES_PORT` de Compose y el puerto en `SPRING_DATASOURCE_URL`.
- Auth: `PULSO_SUPABASE_API_PORT`, `API_EXTERNAL_URL`, `SUPABASE_PUBLIC_URL`,
  `SUPABASE_JWT_ISSUER` y `PULSO_SUPABASE_URL` deben referirse al mismo entorno.
- Mailpit: `PULSO_MAILPIT_PORT` cambia la web; `PULSO_SMTP_PORT` cambia SMTP.

El runner del backend acepta una lista cerrada de variables. Si necesitas
variables exclusivas de Compose, como `PULSO_POSTGRES_PORT` o `POSTGRES_PASSWORD`,
usa un archivo separado para el backend y selecciónalo con
`PULSO_BACKEND_ENV_FILE`; no le pases el archivo de Compose con variables extra.
Cambiar la contraseña en un archivo no modifica la de una base ya inicializada.

## Comandos de validación

Estos comandos se ejecutan desde la raíz. No necesitas ejecutarlos todos para
trabajar a diario; elige los que cubran tu cambio.

| Comando | Qué comprueba / requisito |
| --- | --- |
| `pnpm --filter @pulso/frontend test` | Suite Angular/Vitest, sin levantar backend |
| `pnpm build` | Build de producción del frontend |
| `pnpm backend:test` | Tests Java; Docker debe estar iniciado para las pruebas JDBC con Testcontainers |
| `pnpm backend:build` | Build y tests del backend; JAR en `backend/build/libs/` |
| `pnpm backend:verify` | Con Compose y `.env` local: JDBC, catálogo seed, liveness/readiness con conexión disponible y caída simulada |
| `docker compose --env-file .env.example config --quiet` | Valida el archivo Compose con la plantilla, sin iniciar contenedores ni imprimir valores |

`backend:verify` inicia backends temporales en `18080` y `18081` y usa `55432`
como puerto de base deliberadamente no disponible. Necesita esos puertos libres
y `curl`; se pueden ajustar con `PULSO_VERIFY_BACKEND_PORT` y
`PULSO_VERIFY_UNAVAILABLE_DB_PORT`. Por defecto selecciona `.env` raíz; admite
otro archivo mediante `PULSO_VERIFY_BACKEND_ENV_FILE`. Detiene los procesos que
él mismo inicia. Las pruebas Testcontainers pueden omitir casos sin Docker:
revisa los resultados antes de interpretar una suite verde como cobertura JDBC.

### Supabase CLI: base de datos, Auth y smoke test

La CLI es un flujo alternativo para pruebas SQL, pgTAP y verificaciones de Auth.
**Detén Compose con `docker compose down` antes de usarlo**. Los volúmenes de
Compose se conservan; la CLI administra su propio entorno.

```sh
pnpm db:up
pnpm db:lint
pnpm db:test
pnpm db:verify
```

| Comando adicional | Alcance y efectos |
| --- | --- |
| `pnpm db:status` | Muestra URLs y claves locales de la CLI; no compartas su salida completa |
| `pnpm auth:verify` | Requiere la CLI levantada; crea un usuario local, verifica login, JWT en `/api/v1/me`, rechazos 401 y logout; backend temporal en `18082` (`PULSO_VERIFY_AUTH_PORT`) |
| `pnpm smoke:verify` | Instala con lockfile, levanta la CLI, **resetea su base local**, reinicia su gateway, ejecuta verificaciones/build y un recorrido real de navegador |
| `pnpm db:reset` | **Borra datos locales de la CLI** y reaplica migraciones/seed |
| `pnpm db:down` | Detiene el entorno de la CLI |
| `pnpm db:migration:new <nombre>` | Crea un archivo de migración para editar y revisar |

`auth:verify` y `db:verify` dependen del entorno administrado por la CLI; no
basta con tener Compose levantado. La CLI utiliza claves Auth ES256 propias:
las plantillas HS256 de Compose no sirven para conectar manualmente a ese otro
entorno. Si quieres levantar frontend/backend contra la CLI, consulta sus valores
con `pnpm db:status`, configura su URL/clave pública en el frontend y configura
el backend con su issuer, `SUPABASE_JWT_SECRET=` vacío y
`SUPABASE_JWT_JWK_SET_URI=http://127.0.0.1:54321/auth/v1/.well-known/jwks.json`
para los puertos predeterminados.

Ejecuta el smoke únicamente sobre una base local cuyos datos puedas descartar.
Necesita Python 3 y Chrome/Chromium además de las herramientas anteriores;
admite `PULSO_SMOKE_BROWSER` para indicar el ejecutable. Reserva los puertos
`4200`, `8080` y `9222`, ajustables con `PULSO_SMOKE_FRONTEND_PORT`,
`PULSO_SMOKE_BACKEND_PORT` y `PULSO_SMOKE_CDP_PORT`; detén tus servidores si los
ocupan. Al finalizar limpia archivos y procesos temporales y deja la CLI
levantada. Las verificaciones crean usuarios de prueba; cerrar su sesión y
limpiar temporales **no elimina esas cuentas de la base**.

Para volver al entorno cotidiano, ejecuta `pnpm db:down`, restaura la
configuración Compose si la cambiaste y ejecuta `docker compose up -d`.
El procedimiento detallado está en [`database/README.md`](database/README.md).
`pnpm db:push:dry-run` y `pnpm db:push` operan sobre un proyecto remoto vinculado;
no forman parte de la instalación local.

## Problemas frecuentes

| Síntoma | Qué revisar |
| --- | --- |
| `pnpm` no existe o falla el requisito de Node | Selecciona Node 24 compatible y pnpm 11.17.0; verifica las versiones en esa terminal. No uses Node 25. |
| `sh` no se reconoce en Windows | Instala Git for Windows y ejecuta desde Git Bash, o agrega su `sh` al `PATH`; reabre la terminal. |
| Java no se encuentra, `JAVA_HOME` es inválido o falta el compilador | Usa un JDK 21 completo; verifica `java --version`, `javac --version` y la ruta de `JAVA_HOME`. |
| Docker no conecta o falta `dockerDesktopLinuxEngine` | Inicia Docker Desktop con motor Linux y espera a que `docker info` funcione. |
| Puerto ocupado / `address already in use` | Revisa `docker ps` y tus terminales; detén la instancia que corresponda. No arranques Compose y `pnpm db:up` simultáneamente. |
| `migrator` queda en `Exited (1)` o falla un servicio dependiente | Lee `docker compose logs --tail 100 migrator db auth`; corrige el error y vuelve a ejecutar el migrador. `Exited (0)` es el resultado correcto. |
| `Set SPRING_DATASOURCE_PASSWORD` | Crea `.env` desde la plantilla y revisa qué archivo selecciona el runner y si la terminal tiene una variable vacía. |
| `Unsupported environment value syntax` / `Invalid .env entry` | Guarda el archivo del backend con LF, UTF-8 sin BOM y valores sin comillas, espacios ni comentarios al final. |
| `Unsupported variable in the selected environment file` | Usa solo las variables aceptadas por `run-backend.sh`; separa las exclusivas de Compose del archivo del backend. |
| Cambiaste `.env` pero el backend sigue usando otra base | Revisa `PULSO_BACKEND_ENV_FILE`, los archivos bajo `backend/` y las variables de la terminal; tienen prioridad sobre `.env` raíz. |
| `/api/health` da UP, pero readiness está DOWN o discovery devuelve 503 | Verifica PostgreSQL, credenciales y migrador; liveness por sí sola no demuestra conexión a la base. |
| Falla login o el backend responde 401 con sesión | Comprueba que frontend, issuer y claves del backend correspondan al mismo entorno; no mezcles Compose HS256 con CLI ES256. Reinicia Angular tras editar su entorno. |
| Error CORS | Usa el origen configurado (`localhost:4200` o `127.0.0.1:4200` por defecto); si cambiaste el puerto, actualiza `CORS_ALLOWED_ORIGINS` y reinicia el backend. |
| No aparece el correo de invitación | Revisa Mailpit en `54324`, el contenedor `mailpit` y SMTP del backend en `localhost:54325`. |
| Faltan tablas o el seed está desactualizado | Con Compose activo ejecuta `pnpm db:compose:seed` y revisa el resultado. Un reset destructivo no es el paso habitual. |
| `auth:verify` dice que Supabase no está disponible | Ese script exige el entorno de la CLI; sigue la sección anterior para cambiar de entorno. |
| Smoke no encuentra Python o Chrome | Comprueba `python3`/`python` y el navegador desde la shell que ejecuta el script; configura `PULSO_SMOKE_BROWSER` si hace falta. |

## Trabajo en equipo y documentación

Los estándares del repositorio están en [AGENTS.md](AGENTS.md) y los
[acuerdos de ingeniería](docs/engineering/working-agreements.md). Las ramas deben
incluir el identificador Linear, y los commits/PR siguen
`tipo(PUL-123): descripción` con el número real de la tarea.

El [workflow de PR](.github/workflows/tests.yml) ejecuta suites independientes de
frontend, backend y base de datos en Ubuntu, con Node de `.nvmrc`, pnpm de
`packageManager`, instalación con lockfile y Java 21. La suite de base usa su
propia instancia Supabase CLI. Esos checks no ejecutan el smoke del navegador
ni despliegan la aplicación.

- [Índice de documentación](docs/README.md) y [especificación de producto](docs/especificacion-general.md).
- [Vocabulario del producto](CONTEXT.md) y [arquitectura general](docs/architecture/overview.md).
- [Arquitectura frontend](docs/architecture/frontend.md) y [backend](docs/architecture/backend.md).
- [Contratos HTTP y ejemplos](docs/api.md).
- [Flujo de base de datos](database/README.md), [modelo DBML](database/pulso.dbml)
  y [diagrama de clases](docs/diagrams/pulso-class-diagram.mmd).
