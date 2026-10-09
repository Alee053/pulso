import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import {
  isIntention, isProjectStage, isProficiency,
  type OnboardingPreferences,
  type OnboardingPreferencesPayload,
} from '../domain/preferences'
import type { OnboardingPreferencesDto } from './preferences-dto'
import { apiPaths } from './discovery.catalog.contract'

@Injectable({ providedIn: 'root' })
export class PreferencesApi {
  private readonly http = inject(HttpClient)

  load(): Observable<OnboardingPreferences> {
    return this.http.get<OnboardingPreferencesDto>(
      `${appConfig.apiBaseUrl}${apiPaths.preferences}`,
    ).pipe(map(toOnboardingPreferences))
  }

  save(payload: OnboardingPreferencesPayload): Observable<OnboardingPreferences> {
    return this.http.put<OnboardingPreferencesDto>(
      `${appConfig.apiBaseUrl}${apiPaths.preferences}`,
      payload,
    ).pipe(map(toOnboardingPreferences))
  }
}

function toOnboardingPreferences(
  dto: OnboardingPreferencesDto,
): OnboardingPreferences {
  if (!dto.intentions.every(isIntention) || !dto.preferredStages.every(isProjectStage)
    || !Array.isArray(dto.skills) || dto.skills.some((skill) => !skill || typeof skill.skillId !== 'string'
      || (skill.proficiency !== null && !isProficiency(skill.proficiency)))
    || (dto.onboardingCompletedAt !== null && (typeof dto.onboardingCompletedAt !== 'string'
      || !Number.isFinite(Date.parse(dto.onboardingCompletedAt))))) {
    throw new Error('Invalid onboarding preferences response')
  }
  const intentions = dto.intentions.filter(isIntention)
  const preferredStages = dto.preferredStages.filter(isProjectStage)
  const skills = dto.skills.map((skill) => ({ skillId: skill.skillId,
    proficiency: isProficiency(skill.proficiency) ? skill.proficiency : null }))
  return {
    profileId: dto.profileId,
    intentions,
    preferredSectors: [...dto.preferredSectors],
    preferredStages,
    preferredTags: [...dto.preferredTags],
    preferredSkills: [...dto.preferredSkills],
    skills,
    onboardingCompletedAt: dto.onboardingCompletedAt,
    recommendationWeights: { ...dto.recommendationWeights },
  }
}
