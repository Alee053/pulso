import { HttpClient, HttpParams } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type { StartupSummary } from '../domain/startup'
import { toStartupViewModel } from './startup-view-model'
import type { StartupSummaryDto } from './startups-api'
import { apiPaths, discoveryCatalogContract } from './discovery.catalog.contract'

export type PersistedFeedbackType = 'INTERESTED' | 'DISMISSED'

export interface ProjectPage<T> {
  data: T[]
  meta: {
    offset: number
    limit: number
    hasMore: boolean
    totalCount: number
  }
}

export interface DiscoveryFeedPage<T> extends ProjectPage<T> {
  meta: ProjectPage<T>['meta'] & { nextCursor: string | null }
}

export interface DiscoveryFeedItem {
  startup: ReturnType<typeof toStartupViewModel>
  saved: boolean
  feedbackType: PersistedFeedbackType | null
}

interface ProjectPageDto {
  data: StartupSummaryDto[]
  meta: ProjectPage<never>['meta']
}

interface DiscoveryFeedItemDto extends StartupSummaryDto {
  saved: boolean
  feedbackType: PersistedFeedbackType | null
}

interface DiscoveryFeedPageDto {
  data: DiscoveryFeedItemDto[]
  meta: ProjectPage<never>['meta'] & { nextCursor: string | null }
}

interface ProjectInteractionMutationDto {
  projectId: string
  saved: boolean | null
  feedbackType: PersistedFeedbackType | null
}

@Injectable({ providedIn: 'root' })
export class ProjectInteractionsApi {
  private readonly http = inject(HttpClient)

  loadDiscoveryFeed(
    limit: number = discoveryCatalogContract.defaultLimit,
    cursor: string | null = null,
  ): Observable<DiscoveryFeedPage<DiscoveryFeedItem>> {
    this.validateLimit(limit)
    let params = new HttpParams().set('limit', limit)
    if (cursor !== null) params = params.set('cursor', cursor)
    return this.http.get<DiscoveryFeedPageDto>(
      `${appConfig.apiBaseUrl}${apiPaths.discoveryFeed}`,
      { params },
    ).pipe(map((page) => ({
      data: page.data.map((item) => ({
        startup: toStartupViewModel(toStartupSummary(item)),
        saved: item.saved,
        feedbackType: item.feedbackType,
      })),
      meta: { ...page.meta },
    })))
  }

  loadSavedProjects(
    limit: number = discoveryCatalogContract.defaultLimit,
    offset: number = discoveryCatalogContract.defaultOffset,
  ): Observable<ProjectPage<ReturnType<typeof toStartupViewModel>>> {
    return this.loadCollection(apiPaths.savedProjects, limit, offset)
  }

  loadInterestedProjects(
    limit: number = discoveryCatalogContract.defaultLimit,
    offset: number = discoveryCatalogContract.defaultOffset,
  ): Observable<ProjectPage<ReturnType<typeof toStartupViewModel>>> {
    return this.loadCollection(apiPaths.interestedProjects, limit, offset)
  }

  saveProject(projectId: string): Observable<ProjectInteractionMutationDto> {
    return this.http.put<ProjectInteractionMutationDto>(
      `${appConfig.apiBaseUrl}${apiPaths.savedProjects}/${encodeURIComponent(projectId)}`,
      {},
    )
  }

  unsaveProject(projectId: string): Observable<ProjectInteractionMutationDto> {
    return this.http.delete<ProjectInteractionMutationDto>(
      `${appConfig.apiBaseUrl}${apiPaths.savedProjects}/${encodeURIComponent(projectId)}`,
    )
  }

  setFeedback(
    projectId: string,
    feedbackType: PersistedFeedbackType,
    recommendationWeights: Record<string, number>,
  ): Observable<ProjectInteractionMutationDto> {
    return this.http.put<ProjectInteractionMutationDto>(
      `${appConfig.apiBaseUrl}${apiPaths.projectFeedback}/${encodeURIComponent(projectId)}`,
      { feedbackType, recommendationWeights },
    )
  }

  private loadCollection(
    path: string,
    limit: number,
    offset: number,
  ): Observable<ProjectPage<ReturnType<typeof toStartupViewModel>>> {
    return this.http.get<ProjectPageDto>(
      `${appConfig.apiBaseUrl}${path}`,
      { params: this.pageParams(limit, offset) },
    ).pipe(map((page) => ({
      data: page.data.map((project) => toStartupViewModel(toStartupSummary(project))),
      meta: { ...page.meta },
    })))
  }

  private pageParams(limit: number, offset: number): HttpParams {
    this.validateLimit(limit)
    if (!Number.isInteger(offset)
      || offset < discoveryCatalogContract.minOffset
      || offset > discoveryCatalogContract.maxOffset) {
      throw new RangeError(discoveryCatalogContract.invalidOffsetMessage(discoveryCatalogContract.maxOffset))
    }
    return new HttpParams().set('offset', offset).set('limit', limit)
  }

  private validateLimit(limit: number): void {
    if (!Number.isInteger(limit)
      || limit < discoveryCatalogContract.minLimit
      || limit > discoveryCatalogContract.maxLimit) {
      throw new RangeError(discoveryCatalogContract.invalidLimitMessage(discoveryCatalogContract.maxLimit))
    }
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
