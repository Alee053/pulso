import { HttpClient, HttpParams } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { catchError, map, throwError, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type { CurrentProjectsPage } from '../domain/member-invitation'
import { invitationStrings } from '../strings/invitation.strings'

@Injectable({ providedIn: 'root' })
export class CurrentProjectsApi {
  private readonly http = inject(HttpClient)

  list(offset = 0): Observable<CurrentProjectsPage> {
    if (!Number.isInteger(offset) || offset < 0 || offset > 100000) {
      throw new RangeError('Invalid project page offset')
    }
    return this.http.get<CurrentProjectsPage>(`${appConfig.apiBaseUrl}/api/v1/me/projects`, {
      params: new HttpParams().set('offset', offset).set('limit', 20),
    }).pipe(
      map((page) => {
        if (!page || !Array.isArray(page.data) || !page.meta || page.meta.offset !== offset
          || page.meta.limit !== 20 || typeof page.meta.hasMore !== 'boolean' || page.data.length > 20
          || page.data.some((project) => !project || typeof project.projectId !== 'string' || !project.projectId
            || typeof project.name !== 'string' || !['OWNER', 'ADMIN', 'MEMBER'].includes(project.role)
            || project.membershipStatus !== 'ACTIVE')) {
          throw new Error('Invalid current projects response')
        }
        return {
          data: page.data.map((project) => ({ projectId: project.projectId, name: project.name,
            role: project.role, membershipStatus: project.membershipStatus })),
          meta: { offset: page.meta.offset, limit: page.meta.limit, hasMore: page.meta.hasMore },
        }
      }),
      catchError(() => throwError(() => ({ message: invitationStrings.teamsFailed }))),
    )
  }
}
