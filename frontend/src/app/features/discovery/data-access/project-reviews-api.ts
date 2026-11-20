import { HttpClient, HttpParams } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type { ProjectReviewCandidate, ProjectReviewDecision, ProjectReviewPage, ProjectReviewResult } from '../domain/project-review'
import { apiPaths, projectReviewContract } from './discovery.catalog.contract'

@Injectable({ providedIn: 'root' })
export class ProjectReviewsApi {
  private readonly http = inject(HttpClient)
  private readonly path = `${appConfig.apiBaseUrl}${apiPaths.projectReviews}`

  list(offset = 0): Observable<ProjectReviewPage> {
    if (!Number.isInteger(offset) || offset < 0 || offset > projectReviewContract.maxOffset) throw new RangeError('Invalid review offset')
    return this.http.get<ProjectReviewPage>(this.path, {
      params: new HttpParams().set('offset', offset).set('limit', projectReviewContract.pageSize),
    }).pipe(map(page => {
      if (!page || !Array.isArray(page.data) || page.data.length > projectReviewContract.pageSize || !page.meta
        || page.meta.offset !== offset || page.meta.limit !== projectReviewContract.pageSize || typeof page.meta.hasMore !== 'boolean'
        || !Number.isInteger(page.meta.noteMaxLength) || page.meta.noteMaxLength < 1
        || page.data.some(project => !isCandidate(project))) throw new Error('Invalid review queue')
      return page
    }))
  }

  decide(projectId: string, decision: ProjectReviewDecision): Observable<ProjectReviewResult> {
    return this.http.post<ProjectReviewResult>(`${this.path}/${encodeURIComponent(projectId)}/decision`, decision)
      .pipe(map(result => {
        if (!result || result.projectId !== projectId
          || result.status !== (decision.decision === 'APPROVE' ? 'PUBLISHED' : 'REJECTED')
          || typeof result.reviewedAt !== 'string' || typeof result.reviewedByProfileId !== 'string') {
          throw new Error('Invalid review result')
        }
        return result
      }))
  }
}

function isCandidate(project: ProjectReviewCandidate): boolean {
  return !!project && typeof project.id === 'string' && project.id.length > 0
    && typeof project.name === 'string' && typeof project.tagline === 'string'
    && typeof project.sector === 'string' && typeof project.stage === 'string'
    && typeof project.createdAt === 'string' && typeof project.verificationStatus === 'string'
    && typeof project.canReview === 'boolean' && typeof project.sectorActive === 'boolean'
    && Number.isInteger(project.activeOwnerCount) && project.activeOwnerCount >= 0
    && project.status === 'PENDING_REVIEW'
    && ['description', 'valueProposition', 'city', 'department', 'creatorName', 'publishedAt'].every(key => {
      const value = project[key as keyof ProjectReviewCandidate]
      return value === null || typeof value === 'string'
    })
}
