import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { currentLanguage } from '../../../core/i18n/language'
import type { ConnectionInboxEntry, RequestInbox } from '../domain/connection'
import { RequestsViewComponent } from './requests-view'

const base = 'http://localhost:8080/api/v1/connection-intents'
const inbox = `${base}/received`

function intent(id: string, senderProfileId: string): ConnectionInboxEntry {
  return {
    request: {
      id, senderProfileId, projectId: 'project', opportunityId: null, reason: 'OTHER',
      message: `Request ${id}`, status: 'PENDING', createdAt: '2026-11-21T12:00:00Z',
    },
    projectName: 'Proyecto de prueba', opportunityTitle: null, conversationId: null,
  }
}

function button(root: HTMLElement, label: string): HTMLButtonElement | undefined {
  return Array.from(root.querySelectorAll('button')).find((control) => control.textContent?.trim() === label)
}

function setup(initialInbox: RequestInbox = 'received') {
  TestBed.configureTestingModule({
    imports: [RequestsViewComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()],
  })
  const fixture = TestBed.createComponent(RequestsViewComponent)
  fixture.componentRef.setInput('initialInbox', initialInbox)
  fixture.detectChanges()
  return { fixture, root: fixture.nativeElement as HTMLElement, http: TestBed.inject(HttpTestingController) }
}

describe('RequestsViewComponent', () => {
  afterEach(() => currentLanguage.set('es'))

  it('emits an inbox choice once and restores the received list when navigation changes', () => {
    const { fixture, root, http } = setup()
    http.expectOne(inbox + '?offset=0&limit=21').flush([])
    fixture.detectChanges()
    const selected = vi.fn()
    fixture.componentInstance.inboxSelected.subscribe(selected)
    button(root, 'Enviadas')!.click()
    http.expectOne(base + '/sent?offset=0&limit=21').flush([])
    expect(selected).toHaveBeenCalledExactlyOnceWith('sent')
    fixture.componentRef.setInput('initialInbox', 'sent')
    fixture.detectChanges()
    http.expectNone(base + '/sent?offset=0&limit=21')
    fixture.componentRef.setInput('initialInbox', 'received')
    fixture.detectChanges()
    http.expectOne(inbox + '?offset=0&limit=21').flush([intent('r1', 'person')])
    fixture.detectChanges()
    expect(root.textContent).toContain('Request r1')
    expect(selected).toHaveBeenCalledTimes(1)
    http.verify()
  })

  it('blocks the API sender and reloads the inbox without declining the requests', () => {
    const { fixture, root, http } = setup()
    const anotherSender = intent('c', 'other')
    http.expectOne(`${inbox}?offset=0&limit=21`).flush([
      intent('a', 'sender'), intent('b', 'sender'), anotherSender,
    ])
    fixture.detectChanges()

    const action = root.querySelector('pulso-profile-block-action')!
    action.querySelector('details')!.open = true
    action.querySelectorAll('button')[1].click()
    http.expectOne({ method: 'POST', url: 'http://localhost:8080/api/v1/profiles/sender/block' })
      .flush(null, { status: 204, statusText: 'No Content' })
    http.expectNone((request) => request.url.endsWith('/decline'))
    http.expectOne(`${inbox}?offset=0&limit=21`).flush([anotherSender])
    fixture.detectChanges()

    expect(root.querySelectorAll('pulso-profile-block-action')).toHaveLength(1)
    expect(root.textContent).toContain('Request c')
    expect(root.textContent).not.toContain('Request a')
    expect(root.querySelector('[role="status"]')?.textContent).toContain('Perfil bloqueado')
    http.verify()
  })

  it('shows a retry after an inbox error and paginates while keeping the response localized', () => {
    currentLanguage.set('en')
    const { fixture, root, http } = setup()
    const firstPage = `${inbox}?offset=0&limit=21`
    http.expectOne(firstPage).flush(null, { status: 503, statusText: 'Unavailable' })
    fixture.detectChanges()
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Could not load requests')
    button(root, 'Try again')!.click()
    http.expectOne(firstPage).flush(Array.from({ length: 21 }, (_, index) => intent(String(index), 'sender')))
    fixture.detectChanges()
    expect(root.querySelectorAll('li')).toHaveLength(20)
    button(root, 'Show more requests')!.click()
    http.expectOne(`${inbox}?offset=20&limit=21`).flush([intent('20', 'sender')])
    fixture.detectChanges()
    expect(root.querySelectorAll('li')).toHaveLength(21)
    http.verify()
  })

  it('uses the authorized conversation ID when accepting and renders the persisted history', () => {
    const { fixture, root, http } = setup()
    const entry = intent('a', 'sender')
    http.expectOne(`${inbox}?offset=0&limit=21`).flush([entry])
    fixture.detectChanges()

    expect(button(root, 'Retirar solicitud')).toBeUndefined()
    button(root, 'Aceptar')!.click()
    http.expectOne({ method: 'POST', url: `${base}/a/accept` })
      .flush({ connectionIntentId: 'a', conversationId: 'conversation-1', status: 'ACTIVE' })
    fixture.detectChanges()
    expect(root.textContent).toContain('Aceptada')
    expect(root.querySelector('a[href="/conversaciones/conversation-1"]')).not.toBeNull()
    expect(button(root, 'Rechazar')).toBeUndefined()

    button(root, 'Actualizar solicitudes')!.click()
    http.expectOne(`${inbox}?offset=0&limit=21`).flush([
      { ...entry, request: { ...entry.request, status: 'ACCEPTED' }, conversationId: 'conversation-1' },
    ])
    fixture.detectChanges()
    expect(root.querySelector('a[href="/conversaciones/conversation-1"]')).not.toBeNull()
    http.verify()
  })

  it('offers only withdrawal for a pending sent request and renders the returned status', () => {
    const { fixture, root, http } = setup('sent')
    const entry = intent('a', 'sender')
    http.expectOne(`${base}/sent?offset=0&limit=21`).flush([entry])
    fixture.detectChanges()
    expect(button(root, 'Aceptar')).toBeUndefined()
    expect(button(root, 'Rechazar')).toBeUndefined()

    button(root, 'Retirar solicitud')!.click()
    http.expectOne({ method: 'POST', url: `${base}/a/withdraw` })
      .flush({ ...entry.request, status: 'WITHDRAWN' })
    fixture.detectChanges()
    expect(root.textContent).toContain('Retirada')
    expect(button(root, 'Retirar solicitud')).toBeUndefined()
    http.verify()
  })
})
