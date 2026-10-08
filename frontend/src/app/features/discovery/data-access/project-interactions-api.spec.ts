import { provideHttpClient } from '@angular/common/http'
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { firstValueFrom } from 'rxjs'
import { describe, expect, it } from 'vitest'
import { ProjectInteractionsApi } from './project-interactions-api'

describe('ProjectInteractionsApi', () => {
  it('loads persisted discovery items and maps their interaction state', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })

    const api = TestBed.inject(ProjectInteractionsApi)
    const controller = TestBed.inject(HttpTestingController)
    const responsePromise = firstValueFrom(api.loadDiscoveryFeed())
    const request = controller.expectOne(
      (candidate) => candidate.url === 'http://localhost:8080/api/v1/me/discovery-feed',
    )

    expect(request.request.method).toBe('GET')
    expect(request.request.params.get('limit')).toBe('20')
    expect(request.request.params.get('offset')).toBe('0')
    request.flush({
      data: [{
        id: 'project-1',
        slug: 'eco-pack',
        name: 'EcoPack Bolivia',
        tagline: 'Envases compostables.',
        sector: 'Sostenibilidad',
        city: 'Cochabamba',
        stage: 'MVP',
        saved: true,
        feedbackType: null,
      }],
      meta: { offset: 0, limit: 20, hasMore: true, totalCount: 28 },
    })

    const page = await responsePromise
    expect(page.data[0]?.startup.id).toBe('project-1')
    expect(page.data[0]?.saved).toBe(true)
    expect(page.data[0]?.feedbackType).toBeNull()
    expect(page.meta.totalCount).toBe(28)
    controller.verify()
  })

  it('uses authenticated collection and mutation routes', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })

    const api = TestBed.inject(ProjectInteractionsApi)
    const controller = TestBed.inject(HttpTestingController)
    const savedPromise = firstValueFrom(api.loadSavedProjects(10, 20))
    const savedRequest = controller.expectOne(
      (candidate) => candidate.url === 'http://localhost:8080/api/v1/me/saved-projects',
    )
    expect(savedRequest.request.method).toBe('GET')
    expect(savedRequest.request.params.get('limit')).toBe('10')
    expect(savedRequest.request.params.get('offset')).toBe('20')
    savedRequest.flush({
      data: [],
      meta: { offset: 20, limit: 10, hasMore: false, totalCount: 20 },
    })
    await expect(savedPromise).resolves.toMatchObject({ meta: { totalCount: 20 } })

    const savePromise = firstValueFrom(api.saveProject('project-1'))
    const saveRequest = controller.expectOne(
      'http://localhost:8080/api/v1/me/saved-projects/project-1',
    )
    expect(saveRequest.request.method).toBe('PUT')
    saveRequest.flush({ projectId: 'project-1', saved: true, feedbackType: null })
    await expect(savePromise).resolves.toMatchObject({ saved: true })

    const feedbackPromise = firstValueFrom(api.setFeedback('project-1', 'DISMISSED', { impact: -1 }))
    const feedbackRequest = controller.expectOne(
      'http://localhost:8080/api/v1/me/project-feedback/project-1',
    )
    expect(feedbackRequest.request.method).toBe('PUT')
    expect(feedbackRequest.request.body).toEqual({
      feedbackType: 'DISMISSED',
      recommendationWeights: { impact: -1 },
    })
    feedbackRequest.flush({ projectId: 'project-1', saved: null, feedbackType: 'DISMISSED' })
    await expect(feedbackPromise).resolves.toMatchObject({ feedbackType: 'DISMISSED' })
    controller.verify()
  })
})
