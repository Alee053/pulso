// Prueba de humo del recorrido crítico del MVP (PUL-32).
//
// Recorre registro → onboarding → discovery → interés → conexión → mensaje
// contra un entorno local ya levantado (Supabase Auth + backend Spring) y
// valida en el camino los permisos básicos, la invisibilidad de proyectos no
// publicados y el bloqueo de acceso directo a tablas a través de PostgREST.
//
// No reinicia ni borra la base. Crea cuatro cuentas de prueba con correos
// únicos y un proyecto que archiva al terminar. Para aprobar el proyecto
// promueve temporalmente una de esas cuentas a MODERATOR y la devuelve a USER
// al final. Ver `docs/engineering/mvp-critical-path.md`.

import { spawnSync } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const apiUrl = trimSlash(process.env.PULSO_SMOKE_API_URL ?? 'http://localhost:8080')
const supabaseUrl = trimSlash(process.env.PULSO_SMOKE_SUPABASE_URL ?? 'http://127.0.0.1:54321')
const publishableKey = process.env.PULSO_SMOKE_SUPABASE_PUBLISHABLE_KEY ?? readFrontendPublishableKey()
const databaseMode = (process.env.PULSO_SMOKE_DB_MODE ?? 'compose').toLowerCase()
const databaseContainer = process.env.PULSO_SMOKE_DB_CONTAINER ?? 'supabase_db_pulso'
const emailDomain = process.env.PULSO_SMOKE_EMAIL_DOMAIN ?? 'example.test'
const reviewerEmail = process.env.PULSO_SMOKE_REVIEWER_EMAIL ?? ''
const reviewerPassword = process.env.PULSO_SMOKE_REVIEWER_PASSWORD ?? ''
const browserFrontend = process.env.PULSO_SMOKE_FRONTEND_URL

const runId = `${Date.now().toString(36)}${randomBytes(3).toString('hex')}`
const password = `Pulso-Smoke-${randomBytes(9).toString('base64url')}`
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

let passedChecks = 0
let currentStep = 'preparación'
const cleanupState = { owner: null, projectId: null, archived: false, promotedReviewerId: null }

async function main() {
  if (!publishableKey) {
    throw new Error(
      'Falta la clave pública de Supabase. Define PULSO_SMOKE_SUPABASE_PUBLISHABLE_KEY '
      + 'o crea frontend/.env.development desde su plantilla.',
    )
  }
  if (!['compose', 'cli', 'skip'].includes(databaseMode)) {
    throw new Error('PULSO_SMOKE_DB_MODE debe ser compose, cli o skip.')
  }
  if (databaseMode === 'skip' && (!reviewerEmail || !reviewerPassword)) {
    throw new Error(
      'Con PULSO_SMOKE_DB_MODE=skip define PULSO_SMOKE_REVIEWER_EMAIL y '
      + 'PULSO_SMOKE_REVIEWER_PASSWORD de una cuenta MODERATOR o ADMIN local.',
    )
  }

  step('0. Servicios disponibles')
  await expectStatus('GET', '/api/health', { expected: 200, label: 'el backend responde /api/health' })
  await expectStatus('GET', '/actuator/health/readiness', {
    expected: 200,
    label: 'el backend está listo y conectado a PostgreSQL',
  })
  const authHealth = await fetch(`${supabaseUrl}/auth/v1/health`, { headers: { apikey: publishableKey } })
  check(authHealth.status === 200, 'Supabase Auth responde /auth/v1/health')

  step('1. Registro')
  const owner = await registerAccount('owner')
  const interested = await registerAccount('interesado')
  const outsider = await registerAccount('externo')
  cleanupState.owner = owner
  for (const account of [owner, interested, outsider]) {
    const me = await expectStatus('GET', '/api/v1/me', {
      token: account.token,
      expected: 200,
      label: `GET /api/v1/me devuelve el perfil de la cuenta ${account.label}`,
    })
    check(me.email === account.email && me.profile?.id === account.id,
      `el perfil de ${account.label} se deriva del JWT`)
  }
  await expectStatus('GET', '/api/v1/me', { expected: 401, label: 'GET /api/v1/me sin token devuelve 401' })
  await expectStatus('GET', '/api/v1/me', {
    token: 'token.invalido.smoke',
    expected: 401,
    label: 'GET /api/v1/me con token inválido devuelve 401',
  })

  step('2. Onboarding')
  const catalogs = await expectStatus('GET', '/api/v1/catalogs', {
    token: interested.token,
    expected: 200,
    label: 'los catálogos activos están disponibles',
  })
  const sector = catalogs.data?.sectors?.[0]
  const skill = catalogs.data?.skills?.[0]
  const tag = catalogs.data?.tags?.[0]
  check(Boolean(sector?.id), 'existe al menos un sector activo para el recorrido')
  check(Boolean(skill?.id) && uuidPattern.test(tag?.id ?? ''), 'hay una habilidad y una etiqueta activas')
  for (const [account, intention] of [[owner, 'FIND_PARTNERS'], [interested, 'COLLABORATE']]) {
    await expectStatus('PATCH', '/api/v1/me', {
      token: account.token,
      body: { displayName: `Smoke ${account.label} ${runId}`, city: 'Cochabamba' },
      expected: 200,
      label: `la cuenta ${account.label} completa su nombre visible`,
    })
    const preferences = await expectStatus('PUT', '/api/v1/me/preferences', {
      token: account.token,
      body: {
        intentions: [intention],
        preferredSectors: [sector.id],
        preferredStages: ['MVP'],
        preferredTags: [tag.id],
        completeOnboarding: true,
      },
      expected: 200,
      label: `la cuenta ${account.label} guarda sus preferencias y completa el onboarding`,
    })
    check(Array.isArray(preferences.intentions) && preferences.intentions.includes(intention),
      `las preferencias de ${account.label} se persisten`)
    const me = await api('GET', '/api/v1/me', { token: account.token })
    check(me.status === 200 && Boolean(me.body?.profile?.onboardingCompletedAt),
      `el perfil de ${account.label} registra onboardingCompletedAt`)
  }

  step('3. Proyecto en borrador y en revisión: no visible')
  const projectName = `Smoke PUL-32 ${runId}`
  const projectSlug = `smoke-pul32-${runId}`.toLowerCase()
  const created = await expectStatus('POST', '/api/v1/projects', {
    token: owner.token,
    body: {
      slug: projectSlug,
      name: projectName,
      tagline: 'Proyecto temporal de la prueba de humo del MVP.',
      description: 'Creado por scripts/verify-critical-path.mjs; se archiva al terminar.',
      sectorId: sector.id,
      city: 'Cochabamba',
      stage: 'MVP',
    },
    expected: 201,
    label: 'la cuenta owner crea un proyecto en DRAFT',
  })
  const projectId = created.project?.id
  check(uuidPattern.test(projectId ?? '') && created.project.status === 'DRAFT'
    && created.owner?.role === 'OWNER' && created.owner?.profileId === owner.id,
  'el proyecto nace en DRAFT con la membresía OWNER del creador')
  cleanupState.projectId = projectId

  await assertProjectHidden(projectId, projectSlug, projectName, interested, 'DRAFT')
  await expectStatus('PATCH', `/api/v1/projects/${projectId}`, {
    token: interested.token,
    body: { tagline: 'Cambio no autorizado' },
    expected: 403,
    label: 'una cuenta sin membresía no puede editar el proyecto',
  })
  await expectStatus('POST', `/api/v1/projects/${projectId}/publication-request`, {
    token: interested.token,
    body: {},
    expected: 403,
    label: 'una cuenta sin membresía no puede pedir la publicación',
  })
  await expectStatus('POST', `/api/v1/projects/${projectId}/publication-request`, {
    body: {},
    expected: 401,
    label: 'pedir la publicación sin token devuelve 401',
  })
  const publicationRequest = await expectStatus('POST', `/api/v1/projects/${projectId}/publication-request`, {
    token: owner.token,
    body: {},
    expected: 200,
    label: 'la cuenta owner pide la publicación',
  })
  check(publicationRequest.status === 'PENDING_REVIEW', 'el proyecto pasa a PENDING_REVIEW')
  await assertProjectHidden(projectId, projectSlug, projectName, interested, 'PENDING_REVIEW')

  step('4. Revisión administrativa')
  await expectStatus('GET', '/api/v1/admin/project-reviews', {
    token: owner.token,
    expected: 403,
    label: 'una cuenta USER no accede a la cola de revisión',
  })
  await expectStatus('POST', `/api/v1/admin/project-reviews/${projectId}/decision`, {
    token: owner.token,
    body: { decision: 'APPROVE', verificationStatus: 'UNVERIFIED' },
    expected: 403,
    label: 'la cuenta owner no puede aprobar su propio proyecto',
  })
  const reviewer = await prepareReviewer()
  const queue = await expectStatus('GET', '/api/v1/admin/project-reviews?offset=0&limit=50', {
    token: reviewer.token,
    expected: 200,
    label: 'la cuenta revisora lee la cola de revisión',
  })
  check(queue.data?.some((item) => item.id === projectId && item.canReview === true),
    'el proyecto pendiente aparece en la cola y se puede revisar')
  const decision = await expectStatus('POST', `/api/v1/admin/project-reviews/${projectId}/decision`, {
    token: reviewer.token,
    body: { decision: 'APPROVE', verificationStatus: 'UNVERIFIED', note: 'Aprobado por la prueba de humo.' },
    expected: 200,
    label: 'la cuenta revisora aprueba el proyecto',
  })
  check(decision.status === 'PUBLISHED' && Boolean(decision.publishedAt), 'el proyecto queda PUBLISHED')

  step('5. Discovery')
  const opportunityTitle = 'Colaboración para la prueba de humo'
  const opportunity = await expectStatus('POST', `/api/v1/projects/${projectId}/opportunities`, {
    token: owner.token,
    body: {
      title: opportunityTitle, description: 'Oportunidad de prueba temporal.',
      opportunityType: 'COLLABORATION', city: 'Cochabamba', remoteAllowed: false,
    },
    expected: 201,
    label: 'la cuenta owner abre una oportunidad',
  })
  await expectStatus('POST', `/api/v1/projects/${projectId}/opportunities/${opportunity.id}/skills`, {
    token: owner.token,
    body: { skillId: skill.id, importance: 'REQUIRED' },
    expected: 201,
    label: 'la cuenta owner asocia una habilidad requerida',
  })
  const filterTag = databaseMode === 'skip' ? null : tag
  if (filterTag) {
    runSql(`insert into public.project_tags (project_id, tag_id) values ('${projectId}', '${filterTag.id}')`)
  }
  const detail = await expectStatus('GET', `/api/v1/projects/${projectSlug}`, {
    token: interested.token,
    expected: 200,
    label: 'el detalle público por slug está disponible',
  })
  check(detail.data?.id === projectId, 'el detalle corresponde al proyecto publicado')
  await expectStatus('GET', `/api/v1/projects/${projectId}`, {
    expected: 200,
    label: 'el detalle público también es visible sin sesión',
  })
  check(detail.data?.reviewNote === undefined, 'el detalle público no expone la nota de revisión')
  const share = await expectStatus('GET', `/api/v1/projects/${projectSlug}/share`, {
    expected: 200,
    label: 'el proyecto publicado ofrece un enlace compartible sin sesión',
  })
  const sharePath = new URL(share.url).pathname
  check(sharePath === `/proyectos/${projectSlug}`, 'el enlace compartido conserva la ruta pública por slug')
  const publicOpportunity = detail.data?.opportunities?.find((item) => item.id === opportunity.id)
  check(publicOpportunity?.requiredSkills.includes(skill.name) && publicOpportunity.createdByProfileId === undefined,
    'el detalle usa el identificador estable de la oportunidad y los datos públicos')
  const matches = await expectStatus('GET', `/api/v1/projects/${projectSlug}/opportunity-matches`, {
    token: interested.token,
    expected: 200,
    label: 'la cuenta interesada lee la coincidencia de oportunidad',
  })
  const match = matches.data?.find((item) => item.opportunityId === opportunity.id)
  check(match?.missingFactors.includes('SKILLS')
    && ['INTENTION', 'SECTOR', 'STAGE', 'LOCATION'].every((key) =>
      match.factors.some((factor) => factor.key === key && factor.status === 'MATCHED'))
    && match.factors.some((factor) => factor.key === 'LOCATION' && factor.reason === 'SAME_CITY'),
  'la coincidencia usa el perfil y el onboarding y devuelve los datos de perfil faltantes')
  if (filterTag) {
    check(match.factors.some((factor) => factor.key === 'INTERESTS' && factor.status === 'MATCHED'),
      'la coincidencia usa la etiqueta del catálogo seleccionada en onboarding')
  }
  const filters = new URLSearchParams({
    q: projectName, sector: sector.slug, stage: 'MVP', location: 'Cochabamba',
    hasOpenOpportunities: 'true', limit: '1',
  })
  if (filterTag) filters.append('tags', filterTag.slug)
  const search = await expectStatus('GET', `/api/v1/projects/search?${filters}`, {
    expected: 200,
    label: 'la búsqueda pública responde',
  })
  check(search.data?.some((item) => item.id === projectId), 'la búsqueda encuentra el proyecto publicado')
  filters.set('offset', '1')
  const nextSearch = await expectStatus('GET', `/api/v1/projects/search?${filters}`, {
    expected: 200,
    label: 'la búsqueda combinada admite paginación',
  })
  check(nextSearch.meta?.offset === 1 && nextSearch.meta?.totalCount === 1 && nextSearch.data?.length === 0,
    'la segunda página conserva el total y no repite el resultado')
  const feed = await expectStatus('GET', '/api/v1/me/discovery-feed?limit=50', {
    token: interested.token,
    expected: 200,
    label: 'el feed de discovery responde',
  })
  check(feed.data?.some((item) => item.id === projectId),
    'el feed de la cuenta interesada incluye el proyecto que coincide con sus preferencias')

  step('6. Interés')
  const feedback = await expectStatus('PUT', `/api/v1/me/project-feedback/${projectId}`, {
    token: interested.token,
    body: { feedbackType: 'INTERESTED' },
    expected: 200,
    label: 'la cuenta interesada marca interés',
  })
  check(feedback.feedbackType === 'INTERESTED', 'la decisión INTERESTED se persiste')
  const interestedProjects = await expectStatus('GET', '/api/v1/me/interested-projects?limit=50', {
    token: interested.token,
    expected: 200,
    label: 'la lista de proyectos con interés responde',
  })
  check(interestedProjects.data?.some((item) => item.id === projectId),
    'el proyecto aparece en «Me interesa»')
  const ownerFeedback = await expectStatus('GET', `/api/v1/me/project-feedback/${projectId}`, {
    token: owner.token,
    expected: 200,
    label: 'otra cuenta consulta su propia decisión',
  })
  check(ownerFeedback.feedbackType === null, 'la decisión de interés no es visible para otra cuenta')
  await verifyDiscoveryDecisions(interested, projectId)

  step('7. Conexión')
  const intent = await expectStatus('POST', `/api/v1/projects/${projectId}/connection-intents`, {
    token: interested.token,
    body: {
      reason: 'COLLABORATE', message: 'Hola, me interesa colaborar (prueba de humo).',
      opportunityId: publicOpportunity.id,
    },
    expected: 201,
    label: 'la cuenta interesada envía una solicitud de conexión',
  })
  check(intent.status === 'PENDING' && intent.senderProfileId === interested.id && intent.opportunityId === opportunity.id,
    'la solicitud queda PENDING con el remitente del JWT y la oportunidad elegida')
  await expectStatus('POST', `/api/v1/projects/${projectId}/connection-intents`, {
    token: interested.token,
    body: { reason: 'COLLABORATE', opportunityId: publicOpportunity.id },
    expected: 409,
    label: 'una segunda solicitud pendiente al mismo contexto devuelve 409',
  })
  const pending = await expectStatus('GET', '/api/v1/connection-intents/pending?limit=50', {
    token: owner.token,
    expected: 200,
    label: 'la cuenta owner lee sus solicitudes entrantes',
  })
  check(Array.isArray(pending) && pending.some((item) => item.id === intent.id),
    'la solicitud aparece en la bandeja del equipo')
  const outsiderPending = await expectStatus('GET', '/api/v1/connection-intents/pending?limit=50', {
    token: outsider.token,
    expected: 200,
    label: 'una cuenta externa lee su bandeja',
  })
  check(!outsiderPending.some((item) => item.id === intent.id),
    'la solicitud no aparece en la bandeja de una cuenta externa')
  const ownerRequestNotice = await findNotification(owner, (item) =>
    item.type === 'CONNECTION_REQUEST' && item.connectionIntentId === intent.id)
  check(Boolean(ownerRequestNotice), 'la cuenta owner recibe la notificación CONNECTION_REQUEST')
  await expectStatus('POST', `/api/v1/connection-intents/${intent.id}/accept`, {
    token: interested.token,
    expected: 403,
    label: 'quien envía la solicitud no puede aceptarla',
  })
  await expectStatus('POST', `/api/v1/connection-intents/${intent.id}/accept`, {
    token: outsider.token,
    expected: 403,
    label: 'una cuenta externa no puede aceptar la solicitud',
  })
  const accepted = await expectStatus('POST', `/api/v1/connection-intents/${intent.id}/accept`, {
    token: owner.token,
    expected: 200,
    label: 'la cuenta owner acepta la solicitud',
  })
  const conversationId = accepted.conversationId
  check(uuidPattern.test(conversationId ?? '') && accepted.status === 'ACTIVE'
    && accepted.participants?.length === 2, 'se crea una conversación privada ACTIVE con dos participantes')
  const responseNotice = await findNotification(interested, (item) =>
    item.type === 'CONNECTION_RESPONSE' && item.conversationId === conversationId)
  check(Boolean(responseNotice), 'la cuenta interesada recibe CONNECTION_RESPONSE con la conversación')
  for (const [account, inbox] of [[interested, 'sent'], [owner, 'received']]) {
    const entries = await expectStatus('GET', `/api/v1/connection-intents/${inbox}`, {
      token: account.token, expected: 200, label: `la cuenta ${account.label} vuelve a leer su bandeja`,
    })
    check(entries.some((item) => item.request.id === intent.id && item.request.status === 'ACCEPTED'
      && item.conversationId === conversationId), `la cuenta ${account.label} recupera la misma conversación`)
  }
  await verifyConnectionResponses(owner, interested, projectId)

  step('8. Mensaje')
  const firstBody = 'Gracias por aceptar. ¿Coordinamos una reunión?'
  const sent = await expectStatus('POST', `/api/v1/conversations/${conversationId}/messages`, {
    token: interested.token,
    body: { body: firstBody },
    expected: 201,
    label: 'la cuenta interesada envía el primer mensaje',
  })
  check(sent.senderProfileId === interested.id && sent.status === 'SENT', 'el mensaje se guarda con el remitente del JWT')
  await expectStatus('POST', `/api/v1/conversations/${conversationId}/messages`, {
    token: owner.token,
    body: { body: 'Claro, escribe tu disponibilidad.' },
    expected: 201,
    label: 'la cuenta owner responde',
  })
  const history = await expectStatus('GET', `/api/v1/conversations/${conversationId}/messages?limit=50`, {
    token: interested.token,
    expected: 200,
    label: 'la cuenta interesada lee el historial',
  })
  check(history.data?.length === 2 && history.data[0].body === firstBody,
    'el historial contiene ambos mensajes en orden cronológico')
  await expectStatus('GET', `/api/v1/conversations/${conversationId}/messages`, {
    token: outsider.token,
    expected: 404,
    label: 'una cuenta externa no puede leer la conversación',
  })
  await expectStatus('POST', `/api/v1/conversations/${conversationId}/messages`, {
    token: outsider.token,
    body: { body: 'Intrusión' },
    expected: 404,
    label: 'una cuenta externa no puede escribir en la conversación',
  })
  await expectStatus('POST', `/api/v1/conversations/${conversationId}/messages`, {
    token: interested.token,
    body: { body: 'Mensaje con remitente falso', senderProfileId: owner.id },
    expected: 400,
    label: 'el cliente no puede elegir el remitente del mensaje',
  })
  const messageNotice = await findNotification(owner, (item) =>
    item.type === 'NEW_MESSAGE' && item.conversationId === conversationId)
  check(Boolean(messageNotice), 'la cuenta owner recibe la notificación NEW_MESSAGE')
  check(messageNotice.body === null, 'la notificación no incluye el texto del mensaje')
  await expectStatus('POST', `/api/v1/me/notifications/${messageNotice.id}/read`, {
    token: outsider.token,
    expected: 404,
    label: 'una cuenta externa no puede marcar una notificación ajena',
  })
  const read = await expectStatus('POST', `/api/v1/me/notifications/${messageNotice.id}/read`, {
    token: owner.token,
    expected: 200,
    label: 'la cuenta owner marca la notificación como leída',
  })
  check(read.read === true, 'la notificación queda leída')
  const reloadedNotice = await findNotification(owner, (item) => item.id === messageNotice.id)
  check(reloadedNotice?.read === true && reloadedNotice.readAt === read.readAt,
    'la lectura de la notificación se conserva al volver a consultar')

  if (browserFrontend) {
    step('8b. Interfaz Angular con dos cuentas')
    const browser = spawnSync(process.execPath, [resolve(repositoryRoot, 'scripts/verify-browser-smoke.mjs')], {
      env: {
        ...process.env,
        PULSO_SMOKE_BACKEND_URL: apiUrl,
        PULSO_SMOKE_CONTACT_FIXTURE: JSON.stringify({
          ownerEmail: owner.email, ownerPassword: password, projectName, sharePath,
          sector: sector.slug, tag: filterTag?.name,
        }),
      },
      stdio: 'inherit',
      timeout: 180_000,
    })
    check(browser.status === 0, 'el flujo de navegador con dos cuentas concluye')
  }

  await expectStatus('POST', `/api/v1/projects/${projectId}/opportunities/${opportunity.id}/actions`, {
    token: owner.token, body: { action: 'CLOSE' }, expected: 200, label: 'la cuenta owner cierra la oportunidad',
  })
  const unavailable = await expectStatus('POST', `/api/v1/projects/${projectId}/connection-intents`, {
    token: interested.token, body: { reason: 'COLLABORATE', opportunityId: opportunity.id },
    expected: 404, label: 'una oportunidad cerrada ya no recibe solicitudes',
  })
  check(unavailable.code === 'connection_context_unavailable', 'la API identifica el contexto que dejó de estar disponible')

  step('9. Acceso directo a tablas (RLS y grants)')
  for (const table of ['projects', 'project_members', 'connection_intents', 'conversations', 'messages', 'notifications']) {
    const anonymous = await postgrest(table, null)
    check(anonymous.status === 401 || anonymous.status === 403,
      `PostgREST rechaza leer ${table} sin sesión (HTTP ${anonymous.status})`)
  }
  for (const table of ['projects', 'connection_intents', 'conversations', 'messages', 'notifications']) {
    const authenticated = await postgrest(table, interested.token)
    check(authenticated.status === 401 || authenticated.status === 403,
      `PostgREST rechaza leer ${table} con una sesión de usuario (HTTP ${authenticated.status})`)
  }
  const memberships = await postgrest('project_members', owner.token)
  check(memberships.status === 200 && Array.isArray(memberships.body)
    && memberships.body.every((row) => row.profile_id === owner.id),
  'project_members por PostgREST solo devuelve las membresías propias')

  step('10. Cierre: archivar el proyecto temporal')
  await archiveProject()
  await expectStatus('GET', `/api/v1/projects/${projectId}`, {
    token: interested.token,
    expected: 404,
    label: 'un proyecto archivado deja de ser visible',
  })
  const history2 = await expectStatus('GET', `/api/v1/conversations/${conversationId}/messages?limit=50`, {
    token: interested.token,
    expected: 200,
    label: 'el historial sigue legible para sus participantes',
  })
  check(history2.data?.length === 2, 'archivar el proyecto conserva los mensajes')

  console.log(`\nRecorrido crítico validado: ${passedChecks} comprobaciones correctas.`)
}

async function verifyDiscoveryDecisions(account, projectId) {
  const token = account.token
  const path = `/api/v1/me/project-feedback/${projectId}`
  let decision = await expectStatus('GET', path, {
    token, expected: 200, label: 'la cuenta recupera su interés persistido',
  })
  check(decision.feedbackType === 'INTERESTED', 'la lectura de la decisión devuelve INTERESTED')
  await expectStatus('PUT', `/api/v1/me/saved-projects/${projectId}`, {
    token, expected: 200, label: 'guardar el proyecto es una acción independiente',
  })
  for (const feedbackType of ['INTERESTED', 'DISMISSED']) {
    await expectStatus('PUT', path, {
      token, body: { feedbackType }, expected: 200, label: `la cuenta registra ${feedbackType}`,
    })
    decision = await expectStatus('GET', path, {
      token, expected: 200, label: `la cuenta vuelve a leer ${feedbackType}`,
    })
    check(decision.feedbackType === feedbackType, `la lectura conserva ${feedbackType}`)
    const cleared = await expectStatus('DELETE', path, {
      token, expected: 200, label: `la cuenta borra ${feedbackType}`,
    })
    check(cleared.feedbackType === null, 'el borrado devuelve la decisión vacía')
  }
  decision = await expectStatus('GET', path, {
    token, expected: 200, label: 'la cuenta vuelve a cargar después de borrar',
  })
  check(decision.feedbackType === null, 'no queda interés ni descarte')
  const saved = await expectStatus('GET', '/api/v1/me/saved-projects', {
    token, expected: 200, label: 'la cuenta consulta sus proyectos guardados',
  })
  check(saved.data.some((item) => item.id === projectId), 'borrar decisiones conserva el proyecto guardado')
}

async function verifyConnectionResponses(owner, sender, projectId) {
  const create = () => expectStatus('POST', `/api/v1/projects/${projectId}/connection-intents`, {
    token: sender.token, body: { reason: 'OTHER' }, expected: 201,
    label: 'la cuenta envía una solicitud general al proyecto',
  })
  const general = await create()
  const path = '/api/v1/connection-intents'
  const pending = await expectStatus('GET', `${path}/sent`, {
    token: sender.token, expected: 200, label: 'el remitente consulta sus solicitudes enviadas',
  })
  check(pending.some((entry) => entry.request.id === general.id && entry.request.status === 'PENDING'),
    'la solicitud general aparece pendiente para su remitente')
  await expectStatus('POST', `${path}/${general.id}/withdraw`, {
    token: owner.token, expected: 403, label: 'el owner no puede retirar una solicitud ajena',
  })
  const declined = await expectStatus('POST', `${path}/${general.id}/decline`, {
    token: owner.token, expected: 200, label: 'el owner rechaza la solicitud general',
  })
  check(declined.status === 'DECLINED', 'la respuesta del servidor indica DECLINED')
  const rejectedNotice = await findNotification(sender, (item) => item.connectionIntentId === general.id
    && item.type === 'CONNECTION_RESPONSE' && item.payload.response === 'DECLINED')
  check(rejectedNotice?.conversationId === null, 'el remitente recibe el aviso de rechazo sin conversación')

  const second = await create()
  const withdrawn = await expectStatus('POST', `${path}/${second.id}/withdraw`, {
    token: sender.token, expected: 200, label: 'el remitente retira una solicitud propia pendiente',
  })
  check(withdrawn.status === 'WITHDRAWN', 'la respuesta del servidor indica WITHDRAWN')
  const sent = await expectStatus('GET', `${path}/sent`, {
    token: sender.token, expected: 200, label: 'el remitente vuelve a cargar sus solicitudes',
  })
  check(sent.some((entry) => entry.request.id === general.id && entry.request.status === 'DECLINED')
    && sent.some((entry) => entry.request.id === second.id && entry.request.status === 'WITHDRAWN'),
  'la bandeja conserva el rechazo y el retiro')
}

async function assertProjectHidden(projectId, projectSlug, projectName, viewer, status) {
  for (const reference of [projectId, projectSlug]) {
    await expectStatus('GET', `/api/v1/projects/${reference}`, {
      token: viewer.token,
      expected: 404,
      label: `un proyecto ${status} no tiene detalle público (${reference === projectId ? 'id' : 'slug'})`,
    })
  }
  await expectStatus('GET', `/api/v1/projects/${projectId}`, {
    expected: 404,
    label: `un proyecto ${status} no tiene detalle público sin sesión`,
  })
  const search = await expectStatus('GET', `/api/v1/projects/search?q=${encodeURIComponent(projectName)}`, {
    expected: 200,
    label: 'la búsqueda pública responde',
  })
  check(!search.data?.some((item) => item.id === projectId), `la búsqueda no devuelve un proyecto ${status}`)
  const feed = await expectStatus('GET', '/api/v1/me/discovery-feed?limit=50', {
    token: viewer.token,
    expected: 200,
    label: 'el feed de discovery responde',
  })
  check(!feed.data?.some((item) => item.id === projectId), `el feed no incluye un proyecto ${status}`)
  await expectStatus('POST', `/api/v1/projects/${projectId}/connection-intents`, {
    token: viewer.token,
    body: { reason: 'COLLABORATE' },
    expected: 404,
    label: `no se puede contactar a un proyecto ${status}`,
  })
  await expectStatus('PUT', `/api/v1/me/saved-projects/${projectId}`, {
    token: viewer.token,
    expected: 404,
    label: `no se puede guardar un proyecto ${status}`,
  })
}

async function prepareReviewer() {
  if (reviewerEmail && reviewerPassword) {
    const reviewer = await signIn(reviewerEmail, reviewerPassword, 'revisora')
    check(true, 'se usa la cuenta revisora configurada en PULSO_SMOKE_REVIEWER_EMAIL')
    return reviewer
  }
  const reviewer = await registerAccount('revisora')
  await expectStatus('GET', '/api/v1/admin/project-reviews', {
    token: reviewer.token,
    expected: 403,
    label: 'la cuenta revisora todavía no tiene permisos de plataforma',
  })
  const output = runSql(
    `update public.profiles set platform_role = 'MODERATOR' `
    + `where id = '${reviewer.id}' and platform_role = 'USER' returning id`,
  )
  check(output.includes(reviewer.id), 'la cuenta revisora se promueve temporalmente a MODERATOR')
  cleanupState.promotedReviewerId = reviewer.id
  return reviewer
}

async function archiveProject() {
  if (!cleanupState.projectId || cleanupState.archived || !cleanupState.owner) return
  const archived = await expectStatus('POST', `/api/v1/projects/${cleanupState.projectId}/archive`, {
    token: cleanupState.owner.token,
    body: {},
    expected: 200,
    label: 'la cuenta owner archiva el proyecto temporal',
  })
  check(archived.status === 'ARCHIVED', 'el proyecto temporal queda ARCHIVED')
  cleanupState.archived = true
}

async function cleanup() {
  try {
    await archiveProject()
  } catch (error) {
    console.error(`No se pudo archivar el proyecto temporal ${cleanupState.projectId}: ${error.message}`)
  }
  if (cleanupState.promotedReviewerId) {
    try {
      runSql(`update public.profiles set platform_role = 'USER' where id = '${cleanupState.promotedReviewerId}'`)
      console.log('Limpieza: la cuenta revisora temporal vuelve a USER.')
    } catch (error) {
      console.error(`No se pudo devolver la cuenta revisora a USER: ${error.message}`)
    }
  }
}

async function registerAccount(label) {
  const email = `pulso-smoke-${label}-${runId}@${emailDomain}`
  const response = await fetch(`${supabaseUrl}/auth/v1/signup`, {
    method: 'POST',
    headers: { apikey: publishableKey, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  if (response.status !== 200) {
    const body = await readJson(response)
    const reason = body?.msg ?? body?.error_description ?? body?.message ?? 'sin detalle'
    throw new Error(
      `Supabase Auth no registró la cuenta ${label} (HTTP ${response.status}: ${reason}). `
      + 'Si Auth rechaza el dominio del correo, define PULSO_SMOKE_EMAIL_DOMAIN.',
    )
  }
  check(true, `Supabase Auth registra la cuenta ${label}`)
  return signIn(email, password, label)
}

async function signIn(email, accountPassword, label) {
  const response = await fetch(`${supabaseUrl}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: publishableKey, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: accountPassword }),
  })
  const body = await readJson(response)
  check(response.status === 200 && typeof body?.access_token === 'string',
    `la cuenta ${label} inicia sesión y obtiene un access token`)
  const id = body.user?.id
  check(uuidPattern.test(id ?? ''), `la cuenta ${label} tiene un id de Auth`)
  return { label, email, id, token: body.access_token }
}

async function findNotification(account, predicate) {
  const deadline = Date.now() + 5_000
  while (Date.now() < deadline) {
    const response = await api('GET', '/api/v1/me/notifications?limit=50', { token: account.token })
    if (response.status !== 200) {
      throw new Error(`GET /api/v1/me/notifications devolvió HTTP ${response.status}.`)
    }
    const match = response.body?.data?.find(predicate)
    if (match) return match
    await new Promise((resolveWait) => setTimeout(resolveWait, 250))
  }
  return null
}

async function postgrest(table, token) {
  const headers = { apikey: publishableKey }
  headers.Authorization = `Bearer ${token ?? publishableKey}`
  const response = await fetch(`${supabaseUrl}/rest/v1/${table}?select=*&limit=50`, { headers })
  return { status: response.status, body: await readJson(response) }
}

async function expectStatus(method, path, { token, body, expected, label }) {
  const response = await api(method, path, { token, body })
  if (response.status !== expected) {
    const detail = typeof response.body === 'object' && response.body
      ? ` (${response.body.code ?? response.body.title ?? 'sin detalle'})`
      : ''
    throw new Error(`${label}: se esperaba HTTP ${expected} y se obtuvo HTTP ${response.status}${detail}.`)
  }
  check(true, label)
  return response.body
}

async function api(method, path, { token, body } = {}) {
  const headers = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  let response
  try {
    response = await fetch(`${apiUrl}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(15_000),
    })
  } catch {
    throw new Error(`No se pudo conectar con el backend en ${apiUrl} (${method} ${path}).`)
  }
  return { status: response.status, body: await readJson(response) }
}

async function readJson(response) {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

function runSql(sql) {
  if (databaseMode === 'skip') {
    throw new Error('PULSO_SMOKE_DB_MODE=skip no permite modificar la base.')
  }
  const psql = ['psql', '-U', 'postgres', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-At', '-c', sql]
  const args = databaseMode === 'compose'
    ? ['compose', 'exec', '-T', 'db', ...psql]
    : ['exec', databaseContainer, ...psql]
  const result = spawnSync('docker', args, { cwd: repositoryRoot, encoding: 'utf8' })
  if (result.error || result.status !== 0) {
    throw new Error(
      `No se pudo ejecutar psql con docker (${databaseMode}). Revisa PULSO_SMOKE_DB_MODE `
      + 'y que el contenedor de PostgreSQL esté levantado.',
    )
  }
  return result.stdout
}

function readFrontendPublishableKey() {
  for (const file of ['frontend/.env.development', 'frontend/.env.development.example']) {
    const path = resolve(repositoryRoot, file)
    if (!existsSync(path)) continue
    const line = readFileSync(path, 'utf8')
      .split(/\r?\n/)
      .find((candidate) => candidate.startsWith('PULSO_SUPABASE_PUBLISHABLE_KEY='))
    const value = line?.slice('PULSO_SUPABASE_PUBLISHABLE_KEY='.length).trim()
    if (value) return value
  }
  return ''
}

function step(name) {
  currentStep = name
  console.log(`\n${name}`)
}

function check(condition, label) {
  if (!condition) {
    throw new Error(`${label}: la comprobación falló.`)
  }
  passedChecks += 1
  console.log(`  ✔ ${label}`)
}

function trimSlash(value) {
  return value.replace(/\/+$/, '')
}

try {
  await main()
} catch (error) {
  console.error(`\n✘ Falló el paso «${currentStep}»: ${error instanceof Error ? error.message : error}`)
  process.exitCode = 1
} finally {
  await cleanup()
}
