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

  it.each([
    ['a missing known flag', { flags: [{ key: 'language_switcher', enabled: true }] }],
    ['a duplicated known flag', { flags: [
      { key: 'language_switcher', enabled: true },
      { key: 'language_switcher', enabled: false },
      { key: 'project_creation', enabled: true },
    ] }],
    ['a non-boolean value', { flags: [
      { key: 'language_switcher', enabled: true },
      { key: 'project_creation', enabled: 'true' },
    ] }],
  ])('fails closed when the response contains %s', async (_case, response) => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })
    const service = TestBed.inject(FeatureFlagsService)
    const http = TestBed.inject(HttpTestingController)

    const loading = service.load()
    http.expectOne('http://localhost:8080/api/v1/feature-flags')
      .flush(response as never)
    await loading

    expect(service.isEnabled('language_switcher')).toBe(false)
    expect(service.isEnabled('project_creation')).toBe(false)
    expect(service.hasLoadError()).toBe(true)
  })

  it('ignores unknown response keys without exposing them as enabled features', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })
    const service = TestBed.inject(FeatureFlagsService)
    const http = TestBed.inject(HttpTestingController)

    const loading = service.load()
    http.expectOne('http://localhost:8080/api/v1/feature-flags').flush({ flags: [
      { key: 'language_switcher', enabled: true },
      { key: 'project_creation', enabled: false },
      { key: 'billing_admin', enabled: true },
    ] })
    await loading

    expect(service.values()).toEqual({ language_switcher: true, project_creation: false })
  })

  it('keeps the confirmed state until a successful update response arrives', async () => {
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
    expect(service.isEnabled('project_creation')).toBe(true)
    http.expectOne('http://localhost:8080/api/v1/feature-flags/project_creation')
      .flush({ key: 'project_creation', enabled: false })
    await updating

    expect(service.isEnabled('project_creation')).toBe(false)
    expect(service.hasError()).toBe(false)
  })

  it('preserves the confirmed state when an update fails', async () => {
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
    expect(service.isEnabled('project_creation')).toBe(true)
    http.expectOne('http://localhost:8080/api/v1/feature-flags/project_creation')
      .flush({ detail: 'database connection details' }, { status: 503, statusText: 'Unavailable' })
    await expect(updating).rejects.toThrow()

    expect(service.isEnabled('project_creation')).toBe(true)
    expect(service.hasError()).toBe(true)
  })

  it('preserves the confirmed state when an update response does not match its flag', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })
    const service = TestBed.inject(FeatureFlagsService)
    const http = TestBed.inject(HttpTestingController)
    const loading = service.load()
    http.expectOne('http://localhost:8080/api/v1/feature-flags').flush({ flags: [
      { key: 'language_switcher', enabled: true },
      { key: 'project_creation', enabled: true },
    ] })
    await loading

    const updating = service.update('project_creation', false)
    http.expectOne('http://localhost:8080/api/v1/feature-flags/project_creation')
      .flush({ key: 'language_switcher', enabled: false })
    await expect(updating).rejects.toThrow('Invalid feature flag update response')

    expect(service.isEnabled('project_creation')).toBe(true)
    expect(service.hasError()).toBe(true)
  })

  it('refreshes after an older focus read overlaps a successful update', async () => {
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

    const focusRefresh = service.load()
    const staleRead = http.expectOne('http://localhost:8080/api/v1/feature-flags')
    const updating = service.update('project_creation', false)
    http.expectOne('http://localhost:8080/api/v1/feature-flags/project_creation')
      .flush({ key: 'project_creation', enabled: false })
    await updating
    expect(service.isEnabled('project_creation')).toBe(false)

    staleRead.flush({ flags: [
      { key: 'language_switcher', enabled: true },
      { key: 'project_creation', enabled: true },
    ] })
    await Promise.resolve()
    http.expectOne('http://localhost:8080/api/v1/feature-flags').flush({
      flags: [
        { key: 'language_switcher', enabled: true },
        { key: 'project_creation', enabled: false },
      ],
    })
    await focusRefresh

    expect(service.isEnabled('project_creation')).toBe(false)
  })
})
