import { request } from 'node:http'

const frontendUrl = process.env.PULSO_SMOKE_FRONTEND_URL ?? 'http://localhost:4200'
const backendUrl = process.env.PULSO_SMOKE_BACKEND_URL ?? 'http://localhost:8080'
const cdpPort = Number(process.env.PULSO_SMOKE_CDP_PORT ?? '9222')
const testEmail = `pulso-browser-${Date.now()}@example.test`
const testPassword = 'PulsoBrowser-Auth-123456'
let activeBrowser

async function main() {
  const browser = await connectToBrowser(cdpPort)
  activeBrowser = browser
  const apiRequests = []
  const extraHeadersByRequestId = new Map()

  browser.on('Network.requestWillBeSent', (event) => {
    if (event.request.url.includes('/api/v1/')) {
      const extraHeaders = extraHeadersByRequestId.get(event.request.requestId)
      if (extraHeaders) {
        event.request.extraHeaders = extraHeaders
        extraHeadersByRequestId.delete(event.request.requestId)
      }
      apiRequests.push(event.request)
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
  await waitForText(browser, '1 intención seleccionada.', 'la selección de intenciones')
  await clickButton(browser, 'Entrar a Pulso', 'la entrada a discovery')

  // Discovery
  await waitForText(browser, 'por ver', 'el feed de discovery')
  const discoveryRequest = apiRequests.find((request) =>
    request.url.includes('/api/v1/me/discovery-feed') || request.url.includes('/api/v1/startups'))
  if (!discoveryRequest || !hasBearerHeader(discoveryRequest)) {
    throw new Error('El navegador no envió el bearer token al request de discovery del backend.')
  }

  // Interés
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
  const feedbackRequest = apiRequests.find((request) =>
    request.url.includes('/api/v1/me/project-feedback/') && request.method === 'PUT')
  if (!feedbackRequest || !hasBearerHeader(feedbackRequest)) {
    throw new Error('El navegador no envió el interés al backend con el bearer token.')
  }

  // Cierre de sesión
  await clickButton(browser, 'Perfil', 'la navegación a Perfil')
  await clickButton(browser, 'Cerrar sesión', 'el cierre de sesión')
  await waitForText(browser, 'Conecta con las ideas que mueven Bolivia.', 'la página de acceso tras cerrar sesión')

  await browser.close()
  console.log('Smoke del navegador validado: registro, sesión persistida, /me, onboarding, discovery, interés persistido y logout.')
}

async function clickButton(browserSession, label, description) {
  await waitFor(
    browserSession,
    `[...document.querySelectorAll('button')].some((button) =>
      button.textContent?.replace(/\\s+/g, ' ').trim() === ${JSON.stringify(label)}
      || button.textContent?.includes(${JSON.stringify(label)}))`,
    description,
  )
  await browserSession.evaluate(`
  (() => {
    const buttons = [...document.querySelectorAll('button')].filter((button) => !button.disabled && button.offsetParent !== null)
    const normalized = (button) => button.textContent?.replace(/\\s+/g, ' ').trim() ?? ''
    const target = buttons.find((button) => normalized(button) === ${JSON.stringify(label)})
      ?? buttons.find((button) => normalized(button).includes(${JSON.stringify(label)}))
    target?.click()
  })()
  `)
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
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set
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
    await this.send('Page.reload', { ignoreCache: true })
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
