import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type {
  AcceptedConnection, ConnectionInboxEntry, ConnectionIntent, ConnectionReason, RequestInbox,
} from '../domain/connection'
import { connectionInboxContract } from './discovery.catalog.contract'

@Injectable({ providedIn: 'root' })
export class ConnectionIntentsApi {
  private readonly http = inject(HttpClient)
  private readonly base = `${appConfig.apiBaseUrl}/api/v1`

  list(inbox: RequestInbox, offset: number): Observable<{ entries: ConnectionInboxEntry[]; hasMore: boolean }> {
    const { pageSize } = connectionInboxContract
    return this.http.get<ConnectionInboxEntry[]>(`${this.base}/connection-intents/${inbox}`, {
      params: { offset, limit: pageSize + 1 },
    }).pipe(map((entries) => ({ entries: entries.slice(0, pageSize), hasMore: entries.length > pageSize })))
  }

  create(
    projectId: string, reason: ConnectionReason, message: string | null, opportunityId: string | null,
  ): Observable<ConnectionIntent> {
    return this.http.post<ConnectionIntent>(
      `${this.base}/projects/${encodeURIComponent(projectId)}/connection-intents`,
      { reason, message, opportunityId },
    )
  }

  acceptIntent(id: string): Observable<AcceptedConnection> {
    return this.respond<AcceptedConnection>(id, 'accept')
  }

  declineIntent(id: string): Observable<ConnectionIntent> {
    return this.respond<ConnectionIntent>(id, 'decline')
  }

  withdrawIntent(id: string): Observable<ConnectionIntent> {
    return this.respond<ConnectionIntent>(id, 'withdraw')
  }

  private respond<T>(id: string, action: 'accept' | 'decline' | 'withdraw'): Observable<T> {
    return this.http.post<T>(`${this.base}/connection-intents/${encodeURIComponent(id)}/${action}`, {})
  }
}
