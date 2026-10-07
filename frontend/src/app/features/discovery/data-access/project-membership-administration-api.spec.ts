import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { firstValueFrom } from 'rxjs'
import { describe, expect, it } from 'vitest'
import { ProjectMembershipAdministrationApi } from './project-membership-administration-api'

describe('ProjectMembershipAdministrationApi', () => {
  it('reads bounded member pages and keeps invite email only for pending invitations', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] })
    const api = TestBed.inject(ProjectMembershipAdministrationApi)
    const http = TestBed.inject(HttpTestingController)
    const pending = firstValueFrom(api.list('project/one'))
    const request = http.expectOne(
      'http://localhost:8080/api/v1/projects/project%2Fone/members?offset=0&limit=20',
    )
    expect(request.request.method).toBe('GET')
    request.flush({ data: [
      { id: 'member-1', projectId: 'project/one', profileId: 'profile-1', displayName: 'Ada',
        inviteeEmail: 'unexpected@example.test', role: 'MEMBER', membershipStatus: 'ACTIVE',
        inviteTokenDigest: 'must-not-escape' },
      { id: 'member-2', projectId: 'project/one', profileId: null, displayName: null,
        inviteeEmail: 'pending@example.test', role: 'MEMBER', membershipStatus: 'INVITED' },
    ], meta: { offset: 0, limit: 20, hasMore: false } })

    const page = await pending
    expect(page.data[0].inviteeEmail).toBeNull()
    expect(page.data[1].inviteeEmail).toBe('pending@example.test')
    expect(JSON.stringify(page)).not.toContain('must-not-escape')
    expect(page.data[0]).not.toHaveProperty('inviteTokenDigest')
    http.verify()
  })

  it('sends only the selected action and role and maps the updated membership', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] })
    const api = TestBed.inject(ProjectMembershipAdministrationApi)
    const http = TestBed.inject(HttpTestingController)
    const pending = firstValueFrom(api.administer('project-1', 'member-1', 'changeRole', 'ADMIN'))
    const request = http.expectOne(
      'http://localhost:8080/api/v1/projects/project-1/members/member-1',
    )
    expect(request.request.method).toBe('PATCH')
    expect(request.request.body).toEqual({ action: 'changeRole', role: 'ADMIN' })
    request.flush({ id: 'member-1', projectId: 'project-1', profileId: 'profile-1', role: 'ADMIN',
      membershipStatus: 'ACTIVE', createdByProfileId: 'owner-1', inviteTokenDigest: 'not-returned' })
    const member = await pending
    expect(member).toEqual({ id: 'member-1', projectId: 'project-1', profileId: 'profile-1',
      role: 'ADMIN', membershipStatus: 'ACTIVE' })
    expect(member).not.toHaveProperty('inviteTokenDigest')
    http.verify()
  })

  it('rejects invalid offsets and malformed pages before exposing member data', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] })
    const api = TestBed.inject(ProjectMembershipAdministrationApi)
    const http = TestBed.inject(HttpTestingController)
    expect(() => api.list('project-1', -1)).toThrow(RangeError)
    const pending = firstValueFrom(api.list('project-1')).catch((error: unknown) => error)
    http.expectOne('http://localhost:8080/api/v1/projects/project-1/members?offset=0&limit=20')
      .flush({ data: [{ id: 'bad', projectId: 'project-1', profileId: 'profile-1', role: 'ROOT',
        membershipStatus: 'ACTIVE' }], meta: { offset: 0, limit: 20, hasMore: false } })
    await expect(pending).resolves.toBeInstanceOf(Error)
    http.verify()
  })
})
