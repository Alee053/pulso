export const connectionReasons = [
  'COLLABORATE', 'WORK', 'TRY_PRODUCT', 'BUY', 'INSTITUTION', 'INVEST', 'OFFER_SERVICE', 'OTHER',
] as const

export type ConnectionReason = typeof connectionReasons[number]
export type ConnectionStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'WITHDRAWN' | 'EXPIRED' | 'CONVERTED'
export type RequestInbox = 'received' | 'sent'

export interface ConnectionIntent {
  id: string
  senderProfileId: string
  projectId: string
  opportunityId: string | null
  status: ConnectionStatus
  reason: ConnectionReason
  message: string | null
  createdAt: string
}

export interface ConnectionInboxEntry {
  request: ConnectionIntent
  projectName: string | null
  opportunityTitle: string | null
  conversationId: string | null
}

export interface AcceptedConnection {
  connectionIntentId: string
  conversationId: string
}

export function conversationHref(id: string): string {
  return `/conversaciones/${encodeURIComponent(id)}`
}

export function requestInboxHref(inbox: RequestInbox): string {
  return inbox === 'sent' ? '/solicitudes/enviadas' : '/solicitudes/recibidas'
}
