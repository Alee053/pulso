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

function flushMembers(http: HttpTestingController, roles: Record<string, string>): void {
  for (const [projectId, role] of Object.entries(roles)) {
    http.expectOne(`http://localhost:8080/api/v1/projects/${projectId}/members?offset=0&limit=20`).flush({
      data: [], actor: { role, membershipStatus: 'ACTIVE' }, meta: { offset: 0, limit: 20, hasMore: false },
    })
  }
}

describe('ProjectTeamsComponent', () => {
  it('refreshes permissions and withdraws admin controls after the backend reports a role change', () => {
    const { fixture, http } = setup()
    http.expectOne(path).flush({ data: [{ projectId: 'p1', name: 'EcoPack', role: 'ADMIN', membershipStatus: 'ACTIVE' }],
      meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    flushMembers(http, { p1: 'ADMIN' })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('form')).not.toBeNull()
    fixture.nativeElement.querySelector('[data-action=refresh-teams]').click()
    http.expectOne(path).flush({ data: [{ projectId: 'p1', name: 'EcoPack', role: 'MEMBER', membershipStatus: 'ACTIVE' }],
      meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    flushMembers(http, { p1: 'MEMBER' })
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
    flushMembers(http, { p1: 'MEMBER' })
    fixture.detectChanges()
    const more: HTMLButtonElement = [...fixture.nativeElement.querySelectorAll('button')]
      .find((button: HTMLButtonElement) => button.textContent.trim() === 'Cargar más equipos')
    more.click()
    http.expectOne('http://localhost:8080/api/v1/me/projects?offset=20&limit=20').flush({
      data: [{ projectId: 'p2', name: 'Second', role: 'ADMIN', membershipStatus: 'ACTIVE' }],
      meta: { offset: 20, limit: 20, hasMore: false },
    })
    fixture.detectChanges()
    flushMembers(http, { p2: 'ADMIN' })
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
    flushMembers(http, { p1: 'OWNER', p2: 'ADMIN', p3: 'MEMBER' })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelectorAll('form')).toHaveLength(2)
    const member = fixture.nativeElement.querySelector('[data-project=p3]')
    expect(member.textContent).toContain('Integrante')
    expect(member.querySelector('input')).toBeNull()
    http.verify()
  })

  it('removes invitation and administration controls after demoting own membership', () => {
    const { fixture, http } = setup()
    http.expectOne(path).flush({ data: [{ projectId: 'p1', name: 'EcoPack', role: 'ADMIN', membershipStatus: 'ACTIVE' }],
      meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    const membersPath = 'http://localhost:8080/api/v1/projects/p1/members'
    const self = { id: 'self', projectId: 'p1', profileId: 'actor-1', role: 'ADMIN',
      membershipStatus: 'ACTIVE', displayName: 'Ada', displayTitle: null }
    http.expectOne(`${membersPath}?offset=0&limit=20`).flush({ data: [self], actor: { role: 'ADMIN', membershipStatus: 'ACTIVE' },
      meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement
    select.value = 'MEMBER'
    select.dispatchEvent(new Event('change'))
    fixture.detectChanges()
    fixture.nativeElement.querySelector('[data-action=confirm]').click()
    http.expectOne(`${membersPath}/self`).flush({ ...self, role: 'MEMBER' })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('form')).toBeNull()
    http.expectOne(`${membersPath}?offset=0&limit=20`).flush({ data: [{ ...self, role: 'MEMBER' }],
      actor: { role: 'MEMBER', membershipStatus: 'ACTIVE' }, meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelectorAll('select, form, [data-action=withdraw]')).toHaveLength(0)
    expect(fixture.nativeElement.textContent).toContain('información pública')
    http.verify()
  })

  it('removes a project immediately after own active membership leaves', () => {
    const { fixture, http } = setup()
    http.expectOne(path).flush({ data: [{ projectId: 'p1', name: 'EcoPack', role: 'ADMIN', membershipStatus: 'ACTIVE' }],
      meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    const membersPath = 'http://localhost:8080/api/v1/projects/p1/members'
    const self = { id: 'self', projectId: 'p1', profileId: 'actor-1', role: 'ADMIN',
      membershipStatus: 'ACTIVE', displayName: 'Ada', displayTitle: null }
    http.expectOne(`${membersPath}?offset=0&limit=20`).flush({ data: [self], actor: { role: 'ADMIN', membershipStatus: 'ACTIVE' },
      meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    fixture.nativeElement.querySelector('[data-action=withdraw]').click()
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('perderás su acceso')
    fixture.nativeElement.querySelector('[data-action=confirm]').click()
    http.expectOne(`${membersPath}/self`).flush({ ...self, membershipStatus: 'LEFT' })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('[data-project=p1]')).toBeNull()
    expect(fixture.nativeElement.textContent).toContain('Todavía no formas parte')
    http.verify()
  })

  it('adjusts and cancels stale team pagination when a loaded membership leaves', () => {
    const { fixture, http } = setup()
    const projects = Array.from({ length: 20 }, (_, index) => ({ projectId: `p${index}`, name: `Team ${index}`,
      role: 'MEMBER', membershipStatus: 'ACTIVE' }))
    http.expectOne(path).flush({ data: projects, meta: { offset: 0, limit: 20, hasMore: true } })
    fixture.detectChanges()
    flushMembers(http, Object.fromEntries(projects.map((project) => [project.projectId, project.role])))
    fixture.detectChanges()
    fixture.componentInstance.load()
    const stale = http.expectOne('http://localhost:8080/api/v1/me/projects?offset=20&limit=20')
    fixture.componentInstance.updateOwnMembership({ projectId: 'p0', role: 'MEMBER', membershipStatus: 'LEFT' })
    expect(stale.cancelled).toBe(true)
    fixture.componentInstance.load()
    http.expectOne('http://localhost:8080/api/v1/me/projects?offset=19&limit=20').flush({
      data: [{ projectId: 'p20', name: 'Previously 21st team', role: 'MEMBER', membershipStatus: 'ACTIVE' }],
      meta: { offset: 19, limit: 20, hasMore: false },
    })
    fixture.detectChanges()
    flushMembers(http, { p20: 'MEMBER' })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('[data-project=p0]')).toBeNull()
    expect(fixture.nativeElement.textContent).toContain('Previously 21st team')
    expect(fixture.nativeElement.querySelectorAll('[data-project]')).toHaveLength(20)
    http.verify()
  })
})
