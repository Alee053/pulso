import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { appConfig } from '../../../core/config/app-config'
import { apiPaths } from './discovery.catalog.contract'

export interface BlockedProfile {
  readonly profileId: string
  readonly displayName: string | null
}

interface ProfileBlockPage {
  readonly data: BlockedProfile[]
  readonly meta: {
    readonly offset: number
    readonly limit: number
    readonly hasMore: boolean
  }
}

@Injectable({ providedIn: 'root' })
export class ProfileBlockApi {
  private readonly http = inject(HttpClient)

  list(offset: number) {
    return this.http.get<ProfileBlockPage>(appConfig.apiBaseUrl + apiPaths.profileBlocks, {
      params: { offset },
    })
  }

  blockProfile(profileId: string) {
    return this.http.post<void>(appConfig.apiBaseUrl + apiPaths.blockProfile(profileId), null)
  }

  unblockProfile(profileId: string) {
    return this.http.delete<void>(appConfig.apiBaseUrl + apiPaths.blockProfile(profileId))
  }
}
