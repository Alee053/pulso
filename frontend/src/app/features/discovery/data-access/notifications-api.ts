import { HttpClient, HttpParams } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import type { Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type { InboxNotification, PageWindow } from '../domain/communication'

export type NotificationFilter = 'ALL' | 'UNREAD'

interface NotificationPage {
  data: InboxNotification[]
  meta: PageWindow & { unreadCount: number }
}

@Injectable({ providedIn: 'root' })
export class NotificationsApi {
  private readonly http = inject(HttpClient)
  private readonly url = `${appConfig.apiBaseUrl}/api/v1/me/notifications`

  list(offset = 0, limit?: number, filter: NotificationFilter = 'ALL'): Observable<NotificationPage> {
    let params = new HttpParams().set('offset', offset)
    if (filter !== 'ALL') params = params.set('status', filter)
    if (limit !== undefined) params = params.set('limit', limit)
    return this.http.get<NotificationPage>(this.url, { params })
  }

  markRead(id: string): Observable<InboxNotification> {
    return this.http.post<InboxNotification>(`${this.url}/${encodeURIComponent(id)}/read`, {})
  }
}
