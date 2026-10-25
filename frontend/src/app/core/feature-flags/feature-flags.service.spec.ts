import { provideHttpClient } from '@angular/common/http'
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { afterEach, describe, expect, it } from 'vitest'
import { FeatureFlagsService } from './feature-flags.service'

describe('FeatureFlagsService', () => {
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify()
    TestBed.resetTestingModule()
  })

  it('starts with gated features disabled and applies the persisted response', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })
    const service = TestBed.inject(FeatureFlagsService)
    const http = TestBed.inject(HttpTestingController)

    expect(service.isLoaded()).toBe(false)
    expect(service.isEnabled('language_switcher')).toBe(false)
    expect(service.isEnabled('project_creation')).toBe(false)

    const loading = service.load()
    http.expectOne('http://localhost:8080/api/v1/feature-flags').flush({
      flags: [
        { key: 'language_switcher', enabled: true },
        { key: 'project_creation', enabled: false },
      ],
    })
    await loading

    expect(service.isLoaded()).toBe(true)
    expect(service.isEnabled('language_switcher')).toBe(true)
    expect(service.isEnabled('project_creation')).toBe(false)
    expect(service.hasError()).toBe(false)
  })

  it('fails closed when the flag request fails', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })
    const service = TestBed.inject(FeatureFlagsService)
    const http = TestBed.inject(HttpTestingController)
    const loading = service.load()
    http.expectOne('http://localhost:8080/api/v1/feature-flags')
      .flush({ detail: 'database connection details' }, { status: 503, statusText: 'Unavailable' })
    await loading

    expect(service.isLoaded()).toBe(true)
    expect(service.isEnabled('language_switcher')).toBe(false)
    expect(service.isEnabled('project_creation')).toBe(false)
    expect(service.hasError()).toBe(true)
    expect(service.hasLoadError()).toBe(true)
  })

  it('applies a successful update to shared state', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })
    const service = TestBed.inject(FeatureFlagsService)
    const http = TestBed.inject(HttpTestingController)
    const loading = service.load()
    http.expectOne('http://localhost:8080/api/v1/feature-flags').flush({
      flags: [
        { key: 'language_switcher', enabled: true },
        { key: 'project_creation', enabled: true },
      ],
    })
    await loading

    const updating = service.update('project_creation', false)
    expect(service.isEnabled('project_creation')).toBe(false)
    http.expectOne('http://localhost:8080/api/v1/feature-flags/project_creation')
      .flush({ key: 'project_creation', enabled: false })
    await updating

    expect(service.isEnabled('project_creation')).toBe(false)
    expect(service.hasError()).toBe(false)
  })

  it('reverts the optimistic state after a failed update', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })
    const service = TestBed.inject(FeatureFlagsService)
    const http = TestBed.inject(HttpTestingController)
    const loading = service.load()
    http.expectOne('http://localhost:8080/api/v1/feature-flags').flush({
      flags: [
        { key: 'language_switcher', enabled: true },
        { key: 'project_creation', enabled: true },
      ],
    })
    await loading

    const updating = service.update('project_creation', false)
    expect(service.isEnabled('project_creation')).toBe(false)
    http.expectOne('http://localhost:8080/api/v1/feature-flags/project_creation')
      .flush({ detail: 'database connection details' }, { status: 503, statusText: 'Unavailable' })
    await expect(updating).rejects.toThrow()

    expect(service.isEnabled('project_creation')).toBe(true)
    expect(service.hasError()).toBe(true)
  })
})
