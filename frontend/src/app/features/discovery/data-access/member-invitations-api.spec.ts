import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { firstValueFrom } from 'rxjs'
import { describe, expect, it } from 'vitest'
import { ProjectsApi } from './projects-api'

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
