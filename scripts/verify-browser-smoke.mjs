import { request } from 'node:http'
import { randomBytes } from 'node:crypto'

const frontendUrl = process.env.PULSO_SMOKE_FRONTEND_URL ?? 'http://localhost:4200'
const backendUrl = process.env.PULSO_SMOKE_BACKEND_URL ?? 'http://localhost:8080'
const cdpPort = Number(process.env.PULSO_SMOKE_CDP_PORT ?? '9222')
const testEmail = `pulso-browser-${Date.now()}@example.test`
const testPassword = `PulsoBrowser-${randomBytes(12).toString('base64url')}`
const contactFixture = process.env.PULSO_SMOKE_CONTACT_FIXTURE
  ? JSON.parse(process.env.PULSO_SMOKE_CONTACT_FIXTURE)
  : null
let activeBrowser

async function main() {
  const browser = await connectToBrowser(cdpPort)
  activeBrowser = browser
  const apiRequests = []
  const extraHeadersByRequestId = new Map()

  browser.on('Network.requestWillBeSent', (event) => {
    if (event.request.url.includes('/api/v1/')) {
      const request = { requestId: event.requestId, ...event.request }
      const extraHeaders = extraHeadersByRequestId.get(event.requestId)
      if (extraHeaders) {
        request.extraHeaders = extraHeaders
        extraHeadersByRequestId.delete(event.requestId)
      }
      apiRequests.push(request)
    }
  })

  browser.on('Network.requestWillBeSentExtraInfo', (event) => {
    const request = apiRequests.find((item) => item.requestId === event.requestId)
    if (request) {
      request.extraHeaders = event.headers
    } else {
      extraHeadersByRequestId.set(event.requestId, event.headers)
    }
  })

  await browser.send('Page.enable')
  await browser.send('Runtime.enable')
  await browser.send('Network.enable')
  await browser.navigate(frontendUrl)

  // Registro
  await waitForText(browser, 'Conecta con las ideas que mueven Bolivia.', 'la página de acceso')
  await clickButton(browser, 'Crear cuenta', 'el cambio a modo registro')
  await waitForButton(browser, 'Crear mi cuenta', 'el modo de registro de Auth')
  await fillInput(browser, 'input[name="email"]', testEmail)
  await fillInput(browser, 'input[name="password"]', testPassword)
  await browser.evaluate(`document.querySelector('form button[type="submit"]')?.click()`)
  await waitForText(browser, 'Tu próximo descubrimiento empieza aquí.', 'el onboarding tras el registro')

  await browser.reload()
  await waitForText(browser, 'Tu próximo descubrimiento empieza aquí.', 'la sesión persistida tras recargar')

  const meResult = await browser.evaluate(`
  (async () => {
    const storageKey = Object.keys(localStorage).find((key) => key.endsWith('-auth-token'))
    if (!storageKey) return { status: 0, role: null, email: null }
    const storedSession = JSON.parse(localStorage.getItem(storageKey))
    const response = await fetch(${JSON.stringify(`${backendUrl}/api/v1/me`)}, {
      headers: { Authorization: 'Bearer ' + storedSession.access_token },
    })
    const body = await response.json()
    return { status: response.status, role: body.role ?? null, email: body.email ?? null }
  })()
  `)

  if (
    meResult?.status !== 200
    || meResult.role !== 'authenticated'
    || meResult.email !== testEmail
  ) {
    throw new Error('El navegador no pudo validar los claims autenticados en /api/v1/me.')
  }

  // Onboarding
  await clickButton(browser, 'Colaborar', 'la intención Colaborar')
  await waitForText(browser, '1 intención seleccionada.', 'la selección de intereses')

  await clickButton(browser, 'Entrar a Pulso', 'la entrada a discovery')

  // Discovery
  await waitForText(browser, 'por ver', 'el feed de discovery')
  const discoveryRequest = apiRequests.find((request) =>
    request.url.includes('/api/v1/me/discovery-feed') || request.url.includes('/api/v1/startups'))
  if (!discoveryRequest || !hasBearerHeader(discoveryRequest)) {
    throw new Error('El navegador no envió el bearer token al request de discovery del backend.')
  }

  if (contactFixture) {
    await verifyContact(browser, contactFixture)
  } else {
    await browser.evaluate(`document.querySelector('button.discovery-action--interested')?.click()`)
    await waitFor(
      browser,
      `(async () => {
        const storageKey = Object.keys(localStorage).find((key) => key.endsWith('-auth-token'))
        if (!storageKey) return false
        const token = JSON.parse(localStorage.getItem(storageKey)).access_token
        const response = await fetch(${JSON.stringify(`${backendUrl}/api/v1/me/interested-projects?limit=1`)}, {
          headers: { Authorization: 'Bearer ' + token },
        })
        if (response.status !== 200) return false
        const body = await response.json()
        return (body.meta?.totalCount ?? 0) >= 1
      })()`,
      'el interés persistido en el backend',
    )
  }
  const feedbackRequest = apiRequests.find((request) =>
    request.url.includes('/api/v1/me/project-feedback/') && request.method === 'PUT')
  if (!feedbackRequest || !hasBearerHeader(feedbackRequest)) {
    throw new Error('El navegador no envió el interés al backend con el bearer token.')
  }

  await signOut(browser)
  if (contactFixture) {
    await browser.navigate(new URL(contactFixture.sharePath, frontendUrl).href)
    await waitForText(browser, contactFixture.projectName, 'el enlace compartido sin sesión')
    await waitFor(browser, "!document.querySelector('pulso-project-decision') && !document.querySelector('pulso-opportunity-match')",
      'el detalle público sin datos personales')
  }

  await browser.close()
  console.log('Smoke del navegador validado: registro, sesión persistida, /me, onboarding, discovery, interés persistido y logout.')
}

async function verifyContact(browser, fixture) {
  const { sharePath, projectName, sector, tag, ownerEmail, ownerPassword } = fixture
  const detailUrl = new URL(sharePath, frontendUrl).href
  const opportunityMessage = 'Quiero colaborar con esta oportunidad.'
  const generalMessage = 'Solicitud general para revisar.'
  const withdrawnMessage = 'Solicitud general para retirar.'
  const chatMessage = 'Gracias por aceptar. ¿Cuándo podemos conversar?'

  await browser.send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false })
  await clickButton(browser, 'Mostrar filtros', 'los filtros de Descubrir')
  await fillInput(browser, '#project-search', projectName)
  await changeSelect(browser, '#filter-sector', sector)
  await changeSelect(browser, '#filter-stage', 'MVP')
  await fillInput(browser, '#filter-location', 'Cochabamba')
  await browser.evaluate("document.querySelector('#project-search-filters > div > label input').click()")
  if (tag) {
    const tagBox = `[...document.querySelectorAll('#project-search-filters fieldset label')]
      .find(label => label.innerText.trim() === ${JSON.stringify(tag)})?.querySelector('input')`
    await waitFor(browser, `Boolean(${tagBox})`, 'la etiqueta del catálogo')
    await browser.evaluate(`(${tagBox}).focus()`)
    await pressKey(browser, 'Space')
    await waitFor(browser, `(${tagBox}).checked`, 'la selección de etiqueta por teclado')
  }
  await clickButton(browser, projectName, 'el proyecto con los filtros combinados')
  await waitForText(browser, 'Compatibilidad contigo', 'la coincidencia en el detalle')
  await clickButton(browser, 'Ver por qué', 'la explicación de la coincidencia')
  await waitFor(browser, "Boolean(document.querySelector('pulso-opportunity-match [data-status=MISSING_PROFILE_DATA]'))",
    'los factores de perfil faltantes')
  await clickButton(browser, 'Me interesa', 'la decisión en el detalle')
  await waitForText(browser, 'Marcaste este proyecto como interesante.', 'el interés confirmado')
  await pressKey(browser, 'Escape')
  await clickButton(browser, 'Limpiar búsqueda y filtros', 'limpiar todos los filtros')
  await waitFor(browser, "document.querySelector('#project-search').value === '' && !document.querySelector('.search-results')",
    'el regreso al feed sin filtros')

  await browser.navigate(detailUrl)
  await waitForText(browser, 'Marcaste este proyecto como interesante.', 'el interés al abrir el enlace público')
  await clickButton(browser, 'Borrar decisión', 'el borrado del interés')
  await clickButton(browser, 'Descartar', 'el descarte después de borrar el interés')
  await waitForText(browser, 'Descartaste este proyecto.', 'el descarte confirmado')
  await browser.reload()
  await waitForText(browser, 'Descartaste este proyecto.', 'el descarte persistido al recargar')
  await clickButton(browser, 'Borrar decisión', 'el borrado del descarte')
  await waitFor(browser, "[...document.querySelectorAll('pulso-project-decision button')].some(button => button.textContent.trim() === 'Me interesa')",
    'la decisión vacía en el detalle')
  await requestConnection(browser, 'opportunity', opportunityMessage)
  await waitForText(browser, 'Quedó pendiente de respuesta.', 'la confirmación PENDING')
  await clickLink(browser, '/solicitudes/enviadas')
  await browser.reload()
  await expectRow(browser, 'pulso-requests-view', opportunityMessage, 'Pendiente')

  await browser.navigate(detailUrl)
  await requestConnection(browser, 'opportunity', '')
  await waitForText(browser, 'Ya tienes una solicitud pendiente', 'el rechazo de una duplicada')
  await requestConnection(browser, 'project', generalMessage)
  await waitForText(browser, 'Quedó pendiente de respuesta.', 'la solicitud general pendiente')
  await pressKey(browser, 'Escape')
  await signOut(browser)

  await signIn(browser, ownerEmail, ownerPassword)
  await clickButton(browser, 'Notificaciones', 'las notificaciones del owner en escritorio')
  await waitForText(browser, 'Nueva solicitud de conexión', 'el aviso de solicitud')
  await clickLink(browser, '/solicitudes/recibidas')
  await clickRowButton(browser, 'pulso-requests-view', opportunityMessage, 'Aceptar')
  await expectRow(browser, 'pulso-requests-view', opportunityMessage, 'Aceptada')
  await clickRowButton(browser, 'pulso-requests-view', generalMessage, 'Rechazar')
  await expectRow(browser, 'pulso-requests-view', generalMessage, 'Rechazada')
  await browser.reload()
  await expectRow(browser, 'pulso-requests-view', opportunityMessage, 'Aceptada')
  const conversation = await browser.evaluate(`(${rowExpression('pulso-requests-view', opportunityMessage)})
    ?.querySelector('a[href^="/conversaciones/"]')?.getAttribute('href')`)
  if (!conversation) throw new Error('La recibida aceptada no conserva el enlace a la conversación.')
  await clickLink(browser, conversation)
  await waitForText(browser, 'Todavía no hay mensajes.', 'la conversación que abrió el owner')
  await signOut(browser)

  await signIn(browser, testEmail, testPassword)
  await browser.send('Emulation.setDeviceMetricsOverride', { width: 320, height: 568, deviceScaleFactor: 1, mobile: true })
  await clickButton(browser, 'Notificaciones', 'las notificaciones en móvil')
  await waitForText(browser, 'Aceptaron tu solicitud de conexión', 'el aviso de aceptación')
  await waitForText(browser, 'Rechazaron tu solicitud de conexión', 'el aviso de rechazo')
  await waitFor(browser, "document.querySelector('pulso-notifications-view header button')?.getBoundingClientRect().right <= innerWidth",
    'la acción de actualizar completa en el ancho móvil')
  await clickLink(browser, conversation)
  await browser.reload()
  await waitForText(browser, 'Todavía no hay mensajes.', 'la misma conversación del remitente al recargar')
  await waitFor(browser, "document.querySelector('#message-body')?.getBoundingClientRect().bottom < innerHeight",
    'el campo de mensaje visible al abrir la conversación vacía en móvil')
  await fillInput(browser, '#message-body', chatMessage)
  await browser.evaluate("document.querySelector('#message-body').focus()")
  await pressKey(browser, 'Tab')
  await waitFor(browser, "document.activeElement?.textContent.trim() === 'Enviar mensaje'", 'el envío alcanzable por Tab en móvil')
  await pressKey(browser, 'Enter')
  await waitForText(browser, 'Mensaje enviado.', 'la confirmación de envío desde móvil')
  await waitForText(browser, chatMessage, 'el mensaje en el historial del remitente')
  await clickButton(browser, 'Mensajes', 'la bandeja accesible desde la barra móvil')
  await waitFor(browser, "location.pathname === '/conversaciones'", 'la ruta de Mensajes')
  await clickLink(browser, conversation)
  await waitForText(browser, chatMessage, 'la reapertura del hilo desde la bandeja')
  await waitFor(browser, 'innerWidth === 320 && document.documentElement.scrollWidth === 320', 'la conversación sin desbordamiento en móvil')

  await browser.navigate(detailUrl)
  await requestConnection(browser, 'project', withdrawnMessage)
  await waitForText(browser, 'Quedó pendiente de respuesta.', 'la nueva solicitud general')
  await clickLink(browser, '/solicitudes/enviadas')
  await clickRowButton(browser, 'pulso-requests-view', withdrawnMessage, 'Retirar solicitud')
  await browser.reload()
  await expectRow(browser, 'pulso-requests-view', withdrawnMessage, 'Retirada')
  await signOut(browser)

  await signIn(browser, ownerEmail, ownerPassword)
  await clickButton(browser, 'Notificaciones', 'las notificaciones del destinatario')
  await clickLink(browser, conversation)
  await waitForText(browser, chatMessage, 'el mensaje que leyó el destinatario')
  await clickButton(browser, 'Notificaciones', 'el regreso a las notificaciones')
  const notice = `[...document.querySelectorAll('pulso-notifications-view li')]
    .find(row => row.querySelector('a')?.getAttribute('href') === ${JSON.stringify(conversation)}
      && row.querySelector('h2')?.textContent.trim() === 'Nuevo mensaje')`
  await waitFor(browser, `(${notice})?.querySelector('button')?.textContent.includes('Marcar como leída')`, 'el aviso sin leer')
  await browser.evaluate(`(${notice}).querySelector('button').click()`)
  await waitFor(browser, `(${notice})?.querySelector('.mini-badge')?.textContent.trim() === 'Leída'`, 'el aviso leído')
  await browser.reload()
  await clickButton(browser, 'Notificaciones', 'las notificaciones tras recargar')
  await waitFor(browser, `(${notice})?.querySelector('.mini-badge')?.textContent.trim() === 'Leída'`, 'la lectura persistida')
  console.log('Interfaz verificada con dos cuentas en 1280×900 y 320×568: filtros, coincidencia, interés, duplicados, aceptación, rechazo, retiro, conversación y lectura persistidos.')
}

async function requestConnection(browser, context, message) {
  const within = context === 'opportunity' ? '[aria-labelledby="open-opportunities-title"]' : '.project-detail >'
  const root = `pulso-public-project-detail ${within} pulso-connection-request-form`
  await waitFor(browser, `Boolean(document.querySelector(${JSON.stringify(root + ' summary')}))`, 'el control de contacto')
  await browser.evaluate(`document.querySelector(${JSON.stringify(root + ' summary')}).focus()`)
  await pressKey(browser, 'Enter')
  await waitFor(browser, `Boolean(document.querySelector(${JSON.stringify(root + ' details[open]')}))`, 'el formulario mediante teclado')
  await pressKey(browser, 'Tab')
  await waitFor(browser, `document.activeElement.matches(${JSON.stringify(root + ' form select')})`, 'el foco en el motivo')
  await changeSelect(browser, root + ' select', 'COLLABORATE')
  await fillInput(browser, root + ' textarea', message)
  await pressKey(browser, 'Tab')
  await pressKey(browser, 'Tab')
  await waitFor(browser, `document.activeElement.matches(${JSON.stringify(root + ' button[type=submit]')})`,
    'el botón de solicitud alcanzable por teclado')
  await pressKey(browser, 'Enter')
}

async function signOut(browser) {
  if (await browser.evaluate("document.querySelector('[popovertarget=mobile-destinations]')?.getClientRects().length > 0")) {
    await clickButton(browser, 'Más', 'los otros destinos móviles')
  }
  await clickButton(browser, 'Perfil', 'la navegación a Perfil')
  await clickButton(browser, 'Cerrar sesión', 'el cierre de sesión')
  await waitForText(browser, 'Conecta con las ideas que mueven Bolivia.', 'la página de acceso tras cerrar sesión')
}

async function signIn(browser, email, password) {
  await waitForButton(browser, 'Iniciar sesión', 'el acceso con otra cuenta')
  await fillInput(browser, 'input[name="email"]', email)
  await fillInput(browser, 'input[name="password"]', password)
  await browser.evaluate("document.querySelector('form button[type=submit]').click()")
  await waitFor(browser, "Boolean(document.querySelector('pulso-navigation-bar'))", 'la sesión autenticada')
}

async function changeSelect(browser, selector, value) {
  const changed = await browser.evaluate(`(() => {
    const select = document.querySelector(${JSON.stringify(selector)})
    if (!select) return false
    select.value = ${JSON.stringify(value)}
    select.dispatchEvent(new Event('change', { bubbles: true }))
    return select.value === ${JSON.stringify(value)}
  })()`)
  if (!changed) throw new Error(`No se pudo seleccionar el campo ${selector}.`)
}

async function pressKey(browser, name) {
  const keys = { Tab: ['Tab', 9], Enter: ['Enter', 13], Escape: ['Escape', 27], Space: [' ', 32] }
  const [key, windowsVirtualKeyCode] = keys[name]
  const params = { key, code: name, windowsVirtualKeyCode }
  await browser.send('Input.dispatchKeyEvent', name === 'Enter'
    ? { ...params, type: 'keyDown', text: '\r' }
    : { ...params, type: 'rawKeyDown' })
  await browser.send('Input.dispatchKeyEvent', { ...params, type: 'keyUp' })
}

async function clickLink(browser, path) {
  const anchor = `[...document.querySelectorAll('a[href]')].find(link => link.getAttribute('href') === ${JSON.stringify(path)})`
  await waitFor(browser, `Boolean(${anchor})`, 'el enlace al contexto')
  await browser.evaluate(`(${anchor}).click()`)
  await waitFor(browser, `location.pathname === ${JSON.stringify(path)} && document.readyState === 'complete'`,
    'la navegación al contexto')
}

function rowExpression(component, content) {
  return `[...document.querySelectorAll(${JSON.stringify(component + ' li')})].find(row =>
    row.textContent.includes(${JSON.stringify(content)}))`
}

async function expectRow(browser, component, content, status) {
  await waitFor(browser, `(${rowExpression(component, content)})?.textContent.includes(${JSON.stringify(status)})`,
    `una solicitud con estado ${status}`)
}

async function clickRowButton(browser, component, content, label) {
  const button = `[...((${rowExpression(component, content)})?.querySelectorAll('button') ?? [])]
    .find(button => !button.disabled && button.textContent.trim() === ${JSON.stringify(label)})`
  await waitFor(browser, `Boolean(${button})`, `la acción ${label} en la solicitud`)
  await browser.evaluate(`(${button}).click()`)
}

async function clickButton(browserSession, label, description) {
  const target = `(() => {
    const root = document.querySelector('[role="dialog"]') ?? document
    const visible = [...root.querySelectorAll('button')].filter(button => !button.disabled && button.getClientRects().length > 0)
    const names = button => [button.getAttribute('aria-label'), button.textContent].map(name => name?.replace(/\\s+/g, ' ').trim() ?? '')
    return visible.find(button => names(button).includes(${JSON.stringify(label)}))
      ?? visible.find(button => names(button).some(name => name.includes(${JSON.stringify(label)})))
  })()`
  await waitFor(browserSession, `Boolean(${target})`, description)
  await browserSession.evaluate(`(${target}).click()`)
}

async function waitForButton(browserSession, label, description) {
  await waitFor(
    browserSession,
    `[...document.querySelectorAll('button')].some((button) =>
      button.textContent?.replace(/\\s+/g, ' ').trim() === ${JSON.stringify(label)})`,
    description,
  )
}

function hasBearerHeader(request) {
  return [request.headers, request.extraHeaders]
    .filter(Boolean)
    .some((headers) => Object.entries(headers).some(([name, value]) =>
      name.toLowerCase() === 'authorization'
      && typeof value === 'string'
      && value.startsWith('Bearer '),
    ))
}

async function fillInput(browserSession, selector, value) {
  await browserSession.evaluate(`
    (() => {
      const input = document.querySelector(${JSON.stringify(selector)})
      const type = input instanceof HTMLTextAreaElement ? HTMLTextAreaElement : HTMLInputElement
      const setter = Object.getOwnPropertyDescriptor(type.prototype, 'value')?.set
      if (!input || !setter) return false
      setter.call(input, ${JSON.stringify(value)})
      input.dispatchEvent(new Event('input', { bubbles: true }))
      return true
    })()
  `).then((result) => {
    if (result !== true) {
      throw new Error(`No se pudo completar el campo ${selector}.`)
    }
  })
}

async function waitForText(browserSession, expectedText, description) {
  const normalizedText = expectedText.toLowerCase()
  await waitFor(
    browserSession,
    `document.body.innerText.toLowerCase().includes(${JSON.stringify(normalizedText)})`,
    description,
  )
}

async function waitForAnyText(browserSession, expectedTexts, description) {
  const expression = expectedTexts
    .map((expectedText) => `document.body.innerText.toLowerCase().includes(${JSON.stringify(expectedText.toLowerCase())})`)
    .join(' || ')
  await waitFor(browserSession, expression, description)
}

async function waitFor(browserSession, expression, description) {
  const deadline = Date.now() + 20_000
  while (Date.now() < deadline) {
    try {
      if (await browserSession.evaluate(expression)) {
        return
      }
    } catch {
      // A navigation can briefly destroy the current execution context.
    }
    await new Promise((resolve) => setTimeout(resolve, 100))
  }
  const state = await browserSession.evaluate(`({
    authPage: document.body.innerText.includes('Conecta con las ideas que mueven Bolivia.'),
    onboarding: document.body.innerText.includes('Entrar a Pulso'),
    selectedIntention: document.body.innerText.includes('1 intención seleccionada.'),
    feed: document.body.innerText.toLowerCase().includes('por ver'),
    error: Boolean(document.querySelector('[role="alert"]')),
    alert: document.querySelector('[role="alert"]')?.textContent?.trim().slice(0, 120) ?? null,
  })`).catch(() => null)
  const stateMessage = state ? ` Estado: ${JSON.stringify(state)}.` : ''
  throw new Error(`El navegador no mostró ${description} dentro del tiempo límite.${stateMessage}`)
}

async function connectToBrowser(port) {
  const pages = await getJson(`http://127.0.0.1:${port}/json`)
  const page = pages.find((candidate) => candidate.type === 'page' && candidate.webSocketDebuggerUrl)
  if (!page) {
    throw new Error('Chrome no expuso una página depurable para el smoke test.')
  }

  const session = new CdpSession(page.webSocketDebuggerUrl)
  await session.connect()
  return session
}

function getJson(url) {
  return new Promise((resolve, reject) => {
    request(url, (response) => {
      let body = ''
      response.setEncoding('utf8')
      response.on('data', (chunk) => { body += chunk })
      response.on('end', () => {
        if (response.statusCode !== 200) {
          reject(new Error('Chrome no respondió al endpoint de depuración.'))
          return
        }
        try {
          resolve(JSON.parse(body))
        } catch {
          reject(new Error('Chrome devolvió una respuesta de depuración inválida.'))
        }
      })
    }).on('error', () => reject(new Error('No se pudo conectar con Chrome para el smoke test.'))).end()
  })
}

class CdpSession {
  constructor(webSocketUrl) {
    this.webSocketUrl = webSocketUrl
    this.nextId = 1
    this.pending = new Map()
    this.listeners = new Map()
  }

  connect() {
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.socket?.close()
        reject(new Error('Chrome no respondió al abrir la sesión DevTools.'))
      }, 5_000)
      this.socket = new WebSocket(this.webSocketUrl)
      this.socket.onopen = () => {
        clearTimeout(timeout)
        resolve()
      }
      this.socket.onerror = () => {
        clearTimeout(timeout)
        reject(new Error('No se pudo abrir la sesión DevTools de Chrome.'))
      }
      this.socket.onmessage = (message) => this.handleMessage(message.data)
    })
  }

  send(method, params = {}) {
    const id = this.nextId++
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pending.delete(id)
        reject(new Error('Chrome no respondió a una operación DevTools.'))
      }, 10_000)
      this.pending.set(id, {
        resolve: (result) => {
          clearTimeout(timeout)
          resolve(result)
        },
        reject: (error) => {
          clearTimeout(timeout)
          reject(error)
        },
      })
      try {
        this.socket.send(JSON.stringify({ id, method, params }))
      } catch {
        clearTimeout(timeout)
        this.pending.delete(id)
        reject(new Error('No se pudo enviar una operación a Chrome.'))
      }
    })
  }

  on(method, listener) {
    const listeners = this.listeners.get(method) ?? []
    listeners.push(listener)
    this.listeners.set(method, listeners)
  }

  once(method) {
    return new Promise((resolve) => {
      const listener = (event) => {
        const listeners = this.listeners.get(method) ?? []
        this.listeners.set(method, listeners.filter((candidate) => candidate !== listener))
        resolve(event)
      }
      this.on(method, listener)
    })
  }

  async navigate(url) {
    await this.send('Page.navigate', { url })
  }

  async reload() {
    const previousPage = await this.evaluate('performance.timeOrigin')
    await this.send('Page.reload', { ignoreCache: true })
    await waitFor(this, `performance.timeOrigin !== ${previousPage} && document.readyState === 'complete'`, 'la página recargada')
  }

  async evaluate(expression) {
    const result = await this.send('Runtime.evaluate', {
      expression,
      awaitPromise: true,
      returnByValue: true,
    })
    if (result.exceptionDetails) {
      throw new Error('El navegador produjo un error durante la verificación.')
    }
    return result.result?.value
  }

  async close() {
    this.socket.close()
  }

  handleMessage(rawMessage) {
    const message = JSON.parse(rawMessage)
    if (message.id) {
      const pending = this.pending.get(message.id)
      if (!pending) return
      this.pending.delete(message.id)
      if (message.error) {
        pending.reject(new Error('Chrome rechazó una operación DevTools.'))
      } else {
        pending.resolve(message.result ?? {})
      }
      return
    }

    const listeners = this.listeners.get(message.method) ?? []
    for (const listener of listeners) {
      listener(message.params ?? {})
    }
  }
}

main().catch(async (error) => {
  await activeBrowser?.close().catch(() => {})
  console.error(error instanceof Error ? error.message : 'El smoke del navegador falló.')
  process.exitCode = 1
})
