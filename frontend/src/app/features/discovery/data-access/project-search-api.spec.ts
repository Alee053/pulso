import { provideHttpClient } from '@angular/common/http'
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { firstValueFrom } from 'rxjs'
import { describe, expect, it } from 'vitest'
import { ProjectSearchApi } from './project-search-api'

describe('ProjectSearchApi', () => {
  function setup() {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })

    return {
      api: TestBed.inject(ProjectSearchApi),
      controller: TestBed.inject(HttpTestingController),
    }
  }

  it('sends only the filters that have a value', async () => {
    const { api, controller } = setup()
    const responsePromise = firstValueFrom(api.search({
      query: 'eco',
      stage: 'MVP',
      tags: ['impacto', 'agro'],
      hasOpenOpportunities: true,
    }, 20, 10))
    const request = controller.expectOne((candidate) =>
      candidate.url === 'http://localhost:8080/api/v1/projects/search')

    expect(request.request.method).toBe('GET')
    expect(request.request.params.get('q')).toBe('eco')
    expect(request.request.params.get('stage')).toBe('MVP')
    expect(request.request.params.get('offset')).toBe('20')
    expect(request.request.params.get('limit')).toBe('10')
    expect(request.request.params.getAll('tags')).toEqual(['impacto', 'agro'])
    expect(request.request.params.get('hasOpenOpportunities')).toBe('true')
    expect(request.request.params.has('sector')).toBe(false)
    expect(request.request.params.has('location')).toBe(false)

    request.flush({ data: [], meta: { offset: 20, limit: 10, totalCount: 0 } })
    await responsePromise
    controller.verify()
  })

  it('maps the response to project summaries and keeps the total count', async () => {
    const { api, controller } = setup()
    const responsePromise = firstValueFrom(api.search({ query: 'eco' }))
    const request = controller.expectOne((candidate) =>
      candidate.url === 'http://localhost:8080/api/v1/projects/search')

    request.flush({
      data: [{
        id: 'project-1',
        slug: 'eco-pack',
        name: 'EcoPack',
        tagline: 'Envases biodegradables',
        sector: 'Sostenibilidad',
        city: 'Cochabamba',
        stage: 'MVP',
        imageUrl: null,
        imageAlt: null,
        privateField: 'must not leak into the domain result',
      }],
      meta: { offset: 0, limit: 20, totalCount: 31 },
    })

    const response = await responsePromise
    expect(response.meta).toEqual({ offset: 0, limit: 20, totalCount: 31 })
    expect(response.data).toEqual([{
      id: 'project-1',
      slug: 'eco-pack',
      name: 'EcoPack',
      tagline: 'Envases biodegradables',
      sector: 'Sostenibilidad',
      city: 'Cochabamba',
      stage: 'MVP',
      imageUrl: null,
      imageAlt: null,
    }])
    controller.verify()
  })
})
