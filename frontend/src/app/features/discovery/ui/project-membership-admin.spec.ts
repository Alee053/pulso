import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it } from 'vitest'
import type { CurrentProjectMembership } from '../domain/member-invitation'
import { ProjectMembershipAdminComponent } from './project-membership-admin'

const listPath = 'http://localhost:8080/api/v1/projects/project-1/members?offset=0&limit=20'

function setup(role: CurrentProjectMembership['role'] = 'OWNER') {
  TestBed.configureTestingModule({
    imports: [ProjectMembershipAdminComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()],
  })
  const fixture = TestBed.createComponent(ProjectMembershipAdminComponent)
  fixture.componentRef.setInput('project', {
    projectId: 'project-1', name: 'EcoPack', role, membershipStatus: 'ACTIVE',
  })
  fixture.componentRef.setInput('actorProfileId', 'actor-1')
  fixture.detectChanges()
  return { fixture, http: TestBed.inject(HttpTestingController) }
}

function button(fixture: ReturnType<typeof setup>['fixture'], text: string): HTMLButtonElement {
  const match = [...fixture.nativeElement.querySelectorAll('button')]
    .find((candidate: HTMLButtonElement) => candidate.textContent.trim() === text)
  if (!match) throw new Error(`Button not found: ${text}`)
  return match
}

describe('ProjectMembershipAdminComponent', () => {
  it('shows actions to an active admin and updates an existing member role', () => {
    const { fixture, http } = setup('ADMIN')
    fixture.nativeElement.querySelector('button[aria-expanded]').click()
    http.expectOne(listPath).flush({ data: [{ id: 'member-1', projectId: 'project-1',
      profileId: 'profile-1', displayName: 'Ada', inviteeEmail: null, role: 'MEMBER',
      membershipStatus: 'ACTIVE' }], meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()

    expect(fixture.nativeElement.textContent).toContain('Ada')
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select')
    select.value = 'ADMIN'
    select.dispatchEvent(new Event('change'))
    fixture.detectChanges()
    button(fixture, 'Guardar rol').click()

    const update = http.expectOne('http://localhost:8080/api/v1/projects/project-1/members/member-1')
    expect(update.request.method).toBe('PATCH')
    expect(update.request.body).toEqual({ action: 'changeRole', role: 'ADMIN' })
    update.flush({ id: 'member-1', projectId: 'project-1', profileId: 'profile-1',
      role: 'ADMIN', membershipStatus: 'ACTIVE', createdByProfileId: 'actor-1' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Admin · Activo')
    http.verify()
  })

  it('activates past members and withdraws pending invitations through the same protected endpoint', () => {
    const { fixture, http } = setup()
    fixture.nativeElement.querySelector('button[aria-expanded]').click()
    http.expectOne(listPath).flush({ data: [
      { id: 'left-1', projectId: 'project-1', profileId: 'profile-left', displayName: 'Luis',
        inviteeEmail: null, role: 'MEMBER', membershipStatus: 'LEFT' },
      { id: 'invite-1', projectId: 'project-1', profileId: null, displayName: null,
        inviteeEmail: 'pending@example.test', role: 'MEMBER', membershipStatus: 'INVITED' },
    ], meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('pending@example.test')

    button(fixture, 'Activar').click()
    const activate = http.expectOne('http://localhost:8080/api/v1/projects/project-1/members/left-1')
    expect(activate.request.body).toEqual({ action: 'activate' })
    activate.flush({ id: 'left-1', projectId: 'project-1', profileId: 'profile-left',
      role: 'MEMBER', membershipStatus: 'ACTIVE' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Activo')

    fixture.nativeElement.querySelector('[data-membership-id="invite-1"] button').click()
    const withdraw = http.expectOne('http://localhost:8080/api/v1/projects/project-1/members/invite-1')
    expect(withdraw.request.body).toEqual({ action: 'withdraw' })
    withdraw.flush({ id: 'invite-1', projectId: 'project-1', profileId: null,
      role: 'MEMBER', membershipStatus: 'REMOVED' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Retirado')
    http.verify()
  })

  it('hides administration and performs no roster request for a member', () => {
    const { fixture, http } = setup('MEMBER')
    expect(fixture.nativeElement.querySelector('button[aria-expanded]')).toBeNull()
    http.expectNone((request) => request.url.includes('/members'))
    http.verify()
  })

  it('does not offer self promotion and reports the last-owner invariant from the backend', () => {
    const { fixture, http } = setup('ADMIN')
    fixture.nativeElement.querySelector('button[aria-expanded]').click()
    http.expectOne(listPath).flush({ data: [{ id: 'self-1', projectId: 'project-1',
      profileId: 'actor-1', displayName: 'Owner', inviteeEmail: null, role: 'ADMIN',
      membershipStatus: 'ACTIVE' }], meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select')
    expect([...select.options].map((option) => option.value)).toEqual(['ADMIN', 'MEMBER'])

    select.value = 'MEMBER'
    select.dispatchEvent(new Event('change'))
    fixture.detectChanges()
    button(fixture, 'Guardar rol').click()
    http.expectOne('http://localhost:8080/api/v1/projects/project-1/members/self-1')
      .flush({ code: 'membership_owner_required', detail: 'project must keep at least one active OWNER.' },
        { status: 409, statusText: 'Conflict' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('El proyecto debe conservar al menos un owner activo.')
    expect(fixture.componentInstance.busyMemberId()).toBeNull()
    http.verify()
  })

  it('clears an unsaved role selection when refreshing the roster', () => {
    const { fixture, http } = setup()
    fixture.nativeElement.querySelector('button[aria-expanded]').click()
    http.expectOne(listPath).flush({ data: [{ id: 'member-1', projectId: 'project-1',
      profileId: 'profile-1', displayName: 'Ada', inviteeEmail: null, role: 'MEMBER',
      membershipStatus: 'ACTIVE' }], meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()

    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select')
    select.value = 'ADMIN'
    select.dispatchEvent(new Event('change'))
    fixture.detectChanges()
    expect(select.value).toBe('ADMIN')

    button(fixture, 'Actualizar lista').click()
    http.expectOne(listPath).flush({ data: [{ id: 'member-1', projectId: 'project-1',
      profileId: 'profile-1', displayName: 'Ada', inviteeEmail: null, role: 'MEMBER',
      membershipStatus: 'ACTIVE' }], meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    expect((fixture.nativeElement.querySelector('select') as HTMLSelectElement).value).toBe('MEMBER')
    http.verify()
  })
})
