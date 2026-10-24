import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type { OpportunityMatch } from '../domain/opportunity-match'
import { apiPaths } from './discovery.catalog.contract'

interface OpportunityMatchListDto {
  data: OpportunityMatch[]
}

@Injectable({ providedIn: 'root' })
export class OpportunityMatchApi {
  private readonly http = inject(HttpClient)

  list(projectRef: string): Observable<OpportunityMatch[]> {
    return this.http.get<OpportunityMatchListDto>(
      `${appConfig.apiBaseUrl}${apiPaths.projects}/${encodeURIComponent(projectRef)}/opportunity-matches`,
    ).pipe(
      map((response) => response.data.map((match) => ({
        opportunityId: match.opportunityId,
        title: match.title,
        opportunityType: match.opportunityType,
        score: match.score,
        onboardingCompleted: match.onboardingCompleted,
        missingFactors: match.missingFactors ?? [],
        factors: (match.factors ?? []).map((factor) => ({
          key: factor.key,
          status: factor.status,
          weight: factor.weight,
          points: factor.points,
          matched: factor.matched ?? [],
          unmatched: factor.unmatched ?? [],
          reason: factor.reason ?? null,
        })),
      }))),
    )
  }
}
