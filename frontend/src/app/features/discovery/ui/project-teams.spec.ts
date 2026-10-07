import { signal } from '@angular/core'
import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it } from 'vitest'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { ProjectTeamsComponent } from './project-teams'

const path = 'http://localhost:8080/api/v1/me/projects?offset=0&limit=20'

function setup() {
  const auth = { session: signal<{ id: string; email: string } | null>({ id: 'actor-1', email: 'actor@example.test' }) }
  TestBed.configureTestingModule({ imports: [ProjectTeamsComponent], providers: [
    provideHttpClient(), provideHttpClientTesting(), { provide: SupabaseAuthGateway, useValue: auth },
  ] })
  const fixture = TestBed.createComponent(ProjectTeamsComponent)
  fixture.detectChanges()
  return { fixture, auth, http: TestBed.inject(HttpTestingController) }
}

describe('ProjectTeamsComponent', () => {
  it('refreshes permissions and withdraws admin controls after the backend reports a role change', () => {
    const { fixture, http } = setup()
    http.expectOne(path).flush({ data: [{ projectId: 'p1', name: 'EcoPack', role: 'ADMIN', membershipStatus: 'ACTIVE' }],
      meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('form')).not.toBeNull()
    fixture.nativeElement.querySelector('[data-action=refresh-teams]').click()
    http.expectOne(path).flush({ data: [{ projectId: 'p1', name: 'EcoPack', role: 'MEMBER', membershipStatus: 'ACTIVE' }],
      meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('form')).toBeNull()
    http.verify()
  })

  it('shows an empty state for accounts with no active team', () => {
    const { fixture, http } = setup()
    http.expectOne(path).flush({ data: [], meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Todavía no formas parte de un equipo activo')
    http.verify()
  })

  it('retries load errors without leaking server details', () => {
    const { fixture, http } = setup()
    http.expectOne(path).flush({ detail: 'private membership' }, { status: 503, statusText: 'Unavailable' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).not.toContain('private membership')
    fixture.nativeElement.querySelector('[role=alert] button').click()
    http.expectOne(path).flush({ data: [], meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Todavía no formas parte')
    http.verify()
  })

  it('loads subsequent pages and retains existing teams', () => {
    const { fixture, http } = setup()
    http.expectOne(path).flush({ data: [{ projectId: 'p1', name: 'First', role: 'MEMBER', membershipStatus: 'ACTIVE' }],
      meta: { offset: 0, limit: 20, hasMore: true } })
    fixture.detectChanges()
    const more: HTMLButtonElement = [...fixture.nativeElement.querySelectorAll('button')]
      .find((button: HTMLButtonElement) => button.textContent.trim() === 'Cargar más equipos')
    more.click()
    http.expectOne('http://localhost:8080/api/v1/me/projects?offset=20&limit=20').flush({
      data: [{ projectId: 'p2', name: 'Second', role: 'ADMIN', membershipStatus: 'ACTIVE' }],
      meta: { offset: 20, limit: 20, hasMore: false },
    })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('First')
    expect(fixture.nativeElement.textContent).toContain('Second')
    expect(fixture.nativeElement.querySelectorAll('form')).toHaveLength(1)
    http.verify()
  })

  it('clears stale team and form state and cancels loads when the authenticated account changes', () => {
    const { fixture, auth, http } = setup()
    const pending = http.expectOne(path)
    auth.session.set({ id: 'actor-2', email: 'other@example.test' })
    fixture.detectChanges()
    expect(pending.cancelled).toBe(true)
    http.expectOne(path).flush({ data: [], meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelectorAll('form')).toHaveLength(0)
    http.verify()
  })

  it('shows administration controls only for own active owner and admin memberships', () => {
    const { fixture, http } = setup()
    expect(fixture.nativeElement.textContent).toContain('Cargando tus equipos')
    http.expectOne(path).flush({ data: [
      { projectId: 'p1', name: 'Owner project', role: 'OWNER', membershipStatus: 'ACTIVE' },
      { projectId: 'p2', name: 'Admin project', role: 'ADMIN', membershipStatus: 'ACTIVE' },
      { projectId: 'p3', name: 'Member project', role: 'MEMBER', membershipStatus: 'ACTIVE' },
    ], meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelectorAll('form')).toHaveLength(2)
    const member = fixture.nativeElement.querySelector('[data-project=p3]')
    expect(member.textContent).toContain('Integrante')
    expect(member.querySelector('input')).toBeNull()
    http.verify()
  })
})
