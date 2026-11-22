# Recorrido crítico y cierre del MVP (PUL-32)

Esta página reúne la prueba de humo del recorrido de usuario, los límites de
permisos y RLS, y la verificación de accesibilidad. PUL-111 extiende el
recorrido de PUL-32 con el flujo de contacto en Angular.

## Recorrido crítico

```text
registro → onboarding → Descubrir → interés → solicitud → conversación → aviso leído
```

| Paso | Contrato | Superficie actual |
| --- | --- | --- |
| Registro | Supabase Auth `signup`/`token`; `GET /api/v1/me` | Interfaz y API |
| Onboarding | `PATCH /api/v1/me`, `PUT /api/v1/me/preferences` con `completeOnboarding` | Interfaz y API |
| Publicación | `POST /api/v1/projects`, `publication-request`, revisión en `/api/v1/admin/project-reviews` | Interfaz y API |
| Descubrir | Feed autenticado, búsqueda y detalle público, coincidencia de oportunidades | Interfaz y API |
| Decisión | Guardados y `GET`, `PUT`, `DELETE /api/v1/me/project-feedback/{projectId}` | Interfaz y API |
| Conexión | Creación en el proyecto, `GET /api/v1/connection-intents/{inbox}`, `POST .../{accept,decline,withdraw}` | Detalle y bandeja de **Solicitudes** |
| Mensaje | `GET`, `POST /api/v1/conversations/{conversationId}/messages` | Conversación enlazada desde **Solicitudes** y **Notificaciones** |
| Notificación | `GET /api/v1/me/notifications`, `POST .../{notificationId}/read` | Bandeja, contador y enlace al contexto |

## Pruebas de humo

### `pnpm smoke:critical-path` — recorrido completo por API

[`scripts/verify-critical-path.mjs`](../../scripts/verify-critical-path.mjs)
ejecuta el recorrido contra un entorno local ya levantado (Compose, backend
con `pnpm backend:run`). No reinicia ni borra la base. Requiere Node 24 y
Docker para la promoción temporal de la cuenta revisora.

```sh
pnpm smoke:critical-path
```

Qué hace, en orden:

1. Comprueba `/api/health`, readiness y la salud de Auth.
2. Registra cuentas *owner*, *interesada* y *externa* con correos únicos; valida
   `GET /api/v1/me` y los rechazos `401` sin token o con token inválido.
3. Completa el onboarding de *owner* e *interesada* con el primer sector activo
   y la etapa `MVP`.
4. Crea un proyecto `DRAFT` y comprueba que, en `DRAFT` y en `PENDING_REVIEW`, no
   tiene detalle público (por id, slug ni sin sesión), no aparece en la
   búsqueda ni en el feed, no se puede guardar ni contactar, y que una cuenta
   sin membresía no puede editarlo ni pedir su publicación.
5. Registra una cuenta *revisora*, comprueba que sin rol de plataforma recibe
   `403`, la promueve temporalmente a `MODERATOR` y aprueba el proyecto. El
   propietario no puede aprobar su propio proyecto.
6. Abre una oportunidad y comprueba el enlace público, el feed, los filtros
   combinados, la paginación y la coincidencia por ID de oportunidad. La
   coincidencia usa preferencias y ubicación y muestra la habilidad faltante.
7. Guarda el proyecto y registra, lee y borra interés y descarte. Comprueba
   que otra cuenta solo accede a su propia decisión.
8. Envía la solicitud de conexión, rechaza un duplicado (`409`) e impide que la
   acepten el remitente o una cuenta externa (`403`). El *owner* la acepta y ambas
   cuentas leen el mismo ID de conversación desde su bandeja. Verifica el
   rechazo, el aviso de rechazo y el retiro por el remitente.
9. Intercambia dos mensajes, comprueba el orden del historial, el `404` para una
   cuenta externa, el `400` ante un remitente enviado por el cliente y las
   notificaciones `NEW_MESSAGE`. Lee de nuevo el estado de la notificación y
   comprueba que una oportunidad cerrada ya no recibe solicitudes.
10. Consulta PostgREST (`/rest/v1`) sin sesión y con sesión de usuario: las
    tablas de producto devuelven `401`/`403`; `project_members` solo devuelve las
    membresías propias.
11. Archiva el proyecto temporal, comprueba que deja de ser visible y que el
    historial de mensajes sigue disponible para sus participantes.

Al terminar, incluso si falla, archiva el proyecto y devuelve la cuenta
revisora a `USER`. Las cuentas de prueba quedan en la base local.

| Variable | Valor por defecto | Uso |
| --- | --- | --- |
| `PULSO_SMOKE_API_URL` | `http://localhost:8080` | Backend |
| `PULSO_SMOKE_SUPABASE_URL` | `http://127.0.0.1:54321` | Gateway de Auth y PostgREST |
| `PULSO_SMOKE_SUPABASE_PUBLISHABLE_KEY` | `frontend/.env.development` o su plantilla | Clave pública de Auth |
| `PULSO_SMOKE_DB_MODE` | `compose` | `compose` (`docker compose exec db`), `cli` (`docker exec`) o `skip` |
| `PULSO_SMOKE_DB_CONTAINER` | `supabase_db_pulso` | Contenedor para el modo `cli`, incluso con un entorno aislado de Compose |
| `PULSO_SMOKE_REVIEWER_EMAIL` / `PULSO_SMOKE_REVIEWER_PASSWORD` | vacío | Cuenta `MODERATOR`/`ADMIN` existente; obligatoria con `skip` |
| `PULSO_SMOKE_EMAIL_DOMAIN` | `example.test` | Dominio de los correos temporales |
| `PULSO_SMOKE_FRONTEND_URL` | vacío | Activa el recorrido adicional de Angular en un frontend ya levantado |
| `PULSO_SMOKE_CDP_PORT` | `9222` | Puerto de depuración del navegador de prueba |

Con la Supabase CLI (`pnpm db:up`) usa `PULSO_SMOKE_DB_MODE=cli` y la clave que
muestra `pnpm db:status`. El paso 10 requiere la migración
`20261121200000_restrict_project_member_grants.sql`; con Compose se aplica con
`pnpm db:compose:seed`.

### Navegador con dos cuentas (PUL-111)

Levanta Angular contra el mismo entorno de prueba y usa un perfil temporal de
Chromium con depuración remota. Las URLs del frontend, Spring y Auth deben
coincidir con la configuración de ese entorno.

```sh
chromium --headless --remote-debugging-port=9222 --user-data-dir="$(mktemp -d)" about:blank
```

```sh
PULSO_SMOKE_FRONTEND_URL=http://localhost:4200 pnpm smoke:critical-path
```

La cuenta que se registra completa el onboarding, aplica los filtros, revisa
los factores de coincidencia y registra, recarga y borra sus decisiones. Envía
una solicitud a la oportunidad y otra al proyecto y prueba el duplicado.
El *owner* acepta una y rechaza la otra. Las dos cuentas abren la misma
conversación desde sus entradas persistentes. El *owner* lee el mensaje y
comprueba el estado de su aviso después de marcarlo como leído y recargar.
El remitente también retira una solicitud pendiente y comprueba su estado al
recargar. El modo con acceso al contenedor vincula una etiqueta de catálogo
al proyecto de prueba para verificar ese filtro. El modo `skip` omite la
asociación de la etiqueta.

#### Evidencia de PUL-111

Validación del 22 de noviembre de 2026 en un entorno Compose aislado, con
Spring y Supabase Auth locales.

- `pnpm --filter @pulso/frontend test` — 304 pruebas con Node 24. Incluye
  filtros, adaptadores HTTP, solicitudes, mensajes y notificaciones.
- `pnpm backend:test` — 929 pruebas con Java 21 y Testcontainers, sin
  omisiones. Incluye permisos por membresía, contexto público y acceso a
  conversaciones con sesiones distintas.
- `pnpm smoke:critical-path` con `PULSO_SMOKE_FRONTEND_URL` — 172 comprobaciones.
  Chromium recorrió el flujo con dos sesiones, teclado a 1280×900 y 320×568,
  y verificó la recarga de solicitudes, conversación y aviso leído. La
  cuenta ajena recibió `404` al leer y al enviar un mensaje.
- `pnpm build` — build Angular correcto con aviso de presupuesto inicial
  de 500 kB. El paquete inicial pesó 620,71 kB.
- `pnpm backend:build` — JAR generado. Gradle reutilizó la última ejecución
  de pruebas.

### `pnpm smoke:verify` — entorno Supabase CLI

[`scripts/verify-browser-smoke.mjs`](../../scripts/verify-browser-smoke.mjs) se
ejecuta dentro de `pnpm smoke:verify` (destructivo: reinicia la base de la CLI).
Recorre en Chrome el registro, la sesión persistida tras recargar, `/api/v1/me`,
la intención del onboarding, el feed con bearer token, el interés persistido en
el backend y el cierre de sesión desde **Perfil**.

## Permisos, RLS y visibilidad

El navegador usa Supabase solo para Auth; los datos de producto pasan por la API
y el backend autoriza con el JWT y las membresías persistidas. La base aplica una
segunda barrera, verificada por
[`13_mvp_access_boundaries.sql`](../../database/supabase/tests/database/13_mvp_access_boundaries.sql)
(`pnpm db:test`):

- Toda tabla de `public` tiene RLS activado.
- `anon` no tiene privilegios directos sobre ninguna tabla de `public`.
- `authenticated` solo puede leer `profiles`, `profile_preferences`,
  `project_members`, `sectors`, `skills` y `tags`, cada una con política de
  lectura; solo escribe columnas de su perfil y de sus preferencias.
- Proyectos, oportunidades, solicitudes, conversaciones, mensajes,
  notificaciones, reportes y auditoría devuelven `permission denied` en acceso
  directo, también para el propietario de un proyecto.
- Si una concesión futura expusiera `projects`, RLS seguiría ocultando los
  proyectos no publicados, incluso a su propietario.

La revisión encontró que `project_members` conservaba los privilegios por defecto
de Supabase (`INSERT`, `UPDATE`, `DELETE`, `TRUNCATE` para `anon` y
`authenticated`). RLS ya bloqueaba las escrituras por PostgREST, pero
`TRUNCATE` no está sujeto a RLS. La migración
[`20261121200000_restrict_project_member_grants.sql`](../../database/supabase/migrations/20261121200000_restrict_project_member_grants.sql)
deja solo el `SELECT` de `authenticated` que usa la política de membresías
propias. El backend se conecta con su credencial de servidor y no cambia.

## Accesibilidad

Revisión de la versión compilada con Chromium, axe-core 4.10 (reglas WCAG 2.1 A/AA
y buenas prácticas) y recorrido con `Tab`, en 1280×800 y 320×640, con datos de
prueba simulados para backend y Auth.

| Pantalla | Teclado y foco | Nombres accesibles | Carga / error |
| --- | --- | --- | --- |
| Acceso | Orden lógico; foco visible | Campos con `<label>`; pista y error asociados con `aria-describedby` | Mensajes de estado y error |
| Onboarding | Opciones con `aria-pressed`; foco visible | Nivel de habilidad con etiqueta propia | `role="status"` al cargar; `role="alert"` y reintento al fallar |
| Discovery | Botones Descartar/Guardar/Me interesa como alternativa al gesto, con atajos de teclado | Acciones con nombre accesible | `aria-busy` y `role="status"` al cargar; `role="alert"` y **Reintentar** al fallar |
| Guardados, Solicitudes, Perfil | Foco visible en todos los controles | Sin controles sin nombre | Estados de carga, vacío y error explícitos |
| Detalle de proyecto | El diálogo recibe el foco, lo contiene, cierra con `Escape` y lo devuelve | `aria-modal` y título asociado | Carga, no disponible y error con reintento |

Correcciones:

- En 320 px las etiquetas de la barra inferior se superponían (por ejemplo,
  «Solicitudes» sobre «Guardados»). Ahora cada etiqueta se ajusta con guion
  dentro de su botón.
- La tarjeta de discovery declaraba `role="group"` sobre un `<article>`, un rol
  no permitido para ese elemento. Se quitó el rol.

En PUL-32, axe no informó infracciones en las pantallas simuladas revisadas.
El recorrido de PUL-111 usa Chromium con Spring y Supabase Auth reales e
incluye la selección de etiqueta y el formulario por teclado en escritorio,
y el envío de un mensaje con `Tab` y `Enter` en móvil. Sigue pendiente una
revisión con lector de pantalla.

## Documentación alineada

Se comparó el esquema obtenido al aplicar todas las migraciones con
[`pulso.dbml`](../../database/pulso.dbml) y el
[diagrama de clases](../diagrams/pulso-class-diagram.mmd): las 24 tablas, sus
columnas, los valores de cada restricción `CHECK` y todas las claves foráneas,
incluidas las compuestas. Se corrigieron dos diferencias en el DBML:
`projects.city` es obligatorio y `projects.created_by_profile_id` admite nulos
solo para filas seed del sistema. El diagrama coincide con las tablas y columnas
que muestra. También se revisaron los enlaces relativos y las anclas de
`README.md`, `CONTEXT.md`, `AGENTS.md`, `database/README.md` y `docs/`.

[`docs/stack.md`](../stack.md) describía el estado inicial del repositorio
(orquestación solo con la CLI, interacciones locales, dos proyectos seed) y se
actualizó al estado actual.

## Fuera del MVP

La [especificación](../especificacion-general.md#27-alcance-funcional-objetivo)
describe el alcance objetivo del producto. Esta versión no incluye:

- recuperación de contraseña y gestión de cuenta;
- silenciado, sanciones, panel administrativo completo y analítica;
- verificación de identidad u organizaciones;
- rate limiting y observabilidad de producción.
