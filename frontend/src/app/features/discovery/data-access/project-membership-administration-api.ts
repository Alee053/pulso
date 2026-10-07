import { HttpClient, HttpParams } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type {
  ManagedProjectMember,
  ManagedProjectMembersPage,
  MembershipMutationResult,
  ProjectMembershipRole,
  ProjectMembershipStatus,
} from '../domain/project-membership-administration'

const PAGE_SIZE = 20
const MAX_OFFSET = 100000
const roles: ProjectMembershipRole[] = ['OWNER', 'ADMIN', 'MEMBER']
const statuses: ProjectMembershipStatus[] = ['INVITED', 'ACTIVE', 'LEFT', 'REMOVED']

@Injectable({ providedIn: 'root' })
export class ProjectMembershipAdministrationApi {
  private readonly http = inject(HttpClient)

  list(projectId: string, offset = 0): Observable<ManagedProjectMembersPage> {
    if (!projectId || !Number.isInteger(offset) || offset < 0 || offset > MAX_OFFSET) {
      throw new RangeError('Invalid project membership page')
    }
    return this.http.get<ManagedProjectMembersPage>(
      `${appConfig.apiBaseUrl}/api/v1/projects/${encodeURIComponent(projectId)}/members`,
      { params: new HttpParams().set('offset', offset).set('limit', PAGE_SIZE) },
    ).pipe(map((page) => validatePage(page, offset)))
  }

  administer(
    projectId: string,
    memberId: string,
    action: 'activate' | 'withdraw' | 'changeRole',
    role?: ProjectMembershipRole,
  ): Observable<MembershipMutationResult> {
    if (!projectId || !memberId || (action === 'changeRole'
      && (role === undefined || !roles.includes(role)))) {
      throw new RangeError('Invalid membership action')
    }
    const body = action === 'changeRole' ? { action, role } : { action }
    return this.http.patch<MembershipMutationResult>(
      `${appConfig.apiBaseUrl}/api/v1/projects/${encodeURIComponent(projectId)}/members/${encodeURIComponent(memberId)}`,
      body,
    ).pipe(map((member) => validateMutation(member, projectId, memberId)))
  }
}

function validatePage(page: ManagedProjectMembersPage, offset: number): ManagedProjectMembersPage {
  if (!page || !Array.isArray(page.data) || page.data.length > PAGE_SIZE || !page.meta
    || page.meta.offset !== offset || page.meta.limit !== PAGE_SIZE
    || typeof page.meta.hasMore !== 'boolean'
    || page.data.some((member) => !isManagedMember(member))) {
    throw new Error('Invalid project membership response')
  }
  return {
    data: page.data.map((member) => ({
      id: member.id,
      projectId: member.projectId,
      profileId: member.profileId,
      displayName: member.displayName,
      inviteeEmail: member.membershipStatus === 'INVITED' ? member.inviteeEmail : null,
      role: member.role,
      membershipStatus: member.membershipStatus,
    })),
    meta: { offset: page.meta.offset, limit: page.meta.limit, hasMore: page.meta.hasMore },
  }
}

function isManagedMember(member: ManagedProjectMember): boolean {
  return !!member && typeof member.id === 'string' && !!member.id
    && typeof member.projectId === 'string' && !!member.projectId
    && (member.profileId === null || typeof member.profileId === 'string')
    && (member.displayName === null || typeof member.displayName === 'string')
    && (member.inviteeEmail === null || typeof member.inviteeEmail === 'string')
    && roles.includes(member.role) && statuses.includes(member.membershipStatus)
}

function validateMutation(
  member: MembershipMutationResult,
  projectId: string,
  memberId: string,
): MembershipMutationResult {
  if (!member || member.id !== memberId || member.projectId !== projectId
    || (member.profileId !== null && typeof member.profileId !== 'string')
    || !roles.includes(member.role) || !statuses.includes(member.membershipStatus)) {
    throw new Error('Invalid membership update response')
  }
  return {
    id: member.id,
    projectId: member.projectId,
    profileId: member.profileId,
    role: member.role,
    membershipStatus: member.membershipStatus,
  }
}
