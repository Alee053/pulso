import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import type { Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type { ConversationMessage, PageWindow } from '../domain/communication'

interface MessagePage {
  data: ConversationMessage[]
  meta: PageWindow
}

@Injectable({ providedIn: 'root' })
export class ConversationMessagesApi {
  private readonly http = inject(HttpClient)

  list(conversationId: string, offset: number): Observable<MessagePage> {
    return this.http.get<MessagePage>(this.url(conversationId), { params: { offset } })
  }

  send(conversationId: string, body: string): Observable<ConversationMessage> {
    return this.http.post<ConversationMessage>(this.url(conversationId), { body })
  }

  private url(conversationId: string): string {
    return `${appConfig.apiBaseUrl}/api/v1/conversations/${encodeURIComponent(conversationId)}/messages`
  }
}
