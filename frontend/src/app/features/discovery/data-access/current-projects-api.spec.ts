import { provideHttpClient, withInterceptors } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { firstValueFrom } from 'rxjs'
import { describe, expect, it, vi } from 'vitest'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { authInterceptor } from '../../../core/auth/auth.interceptor'
import { CurrentProjectsApi } from './current-projects-api'

const path = 'http://localhost:8080/api/v1/me/projects?offset=20&limit=20'

describe('CurrentProjectsApi', () => {
  it('loads a bounded page with the authenticated session and discards unrelated fields', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(), { provide: SupabaseAuthGateway, useValue: { getAccessToken: vi.fn().mockResolvedValue('session-token') } },
    ] })
    const http = TestBed.inject(HttpTestingController)
    const result = firstValueFrom(TestBed.inject(CurrentProjectsApi).list(20))
    await new Promise<void>((resolve) => queueMicrotask(resolve))
    const request = http.expectOne(path)
    expect(request.request.headers.get('Authorization')).toBe('Bearer session-token')
    request.flush({ data: [{ projectId: 'p1', name: 'EcoPack', role: 'ADMIN', membershipStatus: 'ACTIVE', privateData: 'hidden' }],
      meta: { offset: 20, limit: 20, hasMore: false } })
    expect(await result).toEqual({ data: [{ projectId: 'p1', name: 'EcoPack', role: 'ADMIN', membershipStatus: 'ACTIVE' }],
      meta: { offset: 20, limit: 20, hasMore: false } })
    http.verify()
  })

  it.each([-1, 100001, 0.5])('rejects invalid offsets before HTTP (%s)', (offset) => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] })
    expect(() => TestBed.inject(CurrentProjectsApi).list(offset)).toThrow(RangeError)
    TestBed.inject(HttpTestingController).verify()
  })

  it.each(['INVITED', 'REMOVED', 'LEFT'])('rejects unexpected membership state %s without showing administrative controls', async (membershipStatus) => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] })
    const result = firstValueFrom(TestBed.inject(CurrentProjectsApi).list(20)).catch((error: unknown) => error)
    const http = TestBed.inject(HttpTestingController)
    http.expectOne(path).flush({ data: [{ projectId: 'p1', name: 'EcoPack', role: 'OWNER', membershipStatus }],
      meta: { offset: 20, limit: 20, hasMore: false } })
    expect(await result).toEqual({ message: 'No pudimos cargar tus equipos. Inténtalo de nuevo.' })
    http.verify()
  })
})
