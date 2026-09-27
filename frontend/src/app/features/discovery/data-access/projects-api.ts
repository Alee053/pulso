import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type {
  CreatedDraftProject,
  ProjectCreateFormValue,
  ProjectStage,
} from '../domain/project'
import type {
  MemberInvitationDto,
  ProjectCreateRequestDto,
  ProjectCreateResponseDto,
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
      status: dto.project.status,
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