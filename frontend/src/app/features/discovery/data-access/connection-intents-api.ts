import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import type { Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type { ConnectionIntent } from '../domain/startup'
import { connectionInboxContract } from './discovery.catalog.contract'

@Injectable({ providedIn: 'root' })
export class ConnectionIntentsApi {
  private readonly http = inject(HttpClient)

  getPendingIntents(offset: number): Observable<ConnectionIntent[]> {
    return this.http.get<ConnectionIntent[]>(`${appConfig.apiBaseUrl}/api/v1/connection-intents/pending`, {
      params: { offset, limit: connectionInboxContract.pageSize + 1 },
    })
  }

  acceptIntent(id: string): Observable<unknown> {
    return this.http.post(`${appConfig.apiBaseUrl}/api/v1/connection-intents/${id}/accept`, {})
  }

  declineIntent(id: string): Observable<unknown> {
    return this.http.post(`${appConfig.apiBaseUrl}/api/v1/connection-intents/${id}/decline`, {})
  }

  withdrawIntent(id: string): Observable<unknown> {
    return this.http.post(`${appConfig.apiBaseUrl}/api/v1/connection-intents/${id}/withdraw`, {})
  }
}
