import { HttpClient, HttpParams } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type {
  ManagedProjectOpportunity,
  OpportunityPage,
  OpportunityContentValue,
  OpportunityStatus,
  OpportunitySkill,
  OpportunitySkillImportance,
} from '../domain/project-opportunity-skills'

interface OpportunityDto {
  id: string
  projectId: string
  opportunityType: string
  title: string
  description: string
  status: string
  city: string | null
  department: string | null
  remoteAllowed: boolean
  createdAt: string
  updatedAt: string
}

interface OpportunityPageDto {
  data: OpportunityDto[]
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
            data: page.data.map((opportunity) => toOpportunity(opportunity, projectId)),
            meta: {
              offset: page.meta.offset,
              limit: page.meta.limit,
              hasMore: page.meta.hasMore && nextOffset <= 100000,
            },
          }
        }),
      )
  }

  createOpportunity(
    projectId: string,
    value: OpportunityContentValue,
    status: 'DRAFT' | 'OPEN',
  ): Observable<ManagedProjectOpportunity> {
    return this.http.post<OpportunityDto>(
      `${this.baseUrl}/${encodeURIComponent(projectId)}/opportunities`,
      { ...toContentRequest(value), status },
    ).pipe(map((dto) => toOpportunity(dto, projectId)))
  }

  updateOpportunity(
    projectId: string,
    opportunityId: string,
    value: OpportunityContentValue,
  ): Observable<ManagedProjectOpportunity> {
    return this.http.put<OpportunityDto>(
      `${this.baseUrl}/${encodeURIComponent(projectId)}/opportunities/${encodeURIComponent(opportunityId)}`,
      toContentRequest(value),
    ).pipe(map((dto) => toOpportunity(dto, projectId)))
  }

  changeOpportunityStatus(
    projectId: string,
    opportunityId: string,
    action: 'OPEN' | 'PAUSE' | 'CLOSE' | 'ARCHIVE',
  ): Observable<ManagedProjectOpportunity> {
    return this.http.post<OpportunityDto>(
      `${this.baseUrl}/${encodeURIComponent(projectId)}/opportunities/${encodeURIComponent(opportunityId)}/actions`,
      { action },
    ).pipe(map((dto) => toOpportunity(dto, projectId)))
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

function toContentRequest(value: OpportunityContentValue): OpportunityContentValue {
  return {
    opportunityType: value.opportunityType,
    title: value.title.trim(),
    description: value.description.trim(),
    city: value.city.trim(),
    department: value.department.trim(),
    remoteAllowed: value.remoteAllowed,
  }
}

function toOpportunity(dto: OpportunityDto, expectedProjectId: string): ManagedProjectOpportunity {
  const types = ['TALENT', 'COLLABORATION', 'CUSTOMER', 'BETA_TESTER', 'INVESTMENT', 'MENTOR', 'SUPPLIER', 'PARTNERSHIP', 'OTHER']
  const statuses = ['DRAFT', 'OPEN', 'PAUSED', 'FILLED', 'CLOSED', 'ARCHIVED']
  if (!dto || typeof dto.id !== 'string' || !dto.id || dto.projectId !== expectedProjectId
    || !types.includes(dto.opportunityType) || typeof dto.title !== 'string'
    || typeof dto.description !== 'string' || !statuses.includes(dto.status)
    || (dto.city !== null && typeof dto.city !== 'string')
    || (dto.department !== null && typeof dto.department !== 'string')
    || typeof dto.remoteAllowed !== 'boolean' || typeof dto.createdAt !== 'string'
    || typeof dto.updatedAt !== 'string') {
    throw new Error('Invalid project opportunity response')
  }
  return {
    id: dto.id,
    projectId: dto.projectId,
    opportunityType: dto.opportunityType as ManagedProjectOpportunity['opportunityType'],
    title: dto.title,
    description: dto.description,
    status: dto.status as OpportunityStatus,
    city: dto.city,
    department: dto.department,
    remoteAllowed: dto.remoteAllowed,
    createdAt: dto.createdAt,
    updatedAt: dto.updatedAt,
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
