import { signal } from '@angular/core'
import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it } from 'vitest'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { MemberInvitationFormComponent } from './member-invitation-form'

const path = 'http://localhost:8080/api/v1/projects/project-1/members'

function setup(role = 'OWNER') {
  const auth = { session: signal<{ id: string; email: string } | null>({ id: 'actor-1', email: 'owner@example.test' }) }
  TestBed.configureTestingModule({ imports: [MemberInvitationFormComponent], providers: [
    provideHttpClient(), provideHttpClientTesting(), { provide: SupabaseAuthGateway, useValue: auth },
  ] })
  const fixture = TestBed.createComponent(MemberInvitationFormComponent)
  fixture.componentRef.setInput('project', { projectId: 'project-1', name: 'EcoPack', role, membershipStatus: 'ACTIVE' })
  fixture.detectChanges()
  return { fixture, auth, http: TestBed.inject(HttpTestingController) }
}

describe('MemberInvitationFormComponent', () => {
  it.each(['MEMBER', 'unknown'])('hides administration for role %s', (role) => {
    const { fixture, http } = setup(role)
    expect(fixture.nativeElement.querySelector('form')).toBeNull()
    fixture.componentInstance.submit()
    http.expectNone(path)
    http.verify()
  })

  it('hides administration after the session ends and cancels a pending request', () => {
    const { fixture, auth, http } = setup('ADMIN')
    fixture.componentInstance.email.setValue('ada@example.test')
    fixture.componentInstance.submit()
    const pending = http.expectOne(path)
    fixture.componentInstance.submit()
    http.expectNone(path)
    auth.session.set(null)
    fixture.detectChanges()
    expect(pending.cancelled).toBe(true)
    expect(fixture.nativeElement.querySelector('form')).toBeNull()
    http.verify()
  })

  it.each(['', 'not-an-email', 'a'.repeat(250) + '@example.test'])('validates invalid email without sending a request', (email) => {
    const { fixture, http } = setup()
    fixture.componentInstance.email.setValue(email)
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'))
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Escribe un correo válido')
    expect(fixture.nativeElement.querySelector('input').getAttribute('aria-invalid')).toBe('true')
    http.expectNone(path)
    http.verify()
  })

  it.each([400, 403, 404, 409])('shows a safe useful failure for status %s without false success', (status) => {
    const { fixture, http } = setup()
    fixture.componentInstance.email.setValue('ada@example.test')
    fixture.componentInstance.submit()
    http.expectOne(path).flush({ detail: 'sensitive membership data' }, { status, statusText: 'Failure' })
    fixture.detectChanges()
    const messages: Record<number, string> = { 400: 'Revisa el correo', 403: 'Solo un owner o admin',
      404: 'Este proyecto ya no está disponible', 409: 'Ya existe una invitación activa' }
    expect(fixture.nativeElement.textContent).toContain(messages[status])
    expect(fixture.nativeElement.textContent).not.toContain('sensitive membership data')
    expect(fixture.nativeElement.textContent).not.toContain('Invitación registrada')
    http.verify()
  })

  it('retries a transient failure and retains form state during a session refresh', () => {
    const { fixture, auth, http } = setup()
    fixture.componentInstance.email.setValue('ada@example.test')
    auth.session.set({ id: 'actor-1', email: 'owner@example.test' })
    fixture.detectChanges()
    fixture.componentInstance.submit()
    http.expectOne(path).flush({}, { status: 503, statusText: 'Unavailable' })
    fixture.detectChanges()
    const retry: HTMLButtonElement = fixture.nativeElement.querySelector('.contact-button')
    retry.click()
    http.expectOne(path).flush({ projectId: 'project-1', role: 'MEMBER', membershipStatus: 'INVITED' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Invitación registrada')
    http.verify()
  })

  it('allows an active owner to register an invitation without offering a role selector', () => {
    const { fixture, http } = setup()
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type=email]')
    expect(fixture.nativeElement.querySelector('select')).toBeNull()
    input.value = 'ada@example.test'
    input.dispatchEvent(new Event('input'))
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'))
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Registrando invitación')
    http.expectOne(path).flush({ projectId: 'project-1', role: 'MEMBER', membershipStatus: 'INVITED' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Invitación registrada')
    http.verify()
  })
})
