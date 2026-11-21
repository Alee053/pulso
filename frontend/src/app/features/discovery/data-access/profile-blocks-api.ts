import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type { ProfileBlockState } from '../domain/profile-block'

@Injectable({ providedIn: 'root' })
export class ProfileBlocksApi {
  private readonly http = inject(HttpClient)
  private readonly url = `${appConfig.apiBaseUrl}/api/v1/me/blocks`

  status(targetProfileId: string): Observable<ProfileBlockState> {
    return this.http.get<ProfileBlockState>(`${this.url}/${encodeURIComponent(targetProfileId)}`)
      .pipe(map((response) => parseState(response, targetProfileId)))
  }

  block(targetProfileId: string): Observable<ProfileBlockState> {
    return this.http.put<ProfileBlockState>(`${this.url}/${encodeURIComponent(targetProfileId)}`, {})
      .pipe(map((response) => parseState(response, targetProfileId)))
  }

  unblock(targetProfileId: string): Observable<ProfileBlockState> {
    return this.http.delete<ProfileBlockState>(`${this.url}/${encodeURIComponent(targetProfileId)}`)
      .pipe(map((response) => parseState(response, targetProfileId)))
  }
}

function parseState(value: unknown, expectedTargetId: string): ProfileBlockState {
  if (!value || typeof value !== 'object') throw new Error('Invalid profile block response')
  const response = value as Partial<ProfileBlockState>
  if (response.targetProfileId !== expectedTargetId || typeof response.blocked !== 'boolean'
    || (response.createdAt !== null && typeof response.createdAt !== 'string')) {
    throw new Error('Invalid profile block response')
  }
  return {
    targetProfileId: response.targetProfileId,
    blocked: response.blocked,
    createdAt: response.createdAt ?? null,
  }
}
