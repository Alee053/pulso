import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { firstValueFrom } from 'rxjs'
import { beforeEach, describe, expect, it } from 'vitest'
import { PublicProjectApi } from './public-project-api'
import { MOCK_PUBLIC_PROJECTS } from './mock-public-projects'

describe('PublicProjectApi', () => {
  let api: PublicProjectApi
  let http: HttpTestingController

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] })
    api = TestBed.inject(PublicProjectApi)
    http = TestBed.inject(HttpTestingController)
  })

  it('loads the canonical public endpoint and projects only public fields', async () => {
    const response = firstValueFrom(api.getPublicDetail('eco-pack'))
    const request = http.expectOne('http://localhost:8080/api/v1/projects/eco-pack')
    expect(request.request.method).toBe('GET')
    request.flush({ data: {
      ...MOCK_PUBLIC_PROJECTS['eco-pack'],
      reviewNotes: 'private review',
      members: [{ ...MOCK_PUBLIC_PROJECTS['eco-pack'].members[0], email: 'private@example.test', invite_token_digest: 'private digest' }],
      opportunities: [{
        title: 'Desarrollador Frontend',
        opportunityType: 'TALENT',
        description: 'Implementa la experiencia web.',
        city: 'Cochabamba',
        department: 'Cochabamba',
        remoteAllowed: true,
        requiredSkills: ['Angular', 'TypeScript'],
        id: 'private-opportunity-id',
        status: 'OPEN',
        importance: 'REQUIRED',
        createdByProfileId: 'private-profile',
      }],
    } })
    const detail = await response
    expect(detail).toEqual({
      ...MOCK_PUBLIC_PROJECTS['eco-pack'],
      imageUrl: null,
      imageAlt: null,
      opportunities: [{
        title: 'Desarrollador Frontend',
        opportunityType: 'TALENT',
        description: 'Implementa la experiencia web.',
        city: 'Cochabamba',
        department: 'Cochabamba',
        remoteAllowed: true,
        requiredSkills: ['Angular', 'TypeScript'],
      }],
    })
    expect(JSON.stringify(detail)).not.toContain('private')
    http.verify()
  })
})

describe('public project failures', () => {
  it.each([
    [400, false, 'Referencia de proyecto inválida'],
    [403, false, 'No tienes permiso para ver este proyecto'],
    [404, false, 'El proyecto no está disponible'],
    [500, true, 'Error del servidor. Intenta de nuevo más tarde'],
    [503, true, 'Error del servidor. Intenta de nuevo más tarde'],
  ])('maps HTTP %s to a safe error', async (status, retryable, message) => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] })
    const response = firstValueFrom(TestBed.inject(PublicProjectApi).getPublicDetail('project-1'))
    const failure = expect(response).rejects.toEqual({ status, retryable, message })
    const http = TestBed.inject(HttpTestingController)
    http.expectOne('http://localhost:8080/api/v1/projects/project-1').flush(
      { detail: 'private backend information' }, { status, statusText: 'Error' },
    )
    await failure
    http.verify()
  })

  it('maps a network failure to a retryable safe error', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] })
    const response = firstValueFrom(TestBed.inject(PublicProjectApi).getPublicDetail('project-1'))
    const failure = expect(response).rejects.toEqual({ status: 0, retryable: true, message: 'No se pudo conectar con el servidor' })
    const http = TestBed.inject(HttpTestingController)
    http.expectOne('http://localhost:8080/api/v1/projects/project-1').error(new ProgressEvent('error'))
    await failure
    http.verify()
  })

  it('rejects a blank reference without making an HTTP request', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] })
    await expect(firstValueFrom(TestBed.inject(PublicProjectApi).getPublicDetail(' '))).rejects.toMatchObject({ status: 400, retryable: false })
    TestBed.inject(HttpTestingController).expectNone(() => true)
  })
})
