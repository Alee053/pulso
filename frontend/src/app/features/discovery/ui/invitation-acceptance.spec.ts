import { signal } from '@angular/core'
import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { InvitationAcceptanceComponent } from './invitation-acceptance'

const path = 'http://localhost:8080/api/v1/project-members/accept'

function setup(authenticated = true, fragment = '#invitation=private-token') {
  history.replaceState(null, '', '/' + fragment)
  const auth = {
    session: signal<{ id: string; email: string } | null>(authenticated ? { id: 'invitee-1', email: 'ada@example.test' } : null),
    isLoading: signal(false), isConfigured: true,
    initialize: vi.fn().mockResolvedValue(undefined), signOut: vi.fn().mockResolvedValue(undefined),
  }
  TestBed.configureTestingModule({ imports: [InvitationAcceptanceComponent], providers: [
    provideHttpClient(), provideHttpClientTesting(), { provide: SupabaseAuthGateway, useValue: auth },
  ] })
  const fixture = TestBed.createComponent(InvitationAcceptanceComponent)
  fixture.detectChanges()
  return { fixture, auth, http: TestBed.inject(HttpTestingController) }
}

afterEach(() => history.replaceState(null, '', '/'))

describe('InvitationAcceptanceComponent', () => {
  it('removes all token parameters and rejects ambiguous links without a request', () => {
    const { fixture, http } = setup(true, '#invite=first-secret&invitation=second-secret&section=team')
    expect(location.hash).toBe('#section=team')
    expect(fixture.nativeElement.textContent).toContain('El enlace de invitación no es válido')
    expect(fixture.nativeElement.innerHTML).not.toContain('first-secret')
    expect(fixture.nativeElement.innerHTML).not.toContain('second-secret')
    fixture.componentInstance.accept()
    http.expectNone(path)
    http.verify()
  })

  it('keeps the invitation through sign-in without submitting anonymously', () => {
    const { fixture, auth, http } = setup(false)
    expect(fixture.nativeElement.querySelector('pulso-auth-panel')).not.toBeNull()
    fixture.componentInstance.accept()
    http.expectNone(path)
    auth.session.set({ id: 'invitee-1', email: 'ada@example.test' })
    fixture.detectChanges()
    fixture.nativeElement.querySelector('[data-action=accept]').click()
    http.expectOne(path).flush({ projectId: 'project-1', role: 'MEMBER', membershipStatus: 'ACTIVE' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Ya formas parte del equipo')
    http.verify()
  })

  it('handles an empty token locally and preserves unrelated URL parameters', () => {
    const { fixture, http } = setup(true, '?safe=1#invitation=&section=team')
    expect(location.search).toBe('?safe=1')
    expect(location.hash).toBe('#section=team')
    expect(fixture.nativeElement.textContent).toContain('El enlace de invitación no es válido')
    fixture.componentInstance.accept()
    http.expectNone(path)
    http.verify()
  })

  it.each([400, 404])('handles invalid, unavailable, used or mismatched invitations safely (%s)', (status) => {
    const { fixture, http } = setup()
    fixture.nativeElement.querySelector('[data-action=accept]').click()
    http.expectOne(path).flush({ detail: 'private-token belongs to another account' }, { status, statusText: 'Failure' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain(status === 400 ? 'El enlace de invitación no es válido' : 'Revisa tu cuenta')
    expect(fixture.nativeElement.innerHTML).not.toContain('private-token')
    expect(fixture.nativeElement.querySelector('[data-action=accept]')).toBeNull()
    http.verify()
  })

  it('allows a mismatched account to switch and accept with the matching account', async () => {
    const { fixture, auth, http } = setup()
    fixture.componentInstance.accept()
    http.expectOne(path).flush({}, { status: 404, statusText: 'Unavailable' })
    await fixture.componentInstance.changeAccount()
    auth.session.set(null)
    fixture.detectChanges()
    auth.session.set({ id: 'invitee-2', email: 'matching@example.test' })
    fixture.detectChanges()
    fixture.nativeElement.querySelector('[data-action=accept]').click()
    http.expectOne(path).flush({ projectId: 'project-1', role: 'MEMBER', membershipStatus: 'ACTIVE' })
    fixture.detectChanges()
    expect(auth.signOut).toHaveBeenCalledOnce()
    expect(fixture.nativeElement.textContent).toContain('Ya formas parte del equipo')
    http.verify()
  })

  it('prevents duplicate submissions and retries a transient failure using the retained token', () => {
    const { fixture, http } = setup()
    fixture.componentInstance.accept()
    fixture.componentInstance.accept()
    http.expectOne(path).flush({ detail: 'private-token' }, { status: 503, statusText: 'Unavailable' })
    fixture.detectChanges()
    fixture.nativeElement.querySelector('[data-action=accept]').click()
    const request = http.expectOne(path)
    expect(request.request.body).toEqual({ token: 'private-token' })
    request.flush({ projectId: 'project-1', role: 'MEMBER', membershipStatus: 'ACTIVE' })
    fixture.detectChanges()
    fixture.componentInstance.accept()
    http.expectNone(path)
    http.verify()
  })

  it('cancels an acceptance result when the account changes while it is pending', () => {
    const { fixture, auth, http } = setup()
    fixture.componentInstance.accept()
    const request = http.expectOne(path)
    auth.session.set({ id: 'invitee-2', email: 'other@example.test' })
    fixture.detectChanges()
    expect(request.cancelled).toBe(true)
    expect(fixture.nativeElement.textContent).not.toContain('Ya formas parte del equipo')
    http.verify()
  })

  it('preserves success across a session refresh and clears the token after acceptance', () => {
    const { fixture, auth, http } = setup()
    fixture.componentInstance.accept()
    http.expectOne(path).flush({ projectId: 'project-1', role: 'MEMBER', membershipStatus: 'ACTIVE' })
    auth.session.set({ id: 'invitee-1', email: 'ada@example.test' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Ya formas parte del equipo')
    expect(fixture.componentInstance.link.hasToken).toBe(false)
    http.verify()
  })

  it('removes the token from the URL and accepts without rendering the token', () => {
    const { fixture, http } = setup()
    expect(location.hash).toBe('')
    expect(fixture.nativeElement.innerHTML).not.toContain('private-token')
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-action=accept]')
    button.click()
    const request = http.expectOne(path)
    expect(request.request.body).toEqual({ token: 'private-token' })
    request.flush({ projectId: 'project-1', role: 'MEMBER', membershipStatus: 'ACTIVE' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Ya formas parte del equipo')
    expect(fixture.nativeElement.querySelector('[data-action=accept]')).toBeNull()
    http.verify()
  })
})
