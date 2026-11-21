import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { signal } from '@angular/core'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it } from 'vitest'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { RequestsViewComponent } from './requests-view'

const actorId = 'profile-member'
const senderId = 'profile-sender'
const pendingUrl = 'http://localhost:8080/api/v1/connection-intents/pending'
const messageUrl = 'http://localhost:8080/api/v1/conversations/conversation-1/messages?offset=0&limit=20'
const blockUrl = `http://localhost:8080/api/v1/me/blocks/${senderId}`

function setup() {
  const auth = { session: signal({ id: actorId, email: null }) }
  TestBed.configureTestingModule({
    imports: [RequestsViewComponent],
    providers: [provideHttpClient(), provideHttpClientTesting(), { provide: SupabaseAuthGateway, useValue: auth }],
  })
  const fixture = TestBed.createComponent(RequestsViewComponent)
  fixture.detectChanges()
  return { fixture, http: TestBed.inject(HttpTestingController) }
}

describe('RequestsViewComponent', () => {
  it('opens the accepted conversation and loads the existing private messaging endpoints', () => {
    const { fixture, http } = setup()
    http.expectOne(pendingUrl).flush([{
      id: 'intent-1', fromProfileId: senderId, projectId: 'project-1', status: 'PENDING',
      reason: 'COLLABORATE', message: 'Me interesa colaborar.', createdAt: '2026-11-20T10:00:00Z',
    }])
    fixture.detectChanges()
    fixture.nativeElement.querySelector('button.solid-button').click()
    const accept = http.expectOne('http://localhost:8080/api/v1/connection-intents/intent-1/accept')
    expect(accept.request.method).toBe('POST')
    accept.flush({
      connectionIntentId: 'intent-1', conversationId: 'conversation-1',
      conversationType: 'PROJECT_CONTACT', projectId: 'project-1', status: 'ACTIVE',
      participants: [
        { profileId: senderId, role: 'INITIATOR' },
        { profileId: actorId, role: 'PROJECT_MEMBER' },
      ],
    })
    http.expectOne(pendingUrl).flush([])
    fixture.detectChanges()
    http.expectOne(blockUrl).flush({ targetProfileId: senderId, blocked: false, createdAt: null })
    http.expectOne(messageUrl).flush({ data: [], meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()

    expect(fixture.nativeElement.textContent).toContain('Conversación abierta')
    expect(fixture.nativeElement.textContent).toContain('Perfil de conexión')
    expect(fixture.nativeElement.textContent).toContain('Todavía no hay mensajes en esta conversación.')
    expect(fixture.nativeElement.querySelector('textarea')).not.toBeNull()
    http.verify()
  })

  it('keeps the request and provides retry after an acceptance error', () => {
    const { fixture, http } = setup()
    http.expectOne(pendingUrl).flush([{
      id: 'intent-1', fromProfileId: senderId, projectId: 'project-1', status: 'PENDING',
      reason: 'COLLABORATE', message: null, createdAt: '2026-11-20T10:00:00Z',
    }])
    fixture.detectChanges()
    fixture.nativeElement.querySelector('button.solid-button').click()
    http.expectOne('http://localhost:8080/api/v1/connection-intents/intent-1/accept')
      .flush({}, { status: 403, statusText: 'Forbidden' })
    fixture.detectChanges()

    expect(fixture.nativeElement.textContent)
      .toContain('No pudimos aceptar la solicitud. Actualiza la lista e inténtalo de nuevo.')
    expect(fixture.nativeElement.querySelector('button.solid-button')).not.toBeNull()
    http.verify()
  })
})
