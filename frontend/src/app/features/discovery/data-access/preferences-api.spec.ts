import { provideHttpClient } from '@angular/common/http'
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { firstValueFrom } from 'rxjs'
import { describe, expect, it } from 'vitest'
import type { OnboardingPreferencesPayload } from '../domain/preferences'
import { PreferencesApi } from './preferences-api'

describe('PreferencesApi', () => {
  it('loads the authenticated onboarding preferences', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })

    const api = TestBed.inject(PreferencesApi)
    const controller = TestBed.inject(HttpTestingController)
    const responsePromise = firstValueFrom(api.load())
    const request = controller.expectOne(
      'http://localhost:8080/api/v1/me/preferences',
    )

    expect(request.request.method).toBe('GET')
    request.flush({
      profileId: 'user-1',
      intentions: ['DISCOVER', 'COLLABORATE'],
      preferredSectors: ['sector-1'],
      preferredStages: ['MVP'],
      preferredTags: ['tag-1'],
      preferredSkills: ['skill-1'],
      skills: [{ skillId: 'skill-1', proficiency: 'ADVANCED' }],
      onboardingCompletedAt: '2026-10-09T12:00:00Z',
      recommendationWeights: { sustainability: 2.5 },
    })

    await expect(responsePromise).resolves.toEqual({
      profileId: 'user-1',
      intentions: ['DISCOVER', 'COLLABORATE'],
      preferredSectors: ['sector-1'],
      preferredStages: ['MVP'],
      preferredTags: ['tag-1'],
      preferredSkills: ['skill-1'],
      skills: [{ skillId: 'skill-1', proficiency: 'ADVANCED' }],
      onboardingCompletedAt: '2026-10-09T12:00:00Z',
      recommendationWeights: { sustainability: 2.5 },
    })
    controller.verify()
  })

  it('replaces preferences through the protected contract', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })

    const api = TestBed.inject(PreferencesApi)
    const controller = TestBed.inject(HttpTestingController)
    const payload: OnboardingPreferencesPayload = {
      intentions: ['DISCOVER', 'INVEST'],
      preferredSectors: ['sector-1'],
      preferredStages: ['MVP'],
      preferredTags: ['tag-1'],
      skills: [{ skillId: 'skill-1', proficiency: 'ADVANCED' }],
      completeOnboarding: true,
    }
    const responsePromise = firstValueFrom(api.save(payload))
    const request = controller.expectOne(
      'http://localhost:8080/api/v1/me/preferences',
    )

    expect(request.request.method).toBe('PUT')
    expect(request.request.body).toEqual(payload)
    request.flush({
      profileId: 'user-1',
      intentions: ['DISCOVER', 'INVEST'],
      preferredSectors: ['sector-1'],
      preferredStages: ['MVP'],
      preferredTags: ['tag-1'],
      preferredSkills: ['skill-1'],
      skills: [{ skillId: 'skill-1', proficiency: 'ADVANCED' }],
      onboardingCompletedAt: '2026-10-09T12:00:00Z',
      recommendationWeights: { sustainability: 2.5 },
    })

    await expect(responsePromise).resolves.toEqual({
      profileId: 'user-1',
      intentions: ['DISCOVER', 'INVEST'],
      preferredSectors: ['sector-1'],
      preferredStages: ['MVP'],
      preferredTags: ['tag-1'],
      preferredSkills: ['skill-1'],
      skills: [{ skillId: 'skill-1', proficiency: 'ADVANCED' }],
      onboardingCompletedAt: '2026-10-09T12:00:00Z',
      recommendationWeights: { sustainability: 2.5 },
    })
    controller.verify()
  })
})
