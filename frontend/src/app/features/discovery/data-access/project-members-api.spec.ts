import { provideHttpClient, withInterceptors } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { firstValueFrom } from 'rxjs'
import { describe, expect, it, vi } from 'vitest'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { authInterceptor } from '../../../core/auth/auth.interceptor'
import { ProjectMembersApi } from './project-members-api'

const path = 'http://localhost:8080/api/v1/projects/project-1/members'
const member = { id: 'member-1', projectId: 'project-1', profileId: 'profile-1', role: 'ADMIN',
  membershipStatus: 'ACTIVE', displayName: 'Ada', displayTitle: 'Diseñadora' }

function setup() {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] })
  return { api: TestBed.inject(ProjectMembersApi), http: TestBed.inject(HttpTestingController) }
}

describe('ProjectMembersApi', () => {
  it('loads an authenticated page and retains only the team contract fields', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(), { provide: SupabaseAuthGateway, useValue: {
        getAccessToken: vi.fn().mockResolvedValue('session-token'),
      } },
    ] })
    const http = TestBed.inject(HttpTestingController)
    const result = firstValueFrom(TestBed.inject(ProjectMembersApi).list('project-1'))
    await new Promise<void>((resolve) => queueMicrotask(resolve))
    const request = http.expectOne(`${path}?offset=0&limit=20`)
    expect(request.request.headers.get('Authorization')).toBe('Bearer session-token')
    request.flush({ data: [{ ...member, inviteeEmail: 'private@example.test' }],
      actor: { role: 'OWNER', membershipStatus: 'ACTIVE' }, meta: { offset: 0, limit: 20, hasMore: false } })
    expect(await result).toEqual({ data: [member], actor: { role: 'OWNER', membershipStatus: 'ACTIVE' },
      meta: { offset: 0, limit: 20, hasMore: false } })
    http.verify()
  })

  it.each([-1, 100001, 0.5, NaN])('rejects offset %s before sending HTTP', (offset) => {
    const { api, http } = setup()
    expect(() => api.list('project-1', offset)).toThrow(RangeError)
    http.verify()
  })

  it.each([
    { ...member, role: 'SUPERUSER' }, { ...member, membershipStatus: 'PENDING' },
    { ...member, projectId: 'other-project' }, { ...member, profileId: undefined },
    { ...member, displayName: 123 },
  ])('rejects inconsistent or malformed membership data', async (row) => {
    const { api, http } = setup()
    const result = firstValueFrom(api.list('project-1')).catch((error: unknown) => error)
    http.expectOne(`${path}?offset=0&limit=20`).flush({ data: [row], actor: { role: 'OWNER', membershipStatus: 'ACTIVE' },
      meta: { offset: 0, limit: 20, hasMore: false } })
    expect(await result).toEqual({ message: 'No pudimos cargar el equipo. Inténtalo de nuevo.', refreshPermissions: false })
    http.verify()
  })

  it.each([
    { data: [member, member], actor: { role: 'OWNER', membershipStatus: 'ACTIVE' }, meta: { offset: 0, limit: 20, hasMore: false } },
    { data: [member], actor: { role: 'OWNER', membershipStatus: 'INVITED' }, meta: { offset: 0, limit: 20, hasMore: false } },
    { data: [member], actor: { role: 'OWNER', membershipStatus: 'ACTIVE' }, meta: { offset: 10, limit: 20, hasMore: false } },
  ])('rejects duplicate rows, inactive actors or a mismatched page', async (response) => {
    const { api, http } = setup()
    const result = firstValueFrom(api.list('project-1')).catch((error: unknown) => error)
    http.expectOne(`${path}?offset=0&limit=20`).flush(response)
    expect(await result).toMatchObject({ message: 'No pudimos cargar el equipo. Inténtalo de nuevo.' })
    http.verify()
  })

  it.each([
    { action: 'activate' as const }, { action: 'withdraw' as const },
    { action: 'changeRole' as const, role: 'OWNER' as const },
  ])('sends the exact $action PATCH and retains only membership fields', async (change) => {
    const { api, http } = setup()
    const result = firstValueFrom(api.update('project-1', 'member-1', change))
    const request = http.expectOne(`${path}/member-1`)
    expect(request.request.method).toBe('PATCH')
    expect(request.request.body).toEqual(change)
    request.flush({ ...member, createdByProfileId: 'private-profile' })
    expect(await result).toEqual({ id: 'member-1', projectId: 'project-1', profileId: 'profile-1', role: 'ADMIN', membershipStatus: 'ACTIVE' })
    http.verify()
  })

  it('encodes resource identifiers and rejects a response for another row', async () => {
    const { api, http } = setup()
    const result = firstValueFrom(api.update('project/1', 'member/1', { action: 'withdraw' })).catch((error: unknown) => error)
    http.expectOne('http://localhost:8080/api/v1/projects/project%2F1/members/member%2F1').flush({ ...member, projectId: 'project/1' })
    expect(await result).toMatchObject({ message: 'No pudimos confirmar el cambio. Actualiza el equipo antes de volver a intentarlo.' })
    http.verify()
  })

  it.each([
    [401, undefined, 'Tu sesión expiró. Vuelve a iniciar sesión.'],
    [403, 'membership_permission', 'Solo un integrante activo con rol owner o admin puede administrar membresías.'],
    [409, 'membership_owner_required', 'El proyecto debe conservar al menos un owner activo.'],
    [409, 'membership_invalid_transition', 'Esa membresía no admite esta acción.'],
    [409, 'membership_conflict', 'La membresía cambió mientras realizabas la acción. Revisa el estado actualizado.'],
  ])('maps status %s / %s without leaking server data', async (status, code, message) => {
    const { api, http } = setup()
    const result = firstValueFrom(api.update('project-1', 'member-1', { action: 'withdraw' })).catch((error: unknown) => error)
    http.expectOne(`${path}/member-1`).flush({ code, detail: 'internal email token SQL' }, { status, statusText: 'Failure' })
    expect(await result).toEqual({ message, refreshPermissions: true })
    http.verify()
  })

  it('explains self elevation using only the recognized detail', async () => {
    const { api, http } = setup()
    const result = firstValueFrom(api.update('project-1', 'member-1', { action: 'changeRole', role: 'OWNER' })).catch((error: unknown) => error)
    http.expectOne(`${path}/member-1`).flush({ code: 'membership_permission', detail: 'cannot elevate own role.' }, { status: 403, statusText: 'Forbidden' })
    expect(await result).toEqual({ message: 'No puedes elevar tu propio rol.', refreshPermissions: true })
    http.verify()
  })
})
