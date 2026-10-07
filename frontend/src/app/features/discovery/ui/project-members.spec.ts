import { signal } from '@angular/core'
import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it, vi } from 'vitest'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { ProjectMembersComponent } from './project-members'

const path = 'http://localhost:8080/api/v1/projects/p1/members'
const member = { id: 'm1', projectId: 'p1', profileId: 'target-1', role: 'MEMBER',
  membershipStatus: 'ACTIVE', displayName: 'Ada', displayTitle: 'Diseñadora' }

function setup(role = 'OWNER') {
  const auth = { session: signal<{ id: string } | null>({ id: 'actor-1' }) }
  TestBed.configureTestingModule({ imports: [ProjectMembersComponent], providers: [provideHttpClient(),
    provideHttpClientTesting(), { provide: SupabaseAuthGateway, useValue: auth }] })
  const fixture = TestBed.createComponent(ProjectMembersComponent)
  fixture.componentRef.setInput('project', { projectId: 'p1', name: 'EcoPack', role, membershipStatus: 'ACTIVE' })
  fixture.detectChanges()
  const http = TestBed.inject(HttpTestingController)
  const page = (data: object[] = [member], actorRole = role, hasMore = false, offset = 0) => {
    http.expectOne(`${path}?offset=${offset}&limit=20`).flush({ data,
      actor: { role: actorRole, membershipStatus: 'ACTIVE' }, meta: { offset, limit: 20, hasMore } })
    fixture.detectChanges()
  }
  const click = (selector: string) => {
    const button = fixture.nativeElement.querySelector(selector) as HTMLButtonElement
    expect(button).not.toBeNull()
    button.click()
    fixture.detectChanges()
  }
  return { fixture, auth, http, page, click }
}

describe('ProjectMembersComponent', () => {
  it('shows the actual role in the selector when its options are first rendered', () => {
    const { fixture, http, page } = setup()
    page()
    expect((fixture.nativeElement.querySelector('select') as HTMLSelectElement).value).toBe('MEMBER')
    http.verify()
  })

  it('confirms a withdrawal and reconciles public fields without losing or duplicating the row', () => {
    const { fixture, http } = setup()
    expect(fixture.nativeElement.textContent).toContain('Cargando integrantes')
    http.expectOne(`${path}?offset=0&limit=20`).flush({ data: [member],
      actor: { role: 'OWNER', membershipStatus: 'ACTIVE' }, meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    fixture.nativeElement.querySelector('[data-action=withdraw]').click()
    fixture.detectChanges()
    http.expectNone(`${path}/m1`)
    expect(fixture.nativeElement.textContent).toContain('Revisa el cambio')
    fixture.nativeElement.querySelector('[data-action=confirm]').click()
    const request = http.expectOne(`${path}/m1`)
    expect(request.request.body).toEqual({ action: 'withdraw' })
    fixture.componentInstance.confirm()
    http.expectNone(`${path}/m1`)
    request.flush({ ...member, membershipStatus: 'REMOVED' })
    http.expectOne(`${path}?offset=0&limit=20`).flush({ data: [{ ...member, membershipStatus: 'REMOVED', displayName: null, displayTitle: null }],
      actor: { role: 'OWNER', membershipStatus: 'ACTIVE' }, meta: { offset: 0, limit: 20, hasMore: false } })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelectorAll('[data-member]')).toHaveLength(1)
    expect(fixture.nativeElement.textContent).toContain('Integrante sin nombre público')
    expect(fixture.nativeElement.textContent).toContain('Retirado')
    expect(fixture.nativeElement.textContent).toContain('Membresía actualizada')
    expect(fixture.nativeElement.querySelector('[data-action=activate]')).not.toBeNull()
    http.verify()
  })

  it('uses authoritative MEMBER permissions even when the parent still says owner', () => {
    const { fixture, http, page } = setup('OWNER')
    const change = vi.fn()
    fixture.componentInstance.ownMembershipChanged.subscribe(change)
    page([member], 'MEMBER')
    expect(fixture.nativeElement.textContent).toContain('Ada')
    expect(fixture.nativeElement.textContent).toContain('información pública')
    expect(fixture.nativeElement.querySelectorAll('select, form, [data-action=withdraw], [data-action=activate]')).toHaveLength(0)
    expect(change).toHaveBeenCalledWith({ projectId: 'p1', role: 'MEMBER', membershipStatus: 'ACTIVE' })
    http.verify()
  })

  it.each(['OWNER', 'ADMIN'])('offers only valid state controls to %s and omits self elevation', (role) => {
    const { fixture, http, page } = setup(role)
    page([
      { ...member, id: 'self', profileId: 'actor-1', role },
      { ...member, id: 'invited', profileId: null, membershipStatus: 'INVITED', displayName: null, displayTitle: null },
      { ...member, id: 'left', membershipStatus: 'LEFT' },
      { ...member, id: 'removed', membershipStatus: 'REMOVED' },
      { ...member, id: 'unbound', profileId: null, membershipStatus: 'REMOVED', displayName: null, displayTitle: null },
    ])
    const self = fixture.nativeElement.querySelector('[data-member=self]') as HTMLElement
    expect(self.textContent).toContain('Tu membresía')
    expect(self.querySelector('[data-action=activate]')).toBeNull()
    if (role === 'ADMIN') expect(self.querySelector('option[value=OWNER]')).toBeNull()
    const invited = fixture.nativeElement.querySelector('[data-member=invited]') as HTMLElement
    expect(invited.textContent).toContain('Invitación sin perfil vinculado')
    expect(invited.querySelector('[data-action=withdraw]')).not.toBeNull()
    expect(invited.querySelector('select, [data-action=activate]')).toBeNull()
    expect(fixture.nativeElement.querySelectorAll('[data-action=activate]')).toHaveLength(2)
    expect(fixture.nativeElement.querySelector('[data-member=unbound] [data-action=activate]')).toBeNull()
    http.verify()
  })

  it('cancels a confirmation with Escape and returns focus without submitting', () => {
    const { fixture, http, page, click } = setup()
    page()
    const opener = fixture.nativeElement.querySelector('[data-action=withdraw]') as HTMLElement
    click('[data-action=withdraw]')
    const panel = fixture.nativeElement.querySelector('[tabindex="-1"]') as HTMLElement
    expect(document.activeElement).toBe(panel)
    panel.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('[data-action=confirm]')).toBeNull()
    expect(document.activeElement).toBe(opener)
    http.expectNone(`${path}/m1`)
    http.verify()
  })

  it('loads overlapping pages without duplicates and retains previously loaded members', () => {
    const { fixture, http, page, click } = setup()
    page([member], 'OWNER', true)
    click('[data-action=more-members]')
    page([member, { ...member, id: 'm2', displayName: 'Leo' }], 'OWNER', false, 20)
    expect(fixture.nativeElement.querySelectorAll('[data-member]')).toHaveLength(2)
    expect(fixture.nativeElement.textContent).toContain('Ada')
    expect(fixture.nativeElement.textContent).toContain('Leo')
    http.verify()
  })

  it('retries the same earlier page after a failed mutation reconciliation and preserves other pages', () => {
    const { fixture, http, page, click } = setup()
    page([member], 'OWNER', true)
    click('[data-action=more-members]')
    page([{ ...member, id: 'm2', displayName: 'Leo' }], 'OWNER', true, 20)
    click('[data-member=m1] [data-action=withdraw]')
    click('[data-action=confirm]')
    http.expectOne(`${path}/m1`).flush({ ...member, membershipStatus: 'REMOVED' })
    http.expectOne(`${path}?offset=0&limit=20`).flush({}, { status: 503, statusText: 'Unavailable' })
    fixture.detectChanges()
    click('[role=alert] button')
    page([{ ...member, membershipStatus: 'REMOVED', displayName: null, displayTitle: null }], 'OWNER', true)
    expect(fixture.nativeElement.querySelectorAll('[data-member]')).toHaveLength(2)
    expect(fixture.nativeElement.textContent).toContain('Leo')
    expect(fixture.nativeElement.querySelector('[data-member=m1]').textContent).not.toContain('Ada')
    click('[data-action=more-members]')
    page([], 'OWNER', false, 40)
    http.verify()
  })

  it('retries a roster failure, shows empty state and hides raw server details', () => {
    const { fixture, http, page, click } = setup()
    http.expectOne(`${path}?offset=0&limit=20`).flush({ detail: 'secret server detail' }, { status: 503, statusText: 'Failure' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).not.toContain('secret server detail')
    click('[role=alert] button')
    page([])
    expect(fixture.nativeElement.textContent).toContain('No hay integrantes visibles')
    http.verify()
  })

  it.each([
    ['membership_owner_required', 'El proyecto debe conservar al menos un owner activo.'],
    ['membership_invalid_transition', 'Esa membresía no admite esta acción.'],
    ['membership_conflict', 'La membresía cambió mientras realizabas la acción.'],
  ])('explains %s and reconciles the roster while preserving the error', (code, message) => {
    const { fixture, http, page, click } = setup()
    page()
    click('[data-action=withdraw]')
    click('[data-action=confirm]')
    http.expectOne(`${path}/m1`).flush({ code, detail: 'private backend data' }, { status: 409, statusText: 'Conflict' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain(message)
    expect(fixture.nativeElement.textContent).not.toContain('private backend data')
    expect(fixture.nativeElement.querySelector('form')).toBeNull()
    page()
    expect(fixture.nativeElement.textContent).toContain(message)
    expect(fixture.nativeElement.querySelectorAll('[data-member]')).toHaveLength(1)
    expect(fixture.nativeElement.textContent).not.toContain('Membresía actualizada')
    http.verify()
  })

  it('withdraws controls and private rows when access is lost', () => {
    const { fixture, http, page, click } = setup()
    page()
    click('[data-action=withdraw]')
    click('[data-action=confirm]')
    http.expectOne(`${path}/m1`).flush({ code: 'membership_permission' }, { status: 403, statusText: 'Forbidden' })
    http.expectOne(`${path}?offset=0&limit=20`).flush({}, { status: 403, statusText: 'Forbidden' })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelectorAll('[data-member], form, select, [data-action=withdraw]')).toHaveLength(0)
    http.verify()
  })

  it('confirms a role change and notifies the parent when own permissions change', () => {
    const { fixture, http, page, click } = setup('ADMIN')
    const changed = vi.fn()
    fixture.componentInstance.ownMembershipChanged.subscribe(changed)
    page([{ ...member, profileId: 'actor-1', role: 'ADMIN' }])
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement
    select.value = 'MEMBER'
    select.dispatchEvent(new Event('change'))
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('El nuevo rol')
    click('[data-action=confirm]')
    const request = http.expectOne(`${path}/m1`)
    expect(request.request.body).toEqual({ action: 'changeRole', role: 'MEMBER' })
    request.flush({ ...member, profileId: 'actor-1', role: 'MEMBER' })
    expect(changed).toHaveBeenCalledWith({ projectId: 'p1', role: 'MEMBER', membershipStatus: 'ACTIVE' })
    http.verify()
  })

  it('reloads the roster when an invitation is registered', () => {
    const { fixture, http, page } = setup()
    page()
    const input = fixture.nativeElement.querySelector('input[type=email]') as HTMLInputElement
    input.value = 'ada@example.test'
    input.dispatchEvent(new Event('input'))
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'))
    http.expectOne(path).flush({ projectId: 'p1', role: 'MEMBER', membershipStatus: 'INVITED' })
    page([member, { ...member, id: 'invite', profileId: null, membershipStatus: 'INVITED', displayName: null }])
    expect(fixture.nativeElement.querySelectorAll('[data-member]')).toHaveLength(2)
    expect(fixture.nativeElement.textContent).toContain('Invitación registrada')
    http.verify()
  })

  it('cancels initial loads on an account switch and keeps same-account refresh stable', () => {
    const { fixture, http, auth, page } = setup()
    const pending = http.expectOne(`${path}?offset=0&limit=20`)
    auth.session.set({ id: 'actor-2' })
    fixture.detectChanges()
    expect(pending.cancelled).toBe(true)
    page([member], 'MEMBER')
    auth.session.set({ id: 'actor-2' })
    fixture.detectChanges()
    http.expectNone(`${path}?offset=0&limit=20`)
    expect(fixture.nativeElement.textContent).toContain('Ada')
    http.verify()
  })

  it('cancels pending actions and clears confirmations on logout', () => {
    const { fixture, http, auth, page, click } = setup()
    page()
    click('[data-action=withdraw]')
    click('[data-action=confirm]')
    const pending = http.expectOne(`${path}/m1`)
    auth.session.set(null)
    fixture.detectChanges()
    expect(pending.cancelled).toBe(true)
    expect(fixture.nativeElement.querySelectorAll('[data-member], form, [data-action=confirm]')).toHaveLength(0)
    http.verify()
  })
})
