import {
  provideHttpClient,
} from '@angular/common/http'
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { firstValueFrom } from 'rxjs'
import { describe, expect, it } from 'vitest'
import { StartupsApi } from './startups-api'

describe('StartupsApi', () => {
  it('requests the public startup catalog with the documented contract', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })

    const api = TestBed.inject(StartupsApi)
    const controller = TestBed.inject(HttpTestingController)
    const responsePromise = firstValueFrom(api.list(10))
    const request = controller.expectOne(
      'http://localhost:8080/api/v1/startups?offset=0&limit=10',
    )

    expect(request.request.method).toBe('GET')
    request.flush({
      data: [{
        id: 'startup-1',
        slug: 'startup-1',
        name: 'Startup One',
        tagline: 'A sample startup',
        sector: 'Technology',
        city: 'La Paz',
        stage: 'MVP',
        description: 'Transport-only field that must not leak into the domain result',
      }],
      meta: { offset: 0, limit: 10, hasMore: false },
    })

    await expect(responsePromise).resolves.toEqual({
      data: [{
        id: 'startup-1',
        slug: 'startup-1',
        name: 'Startup One',
        tagline: 'A sample startup',
        sector: 'Technology',
        city: 'La Paz',
        stage: 'MVP',
      }],
      meta: { offset: 0, limit: 10, hasMore: false },
    })
    controller.verify()
  })

  it('rejects limits outside the backend contract', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })

    const api = TestBed.inject(StartupsApi)

    expect(() => api.list(0)).toThrow(RangeError)
    expect(() => api.list(51)).toThrow(RangeError)
    expect(() => api.list(20, -1)).toThrow(RangeError)
    expect(() => api.list(20, 100_001)).toThrow(RangeError)
  })
})
