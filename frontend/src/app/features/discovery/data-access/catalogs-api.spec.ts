import { provideHttpClient } from '@angular/common/http'
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { firstValueFrom } from 'rxjs'
import { describe, expect, it } from 'vitest'
import { CatalogsApi } from './catalogs-api'

describe('CatalogsApi', () => {
  it('maps the PUL-44 transport contract to Angular domain catalogs', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })

    const api = TestBed.inject(CatalogsApi)
    const controller = TestBed.inject(HttpTestingController)
    const responsePromise = firstValueFrom(api.load())
    const request = controller.expectOne(
      'http://localhost:8080/api/v1/catalogs',
    )

    expect(request.request.method).toBe('GET')
    request.flush({
      data: {
        intentions: ['DISCOVER', 'BUY'],
        stages: ['MVP'],
        sectors: [{
          id: 'sector-1',
          slug: 'sostenibilidad',
          name: 'Sostenibilidad',
          description: 'Soluciones sostenibles.',
          isActive: true,
        }],
        tags: [{
          id: 'tag-1',
          slug: 'inteligencia-artificial',
          name: 'Inteligencia artificial',
          type: 'TOPIC',
          isActive: true,
        }],
        skills: [{
          id: 'skill-1',
          slug: 'java',
          name: 'Java',
          category: 'DESARROLLO',
          isActive: true,
        }],
      },
    })

    await expect(responsePromise).resolves.toEqual({
      intentions: ['DISCOVER', 'BUY'],
      stages: ['MVP'],
      sectors: [{
        id: 'sector-1',
        slug: 'sostenibilidad',
        name: 'Sostenibilidad',
        description: 'Soluciones sostenibles.',
      }],
      tags: [{
        id: 'tag-1',
        slug: 'inteligencia-artificial',
        name: 'Inteligencia artificial',
        type: 'TOPIC',
      }],
      skills: [{
        id: 'skill-1',
        slug: 'java',
        name: 'Java',
        category: 'DESARROLLO',
      }],
    })
    controller.verify()
  })

  it('preserves valid empty catalog lists', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })

    const api = TestBed.inject(CatalogsApi)
    const controller = TestBed.inject(HttpTestingController)
    const responsePromise = firstValueFrom(api.load())
    const request = controller.expectOne(
      'http://localhost:8080/api/v1/catalogs',
    )

    request.flush({
      data: {
        intentions: ['DISCOVER', 'BUY'],
        stages: ['MVP'],
        sectors: [],
        tags: [],
        skills: [],
      },
    })

    await expect(responsePromise).resolves.toEqual({
      intentions: ['DISCOVER', 'BUY'],
      stages: ['MVP'],
      sectors: [],
      tags: [],
      skills: [],
    })
    controller.verify()
  })
})