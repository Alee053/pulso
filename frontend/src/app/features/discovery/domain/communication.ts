export interface PageWindow {
  offset: number
  limit: number
  hasMore: boolean
}

export interface ConversationMessage {
  id: string
  senderProfileId: string
  status: 'SENT' | 'EDITED' | 'DELETED'
  body: string | null
  createdAt: string
}

export interface InboxNotification {
  id: string
  type: string
  conversationId: string | null
  connectionIntentId: string | null
  payload: Record<string, string>
  read: boolean
  createdAt: string
}
