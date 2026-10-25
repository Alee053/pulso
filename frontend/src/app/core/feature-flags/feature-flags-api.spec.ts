import { provideHttpClient } from '@angular/common/http'
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { firstValueFrom } from 'rxjs'
import { afterEach, describe, expect, it } from 'vitest'
import { FeatureFlagsApi } from './feature-flags-api'

describe('FeatureFlagsApi', () => {
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify()
    TestBed.resetTestingModule()
  })

  it('loads only the public feature flags through the backend API', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })
    const api = TestBed.inject(FeatureFlagsApi)
    const http = TestBed.inject(HttpTestingController)
    const result = firstValueFrom(api.load())

    const request = http.expectOne('http://localhost:8080/api/v1/feature-flags')
    expect(request.request.method).toBe('GET')
    request.flush({
      flags: [
        { key: 'language_switcher', enabled: false },
        { key: 'project_creation', enabled: true },
      ],
    })

    await expect(result).resolves.toEqual({
      flags: [
        { key: 'language_switcher', enabled: false },
        { key: 'project_creation', enabled: true },
      ],
    })
  })

  it('sends a boolean update for the selected flag', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })
    const api = TestBed.inject(FeatureFlagsApi)
    const http = TestBed.inject(HttpTestingController)
    const result = firstValueFrom(api.update('project_creation', false))

    const request = http.expectOne(
      'http://localhost:8080/api/v1/feature-flags/project_creation',
    )
    expect(request.request.method).toBe('PUT')
    expect(request.request.body).toEqual({ enabled: false })
    request.flush({ key: 'project_creation', enabled: false })

    await expect(result).resolves.toEqual({
      key: 'project_creation',
      enabled: false,
    })
  })
})
