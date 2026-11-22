import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { type Type } from '@angular/core'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it } from 'vitest'
import { ConnectionRequestFormComponent } from './connection-request-form'
import { ConversationViewComponent } from './conversation-view'
import { NotificationsViewComponent } from './notifications-view'
import type { InboxNotification } from '../domain/communication'

const api = 'http://localhost:8080/api/v1'

function setup<T>(component: Type<T>) {
  TestBed.configureTestingModule({ imports: [component], providers: [provideHttpClient(), provideHttpClientTesting()] })
  const fixture = TestBed.createComponent(component)
  return { fixture, root: fixture.nativeElement as HTMLElement, http: TestBed.inject(HttpTestingController) }
}

describe('ConnectionRequestFormComponent', () => {
  it('uses the opportunity identifier and keeps the entered reason and text after a rejected submission', () => {
    const { fixture, root, http } = setup(ConnectionRequestFormComponent)
    fixture.componentRef.setInput('projectId', 'project')
    fixture.componentRef.setInput('opportunityId', 'open-opportunity')
    fixture.detectChanges()

    const form = root.querySelector('form')!
    form.querySelector('select')!.value = 'WORK'
    form.querySelector('textarea')!.value = '  Hola  '
    const submit = () => {
      form.dispatchEvent(new Event('submit', { cancelable: true }))
      fixture.detectChanges()
      expect(form.querySelector('button')?.disabled).toBe(true)
      return http.expectOne({ method: 'POST', url: `${api}/projects/project/connection-intents` })
    }
    const outcomes = [
      [409, 'connection_intent_duplicate', 'Ya tienes una solicitud pendiente'],
      [404, 'connection_context_unavailable', 'ya no recibe solicitudes'],
      [403, 'connection_intent_forbidden', 'El contacto está restringido'],
    ] as const
    for (const [status, code, message] of outcomes) {
      const request = submit()
      expect(request.request.body).toEqual({ reason: 'WORK', message: 'Hola', opportunityId: 'open-opportunity' })
      request.flush({ code }, { status, statusText: 'Rejected' })
      fixture.detectChanges()
      expect(root.querySelector('[role=alert]')?.textContent).toContain(message)
    }
    submit().flush({ status: 'PENDING' }, { status: 201, statusText: 'Created' })
    fixture.detectChanges()
    expect(root.querySelector('[role=status]')?.textContent).toContain('Quedó pendiente')
    expect(root.querySelector('a')?.getAttribute('href')).toBe('/solicitudes/enviadas')
    http.verify()
  })
})

describe('ConversationViewComponent', () => {
  const messagesUrl = `${api}/conversations/conversation/messages`
  const message = (id: string, body: string) => ({ id, body, senderProfileId: 'me', status: 'SENT', createdAt: '2026-11-21T12:00:00Z' })

  function conversation() {
    const ui = setup(ConversationViewComponent)
    ui.fixture.componentRef.setInput('conversationId', 'conversation')
    ui.fixture.componentRef.setInput('profileId', 'me')
    ui.fixture.detectChanges()
    return ui
  }

  it('reads history in page order, reports successful delivery, and preserves the draft when restricted', () => {
    const { fixture, root, http } = conversation()
    http.expectOne(messagesUrl + '?offset=0')
      .flush({ data: [message('first', 'Primero')], meta: { offset: 0, limit: 1, hasMore: true } })
    fixture.detectChanges()
    root.querySelector<HTMLButtonElement>('header button')!.click()
    http.expectOne(messagesUrl + '?offset=1')
      .flush({ data: [message('second', 'Segundo')], meta: { offset: 1, limit: 1, hasMore: false } })
    fixture.detectChanges()

    const form = root.querySelector<HTMLTextAreaElement>('#message-body')!.closest('form')!
    const textarea = form.querySelector('textarea')!
    const send = (body: string) => {
      textarea.value = body
      textarea.dispatchEvent(new Event('input'))
      form.dispatchEvent(new Event('submit', { cancelable: true }))
      fixture.detectChanges()
      expect(form.querySelector('button')?.disabled).toBe(true)
      return http.expectOne({ method: 'POST', url: messagesUrl })
    }

    const sent = send(' Tercero ')
    expect(sent.request.body).toEqual({ body: 'Tercero' })
    sent.flush(message('third', 'Tercero'), { status: 201, statusText: 'Created' })
    http.expectOne(messagesUrl + '?offset=2')
      .flush({ data: [message('third', 'Tercero')], meta: { offset: 2, limit: 1, hasMore: false } })
    fixture.detectChanges()
    expect([...root.querySelectorAll('li p')].map((entry) => entry.textContent?.trim()))
      .toEqual(['Primero', 'Segundo', 'Tercero'])
    expect(form.querySelector('[role=status]')?.textContent).toContain('Mensaje enviado')
    expect(textarea.value).toBe('')

    send('No se perderá').flush({ code: 'messaging_blocked' }, { status: 403, statusText: 'Forbidden' })
    fixture.detectChanges()
    expect(root.querySelector('[role=alert]')?.textContent).toContain('El contacto está restringido')
    expect(textarea.value).toBe('No se perderá')
    expect(form.querySelector('button')?.disabled).toBe(true)
    http.verify()
  })

  it('does not offer a composer when the caller cannot read the conversation', () => {
    const { fixture, root, http } = conversation()
    http.expectOne(messagesUrl + '?offset=0')
      .flush({ code: 'conversation_not_found' }, { status: 404, statusText: 'Not Found' })
    fixture.detectChanges()
    expect(root.querySelector('[role=alert]')?.textContent).toContain('conversación no está disponible')
    expect(root.querySelector('form')).toBeNull()
    http.verify()
  })
})

describe('NotificationsViewComponent', () => {
  it('uses the server counter, paginates, links to contexts, and updates read state from Spring', () => {
    const { fixture, root, http } = setup(NotificationsViewComponent)
    const notificationUrl = `${api}/me/notifications`
    const first: InboxNotification = {
      id: 'message', type: 'NEW_MESSAGE', conversationId: 'conversation', payload: {},
      connectionIntentId: null, read: false, createdAt: '2026-11-21T12:00:00Z',
    }
    const rejected: InboxNotification = {
      ...first, id: 'declined', type: 'CONNECTION_RESPONSE', conversationId: null,
      connectionIntentId: 'request', payload: { response: 'DECLINED' }, read: true,
    }
    fixture.detectChanges()
    http.expectOne(notificationUrl + '?offset=0').flush({
      data: [first], meta: { offset: 0, limit: 1, hasMore: true, unreadCount: 4 },
    })
    fixture.detectChanges()
    const buttonByName = (name: string) => [...root.querySelectorAll('button')].find((button) => button.textContent?.trim() === name)!
    expect(root.textContent).toContain('4 sin leer')
    expect(root.querySelector('a')?.getAttribute('href')).toBe('/conversaciones/conversation')
    buttonByName('Ver más notificaciones').click()
    http.expectOne(notificationUrl + '?offset=1').flush({
      data: [rejected], meta: { offset: 1, limit: 1, hasMore: false, unreadCount: 4 },
    })
    fixture.detectChanges()
    expect(root.textContent).toContain('Rechazaron tu solicitud')
    expect(root.querySelector('a[href="/solicitudes/enviadas"]')).not.toBeNull()

    buttonByName('Marcar como leída').click()
    http.expectOne({ url: notificationUrl + '/message/read', method: 'POST' }).flush({ ...first, read: true })
    http.expectOne(notificationUrl + '?offset=0&limit=1').flush({ data: [], meta: { unreadCount: 2 } })
    fixture.detectChanges()

    expect(root.textContent).toContain('2 sin leer')
    expect(root.querySelector('li .mini-badge')?.textContent?.trim()).toBe('Leída')
    expect(root.querySelector('li button')).toBeNull()
    http.verify()
  })
})
