import { HttpClient, HttpParams } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type { ConversationMessage, ConversationMessagePage } from '../domain/conversation'

const pageSize = 20

@Injectable({ providedIn: 'root' })
export class ConversationMessagesApi {
  private readonly http = inject(HttpClient)

  list(conversationId: string, offset = 0): Observable<ConversationMessagePage> {
    if (!Number.isInteger(offset) || offset < 0 || offset > 100000) throw new RangeError('Invalid message offset')
    const params = new HttpParams().set('offset', offset).set('limit', pageSize)
    return this.http.get<{ data: unknown[]; meta: { offset: number; limit: number; hasMore: boolean } }>(
      `${appConfig.apiBaseUrl}/api/v1/conversations/${encodeURIComponent(conversationId)}/messages`, { params },
    ).pipe(map((page) => {
      if (!page || !Array.isArray(page.data) || !page.meta || page.meta.offset !== offset
        || page.meta.limit !== pageSize || typeof page.meta.hasMore !== 'boolean') {
        throw new Error('Invalid message page response')
      }
      return {
        data: page.data.map((message) => parseMessage(message, conversationId)),
        meta: { offset, limit: pageSize, hasMore: page.meta.hasMore },
      }
    }))
  }

  send(conversationId: string, body: string): Observable<ConversationMessage> {
    return this.http.post<unknown>(
      `${appConfig.apiBaseUrl}/api/v1/conversations/${encodeURIComponent(conversationId)}/messages`,
      { body: body.trim() },
    ).pipe(map((message) => parseMessage(message, conversationId)))
  }
}

function parseMessage(value: unknown, conversationId: string): ConversationMessage {
  if (!value || typeof value !== 'object') throw new Error('Invalid message response')
  const message = value as Partial<ConversationMessage>
  if (typeof message.id !== 'string' || !message.id || message.conversationId !== conversationId
    || typeof message.senderProfileId !== 'string' || message.messageType !== 'TEXT'
    || !['SENT', 'EDITED', 'DELETED'].includes(message.status ?? '')
    || (message.body !== null && typeof message.body !== 'string')
    || typeof message.createdAt !== 'string'
    || (message.editedAt !== null && typeof message.editedAt !== 'string')
    || (message.deletedAt !== null && typeof message.deletedAt !== 'string')) {
    throw new Error('Invalid message response')
  }
  return {
    id: message.id,
    conversationId: message.conversationId,
    senderProfileId: message.senderProfileId,
    messageType: 'TEXT',
    status: message.status as ConversationMessage['status'],
    body: message.body ?? null,
    createdAt: message.createdAt,
    editedAt: message.editedAt ?? null,
    deletedAt: message.deletedAt ?? null,
  }
}
