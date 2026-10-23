import { HttpClient, HttpParams } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type { StartupSummary } from '../domain/startup'

export interface SearchFilters {
  query?: string
  sector?: string
  stage?: string
  location?: string
  tags?: string[]
  hasOpenOpportunities?: boolean
}

interface SearchResponseDto {
  data: StartupSummary[]
  meta: {
    offset: number
    limit: number
    totalCount: number
  }
}

@Injectable({ providedIn: 'root' })
export class ProjectSearchApi {
  private readonly http = inject(HttpClient)

  search(filters: SearchFilters, offset = 0, limit = 20): Observable<SearchResponseDto> {
    let params = new HttpParams().set('offset', offset).set('limit', limit)

    if (filters.query) params = params.set('q', filters.query)
    if (filters.sector) params = params.set('sector', filters.sector)
    if (filters.stage) params = params.set('stage', filters.stage)
    if (filters.location) params = params.set('location', filters.location)
    if (filters.hasOpenOpportunities) params = params.set('hasOpenOpportunities', 'true')
    
    if (filters.tags && filters.tags.length > 0) {
      filters.tags.forEach((tag) => {
        params = params.append('tags', tag)
      })
    }

    return this.http.get<SearchResponseDto>(`${appConfig.apiBaseUrl}/api/v1/projects/search`, { params })
  }
}