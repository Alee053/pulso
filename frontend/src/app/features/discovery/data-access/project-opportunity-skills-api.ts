import { HttpClient, HttpParams } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type {
  ManagedProjectOpportunity,
  OpportunityPage,
  OpportunitySkill,
  OpportunitySkillImportance,
} from '../domain/project-opportunity-skills'

interface OpportunityPageDto {
  data: Array<{ id: string; title: string; status: string }>
  meta: { offset: number; limit: number; hasMore: boolean }
}

interface OpportunitySkillDto {
  skillId: string
  slug: string
  name: string
  importance: OpportunitySkillImportance
  active: boolean
}

@Injectable({ providedIn: 'root' })
export class ProjectOpportunitySkillsApi {
  private readonly http = inject(HttpClient)
  private readonly baseUrl = `${appConfig.apiBaseUrl}/api/v1/projects`

  listOpportunities(projectId: string, offset: number): Observable<OpportunityPage> {
    const params = new HttpParams().set('offset', offset).set('limit', 50)
    return this.http
      .get<OpportunityPageDto>(
        `${this.baseUrl}/${encodeURIComponent(projectId)}/opportunities`,
        { params },
      )
      .pipe(
        map((page) => {
          if (!page || !Array.isArray(page.data) || !page.meta
            || page.meta.offset !== offset || page.meta.limit !== 50
            || typeof page.meta.hasMore !== 'boolean') {
            throw new Error('Invalid project opportunities response')
          }
          const nextOffset = page.meta.offset + page.meta.limit
          return {
            data: page.data.map((opportunity): ManagedProjectOpportunity => ({
              id: opportunity.id,
              title: opportunity.title,
              status: opportunity.status,
            })),
            meta: {
              offset: page.meta.offset,
              limit: page.meta.limit,
              hasMore: page.meta.hasMore && nextOffset <= 100000,
            },
          }
        }),
      )
  }

  listSkills(projectId: string, opportunityId: string): Observable<OpportunitySkill[]> {
    return this.http.get<OpportunitySkillDto[]>(
      this.skillsUrl(projectId, opportunityId),
    )    .pipe(
      map((skills) => {
        if (!Array.isArray(skills)) throw new Error('Invalid opportunity skills response')
        return skills.map(toOpportunitySkill)
      }),
    )
  }

  associate(
    projectId: string,
    opportunityId: string,
    skillId: string,
    importance: OpportunitySkillImportance,
  ): Observable<OpportunitySkill> {
    return this.http.post<OpportunitySkillDto>(
      this.skillsUrl(projectId, opportunityId),
      { skillId, importance },
    ).pipe(map(toOpportunitySkill))
  }

  changeImportance(
    projectId: string,
    opportunityId: string,
    skillId: string,
    importance: OpportunitySkillImportance,
  ): Observable<OpportunitySkill> {
    return this.http.put<OpportunitySkillDto>(
      `${this.skillsUrl(projectId, opportunityId)}/${encodeURIComponent(skillId)}`,
      { importance },
    ).pipe(map(toOpportunitySkill))
  }

  remove(projectId: string, opportunityId: string, skillId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.skillsUrl(projectId, opportunityId)}/${encodeURIComponent(skillId)}`,
    )
  }

  private skillsUrl(projectId: string, opportunityId: string): string {
    return `${this.baseUrl}/${encodeURIComponent(projectId)}/opportunities/`
      + `${encodeURIComponent(opportunityId)}/skills`
  }
}

function toOpportunitySkill(dto: OpportunitySkillDto): OpportunitySkill {
  if (!dto || typeof dto.skillId !== 'string' || !dto.skillId
    || typeof dto.slug !== 'string' || typeof dto.name !== 'string'
    || (dto.importance !== 'REQUIRED' && dto.importance !== 'PREFERRED')
    || typeof dto.active !== 'boolean') {
    throw new Error('Invalid opportunity skill response')
  }
  return {
    skillId: dto.skillId,
    slug: dto.slug,
    name: dto.name,
    importance: dto.importance,
    active: dto.active,
  }
}
