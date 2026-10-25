import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import type { Observable } from 'rxjs'
import { appConfig } from '../config/app-config'

export const featureFlagKeys = ['language_switcher', 'project_creation'] as const

export type FeatureFlagKey = typeof featureFlagKeys[number]
export type FeatureFlagValues = Record<FeatureFlagKey, boolean>

export interface FeatureFlagDto {
  key: FeatureFlagKey
  enabled: boolean
}

export interface FeatureFlagListDto {
  flags: FeatureFlagDto[]
}

@Injectable({ providedIn: 'root' })
export class FeatureFlagsApi {
  private readonly http = inject(HttpClient)

  load(): Observable<FeatureFlagListDto> {
    return this.http.get<FeatureFlagListDto>(
      `${appConfig.apiBaseUrl}/api/v1/feature-flags`,
    )
  }

  update(key: FeatureFlagKey, enabled: boolean): Observable<FeatureFlagDto> {
    return this.http.put<FeatureFlagDto>(
      `${appConfig.apiBaseUrl}/api/v1/feature-flags/${key}`,
      { enabled },
    )
  }
}
