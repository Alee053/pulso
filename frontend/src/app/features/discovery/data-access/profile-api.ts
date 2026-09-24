import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import { apiPaths } from './discovery.catalog.contract'

export interface ProfileDto {
  id: string
  profileKind: string
  displayName: string | null
  avatarStoragePath: string | null
  shortBio: string | null
  city: string | null
  department: string | null
  profileVisibility: string
  verificationStatus: string
  accountStatus: string
  platformRole: string
  onboardingCompletedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CurrentUserDto {
  subject: string
  email: string | null
  role: string
  audience: string[]
  profile: ProfileDto
}

export interface ProfilePatchRequest {
  displayName?: string
  shortBio?: string
  city?: string
  department?: string
}

@Injectable({ providedIn: 'root' })
export class ProfileApi {
  private readonly http = inject(HttpClient)

  getCurrent(): Observable<CurrentUserDto> {
    return this.http.get<CurrentUserDto>(`${appConfig.apiBaseUrl}${apiPaths.profile}`)
  }

  update(patch: ProfilePatchRequest): Observable<CurrentUserDto> {
    return this.http.patch<CurrentUserDto>(
      `${appConfig.apiBaseUrl}${apiPaths.profile}`,
      patch,
    )
  }
}
