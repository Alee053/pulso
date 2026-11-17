import { provideHttpClient, withInterceptors } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { firstValueFrom } from 'rxjs'
import { describe, expect, it, vi } from 'vitest'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { authInterceptor } from '../../../core/auth/auth.interceptor'
import { ProjectReviewsApi } from './project-reviews-api'

describe('ProjectReviewsApi', () => {
  it('sends the current session token and bounded pagination to the administrative API', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(), { provide: SupabaseAuthGateway, useValue: { getAccessToken: vi.fn().mockResolvedValue('session-token') } }] })
    const http = TestBed.inject(HttpTestingController)
    const result = firstValueFrom(TestBed.inject(ProjectReviewsApi).list(20))
    await new Promise<void>(resolve => queueMicrotask(resolve))
    const request = http.expectOne('http://localhost:8080/api/v1/admin/project-reviews?offset=20&limit=20')
    expect(request.request.headers.get('Authorization')).toBe('Bearer session-token')
    request.flush({ data: [], meta: { offset: 20, limit: 20, hasMore: false, noteMaxLength: 2000 } })
    expect((await result).data).toEqual([])
    http.verify()
  })

  it('does not present an unexpected response as a successful decision', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] })
    const http = TestBed.inject(HttpTestingController)
    const result = firstValueFrom(TestBed.inject(ProjectReviewsApi).decide('project-1', { decision: 'REJECT', note: 'Completar' }))
    const rejected = expect(result).rejects.toThrow('Invalid review result')
    http.expectOne('http://localhost:8080/api/v1/admin/project-reviews/project-1/decision').flush({ projectId: 'other-project', status: 'REJECTED' })
    await rejected
    http.verify()
  })
})
