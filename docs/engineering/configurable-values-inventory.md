# Inventario de strings y reglas configurables

**Estado:** Current (inventario del código desplegado) / Target (archivos y prefijos de extracción, aún no creados) / Decision (clasificación y convención de destino).
**Alcance:** PUL-66 / S2.0.a. Este documento nombra una fuente única para cada valor. No extrae strings, no mueve límites y no cambia el comportamiento del producto.

Las columnas de cada tabla son:

| Columna | Significado |
| --- | --- |
| Clase | `presentation`, `environment`, `api-rule` o `domain-data` |
| Nombre | Identificador estable de destino |
| Ubicación actual | Archivo y símbolo donde vive hoy |
| Valor | Literal actual, o `nombre solamente` cuando el valor es secreto |
| Consumidor | Quién lo usa en runtime |
| Fuente destino | Archivo o mecanismo canónico futuro |
| Ambiente | `all`, `local`, `test`, `hosted`, o `n/a` para datos de dominio |

## Clasificación

- **presentation.** Copy visible en la UI, `aria-label`, placeholders y mensajes de error que la persona ve en el navegador. Incluye títulos `ProblemDetail` que el cliente HTTP muestra o mapea.
- **environment.** URLs, orígenes, puertos, timeouts, identidad pública de Auth y mensajes de arranque cuando falta configuración. Cambian por despliegue.
- **api-rule.** Límites, longitudes, paginación y paths del contrato HTTP. Los límites van a `pulso.<capability>.*`; los paths permanecen en controllers y adapters, no en env.
- **domain-data.** Vocabulario y filas del producto: catálogos, enums de etapa/intención, contenido de proyectos. Permanecen en la base o en el dominio.

No hay rate limiting de API implementado. [`docs/stack.md`](../stack.md) lo deja como seguimiento de producción; este inventario no inventa un valor actual.

## Convención de destino (Target)

### Frontend: strings

Un archivo TypeScript por superficie, junto al feature que lo consume:

```text
frontend/src/app/shared/strings/common.strings.ts
frontend/src/app/core/auth/strings/auth.strings.ts
frontend/src/app/features/discovery/strings/<surface>.strings.ts
```

Reglas:

- Un módulo sin traducción exporta un objeto `as const` con claves en inglés camelCase y valores en español. Un módulo traducible usa `defineStrings(es, en)` (PUL-108) y conserva las mismas claves; el inglés debe tener exactamente las claves del español.
- Un feature importa solo su archivo y `shared/strings` cuando el copy es realmente compartido (`Reintentar`, pie de página, marca).
- No introducir `@angular/localize` ni ngx-translate: el cambio de idioma en caliento se resuelve con `defineStrings` y `LanguageService`, sin dependencias nuevas (ver `docs/architecture/frontend.md`).
- Las plantillas y los mensajes de sesión dejan de llevar literales una vez extraídos; este issue no hace esa extracción.

Ejemplo de forma, no de extracción:

```ts
export const feedStrings = {
  emptyTitle: 'Todavía no hay proyectos publicados.',
  retry: 'Reintentar',
} as const
```

### Frontend: entorno

La fuente única de URLs y Auth público sigue siendo [`frontend/src/app/core/config/app-config.ts`](../../frontend/src/app/core/config/app-config.ts), alimentada por [`frontend/scripts/frontend-env.mjs`](../../frontend/scripts/frontend-env.mjs).

| Nombre | Tipo | Ambiente |
| --- | --- | --- |
| `PULSO_API_BASE_URL` | URL absoluta HTTP(S) | local / hosted |
| `PULSO_SUPABASE_URL` | URL absoluta HTTP(S) | local / hosted |
| `PULSO_SUPABASE_PUBLISHABLE_KEY` | clave pública anon | local / hosted |

El wrapper de build rechaza variables de servidor (`SUPABASE_JWT_SECRET`, `SPRING_DATASOURCE_PASSWORD`, `SUPABASE_SERVICE_ROLE_KEY`, etc.). Esa lista permanece en `frontend-env.mjs`.

### Backend: propiedades y reglas de API

Configuración de entorno y reglas de contrato viven bajo `pulso.<capability>.*` en [`backend/src/main/resources/application.yml`](../../backend/src/main/resources/application.yml), ligadas con `@ConfigurationProperties` como ya hace `SupabaseJwtProperties`.

Los nombres se documentan en [`.env.example`](../../.env.example) con forma local segura, nunca con secretos reales. El perfil de test usa [`backend/src/test/resources/application.yml`](../../backend/src/test/resources/application.yml).

Prefijos previstos, aún sin extraer:

```text
pulso.security.supabase.*     # ya existe
pulso.cors.*                  # ya existe
pulso.discovery.*             # paginación del catálogo
pulso.project-opportunities.* # ya existe: paginación de oportunidades del proyecto
pulso.messages.*              # ya existe: paginación y longitud del texto de los mensajes
pulso.notifications.*         # ya existe: paginación de las notificaciones
pulso.catalogs.*              # tope de lectura
pulso.preferences.*           # tamaños de lista
pulso.profile.*               # longitudes de campo
pulso.project.*               # longitudes de campo
```

La copia de esos límites en Angular va a `frontend/src/app/features/discovery/data-access/discovery.catalog.contract.ts`, alineada con [`docs/api.md`](../api.md). El nombre sigue el módulo de discovery, no el alias HTTP `startups`. No se comparte un paquete TypeScript/Java hasta que la duplicación lo justifique.

### Backend: mensajes de API

Títulos y `detail` de `ProblemDetail` van a `backend/src/main/resources/messages.properties`, con claves `api.<module>.<case>.title|detail`. Las excepciones de dominio siguen siendo tipos; el texto deja de estar inline cuando se extraiga.

Los mensajes de arranque (`IllegalStateException` de JWT) permanecen junto a la validación de configuración. No son copy de producto.

### Rutas HTTP

El path de recurso (`/api/v1/startups`, `/api/v1/me`) es contrato, no variable de entorno. Permanece en `@RequestMapping` y en el adapter Angular. Solo el origin (`PULSO_API_BASE_URL`) es configurable por ambiente.

## Secretos

Estos nombres existen; **este inventario no registra sus valores**. No deben aparecer en archivos versionados de producto ni copiarse a strings.

| Nombre | Dónde se nombra hoy | Destino |
| --- | --- | --- |
| `SPRING_DATASOURCE_PASSWORD` | `.env.example`, `application.yml` | secret manager / env de proceso |
| `SUPABASE_JWT_SECRET` | `.env.example`, `pulso.security.supabase.secret` | secret manager; fallback HS256 local solamente |
| `POSTGRES_PASSWORD` | `compose.yaml` | secret manager / env de Compose |
| `JWT_SECRET` | `compose.yaml` (Auth local) | secret manager / env de Compose |
| `SUPABASE_SERVICE_ROLE_KEY` | rechazada por `frontend-env.mjs` | nunca en el bundle del browser |
| `DATABASE_URL` | rechazada por `frontend-env.mjs` | backend / Compose solamente |

`PULSO_SUPABASE_PUBLISHABLE_KEY` es pública por diseño. El fixture local vive en `frontend/.env.development.example`; no se reproduce aquí.

El default HS256 de Compose/`.env.example` es un fixture de desarrollo. Nunca reutilizarlo en un ambiente hosted.

## Duplicados

Valores que hoy tienen más de una fuente. La columna destino es la fuente única prevista.

| Valor / forma | Copias actuales | Destino único |
| --- | --- | --- |
| Paginación `default 20`, `max 50`, `max offset 100000` | `StartupController`, `StartupsApi` | `pulso.discovery.*` + `discovery.catalog.contract.ts` |
| Etapas `IDEA…ESTABLISHED` | `ProjectDraft.ALLOWED_STAGES`, `OnboardingPreferences.ALLOWED_STAGE_SET` | enum/dominio `project stage`; no env |
| `No se pudo cerrar la sesión. Inténtalo de nuevo.` | `auth-panel.ts`, `profile-view.ts` | `shared/strings/common.strings.ts` → `sessionCloseFailed` |
| `Cerrar sesión` | `auth-panel.html`, `profile-view.html` | `common.strings.ts` → `signOut` |
| `Reintentar` | onboarding, discovery, profile | `common.strings.ts` → `retry` |
| `Hecho con curiosidad` / `Bolivia / 2026` | `auth-page.html`, `onboarding.html` | `common.strings.ts` |
| `UN MAPA DE LO QUE VIENE` | `auth-page.html`, `onboarding.html` | `common.strings.ts` → `eyebrowComingMap` |
| Origin API `http://localhost:8080` | `app-config.ts`, `frontend-env.mjs`, `.env.development.example`, `docs/api.md` | `PULSO_API_BASE_URL` |
| Issuer JWT `http://127.0.0.1:54321/auth/v1` | `application.yml`, `.env.example` | `SUPABASE_JWT_ISSUER` |
| Orígenes CORS | default YAML `http://localhost:4200`; `.env.example` también lista `http://127.0.0.1:4200` | `CORS_ALLOWED_ORIGINS` (el ejemplo es la forma local completa) |
| Longitudes de perfil 120 / 500 | `ProfilePatch`, `profile-view.html` `maxlength` | `pulso.profile.*` |
| Contraseña mínima 6 | `auth-panel.html` `minlength`, `GOTRUE_PASSWORD_MIN_LENGTH` en Compose | regla de Auth; documentar, no env de Angular |
| Rol/audiencia `authenticated` | `SecurityConfig`, `application.yml`, Compose GoTrue | `SUPABASE_JWT_AUDIENCE` + dominio Auth |
| `{field} is required.` / `must not be blank.` / `must not exceed N` | profile, project, preferences | `messages.properties` con el mismo patrón por módulo |
| Intereses frontend `discover` / `invest` / `collaborate` vs intenciones API `DISCOVER` / `INVEST` / `COLLABORATE` | `interests.ts`, `OnboardingPreferences` | dominio; no son el mismo catálogo (ver datos de dominio) |

## Datos de dominio que permanecen como datos

No extraer a strings de UI ni a `application.yml`:

| Dato | Ubicación | Por qué permanece |
| --- | --- | --- |
| Sectores, tags y habilidades activos | `database/supabase/seed.sql`, `GET /api/v1/catalogs` | filas curadas; el cliente muestra `name` del API |
| Nombre, tagline, sector, ciudad y etapa de un proyecto | `GET /api/v1/startups` | contenido de producto |
| `mockStartups` | `frontend/.../data-access/mock-startups.ts` | fixture de tests/prototipo, no runtime |
| `interestOptions` (id, signals, icon) | `frontend/.../data-access/interests.ts` | catálogo local de onboarding; las etiquetas sí son presentation |
| `signalLabels` keys (`agriculture`, `clean-energy`, …) | `recommendations.ts` | ids de señal; las etiquetas visibles son presentation |
| `ALLOWED_INTENTION_SET`, etapas, `profileKind`, `profileVisibility` | records de dominio Java | vocabulario del modelo, no config de ambiente |
| Pesos de recomendación `1.8`, `0.35`, `0.2`, `±0.16/0.04` | `recommendations.ts` | política de dominio; cambiarlos es un cambio de producto |
| `sectorSignals` | `startup-view-model.ts` | heurística de presentación sobre el sector del API |
| Proyectos semilla locales | `database/supabase/seed.sql` | fixtures de desarrollo |

## Inventario por módulo

### Frontend · core/config

| Clase | Nombre | Ubicación actual | Valor | Consumidor | Fuente destino | Ambiente |
| --- | --- | --- | --- | --- | --- | --- |
| environment | `PULSO_API_BASE_URL` | `app-config.ts`, `frontend-env.mjs` | `http://localhost:8080` | `StartupsApi`, `CatalogsApi`, `ProfileApi`, `authInterceptor` | `app-config.ts` ← env | local default; hosted por env |
| environment | `PULSO_SUPABASE_URL` | `app-config.ts`, `frontend-env.mjs` | `http://127.0.0.1:54321` | `SupabaseAuthGateway` | `app-config.ts` ← env | local default; hosted por env |
| environment | `PULSO_SUPABASE_PUBLISHABLE_KEY` | `app-config.ts`, `frontend-env.mjs` | nombre solamente | `SupabaseAuthGateway` | `app-config.ts` ← env | local / hosted |
| api-rule | `discovery.defaultLimit` | `StartupsApi.list(limit = 20)` | `20` | discovery session | `discovery.catalog.contract.ts` | all |
| api-rule | `discovery.maxLimit` | `StartupsApi` | `50` | `StartupsApi` | `discovery.catalog.contract.ts` | all |
| api-rule | `discovery.maxOffset` | `StartupsApi` | `100000` | `StartupsApi` | `discovery.catalog.contract.ts` | all |
| api-rule | `discovery.invalidLimitMessage` | `StartupsApi` | `limit must be an integer between 1 and 50` | validación del adapter (RangeError, no UI) | permanece junto al contrato | all |
| api-rule | `discovery.invalidOffsetMessage` | `StartupsApi` | `offset must be an integer between 0 and 100000` | validación del adapter (RangeError, no UI) | permanece junto al contrato | all |
| api-rule | `catalogs.path` | `catalogs-api.ts` | `/api/v1/catalogs` | `CatalogsApi` | adapter; contrato HTTP, no env | all |
| api-rule | `profile.path` | `profile-api.ts` | `/api/v1/me` | `ProfileApi` | adapter; contrato HTTP, no env | all |
| api-rule | `discovery.path` | `startups-api.ts` | `/api/v1/startups` | `StartupsApi` | adapter; contrato HTTP, no env | all |

### Frontend · shared / documento

| Clase | Nombre | Ubicación actual | Valor | Consumidor | Fuente destino | Ambiente |
| --- | --- | --- | --- | --- | --- | --- |
| presentation | `brand.name` | `brand-mark.ts` | `PULSO` | barra, auth, onboarding | `shared/strings/common.strings.ts` | all |
| presentation | `document.title` | `index.html` | `Pulso · Descubre lo que está creciendo` | browser tab | `index.html` o `common.strings.ts` | all |
| presentation | `document.description` | `index.html` | `Pulso: descubre lo que está creciendo en Bolivia.` | SEO / share | `index.html` o `common.strings.ts` | all |
| presentation | `common.madeWithCuriosity` | `auth-page.html`, `onboarding.html` | `Hecho con curiosidad` | footers | `common.strings.ts` | all |
| presentation | `common.localeLine` | `auth-page.html`, `onboarding.html` | `Bolivia / 2026` | footers | `common.strings.ts` | all |
| presentation | `common.retry` | onboarding, discovery, profile | `Reintentar` | botones de error | `common.strings.ts` | all |
| presentation | `common.signOut` | auth, profile | `Cerrar sesión` | auth y perfil | `common.strings.ts` | all |
| presentation | `common.sessionCloseFailed` | `auth-panel.ts`, `profile-view.ts` | `No se pudo cerrar la sesión. Inténtalo de nuevo.` | auth y perfil | `common.strings.ts` | all |
| presentation | `common.eyebrowComingMap` | `auth-page.html`, `onboarding.html` | `UN MAPA DE LO QUE VIENE` | auth y onboarding | `common.strings.ts` | all |

### Frontend · auth

Cada fila de copy: consumidor `AuthPageComponent` / `AuthPanelComponent`; fuente destino `frontend/src/app/core/auth/strings/auth.strings.ts`; ambiente `all`.

| Clase | Nombre | Ubicación actual | Valor | Consumidor | Fuente destino | Ambiente |
| --- | --- | --- | --- | --- | --- | --- |
| presentation | `auth.page.kicker` | `auth-page.html` | `ENTRA A PULSO` | `AuthPageComponent` | `auth.strings.ts` | all |
| presentation | `auth.page.title` | `auth-page.html` | `Primero tu cuenta. Después, tu pulso.` | `AuthPageComponent` | `auth.strings.ts` | all |
| presentation | `auth.page.lead` | `auth-page.html` | `Crea tu cuenta para descubrir proyectos, guardar lo que te interesa y construir un perfil que te represente.` | `AuthPageComponent` | `auth.strings.ts` | all |
| presentation | `auth.page.stepMark1` | `auth-page.html` | `01` | `AuthPageComponent` | `auth.strings.ts` | all |
| presentation | `auth.page.step1` | `auth-page.html` | `Conoce proyectos locales.` | `AuthPageComponent` | `auth.strings.ts` | all |
| presentation | `auth.page.stepMark2` | `auth-page.html` | `02` | `AuthPageComponent` | `auth.strings.ts` | all |
| presentation | `auth.page.step2` | `auth-page.html` | `Guarda lo que te mueve.` | `AuthPageComponent` | `auth.strings.ts` | all |
| presentation | `auth.page.stepMark3` | `auth-page.html` | `03` | `AuthPageComponent` | `auth.strings.ts` | all |
| presentation | `auth.page.step3` | `auth-page.html` | `Construye tu perfil.` | `AuthPageComponent` | `auth.strings.ts` | all |
| presentation | `auth.eyebrow.session` | `auth-panel.html` | `TU CUENTA` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.eyebrow.signIn` | `auth-panel.html` | `HOLA DE NUEVO` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.eyebrow.signUp` | `auth-panel.html` | `ÚNETE A PULSO` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.title.session` | `auth-panel.html` | `Sesión conectada` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.title.signIn` | `auth-panel.html` | `Vuelve a Pulso` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.title.signUp` | `auth-panel.html` | `Crea tu cuenta` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.lead.session` | `auth-panel.html` | `Tu espacio está listo para seguir descubriendo.` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.lead.signIn` | `auth-panel.html` | `Retoma tus descubrimientos y continúa guardando lo que te inspira.` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.lead.signUp` | `auth-panel.html` | `Empieza a descubrir proyectos y encuentra nuevas ideas para seguir.` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.checkingAccess` | `auth-panel.html` | `Comprobando tu acceso...` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.activeSession` | `auth-panel.html` | `Sesión activa` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.emailLabel` | `auth-panel.html` | `Correo` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.emailPlaceholder` | `auth-panel.html` | `tu@correo.com` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.passwordLabel` | `auth-panel.html` | `Contraseña` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.passwordPlaceholder` | `auth-panel.html` | `Mínimo 6 caracteres` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.submitSignIn` | `auth-panel.html` | `Iniciar sesión` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.submitSignUp` | `auth-panel.html` | `Crear mi cuenta` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.toggleToSignUp` | `auth-panel.html` | `Crear cuenta` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.toggleToSignIn` | `auth-panel.html` | `Ya tengo cuenta` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.missingConfig` | `auth-panel.html` | `El acceso no está disponible en este momento. Inténtalo de nuevo más tarde.` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.initFailed` | `auth-panel.ts` | `No se pudo comprobar tu acceso. Inténtalo de nuevo.` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.missingCredentials` | `auth-panel.ts` | `Escribe tu correo y contraseña para continuar.` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.confirmEmail` | `auth-panel.ts` | `Revisa tu correo para confirmar la cuenta.` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.signUpSuccess` | `auth-panel.ts` | `Cuenta creada. Ya puedes explorar Pulso.` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.signInSuccess` | `auth-panel.ts` | `Sesión iniciada. Ya puedes explorar Pulso.` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.signUpFailed` | `auth-panel.ts` | `No se pudo crear la cuenta con esos datos.` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.signInFailed` | `auth-panel.ts` | `No se pudo iniciar sesión con esos datos.` | `AuthPanelComponent` | `auth.strings.ts` | all |
| presentation | `auth.signedOut` | `auth-panel.ts` | `Sesión cerrada.` | `AuthPanelComponent` | `auth.strings.ts` | all |
| environment | `GOTRUE_PASSWORD_MIN_LENGTH` | `compose.yaml`; espejo UI `auth-panel.html` `minlength="6"` | `6` | GoTrue local y validación HTML | Compose Auth; no es límite del API Pulso | local (hosted según el proyecto Auth) |

### Frontend · discovery / onboarding

Consumidor: `OnboardingComponent`, `PulsoSession`. Destino: `discovery/strings/onboarding.strings.ts`.

| Clase | Nombre | Ubicación actual | Valor | Consumidor | Fuente destino | Ambiente |
| --- | --- | --- | --- | --- | --- | --- |
| presentation | `onboarding.stepMark` | `onboarding.html` | `01 / 01` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.title` | `onboarding.html` | `Descubre lo que está creciendo en Bolivia.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.lead` | `onboarding.html` | `Pulso reúne proyectos con intención, talento y algo nuevo que aportar. Tú decides qué merece tu atención.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.startEyebrow` | `onboarding.html` | `Para empezar` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.startTitle` | `onboarding.html` | `¿Qué estás buscando?` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.startHint` | `onboarding.html` | `Elige todo lo que te mueva.` / `Puedes cambiarlo después.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.selectedAria` | `onboarding.html` | `Seleccionado` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.optionalEyebrow` | `onboarding.html` | `Opcional` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.catalogTitle` | `onboarding.html` | `Afina lo que quieres encontrar` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.catalogLead` | `onboarding.html` | `Estas selecciones nos ayudan a acercarte proyectos que encajen contigo. Puedes cambiarlas después.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.catalogsLoading` | `onboarding.html` | `Cargando sectores, temas y habilidades...` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.sectorsTitle` | `onboarding.html` | `Sectores preferidos` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.sectorsHint` | `onboarding.html` | `Elige los sectores que quieres seguir.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.sectorsEmpty` | `onboarding.html` | `No hay sectores activos disponibles.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.tagsTitle` | `onboarding.html` | `Temas de interés` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.tagsHint` | `onboarding.html` | `Usa los tags activos del catálogo de Pulso.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.tagsEmpty` | `onboarding.html` | `No hay tags activos disponibles.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.skillsTitle` | `onboarding.html` | `Habilidades` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.skillsHint` | `onboarding.html` | `Selecciona capacidades que puedes aportar.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.skillsEmpty` | `onboarding.html` | `No hay habilidades activas disponibles.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.needInterest` | `onboarding.html` | `Selecciona al menos un interés para personalizar tu pulso.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.oneInterest` | `onboarding.html` | `1 interés seleccionado.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.nInterests` | `onboarding.html` | `{{n}} intereses seleccionados.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.enter` | `onboarding.html` | `Entrar a Pulso` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `onboarding.catalogsFailed` | `pulso-session.ts` | `No pudimos cargar sectores, temas y habilidades. Inténtalo de nuevo.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `interest.discover.label` | `interests.ts` | `Descubrir productos` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `interest.discover.short` | `interests.ts` | `Productos` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `interest.discover.description` | `interests.ts` | `Ideas nuevas para usar, probar y compartir.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `interest.invest.label` | `interests.ts` | `Invertir` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `interest.invest.short` | `interests.ts` | `Invertir` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `interest.invest.description` | `interests.ts` | `Equipos con potencial y una historia que seguir.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `interest.collaborate.label` | `interests.ts` | `Colaborar` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `interest.collaborate.short` | `interests.ts` | `Colaborar` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `interest.collaborate.description` | `interests.ts` | `Proyectos que buscan talento, aliados y comunidad.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `interest.opportunities.label` | `interests.ts` | `Encontrar oportunidades` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `interest.opportunities.short` | `interests.ts` | `Oportunidades` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `interest.opportunities.description` | `interests.ts` | `Conecta con lo que está pasando cerca de ti.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `interest.inspiration.label` | `interests.ts` | `Inspirarme` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `interest.inspiration.short` | `interests.ts` | `Inspirarme` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| presentation | `interest.inspiration.description` | `interests.ts` | `Historias locales que abren nuevas posibilidades.` | OnboardingComponent / PulsoSession | onboarding.strings.ts | all |
| domain-data | `interest.*.id` / `signals` | `interests.ts` | ids `discover`, `invest`, `collaborate`, `opportunities`, `inspiration` y sus `signals` | onboarding / ranking | catálogo local; no env | n/a |

### Frontend · discovery / feed

Consumidor: `DiscoveryViewComponent`, `StartupCardComponent`, `PulsoSession`. Destino: `discovery/strings/feed.strings.ts`.

| Clase | Nombre | Ubicación actual | Valor | Consumidor | Fuente destino | Ambiente |
| --- | --- | --- | --- | --- | --- | --- |
| presentation | `feed.loadingEyebrow` | `discovery-view.html` | `CARGANDO DISCOVERY` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.loadingTitle` | `discovery-view.html` | `Buscando proyectos publicados.` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.loadingLead` | `discovery-view.html` | `Estamos consultando el catálogo real de Pulso.` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.errorEyebrow` | `discovery-view.html` | `NO SE PUDO CARGAR` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.errorTitle` | `discovery-view.html` | `El catálogo no está disponible.` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.catalogFailed` | `pulso-session.ts` | `No pudimos cargar los proyectos publicados. Inténtalo de nuevo.` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.progress` | `discovery-view.html` | `Exploración {{n}} / {{total}}` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.remaining` | `discovery-view.html` | `{{n}} por ver` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.skip` | `discovery-view.html` | `Pasar` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.interested` | `discovery-view.html` | `Me interesa` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.seeProfile` | `discovery-view.html` | `Ver perfil` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.save` | `discovery-view.html` | `Guardar` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.saved` | `discovery-view.html` | `Guardado` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.skipAria` | `discovery-view.html` | `Pasar (←)` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.saveAria` | `discovery-view.html` | `Guardar (S)` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.interestedAria` | `discovery-view.html` | `Me interesa (→)` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.emptyEyebrow` | `discovery-view.html` | `CATÁLOGO VACÍO` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.emptyTitle` | `discovery-view.html` | `Todavía no hay proyectos publicados.` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.emptyLead` | `discovery-view.html` | `Cuando exista un proyecto publicado aparecerá aquí para descubrirlo.` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.doneEyebrow` | `discovery-view.html` | `PRIMERA VUELTA COMPLETA` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.doneTitle` | `discovery-view.html` | `Ya viste todo el pulso.` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.doneLead` | `discovery-view.html` | `Tu selección quedó guardada. ¿Quieres explorar con una mirada nueva?` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.restart` | `discovery-view.html` | `Volver a explorar` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.toastSaved` | `discovery-view.ts` | `Guardado en tu selección` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `feed.toastRemoved` | `discovery-view.ts` | `Quitado de tus guardados` | DiscoveryViewComponent / PulsoSession | feed.strings.ts | all |
| presentation | `card.openAria` | `startup-card.html` | `Ver perfil de {{name}}` | StartupCardComponent | feed.strings.ts | all |
| presentation | `card.saveAria` | `startup-card.html` | `Guardar startup` / `Quitar de guardados` | StartupCardComponent | feed.strings.ts | all |
| presentation | `card.openProfile` | `startup-card.html` | `Ver perfil completo` | StartupCardComponent | feed.strings.ts | all |
| presentation | `card.descriptionFallback` | `startup-view-model.ts` | `Proyecto publicado en {{city}}, dentro del sector {{sector}}.` | StartupCardComponent | feed.strings.ts | all |

### Frontend · discovery / saved, navegación, detalle, perfil

Destinos: `saved.strings.ts`, `navigation.strings.ts`, `detail.strings.ts`, `profile.strings.ts`.

| Clase | Nombre | Ubicación actual | Valor | Consumidor | Fuente destino | Ambiente |
| --- | --- | --- | --- | --- | --- | --- |
| presentation | `nav.discover` | `navigation-bar.ts` | `Descubrir` | NavigationBarComponent | navigation.strings.ts | all |
| presentation | `nav.saved` | `navigation-bar.ts` | `Guardados` | NavigationBarComponent | navigation.strings.ts | all |
| presentation | `nav.profile` | `navigation-bar.ts` | `Perfil` | NavigationBarComponent | navigation.strings.ts | all |
| presentation | `nav.aria` | `navigation-bar.html` | `Navegación principal` | NavigationBarComponent | navigation.strings.ts | all |
| presentation | `nav.sessionEyebrow` | `navigation-bar.html` | `SESIÓN ACTIVA` | NavigationBarComponent | navigation.strings.ts | all |
| presentation | `nav.savedSignals` | `navigation-bar.html` | `{{n}} señales guardadas` | NavigationBarComponent | navigation.strings.ts | all |
| presentation | `saved.eyebrow` | `saved-view.html` | `TU SELECCIÓN` | SavedViewComponent | saved.strings.ts | all |
| presentation | `saved.title` | `saved-view.html` | `Lo que te hizo parar.` | SavedViewComponent | saved.strings.ts | all |
| presentation | `saved.lead` | `saved-view.html` | `Una pequeña colección de proyectos para volver cuando tengas tiempo, preguntas o ganas de conectar.` | SavedViewComponent | saved.strings.ts | all |
| presentation | `saved.savedHeading` | `saved-view.html` | `Guardados` | SavedViewComponent | saved.strings.ts | all |
| presentation | `saved.interestedHeading` | `saved-view.html` | `Te interesaron` | SavedViewComponent | saved.strings.ts | all |
| presentation | `saved.noneSaved` | `saved-view.html` | `Aún no guardaste ningún proyecto.` | SavedViewComponent | saved.strings.ts | all |
| presentation | `saved.noneInterested` | `saved-view.html` | `Marca un proyecto como interesante para verlo aquí.` | SavedViewComponent | saved.strings.ts | all |
| presentation | `saved.emptyTitle` | `saved-view.html` | `Tu radar está vacío` | SavedViewComponent | saved.strings.ts | all |
| presentation | `saved.emptyLead` | `saved-view.html` | `Guarda las ideas que quieras volver a mirar y aparecerán aquí.` | SavedViewComponent | saved.strings.ts | all |
| presentation | `detail.closeAria` | `startup-detail-modal.html` | `Cerrar detalle` | StartupDetailModalComponent | detail.strings.ts | all |
| presentation | `detail.founders` | `startup-detail-modal.html` | `Fundadores` | StartupDetailModalComponent | detail.strings.ts | all |
| presentation | `detail.foundersLead` | `startup-detail-modal.html` | `Las personas detrás de la idea` | StartupDetailModalComponent | detail.strings.ts | all |
| presentation | `detail.why` | `startup-detail-modal.html` | `¿Por qué te lo recomendamos?` | StartupDetailModalComponent | detail.strings.ts | all |
| presentation | `detail.lookingFor` | `startup-detail-modal.html` | `Buscan` | StartupDetailModalComponent | detail.strings.ts | all |
| presentation | `detail.foundedPrefix` | `startup-detail-modal.html` | `Desde {{year}}` | StartupDetailModalComponent | detail.strings.ts | all |
| presentation | `detail.contact` | `startup-detail-modal.html` | `Contactar founders` | StartupDetailModalComponent | detail.strings.ts | all |
| presentation | `detail.contactDone` | `startup-detail-modal.html` | `Listo para conectar` | StartupDetailModalComponent | detail.strings.ts | all |
| presentation | `detail.prototypeNote` | `startup-detail-modal.html` | `Proyecto ficticio para el prototipo de Pulso` | StartupDetailModalComponent | detail.strings.ts | all |
| presentation | `profile.eyebrow` | `profile-view.html` | `TU ESPACIO` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.title` | `profile-view.html` | `Tu forma de mirar.` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.lead` | `profile-view.html` | `Cuéntanos un poco sobre ti para que tu espacio en Pulso se sienta propio.` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.loading` | `profile-view.html` | `Cargando tu perfil...` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.formTitle` | `profile-view.html` | `Tu perfil` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.formHint` | `profile-view.html` | `Estos datos ayudan a que tu perfil represente mejor tus intereses dentro de Pulso.` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.displayName` | `profile-view.html` | `Nombre visible` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.shortBio` | `profile-view.html` | `Biografía breve` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.shortBioPlaceholder` | `profile-view.html` | `Cuéntanos brevemente qué te mueve.` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.city` | `profile-view.html` | `Ciudad` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.department` | `profile-view.html` | `Departamento` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.cityPlaceholder` | `profile-view.html` | `La Paz` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.departmentPlaceholder` | `profile-view.html` | `La Paz` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.save` | `profile-view.html` | `Guardar perfil` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.saving` | `profile-view.html` | `Guardando...` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.selection` | `profile-view.html` | `Tu selección` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.activeInterests` | `profile-view.html` | `{{n}} intereses activos` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.noInterests` | `profile-view.html` | `Tus intereses aparecerán aquí cuando completes el onboarding.` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.noteEyebrow` | `profile-view.html` | `Una nota de Pulso` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.noteTitle` | `profile-view.html` | `Lo local también puede sentirse futurista.` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.noteLead` | `profile-view.html` | `Cada proyecto que encuentras abre una puerta para alguien más.` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.loadFailed` | `profile-view.ts` | `No pudimos cargar tu perfil. Inténtalo de nuevo.` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.displayNameRequired` | `profile-view.ts` | `El nombre visible es obligatorio.` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.saved` | `profile-view.ts` | `Perfil guardado correctamente.` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `profile.saveFailed` | `profile-view.ts` | `No se pudo guardar el perfil. Revisa los campos e inténtalo de nuevo.` | ProfileViewComponent | profile.strings.ts | all |
| presentation | `reco.matched` | `recommendations.ts` | `Tus intereses conectan con {{labels}} en {{city}}.` | recommendations.ts / detail modal | recommendations.strings.ts | all |
| presentation | `reco.explore` | `recommendations.ts` | `Te mostramos {{name}} como una exploración nueva en {{city}}, cerca de proyectos que están creciendo.` | recommendations.ts / detail modal | recommendations.strings.ts | all |
| presentation | `reco.signal.agriculture` | `recommendations.ts` | `Agrotech` | ranking / razones | recommendations.strings.ts | all |
| presentation | `reco.signal.collaboration` | `recommendations.ts` | `Colaboración` | ranking / razones | recommendations.strings.ts | all |
| presentation | `reco.signal.cleanEnergy` | `recommendations.ts` | `Energía limpia` | ranking / razones | recommendations.strings.ts | all |
| presentation | `reco.signal.commerce` | `recommendations.ts` | `Comercio` | ranking / razones | recommendations.strings.ts | all |
| presentation | `reco.signal.community` | `recommendations.ts` | `Comunidad` | ranking / razones | recommendations.strings.ts | all |
| presentation | `reco.signal.education` | `recommendations.ts` | `Educación` | ranking / razones | recommendations.strings.ts | all |
| presentation | `reco.signal.food` | `recommendations.ts` | `Alimentos` | ranking / razones | recommendations.strings.ts | all |
| presentation | `reco.signal.growth` | `recommendations.ts` | `Crecimiento` | ranking / razones | recommendations.strings.ts | all |
| presentation | `reco.signal.impact` | `recommendations.ts` | `Impacto` | ranking / razones | recommendations.strings.ts | all |
| presentation | `reco.signal.innovation` | `recommendations.ts` | `Innovación` | ranking / razones | recommendations.strings.ts | all |
| presentation | `reco.signal.investment` | `recommendations.ts` | `Inversión` | ranking / razones | recommendations.strings.ts | all |
| presentation | `reco.signal.local` | `recommendations.ts` | `Local` | ranking / razones | recommendations.strings.ts | all |
| presentation | `reco.signal.mobility` | `recommendations.ts` | `Movilidad` | ranking / razones | recommendations.strings.ts | all |
| presentation | `reco.signal.products` | `recommendations.ts` | `Productos` | ranking / razones | recommendations.strings.ts | all |
| presentation | `reco.signal.sustainability` | `recommendations.ts` | `Sostenibilidad` | ranking / razones | recommendations.strings.ts | all |
| presentation | `reco.signal.technology` | `recommendations.ts` | `Tecnología` | ranking / razones | recommendations.strings.ts | all |
| presentation | `reco.signal.opportunities` | `recommendations.ts` | `Oportunidades` | ranking / razones | recommendations.strings.ts | all |

Ambiente de todo el copy de discovery: `all`.

### Backend · config y ops

| Clase | Nombre | Ubicación actual | Valor | Consumidor | Fuente destino | Ambiente |
| --- | --- | --- | --- | --- | --- | --- |
| environment | `SERVER_PORT` | `application.yml` `server.port` | `8080` | proceso Spring | `application.yml` ← env | local default |
| environment | `SPRING_DATASOURCE_URL` | `application.yml` | `jdbc:postgresql://127.0.0.1:54322/postgres` | Hikari | `application.yml` ← env | local default |
| environment | `SPRING_DATASOURCE_USERNAME` | `application.yml` | `postgres` | Hikari | `application.yml` ← env | local default |
| environment | `SPRING_DATASOURCE_PASSWORD` | `application.yml` | nombre solamente | Hikari | secret / env | all |
| environment | `DB_CONNECTION_TIMEOUT_MS` | `application.yml` hikari | `3000` | datasource | `application.yml` ← env | all |
| environment | `SUPABASE_JWT_ISSUER` | `pulso.security.supabase.issuer` | `http://127.0.0.1:54321/auth/v1` | JWT decoder | ya tipado | local default |
| environment | `SUPABASE_JWT_AUDIENCE` | `pulso.security.supabase.audience` | `authenticated` | JWT validator | ya tipado | all |
| environment | `SUPABASE_JWT_JWK_SET_URI` | `pulso.security.supabase.jwk-set-uri` | vacío en local | JWT decoder | ya tipado | hosted |
| environment | `SUPABASE_JWT_SECRET` | `pulso.security.supabase.secret` | nombre solamente | decoder HS256 | secret / env | local fallback |
| environment | `CORS_ALLOWED_ORIGINS` | `pulso.cors.allowed-origins` | `http://localhost:4200` (YAML); ejemplo también `http://127.0.0.1:4200` | `SecurityConfig` | ya en YAML | local / hosted |
| environment | `spring.application.name` | `application.yml` | `pulso-backend` | health / info | `application.yml` | all |
| api-rule | `ops.healthPath` | `HealthController` | `/api/health` | orquestación | controller | all |
| api-rule | `ops.actuatorHealth` | `application.yml` | `/actuator/health`, liveness, readiness | Compose / probes | Spring Actuator | all |
| environment | `jwt.oneSource` | `SecurityConfig` | `Configure exactly one of SUPABASE_JWT_JWK_SET_URI or SUPABASE_JWT_SECRET.` | arranque | permanece en config | all |
| environment | `jwt.missingSource` | `SecurityConfig` | `Configure SUPABASE_JWT_JWK_SET_URI … or SUPABASE_JWT_SECRET …` | arranque | permanece en config | all |
| environment | `jwt.emptyAudience` | `SecurityConfig` | `SUPABASE_JWT_AUDIENCE must not be empty.` | arranque | permanece en config | all |
| presentation | `jwt.audienceRejected` | `SecurityConfig` | `The JWT audience is not accepted.` | cliente OAuth error | `messages.properties` | all |
| presentation | `jwt.roleRejected` | `SecurityConfig` | `The JWT role is not accepted for user API access.` | cliente OAuth error | `messages.properties` | all |

Compose local (`compose.yaml`) también nombra `SITE_URL`, `API_EXTERNAL_URL`, `JWT_EXPIRY` (`3600`), `GOTRUE_PASSWORD_MIN_LENGTH` (`6`) y puertos `PULSO_POSTGRES_PORT` / `PULSO_MAILPIT_PORT`. Son entorno del stack local, no reglas del API de producto. Los secretos de Compose se listan arriba sin valor.

### Backend · discovery

| Clase | Nombre | Ubicación actual | Valor | Consumidor | Fuente destino | Ambiente |
| --- | --- | --- | --- | --- | --- | --- |
| api-rule | `pulso.discovery.default-offset` | `StartupController` `defaultValue = "0"` | `0` | `GET /api/v1/startups` | `application.yml` | all |
| api-rule | `pulso.discovery.default-limit` | `StartupController` `defaultValue = "20"` | `20` | igual | `application.yml` | all |
| api-rule | `pulso.discovery.max-limit` | `MAX_LIMIT` | `50` | igual | `application.yml` | all |
| api-rule | `pulso.discovery.max-offset` | `MAX_OFFSET` | `100000` | igual | `application.yml` | all |
| api-rule | `pulso.project-opportunities.default-offset` | `ProjectOpportunityProperties` | `0` | `GET /api/v1/projects/{projectId}/opportunities` | `application.yml` | all |
| api-rule | `pulso.project-opportunities.default-limit` | `ProjectOpportunityProperties` | `20` | igual | `application.yml` | all |
| api-rule | `pulso.project-opportunities.max-limit` | `ProjectOpportunityProperties` | `50` | igual | `application.yml` | all |
| api-rule | `pulso.project-opportunities.max-offset` | `ProjectOpportunityProperties` | `100000` | igual | `application.yml` | all |
| api-rule | `pulso.messages.default-offset` | `MessageProperties` | `0` | `GET /api/v1/conversations/{conversationId}/messages` | `application.yml` | all |
| api-rule | `pulso.messages.default-limit` | `MessageProperties` | `20` | igual | `application.yml` | all |
| api-rule | `pulso.messages.max-limit` | `MessageProperties` | `50` | igual | `application.yml` | all |
| api-rule | `pulso.messages.max-offset` | `MessageProperties` | `100000` | igual | `application.yml` | all |
| api-rule | `pulso.notifications.default-offset` | `NotificationProperties` | `0` | `GET /api/v1/me/notifications` | `application.yml` | all |
| api-rule | `pulso.notifications.default-limit` | `NotificationProperties` | `20` | igual | `application.yml` | all |
| api-rule | `pulso.notifications.max-limit` | `NotificationProperties` | `50` | igual | `application.yml` | all |
| api-rule | `pulso.notifications.max-offset` | `NotificationProperties` | `100000` | igual | `application.yml` | all |
| api-rule | `pulso.messages.body-max-length` | `MessageProperties` | `2000` | `POST /api/v1/conversations/{conversationId}/messages` | `application.yml`, `PULSO_MESSAGES_BODY_MAX_LENGTH`; la base limita a 5000 | all |
| api-rule | `discovery.path` | `@RequestMapping` | `/api/v1/startups` | Angular + curl | contrato; no env | all |
| presentation | `api.invalid-request.title` | `ApiExceptionHandler` | `Invalid request` | clientes HTTP | `messages.properties` | all |
| presentation | `api.discovery.offset.detail` | `StartupController` | `offset must be between 0 and 100000.` | `400` | `messages.properties` | all |
| presentation | `api.discovery.limit.detail` | `StartupController` | `limit must be between 1 and 50.` | `400` | `messages.properties` | all |
| presentation | `api.invalid-parameter.detail` | `ApiExceptionHandler` | `Parameter '{{name}}' has an invalid value.` | `400` | `messages.properties` | all |
| presentation | `api.unreadable-body.detail` | `ApiExceptionHandler` | `The request body is invalid.` | `400` | `messages.properties` | all |
| presentation | `api.datastore-unavailable.title` | `ApiExceptionHandler` | `Data store unavailable` | `503` | `messages.properties` | all |
| presentation | `api.datastore-unavailable.detail` | `ApiExceptionHandler` | `The data store is temporarily unavailable.` | `503` | `messages.properties` | all |

No hay rate limit por IP ni por token en este módulo.

### Backend · catalog

| Clase | Nombre | Ubicación actual | Valor | Consumidor | Fuente destino | Ambiente |
| --- | --- | --- | --- | --- | --- | --- |
| api-rule | `pulso.catalogs.max-items-per-catalog` | `ListActiveCatalogs.MAX_ITEMS_PER_CATALOG` | `500` | `GET /api/v1/catalogs` | `application.yml` | all |
| api-rule | `catalogs.path` | `CatalogController` | `/api/v1/catalogs` | Angular | contrato | all |
| domain-data | sectores / tags / skills | `seed.sql`, JDBC | nombres como `Sostenibilidad`, `Innovación` | onboarding | base de datos | all |

### Backend · profile

| Clase | Nombre | Ubicación actual | Valor | Consumidor | Fuente destino | Ambiente |
| --- | --- | --- | --- | --- | --- | --- |
| api-rule | `pulso.profile.display-name-max-length` | `ProfilePatch` | `120` | `PATCH /api/v1/me`, input HTML | `application.yml` | all |
| api-rule | `profile.path` | `MeController` | `/api/v1/me` | Angular `ProfileApi` | contrato HTTP, no env | all |
| api-rule | `pulso.profile.avatar-path-max-length` | `ProfilePatch` | `500` | `PATCH /api/v1/me` | `application.yml` | all |
| api-rule | `pulso.profile.short-bio-max-length` | `ProfilePatch` | `500` | igual + HTML | `application.yml` | all |
| api-rule | `pulso.profile.city-max-length` | `ProfilePatch` | `120` | igual + HTML | `application.yml` | all |
| api-rule | `pulso.profile.department-max-length` | `ProfilePatch` | `120` | igual + HTML | `application.yml` | all |
| domain-data | `profileKind` | `ProfilePatch` | `PERSON`, `ORGANIZATION` | validación | dominio | all |
| domain-data | `profileVisibility` | `ProfilePatch` | `PUBLIC`, `MEMBERS_ONLY`, `PRIVATE` | validación | dominio | all |
| presentation | `api.profile.invalid.title` | `ApiExceptionHandler` | `Invalid profile` | `400` | `messages.properties` | all |
| presentation | `api.profile.blank` | `ProfilePatch` | `{{field}} must not be blank.` | `400` | `messages.properties` | all |
| presentation | `api.profile.max-length` | `ProfilePatch` | `{{field}} must not exceed {{n}} characters.` | `400` | `messages.properties` | all |
| presentation | `api.profile.enum` | `ProfilePatch` | `{{field}} must be one of …` | `400` | `messages.properties` | all |
| presentation | `api.profile.not-editable` | `ProfilePatchRequest` | `Field '{{name}}' is not editable.` | `400` | `messages.properties` | all |
| presentation | `api.profile.not-found.title` | `ApiExceptionHandler` | `Profile not found` | `404` | `messages.properties` | all |
| presentation | `api.profile.not-found.detail` | `ApiExceptionHandler` | `The authenticated profile was not found.` | `404` | `messages.properties` | all |
| presentation | `api.unauthorized.title` | `ApiExceptionHandler` | `Unauthorized` | `401` | `messages.properties` | all |
| presentation | `api.unauthorized.detail` | `ApiExceptionHandler` | `The authenticated subject is invalid.` | `401` | `messages.properties` | all |
| presentation | `profile.not-found.exception` | `ProfileNotFoundException` | `Authenticated profile was not found.` | handler | unificar con `api.profile.not-found.detail` | all |
| presentation | `auth.subject.exception` | `InvalidAuthenticatedSubjectException` | `Authenticated subject is invalid.` | handler | unificar con `api.unauthorized.detail` | all |

### Backend · preference

| Clase | Nombre | Ubicación actual | Valor | Consumidor | Fuente destino | Ambiente |
| --- | --- | --- | --- | --- | --- | --- |
| api-rule | `pulso.preferences.max-items-per-field` | `MAX_ITEMS_PER_FIELD` | `10` | `PUT /api/v1/me/preferences` | `application.yml` | all |
| api-rule | `preferences.path` | `OnboardingPreferencesController` | `/api/v1/me/preferences` | Angular (futuro adapter) / API | contrato HTTP, no env | all |
| api-rule | `pulso.preferences.max-token-length` | `MAX_TOKEN_LENGTH` | `40` | igual | `application.yml` | all |
| api-rule | `pulso.preferences.sector-id-max-length` | `normalizeSectorReference` | `120` | igual | `application.yml` | all |
| domain-data | intenciones | `ALLOWED_INTENTION_SET` | `DISCOVER`, `BUY`, `COLLABORATE`, `WORK`, `INVEST`, `FIND_PARTNERS`, `FIND_CUSTOMERS`, `FIND_PROVIDERS`, `EXPLORE_ECOSYSTEM` | validación | dominio | all |
| domain-data | etapas preferidas | `ALLOWED_STAGE_SET` | mismas que proyecto | validación | dominio compartido de stage | all |
| presentation | `api.preferences.invalid.title` | `ApiExceptionHandler` | `Invalid preferences` | `400` | `messages.properties` | all |
| presentation | `api.preferences.required-profile` | `OnboardingPreferences` | `profileId is required.` | `400` | `messages.properties` | all |
| presentation | `api.preferences.must-be-array` | igual | `{{field}} must be an array.` | `400` | `messages.properties` | all |
| presentation | `api.preferences.max-items` | igual | `{{field}} must not contain more than 10 items.` | `400` | `messages.properties` | all |
| presentation | `api.preferences.strings-only` | igual | `{{field}} must contain strings only.` | `400` | `messages.properties` | all |
| presentation | `api.preferences.duplicates` | igual / `SaveOnboardingPreferences` | `{{field}} must not contain duplicates: '{{value}}'.` | `400` | `messages.properties` | all |
| presentation | `api.preferences.blank` | igual | `{{field}} must not contain blank values.` | `400` | `messages.properties` | all |
| presentation | `api.preferences.unknown` | igual | `{{field}} contains unknown value '{{value}}'.` | `400` | `messages.properties` | all |
| presentation | `api.preferences.invalid-token` | igual | `{{field}} contains an invalid value.` | `400` | `messages.properties` | all |
| presentation | `api.preferences.invalid-sector` | igual | `preferredSectors contains an invalid identifier.` | `400` | `messages.properties` | all |
| presentation | `api.preferences.inactive-sector` | `SaveOnboardingPreferences` | `preferredSectors contains unknown or inactive sector '{{value}}'.` | `400` | `messages.properties` | all |
| presentation | `api.preferences.auth-required` | use cases | `authenticatedProfileId is required.` | `400` | `messages.properties` | all |
| presentation | `api.preferences.body-required` | `SaveOnboardingPreferences` | `preferences are required.` | `400` | `messages.properties` | all |
| presentation | `api.preferences.wrong-owner` | `SaveOnboardingPreferences` | `preferences do not belong to the authenticated profile.` | `400` | `messages.properties` | all |
| presentation | `api.preferences.unknown-field` | `OnboardingPreferencesRequest` | `Field '{{name}}' is not accepted.` | `400` | `messages.properties` | all |
| environment | `preferences.invalid-json` | JDBC adapter | `Stored onboarding preferences contain invalid JSON.` | persistencia | log / excepción interna | all |
| environment | `preferences.serialize-failed` | JDBC adapter | `Onboarding preferences could not be serialized.` | persistencia | log / excepción interna | all |

### Backend · project

| Clase | Nombre | Ubicación actual | Valor | Consumidor | Fuente destino | Ambiente |
| --- | --- | --- | --- | --- | --- | --- |
| api-rule | `pulso.project.slug-min-length` | `ProjectDraft` | `2` | `POST /api/v1/projects` | `application.yml` | all |
| api-rule | `project.path` | `ProjectController` | `/api/v1/projects` | clientes autenticados | contrato HTTP, no env | all |
| api-rule | `pulso.project.slug-max-length` | `ProjectDraft` | `120` | igual | `application.yml` | all |
| api-rule | `pulso.project.name-max-length` | `ProjectDraft` | `160` | igual | `application.yml` | all |
| api-rule | `pulso.project.tagline-max-length` | `ProjectDraft` | `240` | igual | `application.yml` | all |
| api-rule | `pulso.project.description-max-length` | `ProjectDraft` | `5000` | igual | `application.yml` | all |
| api-rule | `pulso.project.city-max-length` | `ProjectDraft` | `120` | igual | `application.yml` | all |
| api-rule | `pulso.project.stage-token-max-length` | `ProjectDraft` | `40` | igual | `application.yml` | all |
| domain-data | `project.stage` | `ALLOWED_STAGES` | `IDEA`, `PROTOTYPE`, `MVP`, `EARLY_STAGE`, `GROWING`, `ESTABLISHED` | validación | dominio | all |
| presentation | `api.project.invalid.title` | `ApiExceptionHandler` | `Invalid project` | `400` | `messages.properties` | all |
| presentation | `api.project.conflict.title` | `ApiExceptionHandler` | `Project conflict` | `409` | `messages.properties` | all |
| presentation | `api.project.conflict.detail` | `ProjectConflictException` | `A project with the same slug already exists.` | `409` | `messages.properties` | all |
| presentation | `api.project.slug-min` | `ProjectDraft` | `slug must be at least 2 characters.` | `400` | `messages.properties` | all |
| presentation | `api.project.sector-required` | `ProjectDraft` | `sectorId is required.` | `400` | `messages.properties` | all |
| presentation | `api.project.field-required` | `ProjectDraft` | `{{field}} is required.` | `400` | `messages.properties` | all |
| presentation | `api.project.field-blank` | `ProjectDraft` | `{{field}} must not be blank.` | `400` | `messages.properties` | all |
| presentation | `api.project.field-max` | `ProjectDraft` | `{{field}} must not exceed {{n}} characters.` | `400` | `messages.properties` | all |
| presentation | `api.project.stage-enum` | `ProjectDraft` | `stage must be one of …` | `400` | `messages.properties` | all |
| presentation | `api.project.auth-required` | `CreateDraftProject` | `authenticatedProfileId is required.` | `400` | `messages.properties` | all |
| presentation | `api.project.draft-required` | `CreateDraftProject` | `project draft is required.` | `400` | `messages.properties` | all |
| presentation | `api.project.inactive-sector` | `CreateDraftProject` | `sectorId must reference an active sector.` | `400` | `messages.properties` | all |
| presentation | `api.project.unknown-field` | `ProjectCreateRequest` | `Field '{{name}}' is not accepted.` | `400` | `messages.properties` | all |
| domain-data | `project.member.role` | `MembershipAdministration` | `OWNER`, `ADMIN`, `MEMBER` | validación | dominio | all |
| domain-data | `project.member.status` | `MembershipAdministration` | `INVITED`, `ACTIVE`, `LEFT`, `REMOVED` | validación | dominio | all |
| presentation | `membership.admin.permission` | `membership-administration.ts` | `Solo un integrante activo con rol owner o admin puede administrar membresías.` | UI de administración | `frontend/src/app/features/discovery/strings/project.strings.ts` | all |
| presentation | `membership.admin.selfElevation` | `membership-administration.ts` | `No puedes elevar tu propio rol.` | UI de administración | `frontend/src/app/features/discovery/strings/project.strings.ts` | all |
| presentation | `membership.admin.invalidTransition` | `membership-administration.ts` | `Esa membresía no admite esta acción.` | UI de administración | `frontend/src/app/features/discovery/strings/project.strings.ts` | all |
| presentation | `membership.admin.ownerRequired` | `membership-administration.ts` | `El proyecto debe conservar al menos un owner activo.` | UI de administración | `frontend/src/app/features/discovery/strings/project.strings.ts` | all |
| presentation | `api.membership.permission` | `MembershipPermissionException` | `active OWNER or ADMIN membership is required.` / `cannot elevate own role.` | `403` Target | `messages.properties` | all |
| presentation | `api.membership.invalid-transition` | `MembershipInvalidTransitionException` | `cannot {action} a membership with status {status}.` | `409` Target | `messages.properties` | all |
| presentation | `api.membership.owner-required` | `MembershipOwnerInvariantException` | `project must keep at least one active OWNER.` | `409` Target | `messages.properties` | all |

## Qué no entra en una extracción posterior

- Secretos y passwords, incluso fixtures locales.
- Filas de catálogo y contenido de proyectos.
- Paths HTTP versionados (`/api/v1/...`) como variables de ambiente.
- Rate limits: no hay valor actual que extraer.
- Constantes de UI que no son reglas de API: `GESTURE_MAX_WIDTH = 1023` en `startup-card.ts` y el timeout del toast `2600` ms en `discovery-view.ts`. Permanecen en el componente.
- Mensajes de test (`backend/src/test`, `*.spec.ts`) salvo que el test fije el copy de producto; entonces el test debe leer la misma fuente que el runtime.
