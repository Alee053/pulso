import { HttpClient, HttpParams } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type { StartupSummary } from '../domain/startup'

export interface StartupSummaryDto {
  id: string
  slug: string
  name: string
  tagline: string
  sector: string
  city: string
  stage: string
}

interface StartupListResponseDto {
  data: StartupSummaryDto[]
  meta: {
    offset: number
    limit: number
    hasMore: boolean
  }
}

export interface StartupListResponse {
  data: StartupSummary[]
  meta: {
    offset: number
    limit: number
    hasMore: boolean
  }
}

@Injectable({ providedIn: 'root' })
export class StartupsApi {
  private readonly http = inject(HttpClient)

  list(limit = 20, offset = 0): Observable<StartupListResponse> {
    if (!Number.isInteger(limit) || limit < 1 || limit > 50) {
      throw new RangeError('limit must be an integer between 1 and 50')
    }
    if (!Number.isInteger(offset) || offset < 0 || offset > 100_000) {
      throw new RangeError('offset must be an integer between 0 and 100000')
    }

    const params = new HttpParams()
      .set('offset', offset)
      .set('limit', limit)
    return this.http.get<StartupListResponseDto>(
      `${appConfig.apiBaseUrl}/api/v1/startups`,
      { params },
    ).pipe(
      map((response) => ({
        data: response.data.map(toStartupSummary),
        meta: {
          offset: response.meta.offset,
          limit: response.meta.limit,
          hasMore: response.meta.hasMore,
        },
      })),
    )
  }
}

function toStartupSummary(dto: StartupSummaryDto): StartupSummary {
  return {
    id: dto.id,
    slug: dto.slug,
    name: dto.name,
    tagline: dto.tagline,
    sector: dto.sector,
    city: dto.city,
    stage: dto.stage,
  }
}
