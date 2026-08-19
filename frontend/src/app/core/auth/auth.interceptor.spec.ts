import {
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http'
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing'
import { HttpClient } from '@angular/common/http'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it, vi } from 'vitest'
import { authInterceptor } from './auth.interceptor'
import { SupabaseAuthGateway } from './supabase-auth.gateway'

describe('authInterceptor', () => {
  it('adds the Supabase bearer token when a session exists', async () => {
    const getAccessToken = vi.fn().mockResolvedValue('test-access-token')

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: SupabaseAuthGateway, useValue: { getAccessToken } },
      ],
    })

    const http = TestBed.inject(HttpClient)
    const controller = TestBed.inject(HttpTestingController)
    const response = firstResponse(http)

    await new Promise<void>((resolve) => queueMicrotask(resolve))
    const request = controller.expectOne('http://localhost:8080/api/v1/me')
    expect(request.request.headers.get('Authorization')).toBe(
      'Bearer test-access-token',
    )
    request.flush({ subject: 'user-1' })

    await response
    controller.verify()
  })

  it('does not send bearer tokens to non-API requests', async () => {
    const getAccessToken = vi.fn().mockResolvedValue('test-access-token')

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: SupabaseAuthGateway, useValue: { getAccessToken } },
      ],
    })

    const http = TestBed.inject(HttpClient)
    const controller = TestBed.inject(HttpTestingController)
    const response = firstResponse(http, 'https://example.test/telemetry')

    const request = controller.expectOne('https://example.test/telemetry')
    expect(request.request.headers.has('Authorization')).toBe(false)
    request.flush({ ok: true })

    await response
    expect(getAccessToken).not.toHaveBeenCalled()
    controller.verify()
  })

  it('continues without credentials when session storage is unavailable', async () => {
    const getAccessToken = vi.fn().mockRejectedValue(new Error('storage unavailable'))

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: SupabaseAuthGateway, useValue: { getAccessToken } },
      ],
    })

    const http = TestBed.inject(HttpClient)
    const controller = TestBed.inject(HttpTestingController)
    const response = firstResponse(http, 'http://localhost:8080/api/v1/startups')

    await new Promise<void>((resolve) => queueMicrotask(resolve))
    const request = controller.expectOne('http://localhost:8080/api/v1/startups')
    expect(request.request.headers.has('Authorization')).toBe(false)
    request.flush({ data: [], meta: { offset: 0, limit: 20, hasMore: false } })

    await response
    controller.verify()
  })
})

async function firstResponse(
  http: HttpClient,
  url = 'http://localhost:8080/api/v1/me',
): Promise<unknown> {
  return new Promise((resolve, reject) => {
    http.get(url).subscribe({ next: resolve, error: reject })
  })
}
