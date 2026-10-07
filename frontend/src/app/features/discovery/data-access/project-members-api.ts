import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { catchError, map, throwError, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import { membershipAdministrationError } from '../domain/membership-administration'
import { membershipRoles, membershipStatuses, type MembershipChange, type MembershipFailure,
  type MembershipRole, type MembershipStatus, type ProjectMember, type ProjectMembersPage,
  type UpdatedMembership } from '../domain/project-member'
import { teamStrings as strings } from '../strings/team.strings'

@Injectable({ providedIn: 'root' })
export class ProjectMembersApi {
  private readonly http = inject(HttpClient)

  list(projectId: string, offset = 0): Observable<ProjectMembersPage> {
    if (!Number.isInteger(offset) || offset < 0 || offset > 100000) {
      throw new RangeError('Invalid membership page offset')
    }
    return this.http.get<unknown>(this.path(projectId), {
      params: new HttpParams().set('offset', offset).set('limit', 20),
    }).pipe(
      map((value) => {
        const page = record(value)
        const meta = record(page['meta'])
        const actor = record(page['actor'])
        const data = page['data']
        if (!Array.isArray(data) || data.length > 20 || meta['offset'] !== offset || meta['limit'] !== 20
          || typeof meta['hasMore'] !== 'boolean' || !isRole(actor['role']) || actor['membershipStatus'] !== 'ACTIVE') {
          throw new Error('Invalid team response')
        }
        const members = data.map((row): ProjectMember => {
          const dto = record(row)
          const member = membership(dto, projectId)
          if (!nullableString(dto['displayName']) || !nullableString(dto['displayTitle'])) {
            throw new Error('Invalid public membership fields')
          }
          return { ...member, displayName: dto['displayName'], displayTitle: dto['displayTitle'] }
        })
        if (new Set(members.map((member) => member.id)).size !== members.length) {
          throw new Error('Duplicate memberships in response')
        }
        return { data: members, actor: { role: actor['role'], membershipStatus: 'ACTIVE' as const },
          meta: { offset, limit: 20, hasMore: meta['hasMore'] } }
      }),
      catchError((error: unknown) => throwError(() => failure(error, 'list'))),
    )
  }

  update(projectId: string, memberId: string, change: MembershipChange): Observable<UpdatedMembership> {
    if (!memberId) throw new RangeError('Membership identifier is required')
    const body = change.action === 'changeRole' ? { action: change.action, role: change.role } : { action: change.action }
    return this.http.patch<unknown>(`${this.path(projectId)}/${encodeURIComponent(memberId)}`, body).pipe(
      map((value) => {
        const result = membership(record(value), projectId)
        if (result.id !== memberId) throw new Error('Unexpected updated membership')
        return result
      }),
      catchError((error: unknown) => throwError(() => failure(error, 'update'))),
    )
  }

  private path(projectId: string): string {
    if (!projectId) throw new RangeError('Project identifier is required')
    return `${appConfig.apiBaseUrl}/api/v1/projects/${encodeURIComponent(projectId)}/members`
  }
}

function record(value: unknown): Record<string, unknown> {
  if (value == null || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid response')
  return value as Record<string, unknown>
}

function isRole(value: unknown): value is MembershipRole {
  return membershipRoles.some((role) => role === value)
}

function nullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string'
}

function isStatus(value: unknown): value is MembershipStatus {
  return membershipStatuses.some((status) => status === value)
}

function membership(dto: Record<string, unknown>, projectId: string): UpdatedMembership {
  if (typeof dto['id'] !== 'string' || !dto['id'] || dto['projectId'] !== projectId
    || !nullableString(dto['profileId']) || !isRole(dto['role']) || !isStatus(dto['membershipStatus'])) {
    throw new Error('Invalid membership response')
  }
  return { id: dto['id'], projectId, profileId: dto['profileId'], role: dto['role'],
    membershipStatus: dto['membershipStatus'] }
}

function failure(error: unknown, operation: 'list' | 'update'): MembershipFailure {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 401) return { message: strings.sessionExpired, refreshPermissions: true }
    const problem = error.error as { code?: unknown; detail?: unknown } | null
    const code = problem && typeof problem === 'object' ? problem.code : undefined
    if (error.status === 403) {
      return { message: operation === 'update' && code === 'membership_permission'
        ? membershipAdministrationError('permission', typeof problem?.detail === 'string' ? problem.detail : undefined).message
        : strings.permission, refreshPermissions: true }
    }
    if (error.status === 409) {
      const message = code === 'membership_owner_required' ? membershipAdministrationError('ownerInvariant').message
        : code === 'membership_invalid_transition' ? membershipAdministrationError('invalidTransition').message
        : strings.conflict
      return { message, refreshPermissions: true }
    }
    if (error.status === 400) return { message: strings.invalidRequest, refreshPermissions: false }
  }
  return { message: operation === 'list' ? strings.failed : strings.changeFailed, refreshPermissions: false }
}
