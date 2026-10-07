import { provideHttpClient, withInterceptors } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { firstValueFrom } from 'rxjs'
import { describe, expect, it, vi } from 'vitest'
import { authInterceptor } from '../../../core/auth/auth.interceptor'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { MemberInvitationsApi } from './member-invitations-api'
import { ProjectsApi } from './projects-api'

describe('MemberInvitationsApi', () => {
  it('accepts using only the private token body and authenticated bearer', async () => {
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(),
      { provide: SupabaseAuthGateway, useValue: { getAccessToken: vi.fn().mockResolvedValue('session-token') } },
    ] })
    const http = TestBed.inject(HttpTestingController)
    const result = firstValueFrom(TestBed.inject(MemberInvitationsApi).accept('private-invitation-token'))
    await new Promise<void>((resolve) => queueMicrotask(resolve))
    const request = http.expectOne('http://localhost:8080/api/v1/project-members/accept')
    expect(request.request.method).toBe('POST')
    expect(request.request.url).not.toContain('private-invitation-token')
    expect(request.request.body).toEqual({ token: 'private-invitation-token' })
    expect(request.request.headers.get('Authorization')).toBe('Bearer session-token')
    request.flush({ projectId: 'project-1', role: 'MEMBER', membershipStatus: 'ACTIVE' })
    expect(await result).toEqual({ projectId: 'project-1', membershipStatus: 'ACTIVE' })
    http.verify()
  })

  it.each([400, 401, 403, 404, 409, 503])('conceals raw server detail for acceptance status %s', async (status) => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] })
    const http = TestBed.inject(HttpTestingController)
    const failure = firstValueFrom(TestBed.inject(MemberInvitationsApi).accept('private-token')).catch((error: unknown) => error)
    http.expectOne('http://localhost:8080/api/v1/project-members/accept')
      .flush({ detail: 'leaked private-token', token: 'private-token' }, { status, statusText: 'Failure' })
    const error = await failure
    expect(JSON.stringify(error)).not.toContain('private-token')
    expect(error).toHaveProperty('retryable', status === 401 || status === 503)
    http.verify()
  })

  it('registers a MEMBER invitation with the authenticated session and only the normalized email', async () => {
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(),
      { provide: SupabaseAuthGateway, useValue: { getAccessToken: vi.fn().mockResolvedValue('session-token') } },
    ] })
    const api = TestBed.inject(MemberInvitationsApi)
    const http = TestBed.inject(HttpTestingController)
    const result = firstValueFrom(api.invite('project-1', ' Ada@Example.COM '))
    await new Promise<void>((resolve) => queueMicrotask(resolve))
    const request = http.expectOne('http://localhost:8080/api/v1/projects/project-1/members')
    expect(request.request.method).toBe('POST')
    expect(request.request.headers.get('Authorization')).toBe('Bearer session-token')
    expect(request.request.body).toEqual({ inviteeEmail: 'ada@example.com' })
    request.flush({ id: 'invite-1', projectId: 'project-1', inviteeEmail: 'ada@example.com',
      profileId: null, role: 'MEMBER', membershipStatus: 'INVITED', createdByProfileId: 'actor-1',
      token: 'unexpected-secret', inviteTokenDigest: 'unexpected-digest' })
    expect(await result).toEqual({ projectId: 'project-1', membershipStatus: 'INVITED' })
    http.verify()
  })
})

describe('ProjectsApi invitations', () => {
  it('normalizes email and does not send actor, role or token in create request', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] })
    const api = TestBed.inject(ProjectsApi)
    const http = TestBed.inject(HttpTestingController)
    const pending = firstValueFrom(api.createInvitation('project-id', ' Ada@Example.com '))
    const request = http.expectOne('http://localhost:8080/api/v1/projects/project-id/members')
    expect(request.request.method).toBe('POST')
    expect(request.request.body).toEqual({ inviteeEmail: 'ada@example.com' })
    request.flush({ id: 'invite-id', projectId: 'project-id', inviteeEmail: 'ada@example.com',
      profileId: null, role: 'MEMBER', membershipStatus: 'INVITED', createdByProfileId: 'owner-id' },
      { status: 201, statusText: 'Created' })
    const result = await pending
    expect(result.membershipStatus).toBe('INVITED')
    expect(result).not.toHaveProperty('inviteTokenDigest')
    http.verify()
  })
  it('submits only token for acceptance', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] })
    const api = TestBed.inject(ProjectsApi)
    const http = TestBed.inject(HttpTestingController)
    const pending = firstValueFrom(api.acceptInvitation('secret-token'))
    const request = http.expectOne('http://localhost:8080/api/v1/project-members/accept')
    expect(request.request.method).toBe('POST')
    expect(request.request.body).toEqual({ token: 'secret-token' })
    request.flush({ id: 'invite-id', projectId: 'project-id', inviteeEmail: 'ada@example.com',
      profileId: 'user-id', role: 'MEMBER', membershipStatus: 'ACTIVE', createdByProfileId: 'owner-id' })
    await expect(pending).resolves.toMatchObject({ membershipStatus: 'ACTIVE', profileId: 'user-id' })
    http.verify()
  })
})
