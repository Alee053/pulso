import { HttpClient, HttpParams } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type { StartupSummary } from '../domain/startup'
import { apiPaths, discoveryCatalogContract } from './discovery.catalog.contract'

export interface StartupSummaryDto {
  id: string
  slug: string
  name: string
  tagline: string
  sector: string
  city: string
  stage: string
  imageUrl?: string | null
  imageAlt?: string | null
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

  list(limit: number = discoveryCatalogContract.defaultLimit, offset: number = discoveryCatalogContract.defaultOffset): Observable<StartupListResponse> {
    if (!Number.isInteger(limit) || limit < discoveryCatalogContract.minLimit || limit > discoveryCatalogContract.maxLimit) {
      throw new RangeError(discoveryCatalogContract.invalidLimitMessage(discoveryCatalogContract.maxLimit))
    }
    if (!Number.isInteger(offset) || offset < discoveryCatalogContract.minOffset || offset > discoveryCatalogContract.maxOffset) {
      throw new RangeError(discoveryCatalogContract.invalidOffsetMessage(discoveryCatalogContract.maxOffset))
    }

    const params = new HttpParams()
      .set('offset', offset)
      .set('limit', limit)
    return this.http.get<StartupListResponseDto>(
      `${appConfig.apiBaseUrl}${apiPaths.startups}`,
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
    imageUrl: dto.imageUrl,
    imageAlt: dto.imageAlt,
  }
}
