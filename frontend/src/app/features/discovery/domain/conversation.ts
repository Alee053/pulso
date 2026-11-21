export interface AcceptedConnectionConversation {
  connectionIntentId: string
  conversationId: string
  conversationType: string
  projectId: string
  status: string
  participants: Array<{ profileId: string; role: string }>
}

export interface ConversationMessage {
  id: string
  conversationId: string
  senderProfileId: string
  messageType: 'TEXT'
  status: 'SENT' | 'EDITED' | 'DELETED'
  body: string | null
  createdAt: string
  editedAt: string | null
  deletedAt: string | null
}

export interface ConversationMessagePage {
  data: ConversationMessage[]
  meta: { offset: number; limit: number; hasMore: boolean }
}
