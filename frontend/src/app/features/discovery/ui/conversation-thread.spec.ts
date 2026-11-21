import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { signal } from '@angular/core'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it } from 'vitest'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { conversationStrings } from '../strings/conversation.strings'
import { ConversationThreadComponent } from './conversation-thread'

const conversationId = 'conversation-1'
const otherProfileId = 'profile-other'
const messageUrl = `http://localhost:8080/api/v1/conversations/${conversationId}/messages`
const blockUrl = `http://localhost:8080/api/v1/me/blocks/${otherProfileId}`

function setup() {
  const auth = { session: signal({ id: 'profile-current', email: null }) }
  TestBed.configureTestingModule({
    imports: [ConversationThreadComponent],
    providers: [provideHttpClient(), provideHttpClientTesting(), { provide: SupabaseAuthGateway, useValue: auth }],
  })
  const fixture = TestBed.createComponent(ConversationThreadComponent)
  fixture.componentRef.setInput('conversationId', conversationId)
  fixture.componentRef.setInput('otherProfileId', otherProfileId)
  fixture.componentRef.setInput('otherDisplayName', 'Ada')
  fixture.detectChanges()
  return { fixture, http: TestBed.inject(HttpTestingController) }
}

function message(overrides: Record<string, unknown> = {}) {
  return {
    id: 'message-1', conversationId, senderProfileId: otherProfileId, messageType: 'TEXT',
    status: 'SENT', body: 'Hola, podemos colaborar.', createdAt: '2026-11-21T10:00:00Z',
    editedAt: null, deletedAt: null, ...overrides,
  }
}

function flushInitial(fixture: ReturnType<typeof setup>['fixture'], http: HttpTestingController): void {
  http.expectOne(blockUrl).flush({ targetProfileId: otherProfileId, blocked: false, createdAt: null })
  http.expectOne(`${messageUrl}?offset=0&limit=20`).flush({
    data: [message()], meta: { offset: 0, limit: 20, hasMore: false },
  })
  fixture.detectChanges()
}

describe('ConversationThreadComponent', () => {
  it('loads messages, sends text, and reports the selected message through the existing report API', async () => {
    const { fixture, http } = setup()
    flushInitial(fixture, http)
    expect(fixture.nativeElement.textContent).toContain('Hola, podemos colaborar.')

    fixture.componentInstance.form.controls.body.setValue('  Gracias por escribir.  ')
    const sending = fixture.componentInstance.send()
    const send = http.expectOne(messageUrl)
    expect(send.request.method).toBe('POST')
    expect(send.request.body).toEqual({ body: 'Gracias por escribir.' })
    send.flush(message({ id: 'message-2', senderProfileId: 'profile-current', body: 'Gracias por escribir.' }))
    await sending
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Gracias por escribir.')
    expect(fixture.componentInstance.form.controls.body.value).toBe('')

    const report = fixture.nativeElement.querySelector('pulso-report-form form') as HTMLFormElement
    report.querySelector('select')!.value = 'ABUSE'
    report.dispatchEvent(new Event('submit', { cancelable: true }))
    const reporting = http.expectOne('http://localhost:8080/api/v1/reports')
    expect(reporting.request.method).toBe('POST')
    expect(reporting.request.body).toEqual({ targetMessageId: 'message-1', reason: 'ABUSE', description: undefined })
    reporting.flush({ id: 'report-1', status: 'OPEN', createdAt: '2026-11-21T10:01:00Z' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Reporte enviado.')
    http.verify()
  })

  it('keeps the draft message visible after a block prevents sending', async () => {
    const { fixture, http } = setup()
    flushInitial(fixture, http)
    fixture.componentInstance.form.controls.body.setValue('No se debe perder')
    const sending = fixture.componentInstance.send()
    http.expectOne(messageUrl).flush({}, { status: 403, statusText: 'Forbidden' })
    await sending

    expect(fixture.componentInstance.form.controls.body.value).toBe('No se debe perder')
    expect(fixture.componentInstance.sendError()).toBe(conversationStrings.blocked)
    http.verify()
  })

  it('does not show a report control for deleted messages', () => {
    const { fixture, http } = setup()
    http.expectOne(blockUrl).flush({ targetProfileId: otherProfileId, blocked: false, createdAt: null })
    http.expectOne(`${messageUrl}?offset=0&limit=20`).flush({
      data: [message({ status: 'DELETED', body: null })],
      meta: { offset: 0, limit: 20, hasMore: false },
    })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelectorAll('pulso-report-form')).toHaveLength(0)
    expect(fixture.nativeElement.textContent).toContain(conversationStrings.deleted)
    http.verify()
  })
})
