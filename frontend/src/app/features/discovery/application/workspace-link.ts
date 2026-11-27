import { requestInboxHref, type RequestInbox } from '../domain/connection'

export type WorkspaceLink =
  | { kind: 'project'; reference: string }
  | { kind: 'messages' }
  | { kind: 'conversation'; id: string }
  | { kind: 'requests'; inbox: RequestInbox }

export function readWorkspaceLink(pathname: string): WorkspaceLink | null {
  const path = pathname.replace(/\/$/, '')
  const project = /^\/proyectos\/([^/]+)$/.exec(path)
  if (project) {
    try {
      return { kind: 'project', reference: decodeURIComponent(project[1]) }
    } catch {
      return null
    }
  }

  if (path === '/conversaciones') return { kind: 'messages' }
  const conversation = /^\/conversaciones\/([0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})$/i.exec(path)
  if (conversation) return { kind: 'conversation', id: conversation[1] }
  if (path === requestInboxHref('received')) return { kind: 'requests', inbox: 'received' }
  if (path === requestInboxHref('sent')) return { kind: 'requests', inbox: 'sent' }
  return null
}
