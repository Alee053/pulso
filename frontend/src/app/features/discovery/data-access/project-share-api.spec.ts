import { TestBed } from '@angular/core/testing'
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing'
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { ProjectShareApi } from './project-share-api'

describe('ProjectShareApi', () => {
  let api: ProjectShareApi
  let httpMock: HttpTestingController

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ProjectShareApi],
    })
    api = TestBed.inject(ProjectShareApi)
    httpMock = TestBed.inject(HttpTestingController)
  })

  afterEach(() => {
    httpMock.verify()
  })

  it('debe crearse', () => {
    expect(api).toBeTruthy()
  })

  it('debe generar el enlace', async () => {
    const promise = new Promise<any>((resolve, reject) => {
      api.generate('eco-pack').subscribe({ next: resolve, error: reject })
    })

    const req = httpMock.expectOne('http://localhost:8080/api/v1/projects/eco-pack/share')
    expect(req.request.method).toBe('GET')
    req.flush({
      projectId: '33333333-3333-3333-3333-333333333333',
      slug: 'eco-pack',
      url: 'http://localhost:4200/proyectos/eco-pack',
    })

    const result = await promise
    expect(result.slug).toBe('eco-pack')
    expect(result.url).toContain('/proyectos/eco-pack')
  })

  it('debe registrar el evento SHARED', async () => {
    const promise = new Promise<any>((resolve, reject) => {
      api.register('eco-pack', 'FEED').subscribe({ next: resolve, error: reject })
    })

    const req = httpMock.expectOne('http://localhost:8080/api/v1/projects/eco-pack/share')
    expect(req.request.method).toBe('POST')
    expect(req.request.body).toEqual({ origin: 'FEED' })
    req.flush({
      projectId: '33333333-3333-3333-3333-333333333333',
      slug: 'eco-pack',
      url: 'http://localhost:4200/proyectos/eco-pack',
    })

    const result = await promise
    expect(result.slug).toBe('eco-pack')
  })
})