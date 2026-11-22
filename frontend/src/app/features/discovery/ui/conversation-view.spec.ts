import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it } from 'vitest'
import { ConversationViewComponent } from './conversation-view'

const conversationId = 'conversation-1'
const currentProfileId = 'profile-current'
const otherProfileId = 'profile-other'
const messagesUrl = `http://localhost:8080/api/v1/conversations/${conversationId}/messages`

function setup() {
  TestBed.configureTestingModule({
    imports: [ConversationViewComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()],
  })
  const fixture = TestBed.createComponent(ConversationViewComponent)
  fixture.componentRef.setInput('conversationId', conversationId)
  fixture.componentRef.setInput('profileId', currentProfileId)
  fixture.detectChanges()
  return { fixture, root: fixture.nativeElement as HTMLElement, http: TestBed.inject(HttpTestingController) }
}

function message(overrides: Record<string, unknown> = {}) {
  return {
    id: 'message-1', conversationId, senderProfileId: otherProfileId, messageType: 'TEXT',
    status: 'SENT', body: 'Hola, podemos colaborar.', createdAt: '2026-11-21T10:00:00Z',
    editedAt: null, deletedAt: null, ...overrides,
  }
}

function loadMessages(fixture: ReturnType<typeof setup>['fixture'], http: HttpTestingController, data: unknown[]): void {
  http.expectOne(`${messagesUrl}?offset=0`).flush({
    data,
    meta: { offset: 0, limit: 20, hasMore: false },
  })
  fixture.detectChanges()
}

describe('ConversationViewComponent', () => {
  it('lets a participant report a non-deleted conversation message', () => {
    const { fixture, root, http } = setup()
    loadMessages(fixture, http, [message()])

    const form = root.querySelector<HTMLFormElement>('pulso-report-form form')
    expect(form).not.toBeNull()
    form!.querySelector<HTMLSelectElement>('select')!.value = 'ABUSE'
    form!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))

    const report = http.expectOne('http://localhost:8080/api/v1/reports')
    expect(report.request.method).toBe('POST')
    expect(report.request.body).toEqual({ targetMessageId: 'message-1', reason: 'ABUSE', description: undefined })
    report.flush({ id: 'report-1', status: 'OPEN', createdAt: '2026-11-21T10:01:00Z' })
    fixture.detectChanges()

    expect(root.textContent).toContain('Reporte enviado.')
    http.verify()
  })

  it('lets a participant block the other profile from its conversation message', () => {
    const { fixture, root, http } = setup()
    loadMessages(fixture, http, [message()])

    const action = root.querySelector<HTMLElement>('pulso-profile-block-action')
    expect(action).not.toBeNull()
    expect(action!.textContent).toContain('Bloquear perfil')
    action!.querySelector<HTMLDetailsElement>('details')!.open = true
    action!.querySelectorAll<HTMLButtonElement>('button')[1].click()

    const block = http.expectOne(`http://localhost:8080/api/v1/profiles/${otherProfileId}/block`)
    expect(block.request.method).toBe('POST')
    block.flush(null, { status: 204, statusText: 'No Content' })
    fixture.detectChanges()

    expect(action!.textContent).toContain('Perfil bloqueado')
    http.verify()
  })

  it('keeps deleted messages available for context without offering a report action', () => {
    const { fixture, root, http } = setup()
    loadMessages(fixture, http, [message({ status: 'DELETED', body: null })])

    expect(root.querySelector('pulso-report-form')).toBeNull()
    expect(root.querySelector('pulso-profile-block-action')).not.toBeNull()
    http.verify()
  })
})
