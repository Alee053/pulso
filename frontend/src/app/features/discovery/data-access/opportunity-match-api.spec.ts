import { provideHttpClient } from '@angular/common/http'
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { firstValueFrom } from 'rxjs'
import { describe, expect, it } from 'vitest'
import { OpportunityMatchApi } from './opportunity-match-api'

describe('OpportunityMatchApi', () => {
  it('requests the matches of a project by its reference and keeps the explanation', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })
    const api = TestBed.inject(OpportunityMatchApi)
    const controller = TestBed.inject(HttpTestingController)

    const responsePromise = firstValueFrom(api.list('eco pack'))
    const request = controller.expectOne(
      'http://localhost:8080/api/v1/projects/eco%20pack/opportunity-matches',
    )
    expect(request.request.method).toBe('GET')
    request.flush({
      data: [{
        opportunityId: 'opportunity-1',
        title: 'Desarrollador',
        opportunityType: 'TALENT',
        score: 58,
        onboardingCompleted: true,
        missingFactors: ['STAGE'],
        factors: [{
          key: 'SKILLS',
          status: 'PARTIAL',
          weight: 35,
          points: 23,
          matched: ['Java'],
          unmatched: ['Figma'],
          reason: null,
          internal: 'must not leak into the domain result',
        }],
      }],
    })

    const matches = await responsePromise
    expect(matches).toEqual([{
      opportunityId: 'opportunity-1',
      title: 'Desarrollador',
      opportunityType: 'TALENT',
      score: 58,
      onboardingCompleted: true,
      missingFactors: ['STAGE'],
      factors: [{
        key: 'SKILLS',
        status: 'PARTIAL',
        weight: 35,
        points: 23,
        matched: ['Java'],
        unmatched: ['Figma'],
        reason: null,
      }],
    }])
    controller.verify()
  })
})
