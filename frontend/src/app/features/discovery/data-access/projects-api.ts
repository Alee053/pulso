import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type {
  CreatedDraftProject,
  ProjectCreateFormValue,
  ProjectStage,
  ProjectUpdateFormValue,
  UpdatedProject,
} from '../domain/project'
import { projectStatuses, type ProjectStatus } from '../domain/project-review'
import type {
  MemberInvitationDto,
  ProjectCreateRequestDto,
  ProjectCreateResponseDto,
  ProjectUpdateRequestDto,
  ProjectUpdateResponseDto,
} from './projects-dto'
import { apiPaths } from './discovery.catalog.contract'

@Injectable({ providedIn: 'root' })
export class ProjectsApi {
  private readonly http = inject(HttpClient)
  createInvitation(
    projectId: string,
    inviteeEmail: string,
  ): Observable<MemberInvitationDto> {
    return this.http.post<MemberInvitationDto>(
      `${appConfig.apiBaseUrl}/api/v1/projects/${encodeURIComponent(projectId)}/members`,
      {
        inviteeEmail: inviteeEmail.trim().toLowerCase(),
      },
    )
  }

  acceptInvitation(
    token: string,
  ): Observable<MemberInvitationDto> {
    return this.http.post<MemberInvitationDto>(
      `${appConfig.apiBaseUrl}/api/v1/project-members/accept`,
      { token },
    )
  }
  createDraft(value: ProjectCreateFormValue): Observable<CreatedDraftProject> {
    const request = toProjectCreateRequest(value)

    return this.http
      .post<ProjectCreateResponseDto>(
        `${appConfig.apiBaseUrl}${apiPaths.projects}`,
        request,
      )
      .pipe(map(toCreatedDraftProject))
  }

  update(
    projectId: string,
    value: ProjectUpdateFormValue,
  ): Observable<UpdatedProject> {
    return this.http
      .patch<ProjectUpdateResponseDto>(
        `${appConfig.apiBaseUrl}${apiPaths.projects}/${encodeURIComponent(projectId)}`,
        toProjectUpdateRequest(value),
      )
      .pipe(map(toUpdatedProject))
  }

  requestPublication(projectId: string): Observable<ProjectStatus> {
    return this.lifecycleAction(projectId, 'publication-request')
  }

  archive(projectId: string): Observable<ProjectStatus> {
    return this.lifecycleAction(projectId, 'archive')
  }

  private lifecycleAction(projectId: string, action: 'publication-request' | 'archive') {
    return this.http.post<{ projectId: string; status: ProjectStatus }>(
      `${appConfig.apiBaseUrl}${apiPaths.projects}/${encodeURIComponent(projectId)}/${action}`,
      {},
    ).pipe(map((response) => {
      if (!response || response.projectId !== projectId
        || !['DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED', 'SUSPENDED'].includes(response.status)) {
        throw new Error('Invalid project lifecycle response')
      }
      return response.status
    }))
  }
}

function toProjectCreateRequest(
  value: ProjectCreateFormValue,
): ProjectCreateRequestDto {
  const description = value.description.trim()

  return {
    slug: value.slug.trim().toLowerCase(),
    name: value.name.trim(),
    tagline: value.tagline.trim(),
    description: description.length > 0 ? description : null,
    sectorId: value.sectorId,
    city: value.city.trim(),
    stage: value.stage,
  }
}

function toCreatedDraftProject(
  dto: ProjectCreateResponseDto,
): CreatedDraftProject {
  return {
    project: {
      id: dto.project.id,
      slug: dto.project.slug,
      name: dto.project.name,
      tagline: dto.project.tagline,
      description: dto.project.description,
      sectorId: dto.project.sectorId,
      city: dto.project.city,
      stage: dto.project.stage as ProjectStage,
      status: dto.project.status as ProjectStatus,
    },
    owner: {
      id: dto.owner.id,
      projectId: dto.owner.projectId,
      profileId: dto.owner.profileId,
      role: dto.owner.role,
      membershipStatus: dto.owner.membershipStatus,
    },
  }
}

function toProjectUpdateRequest(
  value: ProjectUpdateFormValue,
): ProjectUpdateRequestDto {
  return {
    ...(value.name !== undefined ? { name: value.name.trim() } : {}),
    ...(value.tagline !== undefined ? { tagline: value.tagline.trim() } : {}),
    ...(value.description !== undefined
      ? { description: normalizeOptionalText(value.description) }
      : {}),
    ...(value.valueProposition !== undefined
      ? { valueProposition: normalizeOptionalText(value.valueProposition) }
      : {}),
    ...(value.stage !== undefined ? { stage: value.stage } : {}),
    ...(value.sectorId !== undefined ? { sectorId: value.sectorId } : {}),
    ...(value.city !== undefined
      ? { city: normalizeOptionalText(value.city) }
      : {}),
    ...(value.department !== undefined
      ? { department: normalizeOptionalText(value.department) }
      : {}),
  }
}

function normalizeOptionalText(value: string | null): string | null {
  return value === null ? null : value.trim()
}

function toUpdatedProject(dto: ProjectUpdateResponseDto): UpdatedProject {
  if (!dto?.project || !projectStatuses.includes(dto.project.status as ProjectStatus)) {
    throw new Error('Invalid project update response')
  }
  return {
    id: dto.project.id,
    slug: dto.project.slug,
    name: dto.project.name,
    tagline: dto.project.tagline,
    description: dto.project.description,
    valueProposition: dto.project.valueProposition,
    sectorId: dto.project.sectorId,
    stage: dto.project.stage as ProjectStage,
    city: dto.project.city,
    department: dto.project.department,
    status: dto.project.status as ProjectStatus,
    verificationStatus: dto.project.verificationStatus,
    createdByProfileId: dto.project.createdByProfileId,
    createdAt: dto.project.createdAt,
    updatedAt: dto.project.updatedAt,
  }
}
