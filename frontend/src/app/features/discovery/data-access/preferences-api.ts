import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type {
  OnboardingPreferences,
  OnboardingPreferencesPayload,
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
  return {
    profileId: dto.profileId,
    intentions: dto.intentions as OnboardingPreferences['intentions'],
    preferredSectors: [...dto.preferredSectors],
    preferredStages: dto.preferredStages as OnboardingPreferences['preferredStages'],
    preferredTags: [...dto.preferredTags],
    preferredSkills: [...dto.preferredSkills],
    recommendationWeights: { ...dto.recommendationWeights },
  }
}
