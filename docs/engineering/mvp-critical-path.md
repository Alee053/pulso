# Recorrido crítico y cierre del MVP (PUL-32)

Esta página reúne la verificación transversal de la primera versión de Pulso:
la prueba de humo del recorrido de usuario, los límites de permisos y RLS, la
revisión de accesibilidad y la alineación de la documentación. No describe
funcionalidades nuevas; indica cómo comprobar las existentes y qué queda fuera
del MVP.

## Recorrido crítico

```text
registro → onboarding → discovery → interés → conexión → mensaje
```

| Paso | Contrato | Superficie actual |
| --- | --- | --- |
| Registro | Supabase Auth `signup`/`token`; `GET /api/v1/me` | Interfaz y API |
| Onboarding | `PATCH /api/v1/me`, `PUT /api/v1/me/preferences` con `completeOnboarding` | Interfaz y API |
| Publicación | `POST /api/v1/projects`, `publication-request`, revisión en `/api/v1/admin/project-reviews` | Interfaz y API |
| Discovery | `GET /api/v1/me/discovery-feed`, `GET /api/v1/projects/search`, `GET /api/v1/projects/{projectRef}` | Interfaz y API |
| Interés | `PUT /api/v1/me/project-feedback/{projectId}` con `INTERESTED` | Interfaz y API |
| Conexión | `POST /api/v1/projects/{projectId}/connection-intents`; `GET /api/v1/connection-intents/pending`; `POST .../accept` | Envío solo por API; bandeja y aceptación en **Solicitudes** |
| Mensaje | `POST`/`GET /api/v1/conversations/{conversationId}/messages`; `GET /api/v1/me/notifications` | Solo API |

La interfaz todavía no envía solicitudes de conexión ni muestra conversaciones
o notificaciones: el detalle de proyecto indica que el contacto está en vista
previa. Por eso el recorrido completo se valida contra la API y el tramo de
interfaz se valida hasta el interés.

## Pruebas de humo

### `pnpm smoke:critical-path` — recorrido completo por API

[`scripts/verify-critical-path.mjs`](../../scripts/verify-critical-path.mjs)
recorre los seis pasos contra un entorno local ya levantado (Compose, backend
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
6. Comprueba que el proyecto publicado aparece en el detalle, la búsqueda y el
   feed de la cuenta interesada, sin exponer la nota de revisión.
7. Marca interés y comprueba que la decisión es privada de cada cuenta.
8. Envía la solicitud de conexión, rechaza un duplicado (`409`), verifica la
   bandeja y la notificación del equipo, impide que la acepten el remitente o
   una cuenta externa (`403`) y la acepta con la cuenta *owner*.
9. Intercambia dos mensajes, comprueba el orden del historial, el `404` para una
   cuenta externa, el `400` ante un remitente enviado por el cliente y las
   notificaciones `NEW_MESSAGE` sin el texto del mensaje.
10. Consulta PostgREST (`/rest/v1`) sin sesión y con sesión de usuario: las
    tablas de producto devuelven `401`/`403`; `project_members` solo devuelve las
    membresías propias.
11. Archiva el proyecto temporal, comprueba que deja de ser visible y que el
    historial de mensajes sigue disponible para sus participantes.

Al terminar, incluso si falla, archiva el proyecto y devuelve la cuenta
revisora a `USER`. Las cuatro cuentas quedan en la base local, igual que con
`pnpm auth:verify`. El script no imprime tokens ni contraseñas.

| Variable | Valor por defecto | Uso |
| --- | --- | --- |
| `PULSO_SMOKE_API_URL` | `http://localhost:8080` | Backend |
| `PULSO_SMOKE_SUPABASE_URL` | `http://127.0.0.1:54321` | Gateway de Auth y PostgREST |
| `PULSO_SMOKE_SUPABASE_PUBLISHABLE_KEY` | `frontend/.env.development` o su plantilla | Clave pública de Auth |
| `PULSO_SMOKE_DB_MODE` | `compose` | `compose` (`docker compose exec db`), `cli` (`docker exec`) o `skip` |
| `PULSO_SMOKE_DB_CONTAINER` | `supabase_db_pulso` | Contenedor para el modo `cli` |
| `PULSO_SMOKE_REVIEWER_EMAIL` / `PULSO_SMOKE_REVIEWER_PASSWORD` | vacío | Cuenta `MODERATOR`/`ADMIN` existente; obligatoria con `skip` |
| `PULSO_SMOKE_EMAIL_DOMAIN` | `example.test` | Dominio de los correos temporales |

Con la Supabase CLI (`pnpm db:up`) usa `PULSO_SMOKE_DB_MODE=cli` y la clave que
muestra `pnpm db:status`. El paso 10 requiere la migración
`20261121200000_restrict_project_member_grants.sql`; con Compose se aplica con
`pnpm db:compose:seed`.

### `pnpm smoke:verify` — recorrido de interfaz

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

Tras las correcciones, axe no informa infracciones en las pantallas revisadas.
La revisión usó datos simulados; queda pendiente repetir el recorrido de teclado
con datos reales y con lector de pantalla.

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

- interfaz para enviar solicitudes de conexión, conversar o ver notificaciones
  (existen en la API);
- recuperación de contraseña y gestión de cuenta;
- silenciado, sanciones, panel administrativo completo y analítica;
- verificación de identidad u organizaciones;
- rate limiting y observabilidad de producción.
