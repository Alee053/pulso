import { HttpClient, HttpErrorResponse } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { catchError, map, throwError, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type { InvitationFailure, MembershipResult } from '../domain/member-invitation'
import { invitationStrings as strings } from '../strings/invitation.strings'

interface MembershipResponseDto {
  projectId: string
  role: string
  membershipStatus: string
}

@Injectable({ providedIn: 'root' })
export class MemberInvitationsApi {
  private readonly http = inject(HttpClient)

  invite(projectId: string, email: string): Observable<MembershipResult> {
    return this.http.post<MembershipResponseDto>(
      `${appConfig.apiBaseUrl}/api/v1/projects/${encodeURIComponent(projectId)}/members`,
      { inviteeEmail: email.trim().toLowerCase() },
    ).pipe(
      map((response) => toMembershipResult(response, 'INVITED')),
      catchError((error: unknown) => throwError(() => invitationFailure(error, 'invite'))),
    )
  }

  accept(token: string): Observable<MembershipResult> {
    return this.http.post<MembershipResponseDto>(
      `${appConfig.apiBaseUrl}/api/v1/project-members/accept`, { token },
    ).pipe(
      map((response) => toMembershipResult(response, 'ACTIVE')),
      catchError((error: unknown) => throwError(() => invitationFailure(error, 'accept'))),
    )
  }
}

function toMembershipResult(dto: MembershipResponseDto, status: MembershipResult['membershipStatus']): MembershipResult {
  if (!dto || typeof dto.projectId !== 'string' || !dto.projectId || dto.role !== 'MEMBER' || dto.membershipStatus !== status) {
    throw new Error('Invalid membership response')
  }
  return { projectId: dto.projectId, membershipStatus: status }
}

function invitationFailure(error: unknown, operation: 'invite' | 'accept'): InvitationFailure {
  const status = error instanceof HttpErrorResponse ? error.status : 0
  if (status === 401) return { message: strings.sessionExpired, retryable: true }
  if (operation === 'accept') {
    if (status === 400) return { message: strings.invalid, retryable: false }
    if (status === 404 || status === 403 || status === 409) return { message: strings.unavailable, retryable: false }
    return { message: strings.acceptFailed, retryable: true }
  }
  const messages: Record<number, string> = {
    400: strings.emailRejected, 403: strings.permission,
    404: strings.projectUnavailable, 409: strings.duplicate,
  }
  return { message: messages[status] ?? strings.inviteFailed, retryable: !messages[status] }
}
